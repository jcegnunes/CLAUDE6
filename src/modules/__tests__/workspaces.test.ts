import { describe, expect, it } from 'vitest';
import { canUseModule, getAssignableModules, getAvailableWorkspaces } from '../workspaces';

const ids = (list: Array<{ id: string }>) => list.map(w => w.id);

describe('Módulos liberados por usuário', () => {
  it('sem restrição no cadastro: todos os módulos do perfil', () => {
    expect(ids(getAvailableWorkspaces({}, { role: 'tecnico', allowedModules: null }))).toEqual(['ensaios', 'treinamentos', 'spda']);
    expect(ids(getAvailableWorkspaces({}, { role: 'tecnico' }))).toEqual(['ensaios', 'treinamentos', 'spda']);
  });

  it('restrito: só os módulos marcados', () => {
    expect(ids(getAvailableWorkspaces({}, { role: 'tecnico', allowedModules: ['treinamentos'] }))).toEqual(['treinamentos']);
    expect(ids(getAvailableWorkspaces({}, { role: 'administrativo', allowedModules: ['ensaios'] }))).toEqual(['ensaios']);
    expect(getAvailableWorkspaces({}, { role: 'tecnico', allowedModules: [] })).toEqual([]);
  });

  it('administrador acessa todos, mesmo com restrição gravada', () => {
    expect(canUseModule({ role: 'admin', allowedModules: ['ensaios'] }, 'treinamentos')).toBe(true);
    expect(canUseModule({ role: 'tecnico', isMasterAdmin: true, allowedModules: [] }, 'ensaios')).toBe(true);
  });

  it('módulo desligado para a empresa não aparece nem pode ser liberado', () => {
    const info = { enabledModules: { treinamentos: false, spda: false } };
    expect(ids(getAssignableModules(info))).toEqual(['ensaios']);
    expect(ids(getAvailableWorkspaces(info, { role: 'tecnico', allowedModules: null }))).toEqual(['ensaios']);
  });

  it('perfil cliente continua só com os ensaios', () => {
    expect(ids(getAvailableWorkspaces({}, { role: 'cliente', allowedModules: null }))).toEqual(['ensaios']);
  });
});

describe('Gestão de Acessos (só master)', () => {
  it('aparece só para o administrador master', () => {
    expect(ids(getAvailableWorkspaces({}, { role: 'admin', isMasterAdmin: true, allowedModules: null }))).toContain('acessos');
    expect(ids(getAvailableWorkspaces({}, { role: 'admin', isMasterAdmin: false, allowedModules: null }))).not.toContain('acessos');
    expect(ids(getAvailableWorkspaces({}, { role: 'tecnico', allowedModules: null }))).not.toContain('acessos');
  });

  it('não entra na lista de módulos liberáveis no cadastro de usuários', () => {
    expect(ids(getAssignableModules({}))).not.toContain('acessos');
  });

  it('Ensaios de EPI desligado para a empresa some da escolha de módulos', () => {
    expect(ids(getAvailableWorkspaces({ enabledModules: { ensaios: false } }, { role: 'tecnico', allowedModules: null }))).toEqual(['treinamentos', 'spda']);
  });
});

describe('Tela inicial de cada módulo', () => {
  it('o SPDA abre no Painel do SPDA (nunca no painel dos Ensaios de EPI)', () => {
    const ws = getAvailableWorkspaces({}, { role: 'tecnico', allowedModules: null });
    expect(ws.find(w => w.id === 'spda')?.home).toBe('spda_dashboard');
    expect(ws.find(w => w.id === 'treinamentos')?.home).toBe('treinamentos');
    expect(ws.find(w => w.id === 'ensaios')?.home).toBe('dashboard');
  });
});
