/** Inspeção a abrir quando a tela "Inspeções e laudos" for aberta por outra tela do módulo. */
let pendingOpenId: string | null = null;

export function openInspectionOnNextVisit(id: string): void {
  pendingOpenId = id;
}

export function takePendingInspection(): string | null {
  const id = pendingOpenId;
  pendingOpenId = null;
  return id;
}

/** OS escolhida na tela Ordens de Serviço (módulo SPDA): "Executar Ensaios" e "Relatório". */
let pendingOsInspection: string | null = null;
let pendingOsReport: string | null = null;

/** Executar Ensaios da OS: abre a inspeção SPDA dela (ou a nova inspeção já com a OS escolhida). */
export function requestServiceOrderInspection(osId: string): void {
  pendingOsInspection = osId;
}

export function takePendingServiceOrderInspection(): string | null {
  const id = pendingOsInspection;
  pendingOsInspection = null;
  return id;
}

/** Relatório da OS: a tela Relatório abre com a inspeção dessa OS escolhida. */
export function requestServiceOrderReport(osId: string): void {
  pendingOsReport = osId;
}

export function takePendingServiceOrderReport(): string | null {
  const id = pendingOsReport;
  pendingOsReport = null;
  return id;
}

/** "Nova inspeção" pedida de fora da lista (barra inferior do app): abre o diálogo de nova inspeção. */
let pendingNew = false;
export const NEW_INSPECTION_EVENT = 'spda-new-inspection';

export function requestNewInspection(): void {
  pendingNew = true;
  window.dispatchEvent(new Event(NEW_INSPECTION_EVENT));
}

export function takePendingNewInspection(): boolean {
  const v = pendingNew;
  pendingNew = false;
  return v;
}

/** Telas dos cadastros: na plataforma são as telas comuns (Clientes, Ordens de Serviço, Instrumentos). */
const PLATFORM_VIEW: Record<string, string> = { clientes: 'clients', os: 'service_orders', instrumentos: 'instruments' };

/** Troca de tela dentro da plataforma (o App escuta o evento jvm-navigate). */
export function navigateTo(view: string): void {
  window.dispatchEvent(new CustomEvent<string>('jvm-navigate', { detail: PLATFORM_VIEW[view] || view }));
}
