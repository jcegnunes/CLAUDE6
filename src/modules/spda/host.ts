/**
 * Dados "de fora" do módulo SPDA: empresa, usuário, clientes, instrumentos e OS.
 *
 * - Na plataforma JVM Dielectric (SPDA 10): host.platform.ts (lê o DielectricStorageService).
 * - O app SPDA independente (APK, projeto jvm-spda_v8.0) usa o seu host.standalone.ts,
 *   com cadastros próprios, só no aparelho.
 */
import type { Client, CompanyLabInfo, LabInstrument, ServiceOrder, UserRole } from '../../types';

export interface SpdaHostUser {
  id: string;
  name: string;
  role: UserRole;
  cargo?: string;
  creaOrCft?: string;
}

export interface SpdaHost {
  companyId(): string;
  currentUser(): SpdaHostUser | null;
  clients(): Client[];
  /** Cliente por id (inclusive de outras empresas, como no cadastro da plataforma) */
  findClient(id: string): Client | undefined;
  instruments(): LabInstrument[];
  serviceOrders(): ServiceOrder[];
  nextServiceOrderNumber(): string;
  saveServiceOrder(os: ServiceOrder): ServiceOrder;
  companyInfo(): Partial<CompanyLabInfo> | null;
  /** Grava campos dos dados da empresa (ex.: parâmetros do SPDA); ausente = só leitura */
  updateCompanyInfo?(patch: Partial<CompanyLabInfo>): void;
}
