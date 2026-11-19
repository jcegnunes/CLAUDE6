/**
 * Dados "de fora" do módulo SPDA dentro da plataforma JVM Dielectric: empresa,
 * usuário logado, clientes, instrumentos e OS vêm do DielectricStorageService
 * (os mesmos cadastros dos ensaios de EPI e do Treinamentos, sincronizados com o Supabase).
 * "Gerar OS" cria uma OS NOVA no cadastro de OS da plataforma (numeração oficial OS-…).
 */
import { DielectricStorageService } from '../../services/syncEngine';
import type { SpdaHost } from './host';
import { isSpdaServiceOrder } from './osText';

const companyId = () => DielectricStorageService.getSessionCompanyId() || DielectricStorageService.getActiveCompany()?.id || '';

export const host: SpdaHost = {
  companyId,
  currentUser: () => {
    const u = DielectricStorageService.getCurrentUser();
    return u && u.id ? { id: u.id, name: u.name, role: u.role, cargo: u.cargo, creaOrCft: u.creaOrCft } : null;
  },
  clients: () => DielectricStorageService.getClients(companyId() || undefined),
  findClient: id => DielectricStorageService.getClients('ALL').find(c => c.id === id),
  instruments: () => DielectricStorageService.getInstruments(companyId() || undefined),
  // só as OS do módulo SPDA (as dos Ensaios de EPI e de outros módulos não aparecem)
  serviceOrders: () => DielectricStorageService.getServiceOrders(companyId() || undefined).filter(isSpdaServiceOrder),
  nextServiceOrderNumber: () => DielectricStorageService.generateNextOSNumber(),
  saveServiceOrder: os => DielectricStorageService.saveServiceOrder(os),
  companyInfo: () => DielectricStorageService.getCompanyInfo(),
  updateCompanyInfo: patch => DielectricStorageService.saveCompanyInfo({ ...DielectricStorageService.getCompanyInfo(), ...patch })
};
