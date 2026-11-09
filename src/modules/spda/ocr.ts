/**
 * Leitura do valor medido a partir da foto do visor do instrumento
 * (micro-ohmímetro, terrômetro). OCR no próprio aparelho com tesseract.js —
 * a foto não é enviada a nenhum servidor. Na primeira leitura o modelo de
 * reconhecimento é baixado (precisa de internet) e fica em cache.
 *
 * O valor lido é SEMPRE mostrado para o usuário confirmar antes de preencher.
 */

export interface OcrReading {
  /** Texto bruto reconhecido */
  text: string;
  /** Valor em ohms (já convertido de mΩ/kΩ/µΩ), ou null se não encontrado */
  valueOhm: number | null;
  /** Todos os valores candidatos encontrados (em Ω) */
  candidates: number[];
  /** Confiança do OCR (0–100) */
  confidence: number;
}

// O OCR costuma ler o símbolo Ω como "O", "Q" ou "0" — aceitos como Ω na unidade.
const OMEGA = '[ΩωOQ0]';
const UNIT_FACTOR: Array<{ re: RegExp; factor: number }> = [
  { re: new RegExp(`^(?:m\\s*${OMEGA}|m\\s*ohms?|m(?![a-zA-Z]))`), factor: 1e-3 },
  { re: new RegExp(`^(?:[µu]\\s*${OMEGA}|[µu]\\s*ohms?)`, 'i'), factor: 1e-6 },
  { re: new RegExp(`^(?:k\\s*${OMEGA}|k\\s*ohms?)`, 'i'), factor: 1e3 },
  { re: new RegExp(`^(?:M\\s*${OMEGA}|M\\s*ohms?)`), factor: 1e6 }
];
const OHM_ONLY = /^(?:[ΩωOQ]|ohm)/i;

/** Corrige trocas típicas do OCR em visores (O→0, l/I→1, S→5, B→8, Z→2) só junto de dígitos. */
function fixDigits(text: string): string {
  return text
    .replace(/(?<=[\d.,])[Oo]|[Oo](?=[\d.,])/g, '0')
    .replace(/(?<=[\d.,])[lI|]|[lI|](?=[\d.,])/g, '1')
    .replace(/(?<=[\d.,])S|S(?=[\d.,])/g, '5')
    .replace(/(?<=[\d.,])B|B(?=[\d.,])/g, '8')
    .replace(/(?<=[\d.,])Z|Z(?=[\d.,])/g, '2');
}

/**
 * Extrai o valor medido (em Ω) do texto do visor.
 * Aceita vírgula ou ponto decimal, números como ".085", e unidades mΩ/kΩ/µΩ/Ω
 * (sem unidade: Ω). Entre vários números, escolhe o primeiro que tiver
 * unidade; senão, o de mais dígitos (normalmente a leitura principal do visor).
 */
/** defaultFactor: unidade assumida quando o visor não mostra unidade (1e-3 = mΩ, continuidade). */
export function parseMeasuredValue(rawText: string, defaultFactor = 1): { valueOhm: number | null; candidates: number[] } {
  const text = fixDigits(rawText.replace(/\r/g, ' '));
  const re = /(-?\d*[.,]\d+|-?\d+(?:[.,]\d+)?)\s*([^\d\s.,-]{0,5})/g;
  const found: Array<{ value: number; digits: number; hasUnit: boolean }> = [];
  let m: RegExpExecArray | null;
  while ((m = re.exec(text)) !== null) {
    const numText = m[1].replace(',', '.');
    let value = Number(numText.startsWith('.') ? `0${numText}` : numText.startsWith('-.') ? numText.replace('-.', '-0.') : numText);
    if (!Number.isFinite(value)) continue;
    const unit = m[2] || '';
    const u = UNIT_FACTOR.find(x => x.re.test(unit));
    const hasUnit = !!u || OHM_ONLY.test(unit);
    if (u) value *= u.factor;
    else if (!hasUnit) value *= defaultFactor;
    found.push({ value: Number(value.toPrecision(6)), digits: numText.replace(/\D/g, '').length, hasUnit });
  }
  const valid = found.filter(f => f.value >= 0);
  const candidates = valid.map(f => f.value);
  const best = valid.find(f => f.hasUnit) || [...valid].sort((a, b) => b.digits - a.digits)[0];
  return { valueOhm: best ? best.value : null, candidates };
}

