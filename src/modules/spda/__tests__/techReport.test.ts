import { describe, expect, it } from 'vitest';
import { CHECKLIST_TEMPLATE } from '../checklist';
import { TECH_SECTIONS, defaultConclusao, defaultIntroducao, techText } from '../techReport';
import type { SpdaInspection } from '../types';

const base = (p: Partial<SpdaInspection> = {}): SpdaInspection => ({
  id: 'i1', companyId: 'c', number: 'SPDA-2026-0001', status: 'rascunho', clientName: 'Cliente Teste LTDA', buildingName: '',
  address: 'Rua A, 10', city: 'Brasília', state: 'DF', protectionLevel: 'III', method: 'gaiola', inspectionKind: 'periodica',
  criticalStructure: false, inspectionDate: '2026-11-13', soilCondition: 'seco', instruments: [], technicianName: '',
  checklist: [], continuity: [], grounding: [], serviceOrderNumber: 'OS-SPDA-2026-0001',
  buildings: [{ id: 'b1', name: 'Galpão A', type: 'industrial', spdaKind: 'externo', protectionLevel: 'III' } as SpdaInspection['buildings'] extends (infer T)[] | undefined ? T : never],
  ...p
} as SpdaInspection);

describe('Relatório técnico', () => {
  it('introdução cita cliente, edificação, local, OS e data', () => {
    const t = defaultIntroducao(base());
    expect(t).toMatch(/inspeção periódica/);
    expect(t).toMatch(/escopo compreende/);
    expect(t).toMatch(/Galpão A/);
    expect(t).toMatch(/Cliente Teste LTDA/);
    expect(t).toMatch(/Brasília\/DF/);
    expect(t).toMatch(/OS-SPDA-2026-0001/);
    expect(t).toMatch(/13\/11\/2026/);
  });

  it('conclusão segue o parecer e a próxima inspeção', () => {
    const allC = CHECKLIST_TEMPLATE.map(i => ({ itemId: i.id, status: 'C' as const, buildingId: i.subsystem === 'documentacao' ? undefined : 'b1' }));
    expect(defaultConclusao(base({ checklist: allC }))).toMatch(/conforme.*13\/11\/2027/);
    expect(defaultConclusao(base())).toMatch(/Nenhum item foi inspecionado ou ensaiado/);
    // um item avaliado já gera parecer (sem esperar o checklist completo)
    expect(defaultConclusao(base({ checklist: [{ itemId: 'DES-01', status: 'C', buildingId: 'b1' }] }))).toMatch(/encontra-se conforme/);
  });

  it('texto editado substitui o padrão; vazio volta ao padrão', () => {
    expect(techText(base({ techReport: { introducao: 'Texto próprio' } }), 'introducao')).toBe('Texto próprio');
    expect(techText(base({ techReport: { introducao: '  ' } }), 'introducao')).toMatch(/Este relatório/);
    expect(techText(base(), 'metodoAterramento')).toMatch(/NBR 15749/);
    expect(techText(base(), 'metodoContinuidade')).toMatch(/4 fios.*1 A a 5 A/);
  });

  it('sumário com as seções do relatório', () => {
    expect(TECH_SECTIONS.map(s => s.title)).toEqual([
      'Resumo executivo', 'Objetivo e escopo', 'Referências normativas', 'Caracterização da edificação',
      'Descrição dos subsistemas evidenciados na inspeção', 'Metodologia',
      'Folhas de ensaio e relatórios fotográficos', 'Não conformidades e recomendações', 'Conclusão e parecer técnico', 'Responsabilidade técnica e anexos'
    ]);
  });
});

describe('Resultado escolhido no relatório técnico', () => {
  it('parecer final e subsistemas: escolhido vence o calculado; automático segue a inspeção', async () => {
    const { finalResult, subsystemResult, worstResult, defaultSubsystemResultText } = await import('../techReport');
    const allC = CHECKLIST_TEMPLATE.map(i => ({ itemId: i.id, status: 'C' as const, buildingId: i.subsystem === 'documentacao' ? undefined : 'b1' }));
    const v = base({ checklist: allC });
    expect(finalResult(v)).toBe('aprovado');
    expect(defaultConclusao(v)).toMatch(/foi aprovado/);
    const p = base({ checklist: allC, techReport: { results: { final: 'parcial', b1_captacao: 'reprovado' } } });
    expect(finalResult(p)).toBe('parcial');
    expect(defaultConclusao(p)).toMatch(/atende parcialmente/);
    expect(techText(p, 'resumo')).toMatch(/atende parcialmente/);
    expect(subsystemResult(p, 'b1', 'captacao', 'conforme')).toBe('reprovado');
    expect(subsystemResult(p, 'b1', 'descida', 'conforme')).toBe('aprovado');
    expect(worstResult(['aprovado', 'parcial', 'incompleto'])).toBe('parcial');
    expect(defaultSubsystemResultText('descida', 'parcial', 2)).toMatch(/atende parcialmente.*2 não conformidade/);
  });

  it('recomendações gerais: editadas, da inspeção ou a lista padrão', () => {
    expect(techText(base(), 'recomendacoes')).toMatch(/^a\) Manter o plano/);
    expect(techText(base({ recommendations: 'Trocar conector D3' }), 'recomendacoes')).toBe('Trocar conector D3');
    expect(techText(base({ techReport: { recomendacoes: 'Minha recomendação' } }), 'recomendacoes')).toBe('Minha recomendação');
  });
});

