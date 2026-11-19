/**
 * Passo a passo da nova inspeção: Dados → Captação → Descida → Aterramento → DPS → Conclusão e laudo.
 * Começa ao clicar em "Nova inspeção"; a barra de etapas aparece no topo de cada tela da sequência
 * enquanto a inspeção guiada for a que está aberta. Pode ser encerrado a qualquer momento.
 */
import React, { useEffect, useState } from 'react';
import { ArrowLeft, ArrowRight, CheckCircle2, ListChecks, Save, X } from 'lucide-react';
import { getInspection } from '../repository';
import { navigateTo, openInspectionOnNextVisit } from './navigation';
import { preselectInspection } from './InspectionPicker';
import { btnPrimary, btnSecondary } from './ui';
import { notify } from './dialogs';

export type GuidedStep = 'dados' | 'captacao' | 'descida' | 'aterramento' | 'dps' | 'laudo';

export const GUIDED_STEPS: Array<{ id: GuidedStep; label: string; view: string }> = [
  { id: 'dados', label: 'Dados', view: 'spda' },
  { id: 'captacao', label: 'Captação', view: 'spda_captacao' },
  { id: 'descida', label: 'Descida', view: 'spda_descida' },
  { id: 'aterramento', label: 'Aterramento', view: 'spda_aterramento' },
  { id: 'dps', label: 'DPS', view: 'spda_dps' },
  { id: 'laudo', label: 'Conclusão e laudo', view: 'spda' }
];

const KEY = 'jvm_spda_guided';
const EVT = 'jvm-guided';
let guidedId: string | null = (() => { try { return localStorage.getItem(KEY); } catch { return null; } })();
/** Aba a abrir no editor da inspeção na próxima visita (dados ou conclusão). */
let pendingTab: 'dados' | 'conclusao' | null = null;

function setGuided(id: string | null) {
  guidedId = id;
  try { if (id) localStorage.setItem(KEY, id); else localStorage.removeItem(KEY); } catch { /* sem armazenamento */ }
  window.dispatchEvent(new Event(EVT));
}

export const startGuided = (inspectionId: string) => setGuided(inspectionId);
export const stopGuided = () => setGuided(null);
export const takePendingTab = () => { const t = pendingTab; pendingTab = null; return t; };

/** Inspeção do passo a passo (se ainda existir). */
export function useGuidedId(): string | null {
  const [, setV] = useState(0);
  useEffect(() => {
    const on = () => setV(v => v + 1);
    window.addEventListener(EVT, on);
    return () => window.removeEventListener(EVT, on);
  }, []);
  return guidedId && getInspection(guidedId) ? guidedId : null;
}

/** Vai para uma etapa (troca de tela; Dados e Conclusão abrem o editor na aba certa). */
export function goGuidedStep(step: GuidedStep, inspectionId: string, onEditorTab?: (tab: 'dados' | 'conclusao') => void) {
  const s = GUIDED_STEPS.find(x => x.id === step)!;
  if (s.view === 'spda') {
    const tab = step === 'laudo' ? 'conclusao' : 'dados';
    if (onEditorTab) { onEditorTab(tab); return; }
    pendingTab = tab;
    openInspectionOnNextVisit(inspectionId);
  } else {
    preselectInspection(inspectionId);
  }
  navigateTo(s.view);
  window.scrollTo(0, 0);
}

type NavProps = {
  inspectionId: string; current: GuidedStep;
  onEditorTab?: (tab: 'dados' | 'conclusao') => void; beforeLeave?: () => void;
  /** Quando informado, impede sair da etapa e mostra este aviso (ex.: edificação em edição) */
  blockedMsg?: string;
  /** Depois de Salvar na última etapa (ex.: abrir o relatório simplificado dos ensaios) */
  onFinish?: () => void;
};

/** Navegação entre etapas (compartilhada pela barra do topo e pelo rodapé). */
function useGuidedNav({ inspectionId, current, onEditorTab, beforeLeave, blockedMsg, onFinish }: NavProps) {
  const idx = GUIDED_STEPS.findIndex(s => s.id === current);
  const go = (step: GuidedStep) => {
    if (blockedMsg) { void notify(blockedMsg); return; }
    beforeLeave?.();
    const s = GUIDED_STEPS.find(x => x.id === step)!;
    const sameEditor = s.view === 'spda' && GUIDED_STEPS[idx].view === 'spda';
    goGuidedStep(step, inspectionId, sameEditor ? onEditorTab : undefined);
  };
  // Salvar (última etapa): grava, encerra o passo a passo e segue (relatório simplificado)
  const finish = () => {
    if (blockedMsg) { void notify(blockedMsg); return; }
    beforeLeave?.();
    stopGuided();
    onFinish?.();
  };
  return { idx, prev: GUIDED_STEPS[idx - 1], next: GUIDED_STEPS[idx + 1], go, finish };
}