/** Prepara a foto para o OCR: tons de cinza, contraste e ampliação (canvas). */
export async function preprocessForOcr(dataUrl: string): Promise<string> {
  const img = await new Promise<HTMLImageElement>((resolve, reject) => {
    const i = new Image();
    i.onload = () => resolve(i);
    i.onerror = () => reject(new Error('Não foi possível abrir a foto'));
    i.src = dataUrl;
  });
  const scale = Math.min(3, Math.max(1, 1600 / Math.max(img.width, img.height)));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(img.width * scale);
  canvas.height = Math.round(img.height * scale);
  const ctx = canvas.getContext('2d');
  if (!ctx) return dataUrl;
  ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
  const data = ctx.getImageData(0, 0, canvas.width, canvas.height);
  const px = data.data;
  // tons de cinza + esticar contraste (2 % a 98 %)
  const gray = new Uint8ClampedArray(px.length / 4);
  const hist = new Array(256).fill(0);
  for (let i = 0, j = 0; i < px.length; i += 4, j++) {
    const g = Math.round(0.299 * px[i] + 0.587 * px[i + 1] + 0.114 * px[i + 2]);
    gray[j] = g;
    hist[g]++;
  }
  const total = gray.length;
  let lo = 0, hi = 255, acc = 0;
  for (let v = 0; v < 256; v++) { acc += hist[v]; if (acc > total * 0.02) { lo = v; break; } }
  acc = 0;
  for (let v = 255; v >= 0; v--) { acc += hist[v]; if (acc > total * 0.02) { hi = v; break; } }
  const range = Math.max(1, hi - lo);
  for (let i = 0, j = 0; i < px.length; i += 4, j++) {
    const v = Math.max(0, Math.min(255, ((gray[j] - lo) * 255) / range));
    px[i] = px[i + 1] = px[i + 2] = v;
  }
  ctx.putImageData(data, 0, 0);
  return canvas.toDataURL('image/png');
}

type TesseractWorker = { recognize: (img: string) => Promise<{ data: { text: string; confidence: number } }>; setParameters: (p: Record<string, string>) => Promise<unknown>; terminate: () => Promise<unknown> };
let workerPromise: Promise<TesseractWorker> | null = null;

async function getWorker(): Promise<TesseractWorker> {
  if (!workerPromise) {
    workerPromise = (async () => {
      const { createWorker, PSM } = await import('tesseract.js');
      const worker = await createWorker('eng');
      await worker.setParameters({
        tessedit_char_whitelist: '0123456789.,-mkMuµΩohmOHM ',
        tessedit_pageseg_mode: PSM.SPARSE_TEXT
      });
      return worker as unknown as TesseractWorker;
    })().catch(err => {
      workerPromise = null;
      throw err;
    });
  }
  return workerPromise;
}

/** Reconhece o valor do visor (com pré-processamento). */
export async function readMeasuredValue(photoDataUrl: string, defaultFactor = 1): Promise<OcrReading> {
  const worker = await getWorker();
  const prepared = await preprocessForOcr(photoDataUrl).catch(() => photoDataUrl);
  let { data } = await worker.recognize(prepared);
  let parsed = parseMeasuredValue(data.text || '', defaultFactor);
  if (parsed.valueOhm === null) {
    // segunda tentativa com a foto original
    ({ data } = await worker.recognize(photoDataUrl));
    parsed = parseMeasuredValue(data.text || '', defaultFactor);
  }
  return { text: (data.text || '').trim(), confidence: Math.round(data.confidence || 0), ...parsed };
}
