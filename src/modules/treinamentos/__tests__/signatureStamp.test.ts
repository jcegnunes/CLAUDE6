import { describe, expect, it } from 'vitest';
import { stampFromDetails } from '../signingCerts';
import type { CertDetails } from '../digitalSignature';

const base: CertDetails = {
  commonName: '', subjectDn: '', kind: '', level: 'A1', icpBrasil: true, issuerCn: 'AC', issuerDn: 'CN=AC', chain: ['AC'],
  serial: '01', validFrom: '', validTo: '', policies: [], keyUsage: [], extKeyUsage: [], signatureAlgorithm: '', keyBits: 2048, sha256: ''
};

describe('Bloco de assinatura montado com os dados do certificado', () => {
  it('e-CPF: nome do titular e CPF mascarado', () => {
    const s = stampFromDetails({ ...base, kind: 'e-CPF', commonName: 'JOAO CARLOS EVARISTO GUEDES NUNES:52998224725', subjectDn: 'C=BR, CN=JOAO...', cpf: '529.982.247-25' }, 'Instrutor', 'Ana');
    expect(s.person).toBe('JOAO CARLOS EVARISTO GUEDES NUNES');
    expect(s.docLine).toBe('e-CPF · CPF ***.982.247-**');
    expect(s.cn).toBe('JOAO CARLOS EVARISTO GUEDES NUNES:52998224725');
    expect(s.dn).toBe('C=BR, CN=JOAO...');
  });

  it('e-CNPJ: nome do instrutor cadastrado, empresa e CNPJ', () => {
    const s = stampFromDetails({ ...base, kind: 'e-CNPJ', commonName: 'JVM ENGENHARIA LTDA:29894500000104', cnpj: '29.894.500/0001-04', responsibleName: 'JOAO CARLOS EVARISTO GUEDES NUNES' }, 'Instrutor', 'Ana');
    expect(s.person).toBe('Ana');
    expect(s.docLine).toBe('e-CNPJ JVM ENGENHARIA LTDA · CNPJ 29.894.500/0001-04');
  });

  it('sem responsável no e-CNPJ: usa o nome cadastrado do instrutor', () => {
    expect(stampFromDetails({ ...base, kind: 'e-CNPJ', commonName: 'X LTDA:29894500000104' }, 'Instrutor', 'Ana Instrutora').person).toBe('Ana Instrutora');
  });
});

describe('Carimbo do e-CNPJ', () => {
  it('mostra o nome do instrutor cadastrado, não o responsável legal da empresa', async () => {
    const { stampFromDetails } = await import('../signingCerts');
    const d: any = { kind: 'e-CNPJ', commonName: 'JVM ENGENHARIA LTDA:29894500000104', subjectDn: 'CN=JVM', responsibleName: 'MARIA ARAUJO MARTINS', cnpj: '29.894.500/0001-04' };
    const st = stampFromDetails(d, 'Instrutor', 'JOAO CARLOS EVARISTO GUEDES NUNES');
    expect(st.person).toBe('JOAO CARLOS EVARISTO GUEDES NUNES');
    expect(st.docLine).toMatch(/e-CNPJ JVM ENGENHARIA LTDA/);
  });
});
