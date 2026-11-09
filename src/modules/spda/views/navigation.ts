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
