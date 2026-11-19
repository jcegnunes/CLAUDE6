import { describe, expect, it } from 'vitest';
import { MAX_PDF_MB, dataUrlSizeKb, pdfProblem } from '../views/PdfAttachment';

describe('Anexo de PDF (ART e certificado de calibração)', () => {
  it('aceita só PDF até o limite', () => {
    expect(pdfProblem({ name: 'ART.pdf', type: 'application/pdf', size: 200_000 })).toBe('');
    expect(pdfProblem({ name: 'ART.PDF', type: '', size: 200_000 })).toBe('');
    expect(pdfProblem({ name: 'foto.jpg', type: 'image/jpeg', size: 1000 })).toMatch(/PDF/);
    expect(pdfProblem({ name: 'grande.pdf', type: 'application/pdf', size: (MAX_PDF_MB + 1) * 1024 * 1024 })).toMatch(/limite é 10 MB/);
  });

  it('tamanho aproximado do arquivo guardado', () => {
    expect(dataUrlSizeKb('data:application/pdf;base64,' + 'A'.repeat(4096))).toBe(3);
    expect(dataUrlSizeKb(undefined)).toBe(0);
  });
});
