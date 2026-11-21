/**
 * Miliohmímetro INBRAT INMD 1 Pro pelo Bluetooth (só no APP JVM SPDA: a conexão é feita no Android,
 * InbratBle.java). Protocolo em android-spda/PROTOCOLO_INBRAT_INMD1PRO.md.
 */
import type { InstrumentBluetooth, LabInstrument } from '../../types';

export const INBRAT_INMD_PROTOCOL = 'inbrat-inmd1pro' as const;
export const INBRAT_INMD_LABEL = 'INBRAT INMD 1 Pro (miliohmímetro)';

/** Acima disso (o aparelho manda 3000 mΩ) ou sem corrente = pontas abertas / fora de escala. */
const OVER_RANGE_MILLIOHM = 2999;
const MIN_CURRENT_A = 0.05;

export interface InmdReading {
  /** nº do ensaio na memória do instrumento */
  deviceTestNo: number;
  status: number;
  resistanceMilliOhm: number;
  currentA: number;
  /** pontas abertas, fora de escala ou erro: não usar o valor */
  invalid: boolean;
  invalidReason?: string;
}

/** Decodifica a notificação de resultado (13 bytes: uint32 nº, uint8 situação, float32 mΩ, float32 A). */
export function decodeInmdResult(hex: string): InmdReading {
  const clean = (hex || '').replace(/[^0-9a-f]/gi, '');
  if (clean.length < 26) throw new Error('Resposta do instrumento incompleta.');
  const bytes = new Uint8Array(clean.length / 2);
  for (let i = 0; i < bytes.length; i++) bytes[i] = parseInt(clean.substr(i * 2, 2), 16);
  const dv = new DataView(bytes.buffer);
  const deviceTestNo = dv.getUint32(0, true);
  const status = dv.getUint8(4);
  const resistanceMilliOhm = dv.getFloat32(5, true);
  const currentA = dv.getFloat32(9, true);
  let invalidReason: string | undefined;
  if (!Number.isFinite(resistanceMilliOhm) || !Number.isFinite(currentA)) invalidReason = 'Leitura inválida do instrumento.';
  else if (resistanceMilliOhm >= OVER_RANGE_MILLIOHM || currentA < MIN_CURRENT_A) invalidReason = 'Fora de escala: verifique se as pontas estão bem conectadas aos pontos medidos.';
  else if (status !== 0) invalidReason = `O instrumento indicou erro na medição (código ${status}).`;
  else if (resistanceMilliOhm < 0) invalidReason = 'Leitura negativa: verifique as pontas e meça de novo.';
  return { deviceTestNo, status, resistanceMilliOhm, currentA, invalid: !!invalidReason, invalidReason };
}

/** Nome do ensaio gravado no instrumento: até 11 caracteres ASCII. */
export function inmdTestName(text: string): string {
  const ascii = (text || '').normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^\x20-\x7e]/g, '').replace(/\s+/g, ' ').trim();
  return (ascii || 'SPDA').slice(0, 11);
}

/** Nome que o INMD 1 Pro anuncia no Bluetooth (ex.: IN433A). */
export function looksLikeInbrat(name: string): boolean {
  return /^IN[0-9A-F]{3,}/i.test(name || '') || /inbrat|inmd/i.test(name || '');
}

export function instrumentHasInmd(inst: Pick<LabInstrument, 'bluetooth'>): inst is { bluetooth: InstrumentBluetooth } {
  return !!inst.bluetooth && inst.bluetooth.protocol === INBRAT_INMD_PROTOCOL && !!inst.bluetooth.address;
}

// ------------------------------------------------------------ ponte com o Android
type BridgeEvent =
  | { type: 'found'; address: string; name: string; rssi?: number }
  | { type: 'done' | 'connecting' | 'connected' | 'measuring' }
  | { type: 'result'; hex: string; serial?: string }
  | { type: 'error'; message: string };

type NativeBle = {
  bleAvailable?: () => boolean;
  bleScan?: (requestId: string, seconds: number) => void;
  bleMeasure?: (requestId: string, address: string, testName: string) => void;
  bleCancel?: () => void;
};

const listeners = new Map<string, (e: BridgeEvent) => void>();

function bridge(): NativeBle | undefined {
  if (typeof window === 'undefined') return undefined;
  const w = window as unknown as { AndroidBridge?: NativeBle; __spdaBle?: (id: string, e: BridgeEvent) => void };
  if (!w.__spdaBle) w.__spdaBle = (id, e) => listeners.get(id)?.(e);
  return w.AndroidBridge;
}

/** O app tem o Bluetooth do INMD 1 Pro (APK 10.0.26 ou mais novo). */
export function bleAvailable(): boolean {
  try { return !!bridge()?.bleAvailable?.(); } catch { return false; }
}

let seq = 0;
const newId = () => `ble-${Date.now()}-${++seq}`;

/** Busca aparelhos Bluetooth por alguns segundos. */
export function scanBleDevices(onFound: (d: { address: string; name: string; rssi?: number }) => void, seconds = 10): Promise<void> {
  const b = bridge();
  if (!b?.bleScan) return Promise.reject(new Error('Bluetooth disponível só no APP JVM SPDA (Android).'));
  const id = newId();
  return new Promise((resolve, reject) => {
    listeners.set(id, e => {
      if (e.type === 'found') onFound(e);
      else if (e.type === 'done') { listeners.delete(id); resolve(); }
      else if (e.type === 'error') { listeners.delete(id); reject(new Error(e.message)); }
    });
    b.bleScan!(id, seconds);
  });
}

export type MeasureStage = 'connecting' | 'connected' | 'measuring';

/** Faz um ensaio no INMD 1 Pro e devolve a leitura decodificada. */
export function measureInmd(address: string, testName: string, onStage?: (s: MeasureStage) => void): Promise<InmdReading & { serial?: string }> {
  const b = bridge();
  if (!b?.bleMeasure) return Promise.reject(new Error('Bluetooth disponível só no APP JVM SPDA (Android).'));
  const id = newId();
  return new Promise((resolve, reject) => {
    listeners.set(id, e => {
      if (e.type === 'connecting' || e.type === 'connected' || e.type === 'measuring') onStage?.(e.type);
      else if (e.type === 'result') {
        listeners.delete(id);
        try { resolve({ ...decodeInmdResult(e.hex), serial: e.serial || undefined }); } catch (err) { reject(err); }
      } else if (e.type === 'error') { listeners.delete(id); reject(new Error(e.message)); }
    });
    b.bleMeasure!(id, address, inmdTestName(testName));
  });
}

export function cancelBle(): void {
  try { bridge()?.bleCancel?.(); } catch { /* nada */ }
}
