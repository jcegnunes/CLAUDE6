import { describe, expect, it } from 'vitest';
import { formatCnpj, mapBrasilApi, mapCnpjWs, validCnpj } from '../cnpjLookup';

describe('CNPJ', () => {
  it('máscara e dígitos verificadores', () => {
    expect(formatCnpj('11222333000181')).toBe('11.222.333/0001-81');
    expect(formatCnpj('112223')).toBe('11.222.3');
    expect(validCnpj('11.222.333/0001-81')).toBe(true);
    expect(validCnpj('11.222.333/0001-82')).toBe(false);
    expect(validCnpj('00000000000000')).toBe(false);
    expect(validCnpj('123')).toBe(false);
  });

  it('BrasilAPI → cliente', () => {
    const d = mapBrasilApi({
      razao_social: 'EMPRESA TESTE LTDA', nome_fantasia: 'TESTE', descricao_tipo_de_logradouro: 'RUA', logradouro: 'DAS FLORES',
      numero: '100', complemento: 'SALA 2', bairro: 'CENTRO', municipio: 'BRASILIA', uf: 'df', cep: '70000000',
      ddd_telefone_1: '6133334444', email: 'CONTATO@TESTE.COM', descricao_situacao_cadastral: 'Ativa'
    });
    expect(d).toMatchObject({
      razaoSocial: 'EMPRESA TESTE LTDA', nomeFantasia: 'TESTE', endereco: 'RUA DAS FLORES', numero: '100', complemento: 'SALA 2',
      bairro: 'CENTRO', cidade: 'BRASILIA', estado: 'DF', cep: '70000-000', telefone: '(61) 3333-4444', email: 'contato@teste.com',
      situacao: 'ATIVA', fonte: 'BrasilAPI'
    });
  });

  it('CNPJ.ws → cliente (com inscrição estadual)', () => {
    const d = mapCnpjWs({
      razao_social: 'EMPRESA TESTE LTDA',
      estabelecimento: {
        nome_fantasia: 'TESTE', tipo_logradouro: 'Avenida', logradouro: 'Central', numero: '5', bairro: 'Asa Sul',
        cidade: { nome: 'Brasília' }, estado: { sigla: 'DF' }, cep: '70000-000', ddd1: '61', telefone1: '999998888',
        email: null, situacao_cadastral: 'Ativa', inscricoes_estaduais: [{ inscricao_estadual: '0712345600123', ativo: true }]
      }
    });
    expect(d).toMatchObject({ endereco: 'Avenida Central', cidade: 'Brasília', estado: 'DF', telefone: '(61) 99999-8888', email: '', inscricaoEstadual: '0712345600123', fonte: 'CNPJ.ws' });
  });
});

describe('CPF', () => {
  it('máscara, dígitos verificadores e tipo do documento', async () => {
    const { formatCpf, validCpf, docKindOf, docLabel, formatDoc } = await import('../cnpjLookup');
    expect(formatCpf('52998224725')).toBe('529.982.247-25');
    expect(formatCpf('5299822')).toBe('529.982.2');
    expect(validCpf('529.982.247-25')).toBe(true);
    expect(validCpf('529.982.247-24')).toBe(false);
    expect(validCpf('111.111.111-11')).toBe(false);
    expect(docKindOf('529.982.247-25')).toBe('cpf');
    expect(docKindOf('11.222.333/0001-81')).toBe('cnpj');
    expect(docKindOf('')).toBe('cnpj');
    expect(docLabel('529.982.247-25')).toBe('CPF');
    expect(formatDoc('11222333000181', 'cnpj')).toBe('11.222.333/0001-81');
    expect(formatDoc('11222333000181', 'cpf')).toBe('112.223.330-00');
  });
});
