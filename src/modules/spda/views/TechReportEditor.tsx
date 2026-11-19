/**
 * Edição do relatório técnico completo (menu Relatório): caixa de texto de todos os campos narrativos,
 * na ordem dos capítulos, e seleção do resultado de cada subsistema e do parecer final
 * (Aprovado / Reprovado / Atende parcialmente; sem escolha = calculado pela inspeção).
 */
import React from 'react';
import { CheckCircle2, CircleSlash, RotateCcw, Wand2, XCircle } from 'lucide-react';
import { saveInspection } from '../repository';
import { DEFAULT_VALIDITY_MONTHS, VALIDITY_OPTIONS, buildingsOf, inspectionVerdict, listNonConformities, nextInspectionDate, validityText, type ContinuityGroup } from '../rules';
import { formatDateBR } from '../../../utils/dateUtils';
import type { ReportResult, SpdaInspection } from '../types';
import {
  RESULT_LABEL, RESULT_OPTION_LABEL, TECH_TEXT_LABEL, defaultSubsystemResultText, finalResult, resultFromVerdict,
  subsystemKey, subsystemResult, techText, type ShownResult, type TechTextKey
} from '../techReport';
import { describeSubsystems } from '../systemDescriptions';
import { ContinuityDiagram, FallOfPotentialDiagram } from './MethodDiagrams';
import { groupData } from './TechReport';
import { alertError, btnPrimary, btnSecondary, cardCls, inputCls } from './ui';

const GROUPS: ContinuityGroup[] = ['captacao', 'descida', 'aterramento', 'dps'];
const GROUP_TITLE: Record<ContinuityGroup, string> = { captacao: 'Captação', descida: 'Descida', aterramento: 'Aterramento', dps: 'Equipotencialização e DPS' };

const OPTION_STYLE: Record<ReportResult, { on: string; icon: React.ElementType }> = {
  aprovado: { on: 'bg-emerald-600 border-emerald-600 text-white', icon: CheckCircle2 },
  reprovado: { on: 'bg-red-600 border-red-600 text-white', icon: XCircle },
  parcial: { on: 'bg-amber-500 border-amber-500 text-white', icon: CircleSlash }
};
const SHOWN_PILL: Record<ShownResult, string> = {
  aprovado: 'bg-emerald-50 text-emerald-800 border-emerald-300', reprovado: 'bg-red-50 text-red-700 border-red-300',
  parcial: 'bg-amber-50 text-amber-800 border-amber-300', incompleto: 'bg-slate-50 text-slate-600 border-slate-300'
};