describe('Folha de tabulação', () => {
  it('lista as medições do subsistema/edificação e gera a observação conforme os resultados', async () => {
    const { sheetTests, defaultSheetObs } = await import('../views/ContinuitySheet');
    const { newContinuityTest } = await import('../repository');
    const t1 = newContinuityTest({ kind: 'F5', buildingId: 'b1', from: 'D1', to: 'D2', resistanceOhm: 0.0154 });
    const t2 = newContinuityTest({ kind: 'F5', buildingId: 'b1', from: 'D2', to: 'D3', resistanceOhm: 0.0101 });
    const t3 = newContinuityTest({ kind: 'F6', buildingId: 'b1', from: 'A1', to: 'A2', resistanceOhm: 0.25 });
    const v = base({ continuity: [t1, t2, t3] });
    const b = v.buildings![0];
    expect(sheetTests(v, b, 'descida').map(t => t.from)).toEqual(['D1', 'D2']);
    expect(defaultSheetObs(v, b, 'descida')).toMatch(/inferior ou igual a 200 mΩ/);
    expect(defaultSheetObs(v, b, 'aterramento')).toMatch(/1 trecho\(s\) acima de 200 mΩ: ATE-C01 \(A1 → A2: 250 mΩ\)/);
    expect(defaultSheetObs(v, b, 'captacao')).toMatch(/não realizadas/);
    // NC do checklist do componente entra nas observações
    const comNc = base({ continuity: [t1, t2], checklist: [{ itemId: 'DES-04', status: 'NC', obs: 'Descida D2 rompida', buildingId: 'b1' }] });
    expect(defaultSheetObs(comNc, comNc.buildings![0], 'descida')).toMatch(/Não conformidade DES-04 .*Descida D2 rompida/);
  });
});

describe('Relatório fotográfico', () => {
  it('reúne as fotos do checklist e dos ensaios do subsistema, com a origem', async () => {
    const { photoEntries } = await import('../views/PhotoReport');
    const { newContinuityTest, newGroundingTest } = await import('../repository');
    const foto = (id: string) => ({ id, url: 'data:image/png;base64,AA', caption: 'Foto ' + id, timestamp: '2026-11-14T10:00:00Z', userName: 'Teste' });
    const v = base({
      checklist: [{ itemId: 'DES-04', status: 'NC', obs: 'Conector oxidado', buildingId: 'b1', photos: ['data:image/png;base64,BB'] }],
      continuity: [
        newContinuityTest({ kind: 'F5', buildingId: 'b1', from: 'D1', to: 'D2', resistanceOhm: 0.0184, photos: [foto('p1')] }),
        newContinuityTest({ kind: 'F6', buildingId: 'b1', from: 'A1', to: 'A2', resistanceOhm: 0.31, photos: [foto('p2')] })
      ],
      grounding: [{ ...newGroundingTest({ buildingId: 'b1', point: 'CI-1' }), r62: 4.7, photos: [foto('p3')] }]
    });
    const b = v.buildings![0];
    const des = photoEntries(v, b, 'descida');
    expect(des.map(p => p.source)).toEqual(['inspecao', 'continuidade']);
    expect(des[0]).toMatchObject({ caption: 'Conector oxidado', detail: 'Não conforme', tone: 'bad' });
    expect(des[1].origin).toMatch(/Continuidade DES-C01 · D1 → D2/);
    const ate = photoEntries(v, b, 'aterramento');
    expect(ate.map(p => p.source)).toEqual(['continuidade', 'aterramento']);
    expect(ate[0]).toMatchObject({ tone: 'bad' });
    expect(photoEntries(v, b, 'captacao')).toHaveLength(0);
  });
});

