import { describe, expect, it } from 'vitest';
import forge from 'node-forge';
import { verifyRsaPkcs1Strict } from '../pades';

const A = forge.asn1;
const SHA256 = '2.16.840.1.101.3.4.2.1';
const keys = forge.pki.rsa.generateKeyPair({ bits: 1024, e: 0x10001 });

function digestOf(text: string): string {
  const md = forge.md.sha256.create();
  md.update(text);
  return md.digest().getBytes();
}

/** Assina com um DigestInfo arbitrário (o forge aplica o preenchimento 00 01 FF..FF 00). */
function signRaw(digestInfoDer: string): string {
  // esquema "NONE": o forge só aplica o preenchimento 00 01 FF..FF 00 ao bloco informado
  return keys.privateKey.sign(digestInfoDer as unknown as forge.md.MessageDigest, 'NONE');
}

const digestInfo = (alg: forge.asn1.Asn1[], digest: string) => A.toDer(A.create(A.Class.UNIVERSAL, A.Type.SEQUENCE, true, [
  A.create(A.Class.UNIVERSAL, A.Type.SEQUENCE, true, alg),
  A.create(A.Class.UNIVERSAL, A.Type.OCTETSTRING, false, digest)
])).getBytes();
const oid = (o: string) => A.create(A.Class.UNIVERSAL, A.Type.OID, false, A.oidToDer(o).getBytes());
const nul = () => A.create(A.Class.UNIVERSAL, A.Type.NULL, false, '');

describe('Verificação RSA estrita (node-forge GHSA-86w9-cpqp-85rv)', () => {
  const digest = digestOf('certificado de treinamento');

  it('aceita a assinatura PKCS#1 v1.5 normal (com e sem NULL nos parâmetros)', () => {
    const md = forge.md.sha256.create();
    md.update('certificado de treinamento');
    expect(verifyRsaPkcs1Strict(keys.publicKey, SHA256, digest, keys.privateKey.sign(md))).toBe(true);
    expect(verifyRsaPkcs1Strict(keys.publicKey, SHA256, digest, signRaw(digestInfo([oid(SHA256)], digest)))).toBe(true);
  });

  it('recusa resumo diferente, assinatura alterada e DigestInfo com elementos extras', () => {
    const good = signRaw(digestInfo([oid(SHA256), nul()], digest));
    expect(verifyRsaPkcs1Strict(keys.publicKey, SHA256, digestOf('outro documento'), good)).toBe(false);
    const tampered = good.slice(0, -1) + String.fromCharCode(good.charCodeAt(good.length - 1) ^ 1);
    expect(verifyRsaPkcs1Strict(keys.publicKey, SHA256, digest, tampered)).toBe(false);
    // elemento extra escondido no AlgorithmIdentifier: o verify() do forge aceita, o estrito não
    const extra = signRaw(digestInfo([oid(SHA256), nul(), A.create(A.Class.UNIVERSAL, A.Type.OCTETSTRING, false, 'lixo')], digest));
    expect(verifyRsaPkcs1Strict(keys.publicKey, SHA256, digest, extra)).toBe(false);
  });
});

describe('Cadeia de certificados com verificação estrita', () => {
  it('raízes ICP-Brasil (v5 e v12) conferem consigo mesmas', async () => {
    const { issuedBy } = await import('../pades');
    const { ICP_TRUST_ANCHORS_B64 } = await import('../icpBrasil');
    Object.values(ICP_TRUST_ANCHORS_B64).forEach(b64 => {
      const root = forge.pki.certificateFromAsn1(forge.asn1.fromDer(forge.util.decode64(b64)));
      expect(issuedBy(root, root)).toBe(true);
    });
  });

  it('certificado emitido pela AC confere; com outra chave não', async () => {
    const { issuedBy } = await import('../pades');
    const make = (subjectKey: forge.pki.rsa.PublicKey, cn: string, issuerCn: string, signer: forge.pki.rsa.PrivateKey) => {
      const c = forge.pki.createCertificate();
      c.publicKey = subjectKey;
      c.serialNumber = '01';
      c.validity.notBefore = new Date(2026, 0, 1);
      c.validity.notAfter = new Date(2027, 0, 1);
      c.setSubject([{ name: 'commonName', value: cn }]);
      c.setIssuer([{ name: 'commonName', value: issuerCn }]);
      c.sign(signer, forge.md.sha256.create());
      // passa pelo DER, como os certificados lidos do PDF
      return forge.pki.certificateFromAsn1(forge.asn1.fromDer(forge.asn1.toDer(forge.pki.certificateToAsn1(c))));
    };
    const ca = make(keys.publicKey, 'AC TESTE', 'AC TESTE', keys.privateKey);
    const other = forge.pki.rsa.generateKeyPair({ bits: 1024, e: 0x10001 });
    const leaf = make(other.publicKey, 'TITULAR', 'AC TESTE', keys.privateKey);
    const fake = make(other.publicKey, 'TITULAR', 'AC TESTE', other.privateKey);
    expect(issuedBy(ca, leaf)).toBe(true);
    expect(issuedBy(ca, fake)).toBe(false);
  });
});