/** Botões Aprovado / Reprovado / Atende parcialmente + Automático (calculado). */
export const ResultPicker: React.FC<{
  label: string; chosen?: ReportResult; computed: ShownResult; readOnly: boolean; onChange: (r: ReportResult | undefined) => void;
}> = ({ label, chosen, computed, readOnly, onChange }) => (
  <div className="flex flex-wrap items-center gap-1.5" role="radiogroup" aria-label={label}>
    {(Object.keys(RESULT_OPTION_LABEL) as ReportResult[]).map(r => {
      const on = chosen === r;
      const I = OPTION_STYLE[r].icon;
      return (
        <button key={r} type="button" role="radio" aria-checked={on} disabled={readOnly} onClick={() => onChange(r)}
          className={`inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg border text-[11px] font-bold disabled:opacity-60 ${on ? OPTION_STYLE[r].on : 'bg-white border-slate-300 text-slate-700 hover:bg-slate-50'}`}>
          <I className="w-3.5 h-3.5" /> {RESULT_OPTION_LABEL[r]}
        </button>
      );
    })}
    <button type="button" role="radio" aria-checked={!chosen} disabled={readOnly} onClick={() => onChange(undefined)}
      className={`inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg border text-[11px] font-bold disabled:opacity-60 ${!chosen ? 'bg-[#0A2540] border-[#0A2540] text-white' : 'bg-white border-slate-300 text-slate-700 hover:bg-slate-50'}`}
      title="Usa o resultado calculado pelos itens e ensaios da inspeção">
      <Wand2 className="w-3.5 h-3.5" /> Automático
    </button>
    <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full border ${SHOWN_PILL[computed]}`}>calculado: {RESULT_LABEL[computed].toLowerCase()}</span>
  </div>
);

/** Caixa de texto com padrão automático e "Restaurar texto padrão". */
const TextBox: React.FC<{ id: string; label: string; value?: string; fallback: string; rows?: number; readOnly: boolean; onChange: (s: string) => void; children?: React.ReactNode }> = ({ id, label, value, fallback, rows = 4, readOnly, onChange, children }) => {
  const custom = value?.trim();
  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-2 mb-1">
        <label htmlFor={id} className="font-bold text-slate-700 text-xs">{label}</label>
        <span className="flex items-center gap-2">
          <span className="text-[10px] text-slate-400">{custom ? 'texto editado' : 'texto padrão'}</span>
          {custom && !readOnly && <button type="button" className={btnSecondary} onClick={() => onChange('')}><RotateCcw className="w-3.5 h-3.5" /> Restaurar texto padrão</button>}
        </span>
      </div>
      <textarea id={id} className={inputCls} rows={rows} disabled={readOnly} value={custom ? value : fallback} onChange={e => onChange(e.target.value)} />
      {children}
    </div>
  );
};

const Chapter: React.FC<{ n: string; title: string; children: React.ReactNode }> = ({ n, title, children }) => (
  <section className={`${cardCls} p-4 space-y-3`}>
    <h3 className="font-bold text-sm text-slate-900"><span className="text-amber-600 mr-1.5">{n}</span>{title}</h3>
    {children}
  </section>
);

export const TechReportEditor: React.FC<{ inspection: SpdaInspection; readOnly: boolean; onGenerate: () => void }> = ({ inspection: v, readOnly, onGenerate }) => {
  const bs = buildingsOf(v);
  const multi = bs.length > 1;
  const ncsAll = listNonConformities(v);
  const tr = v.techReport || {};

  const save = (next: SpdaInspection) => {
    try { saveInspection(next); } catch (err) { alertError(err, 'Não foi possível salvar'); }
  };
  const setText = (key: TechTextKey, value: string) => save({ ...v, techReport: { ...tr, [key]: value } });
  const setSub = (key: string, value: string) => save({ ...v, techReport: { ...tr, subsystemTexts: { ...tr.subsystemTexts, [key]: value } } });
  const setResult = (key: string, r: ReportResult | undefined) => {
    const results = { ...tr.results };
    if (r) results[key] = r; else delete results[key];
    save({ ...v, techReport: { ...tr, results } });
  };

  const text = (k: TechTextKey, rows = 4, children?: React.ReactNode) => (
    <TextBox key={k} id={`tr_${k}`} label={TECH_TEXT_LABEL[k]} value={tr[k]} fallback={techText({ ...v, techReport: { ...tr, [k]: '' } }, k)} rows={rows} readOnly={readOnly} onChange={s => setText(k, s)}>{children}</TextBox>
  );
  const figure = (node: React.ReactNode, cap: string) => (
    <figure className="mt-2 max-w-xl border border-slate-200 rounded-lg bg-white p-2">{node}<figcaption className="text-[10px] text-slate-500 mt-1">{cap} — esquema ilustrativo que sai no relatório junto com este texto.</figcaption></figure>
  );
  const parts = (bs.length ? bs : [undefined]);
  const finalComputed = resultFromVerdict(inspectionVerdict(v));

  return (
    <div className="space-y-3">
      <div className={`${cardCls} p-4`}>
        <h3 className="font-bold text-sm text-slate-900">Edição do relatório técnico completo</h3>
        <p className="text-[11px] text-slate-500 mt-1">
          Todos os textos abaixo saem no relatório; vazio volta ao texto padrão gerado pela inspeção. O resultado de cada subsistema e o parecer final
          podem ser escolhidos (Aprovado, Reprovado ou Atende parcialmente) — em <b>Automático</b>, vale o calculado pelos itens e ensaios.
        </p>
      </div>

      <section className={`${cardCls} p-4 space-y-2`} aria-label="Validade do laudo">
        <h3 className="font-bold text-sm text-slate-900">Validade do laudo</h3>
        <div className="flex flex-wrap items-center gap-1.5" role="radiogroup" aria-label="Validade do laudo">
          {VALIDITY_OPTIONS.map(m => {
            const on = v.validityMonths === m;
            return (
              <button key={m} type="button" role="radio" aria-checked={on} disabled={readOnly}
                onClick={() => save({ ...v, validityMonths: m })}
                className={`px-3 py-1.5 rounded-lg border text-xs font-bold disabled:opacity-60 ${on ? 'bg-[#0A2540] border-[#0A2540] text-white' : 'bg-white border-slate-300 text-slate-700 hover:bg-slate-50'}`}>
                {m} meses
              </button>
            );
          })}
          <button type="button" role="radio" aria-checked={!v.validityMonths} disabled={readOnly}
            onClick={() => { const { validityMonths: _omit, ...rest } = v; save(rest as SpdaInspection); }}
            className={`px-3 py-1.5 rounded-lg border text-xs font-bold disabled:opacity-60 ${!v.validityMonths ? 'bg-[#0A2540] border-[#0A2540] text-white' : 'bg-white border-slate-300 text-slate-700 hover:bg-slate-50'}`}
            title="Sem escolha, o laudo vale 12 meses">
            Automático ({DEFAULT_VALIDITY_MONTHS} meses)
          </button>
        </div>
        <p className="text-[11px] text-slate-600">
          Validade: <b>{validityText(v)}</b> · próxima inspeção até <b>{formatDateBR(nextInspectionDate(v)) || '—'}</b>.
          <span className="text-slate-500"> Vale para o laudo, o relatório técnico, as recomendações e o Dashboard.</span>
        </p>
      </section>

      <Chapter n="1" title="Resumo executivo">{text('resumo', 4)}</Chapter>
      <Chapter n="2" title="Objetivo e escopo">{text('introducao', 6)}</Chapter>

      <Chapter n="5" title="Descrição dos subsistemas evidenciados na inspeção">
        {parts.map(b => b && (
          <div key={b.id} className="space-y-3">
            {multi && <p className="text-xs font-black text-teal-800">{b.name || 'Edificação'}</p>}
            {describeSubsystems(v, b).filter(sd => !sd.empty || groupData(v, b, sd.key, 1, ncsAll).hasData).map(sd => {
              const key = `desc_${subsystemKey(b.id, sd.key)}`;
              return <TextBox key={key} id={`tr_${key}`} label={`${sd.title}${multi ? ` — ${b.name}` : ''}`} value={tr.subsystemTexts?.[key]} fallback={sd.intro} rows={3} readOnly={readOnly} onChange={s => setSub(key, s)} />;
            })}
          </div>
        ))}
        <p className="text-[10.5px] text-slate-500">Subsistemas sem tipo selecionado e sem itens avaliados ou medições não aparecem no relatório. A descrição técnica de cada tipo selecionado, a ilustração e as fotos vêm da seleção feita nas telas de Captação, Descida e Aterramento.</p>
      </Chapter>

      <Chapter n="6" title="Metodologia">
        {text('metodoInspecao', 4)}
        {text('metodoContinuidade', 5, figure(<ContinuityDiagram target="descida" />, 'Figura 1'))}
        {text('metodoContinuidadeAterramento', 3, figure(<ContinuityDiagram target="aterramento" />, 'Figura 2'))}
        {text('metodoAterramento', 5, figure(<FallOfPotentialDiagram step={v.grounding.some(g => g.probeStepPct === 5) ? 5 : 10} />, 'Figura 3'))}
      </Chapter>

      <Chapter n="7" title="Resultado de cada subsistema (folhas de ensaio)">
        {parts.map(b => (
          <div key={b?.id || 'geral'} className="space-y-3">
            {multi && b && <p className="text-xs font-black text-teal-800">{b.name || 'Edificação'}</p>}
            {GROUPS.map(g => ({ g, d: groupData(v, b, g, 1, ncsAll) })).filter(x => x.d.hasData).map(({ g, d }) => {
              const key = subsystemKey(b?.id, g);
              const chosen = tr.results?.[key];
              const shown = subsystemResult(v, b?.id, g, d.verdict);
              return (
                <div key={key} className="border border-slate-200 rounded-xl p-3 space-y-2">
                  <p className="text-xs font-bold text-slate-800">{GROUP_TITLE[g]}{multi && b ? ` — ${b.name}` : ''} <span className="font-normal text-slate-500">· {d.items.length} item(ns) · {d.tests.length} ensaio(s) · {d.ncs.length} NC</span></p>
                  <ResultPicker label={`Resultado — ${GROUP_TITLE[g]}`} chosen={chosen} computed={resultFromVerdict(d.verdict)} readOnly={readOnly} onChange={r => setResult(key, r)} />
                  <TextBox id={`tr_res_${key}`} label="Texto do resultado" value={tr.subsystemTexts?.[`res_${key}`]} fallback={defaultSubsystemResultText(g, shown, d.ncs.length)} rows={2} readOnly={readOnly} onChange={s => setSub(`res_${key}`, s)} />
                </div>
              );
            })}
          </div>
        ))}
      </Chapter>

      <Chapter n="8" title="Recomendações">{text('recomendacoes', 4)}</Chapter>

      <Chapter n="9" title="Conclusão e parecer técnico">
        <div className="border border-slate-200 rounded-xl p-3 space-y-2">
          <p className="text-xs font-bold text-slate-800">Parecer final do SPDA <span className={`ml-1 text-[10px] font-bold px-2 py-0.5 rounded-full border ${SHOWN_PILL[finalResult(v)]}`}>no relatório: {RESULT_LABEL[finalResult(v)].toLowerCase()}</span></p>
          <ResultPicker label="Parecer final" chosen={tr.results?.final} computed={finalComputed} readOnly={readOnly} onChange={r => setResult('final', r)} />
        </div>
        {text('conclusao', 5)}
      </Chapter>

      <div className="flex justify-end">
        <button type="button" className={btnPrimary} onClick={onGenerate}>Gerar relatório técnico completo</button>
      </div>
    </div>
  );
};