describe('Relatório por edificação', () => {
  it('restringe a inspeção à edificação escolhida, mantendo as respostas gerais', async () => {
    const { scopeInspection } = await import('../views/ReportTypes');
    const v = {
      buildings: [{ id: 'A', name: 'Bloco A' }, { id: 'B', name: 'Bloco B' }],
      checklist: [{ itemId: 'DOC-01', status: 'C' }, { itemId: 'CAP-01', buildingId: 'A', status: 'C' }, { itemId: 'CAP-01', buildingId: 'B', status: 'NC' }],
      continuity: [{ id: '1', buildingId: 'A' }, { id: '2', buildingId: 'B' }],
      grounding: [{ id: 'g', buildingId: 'B' }]
    } as unknown as Parameters<typeof scopeInspection>[0];
    const b = scopeInspection(v, v.buildings![1]);
    expect(b.buildings!.map(x => x.id)).toEqual(['B']);
    expect(b.checklist.map(a => a.itemId + (a.buildingId || ''))).toEqual(['DOC-01', 'CAP-01B']);
    expect(b.continuity.map(t => t.id)).toEqual(['2']);
    expect(b.grounding).toHaveLength(1);
    expect(scopeInspection(v)).toBe(v);
  });
});

describe('Parecer do laudo', () => {
  it('segue o parecer escolhido; texto editado substitui o padrão', async () => {
    const { defaultLaudoConclusao, LAUDO_RESULT_LABEL, finalResult } = await import('../techReport');
    const allC = CHECKLIST_TEMPLATE.map(i => ({ itemId: i.id, status: 'C' as const, buildingId: i.subsystem === 'documentacao' ? undefined : 'b1' }));
    const ok = base({ checklist: allC });
    expect(LAUDO_RESULT_LABEL[finalResult(ok)]).toBe('SPDA APROVADO');
    expect(defaultLaudoConclusao(ok)).toMatch(/está aprovado/);
    const parcial = base({ checklist: allC, techReport: { results: { final: 'parcial' } } });
    expect(LAUDO_RESULT_LABEL[finalResult(parcial)]).toBe('SPDA ATENDE PARCIALMENTE');
    expect(defaultLaudoConclusao(parcial)).toMatch(/atende parcialmente/);
    expect(techText(base({ techReport: { laudoConclusao: 'Parecer do engenheiro' } }), 'laudoConclusao')).toBe('Parecer do engenheiro');
  });
});

describe('Relatório técnico — só subsistemas avaliados', () => {
  it('subsistema sem itens avaliados nem medições fica de fora', async () => {
    const { groupData } = await import('../views/TechReport');
    const { newContinuityTest } = await import('../repository');
    const v = base({
      checklist: [{ itemId: 'CAP-01', status: 'C', buildingId: 'b1' }],
      continuity: [newContinuityTest({ kind: 'F5', buildingId: 'b1', from: 'D1', to: 'D2', resistanceOhm: null })]
    });
    const b = v.buildings![0];
    expect(groupData(v, b, 'captacao', 1, []).hasData).toBe(true);
    expect(groupData(v, b, 'descida', 1, []).hasData).toBe(false); // medição sem valor
    expect(groupData(v, b, 'dps', 1, []).hasData).toBe(false);
    const medido = base({ continuity: [newContinuityTest({ kind: 'EQ', buildingId: 'b1', from: 'BEP', to: 'QGBT', resistanceOhm: 0.01 })] });
    expect(groupData(medido, medido.buildings![0], 'dps', 1, []).hasData).toBe(true);
  });
});

describe('Nº e emissão do laudo', () => {
  it('nº editado ou da inspeção; data e hora editadas ou do momento', async () => {
    const { laudoNumber, laudoIssued } = await import('../techReport');
    expect(laudoNumber({ number: 'SPDA-2026-0001' })).toBe('SPDA-2026-0001');
    expect(laudoNumber({ number: 'SPDA-2026-0001', laudoNumber: ' LAUDO-123/2026 ' })).toBe('LAUDO-123/2026');
    const now = new Date(2026, 10, 16, 9, 5);
    expect(laudoIssued({}, now)).toEqual({ date: '2026-11-16', time: '09:05' });
    expect(laudoIssued({ laudoDate: '2026-12-01', laudoTime: '14:30' }, now)).toEqual({ date: '2026-12-01', time: '14:30' });
    expect(laudoIssued({ laudoDate: '2026-12-01' }, now)).toEqual({ date: '2026-12-01', time: '09:05' });
  });
});
