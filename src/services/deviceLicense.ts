/**
 * Licença do APP em um único aparelho: o APP JVM SPDA informa o ID fixo do celular
 * (ANDROID_ID com hash, AndroidBridge.getDeviceId) e o banco aceita um aparelho por usuário
 * (supabase/modules/aparelhos.sql). A plataforma web não tem limite.
 */
import { APP_TARGET } from '../appTarget';

/** Aplicativo que tem licença por aparelho (hoje só o APP JVM SPDA). */
export const LICENSED_APP = 'spda';

type DeviceBridge = { getDeviceId?: () => string; getDeviceName?: () => string };

/** ID e nome deste celular, ou null fora do APP (web) ou em APK antigo sem o ID. */
export function appDeviceInfo(target: string = APP_TARGET): { id: string; name: string } | null {
  if (target !== LICENSED_APP || typeof window === 'undefined') return null;
  try {
    const b = (window as unknown as { AndroidBridge?: DeviceBridge }).AndroidBridge;
    const id = b?.getDeviceId?.() || '';
    if (id.length < 8) return null;
    return { id, name: (b?.getDeviceName?.() || '').slice(0, 120) };
  } catch {
    return null;
  }
}