/**
 * Rodapé de cada tela do passo a passo: botões grandes para voltar e avançar para a próxima etapa,
 * no fim da página (não é preciso subir até a barra do topo).
 */
export const GuidedFooter: React.FC<NavProps> = props => {
  const { idx, prev, next, go, finish } = useGuidedNav(props);
  return (
    <nav className="rounded-2xl border border-blue-200 bg-blue-50 p-3 space-y-2" aria-label="Avançar no passo a passo">
      <p className="text-[11px] font-bold text-blue-900 text-center">Etapa {idx + 1} de {GUIDED_STEPS.length}: {GUIDED_STEPS[idx].label}</p>
      <div className="grid grid-cols-2 gap-2">
        {prev
          ? <button type="button" className={`${btnSecondary} justify-center !py-3`} onClick={() => go(prev.id)}><ArrowLeft className="w-4 h-4" /> Voltar: {prev.label}</button>
          : <span />}
        {next
          ? <button type="button" className={`${btnPrimary} justify-center !py-3`} onClick={() => go(next.id)}>Próxima: {next.label} <ArrowRight className="w-4 h-4" /></button>
          : <button type="button" className={`${btnPrimary} justify-center !py-3`} onClick={finish}><Save className="w-4 h-4" /> Salvar</button>}
      </div>
    </nav>
  );
};

/**
 * Barra de etapas. `current` = etapa da tela atual; `onEditorTab` troca a aba quando já se está no editor;
 * `beforeLeave` grava pendências antes de sair da tela.
 */
export const GuidedBar: React.FC<NavProps & { number?: string }> = props => {
  const { number } = props;
  const { idx, prev, next, go, finish } = useGuidedNav(props);
  return (
    <div className="rounded-2xl border border-blue-200 bg-blue-50 p-3 space-y-2" role="navigation" aria-label="Passo a passo da inspeção">
      <div className="flex flex-wrap items-center gap-2">
        <ListChecks className="w-4 h-4 text-blue-700" />
        <p className="text-xs font-bold text-blue-900 flex-1 min-w-[180px]">
          Passo a passo da inspeção {number || ''} — etapa {idx + 1} de {GUIDED_STEPS.length}: {GUIDED_STEPS[idx].label}
        </p>
        <button type="button" className="text-[11px] text-blue-700 hover:underline inline-flex items-center gap-1" onClick={stopGuided}>
          <X className="w-3 h-3" /> Sair do passo a passo
        </button>
      </div>
      <ol className="flex flex-wrap gap-1">
        {GUIDED_STEPS.map((s, i) => {
          const done = i < idx, on = i === idx;
          return (
            <li key={s.id}>
              <button type="button" onClick={() => !on && go(s.id)} aria-current={on ? 'step' : undefined}
                className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-bold border transition-colors ${on ? 'bg-[#0A2540] text-white border-[#0A2540]' : done ? 'bg-white text-emerald-700 border-emerald-300 hover:bg-emerald-50' : 'bg-white text-slate-600 border-slate-300 hover:bg-slate-50'}`}>
                {done ? <CheckCircle2 className="w-3 h-3" /> : <span className="w-4 text-center">{i + 1}</span>} {s.label}
              </button>
            </li>
          );
        })}
      </ol>
      <div className="flex flex-wrap justify-between gap-2">
        {prev ? <button type="button" className={btnSecondary} onClick={() => go(prev.id)}><ArrowLeft className="w-3.5 h-3.5" /> {prev.label}</button> : <span />}
        {next
          ? <button type="button" className={btnPrimary} onClick={() => go(next.id)}>Próxima: {next.label} <ArrowRight className="w-3.5 h-3.5" /></button>
          : <button type="button" className={btnPrimary} onClick={finish}><Save className="w-3.5 h-3.5" /> Salvar</button>}
      </div>
    </div>
  );
};
