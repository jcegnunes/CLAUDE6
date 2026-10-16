import { describe, expect, it } from 'vitest';
import forge from 'node-forge';
import { jsPDF } from 'jspdf';
import { inspectCertificatePem, parseCertificatePem, signPdf } from '../digitalSignature';
import { verifyPdfSignatures } from '../pades';
import { pickCertificate, pkceChallenge } from '../cloudSigning';
import { isCloudSessionExpired } from '../signingCerts';

/** Certificado "na nuvem": só o PEM sai do PSC; a chave fica lá (aqui, na simulação). */
function makeCloudCert(cn: string) {
  const keys = forge.pki.rsa.generateKeyPair(2048);
  const cert = forge.pki.createCertificate();
  cert.publicKey = keys.publicKey;
  cert.serialNumber = '7b';
  cert.validity.notBefore = new Date(Date.now() - 864e5);
  cert.validity.notAfter = new Date(Date.now() + 365 * 864e5);
  cert.setSubject([{ shortName: 'C', value: 'BR' }, { shortName: 'CN', value: cn }]);
  cert.setIssuer([{ shortName: 'C', value: 'BR' }, { shortName: 'CN', value: 'AC NUVEM TESTE' }]);
  cert.sign(keys.privateKey, forge.md.sha256.create());
  return { pem: forge.pki.certificateToPem(cert), key: keys.privateKey };
}

/** PSC simulado: assinatura RAW (PKCS#1 v1.5) sobre o hash SHA-256 recebido. */
const pscSign = (key: forge.pki.rsa.PrivateKey) => async (digest: Uint8Array) => {
  const md = { algorithm: 'sha256', digest: () => forge.util.createBuffer(String.fromCharCode(...digest)) } as unknown as forge.md.MessageDigest;
  return Uint8Array.from(key.sign(md), c => c.charCodeAt(0));
};

const basePdf = () => {
  const doc = new jsPDF();
  doc.text('Certificado VAL-TRE-2610-NUVEM234', 20, 20);
  return new Uint8Array(doc.output('arraybuffer'));
};

describe('Certificado em nuvem (PSC)', () => {
  it('assina o PDF só com o hash enviado ao PSC; a assinatura PAdES confere', async () => {
    const { pem, key } = makeCloudCert('INSTRUTOR NUVEM:11144477735');
    const sent: Uint8Array[] = [];
    const sign = pscSign(key);
    const signed = await signPdf(basePdf(), [{
      p12: new Uint8Array(), password: '', name: 'INSTRUTOR NUVEM', reason: 'Instrutor',
      remote: { certPem: pem, signHash: async d => { sent.push(d); return sign(d); } }
    }]);
    expect(sent).toHaveLength(1);
    expect(sent[0]).toHaveLength(32);
    const [s] = verifyPdfSignatures(signed).signatures;
    expect(s.integrity).toBe(true);
    expect(s.cryptoValid).toBe(true);
    expect(s.signingCertOk).toBe(true);
    expect(s.policyName).toBe('AD-RB v1.3');
    expect(s.signerCn).toBe('INSTRUTOR NUVEM:11144477735');
  }, 60000);

  it('recusa assinatura do PSC que não confere com o certificado cadastrado', async () => {
    const { pem } = makeCloudCert('A:11144477735');
    const other = makeCloudCert('B:52998224725');
    await expect(signPdf(basePdf(), [{
      p12: new Uint8Array(), password: '', name: 'A', reason: 'r',
      remote: { certPem: pem, signHash: pscSign(other.key) }
    }])).rejects.toThrow(/não confere/);
  }, 60000);

  it('lê o PEM devolvido pelo PSC (também com quebras escapadas)', () => {
    const { pem } = makeCloudCert('FULANO DE TAL:11144477735');
    const escaped = pem.replace(/\r?\n/g, '\\n');
    expect(parseCertificatePem(escaped).subject.getField('CN').value).toBe('FULANO DE TAL:11144477735');
    const info = inspectCertificatePem(pem);
    expect(info.holderName).toBe('FULANO DE TAL');
    expect(info.holderDoc).toBe('11144477735');
    expect(() => parseCertificatePem('lixo')).toThrow();
  });

  it('escolhe na conta o certificado do CPF informado', () => {
    const a = { alias: 'A', certificate: makeCloudCert('A:52998224725').pem };
    const b = { alias: 'B', certificate: makeCloudCert('B:11144477735').pem };
    expect(pickCertificate([a, b], '111.444.777-35', inspectCertificatePem)?.alias).toBe('B');
    expect(pickCertificate([a, b], '', inspectCertificatePem)?.alias).toBe('A');
    expect(pickCertificate([], '111', inspectCertificatePem)).toBeUndefined();
  });

  it('PKCE S256 (RFC 7636, exemplo do apêndice B)', async () => {
    expect(await pkceChallenge('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk')).toBe('E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM');
  });

  it('sessão de assinatura vencida ou sem data', () => {
    expect(isCloudSessionExpired({ tokenExpiresAt: new Date(Date.now() + 60_000).toISOString() })).toBe(false);
    expect(isCloudSessionExpired({ tokenExpiresAt: new Date(Date.now() - 1).toISOString() })).toBe(true);
    expect(isCloudSessionExpired({})).toBe(true);
  });
});
