/**
 * Leitura de visores de 7 segmentos (LCD/LED de micro-ohmímetros e terrômetros), 100 % offline.
 *
 * O OCR de texto (tesseract) lê mal dígitos de 7 segmentos — os segmentos são traços separados e
 * inclinados. Aqui o reconhecimento é geométrico:
 *  1. localiza o visor na foto (região clara, retangular, com traços escuros dentro);
 *  2. binariza o visor, corrige a inclinação dos dígitos e separa cada dígito pela projeção vertical;
 *  3. em cada dígito verifica quais dos 7 segmentos (a–g) estão acesos e converte para o número;
 *  4. o ponto decimal é o pequeno borrão na base, entre dois dígitos.
 *
 * Funciona sobre arrays de pixels (sem DOM) para poder ser testado; `loadImagePixels` usa o canvas.
 */

export type Box = { x: number; y: number; w: number; h: number };
export type Pixels = { w: number; h: number; data: Uint8ClampedArray };

export interface SevenSegResult {
  /** Texto do visor, ex.: "21.4" */
  text: string;
  value: number | null;
  /** Região do visor usada (px da imagem) */
  box: Box;
  digits: number;
  /** Altura dos dígitos / altura da região (0–1) */
  band: number;
  /** 0–100 */
  confidence: number;
}

// ---------------------------------------------------------------- utilitários de imagem

function toGray(p: Pixels): { gray: Uint8Array; sat: Float32Array } {
  const n = p.w * p.h;
  const gray = new Uint8Array(n);
  const sat = new Float32Array(n);
  for (let i = 0, j = 0; j < n; i += 4, j++) {
    const r = p.data[i], g = p.data[i + 1], b = p.data[i + 2];
    gray[j] = (r * 299 + g * 587 + b * 114) / 1000;
    const mx = Math.max(r, g, b), mn = Math.min(r, g, b);
    sat[j] = mx === 0 ? 0 : (mx - mn) / mx;
  }
  return { gray, sat };
}

function otsu(values: ArrayLike<number>): number {
  const hist = new Array(256).fill(0);
  for (let i = 0; i < values.length; i++) hist[values[i]]++;
  const total = values.length;
  let sum = 0;
  for (let t = 0; t < 256; t++) sum += t * hist[t];
  let sumB = 0, wB = 0, best = 0, thr = 127;
  for (let t = 0; t < 256; t++) {
    wB += hist[t];
    if (!wB) continue;
    const wF = total - wB;
    if (!wF) break;
    sumB += t * hist[t];
    const mB = sumB / wB, mF = (sum - sumB) / wF;
    const between = wB * wF * (mB - mF) * (mB - mF);
    if (between > best) { best = between; thr = t; }
  }
  return thr;
}

type Comp = { id: number; x0: number; y0: number; x1: number; y1: number; n: number };

/** Componentes conexos de uma máscara (1 = ativo). */
function components(mask: Uint8Array, w: number, h: number, eight: boolean): { labels: Int32Array; comps: Comp[] } {
  const labels = new Int32Array(w * h).fill(-1);
  const comps: Comp[] = [];
  const stack: number[] = [];
  for (let start = 0; start < mask.length; start++) {
    if (!mask[start] || labels[start] !== -1) continue;
    const c: Comp = { id: comps.length, x0: w, y0: h, x1: -1, y1: -1, n: 0 };
    labels[start] = c.id;
    stack.push(start);
    while (stack.length) {
      const i = stack.pop()!;
      const x = i % w, y = (i - x) / w;
      c.n++;
      if (x < c.x0) c.x0 = x; if (x > c.x1) c.x1 = x;
      if (y < c.y0) c.y0 = y; if (y > c.y1) c.y1 = y;
      for (let dy = -1; dy <= 1; dy++) {
        const yy = y + dy;
        if (yy < 0 || yy >= h) continue;
        for (let dx = -1; dx <= 1; dx++) {
          if (!dx && !dy) continue;
          if (!eight && dx && dy) continue;
          const xx = x + dx;
          if (xx < 0 || xx >= w) continue;
          const k = yy * w + xx;
          if (mask[k] && labels[k] === -1) { labels[k] = c.id; stack.push(k); }
        }
      }
    }
    comps.push(c);
  }
  return { labels, comps };
}

/** Recorta e reamostra (bilinear) uma região do array de cinza, girando `angleDeg` em torno do centro. */
function cropGray(gray: Uint8Array, w: number, h: number, box: Box, outH: number, angleDeg = 0): { g: Uint8Array; w: number; h: number } {
  const scale = outH / box.h;
  const ow = Math.max(8, Math.min(1400, Math.round(box.w * scale)));
  const oh = outH;
  const g = new Uint8Array(ow * oh);
  const cos = Math.cos((angleDeg * Math.PI) / 180), sin = Math.sin((angleDeg * Math.PI) / 180);
  const cx = box.x + box.w / 2, cy = box.y + box.h / 2;
  const kx = box.w / ow, ky = 1 / scale;
  for (let y = 0; y < oh; y++) {
    const v = (y + 0.5) * ky - box.h / 2;
    for (let x = 0; x < ow; x++) {
      const u = (x + 0.5) * kx - box.w / 2;
      const sx = Math.min(w - 1.001, Math.max(0, cx + u * cos - v * sin - 0.5));
      const sy = Math.min(h - 1.001, Math.max(0, cy + u * sin + v * cos - 0.5));
      const y0 = Math.floor(sy), fy = sy - y0;
      const x0 = Math.floor(sx), fx = sx - x0;
      const i = y0 * w + x0;
      const top = gray[i] * (1 - fx) + gray[i + 1] * fx;
      const bot = gray[i + w] * (1 - fx) + gray[i + w + 1] * fx;
      g[y * ow + x] = top * (1 - fy) + bot * fy;
    }
  }
  return { g, w: ow, h: oh };
}

// ---------------------------------------------------------------- decodificação dos dígitos

const SEGS = 'abcdefg';
const PATTERNS: Array<[string, string]> = [
  ['abcdef', '0'], ['bc', '1'], ['abdeg', '2'], ['abcdg', '3'], ['bcfg', '4'], ['acdfg', '5'],
  ['acdefg', '6'], ['cdefg', '6'], ['abc', '7'], ['abcf', '7'], ['abcdefg', '8'], ['abcdfg', '9'], ['abcfg', '9'],
  ['g', '-']
];
const toMask = (s: string) => [...s].reduce((m, ch) => m | (1 << SEGS.indexOf(ch)), 0);
const PATTERN_MASKS = PATTERNS.map(([s, d]) => ({ m: toMask(s), d }));
const bits = (x: number) => { let c = 0; while (x) { c += x & 1; x >>= 1; } return c; };

// zonas dos segmentos no retângulo do dígito (frações de largura/altura)
const ZONES: Record<string, [number, number, number, number]> = {
  a: [0.3, 0.7, 0.0, 0.16],
  b: [0.66, 1.0, 0.14, 0.42],
  c: [0.66, 1.0, 0.58, 0.86],
  d: [0.3, 0.7, 0.84, 1.0],
  e: [0.0, 0.34, 0.58, 0.86],
  f: [0.0, 0.34, 0.14, 0.42],
  g: [0.3, 0.7, 0.42, 0.58]
};

function classify(cell: Uint8Array, cw: number, ch: number): { d: string; score: number } {
  const frac: Record<string, number> = {};
  for (const s of SEGS) {
    const [xa, xb, ya, yb] = ZONES[s];
    const x0 = Math.floor(xa * cw), x1 = Math.max(x0 + 1, Math.ceil(xb * cw));
    const y0 = Math.floor(ya * ch), y1 = Math.max(y0 + 1, Math.ceil(yb * ch));
    let on = 0, tot = 0;
    for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) { tot++; on += cell[y * cw + x]; }
    frac[s] = tot ? on / tot : 0;
  }
  const mx = Math.max(...Object.values(frac));
  const thr = Math.max(0.12, mx * 0.38);
  let m = 0;
  for (const s of SEGS) if (frac[s] >= thr) m |= 1 << SEGS.indexOf(s);
  let best = { d: '?', dist: 99 };
  for (const p of PATTERN_MASKS) {
    const dist = bits(p.m ^ m);
    if (dist < best.dist) best = { d: p.d, dist };
  }
  if (best.dist === 0) return { d: best.d, score: 1 };
  if (best.dist === 1 && best.d !== '-') return { d: best.d, score: 0.55 };
  return { d: '?', score: 0 };
}

/** Lê os dígitos de uma região já recortada (cinza). darkInk: segmentos escuros sobre fundo claro (LCD). */
export function decodeDigits(g: Uint8Array, w: number, h: number, darkInk = true): { text: string; value: number | null; digits: number; confidence: number; band: number } | null {
  const fail = null;
  // binarização (Otsu na área interna, para não pesar a moldura)
  const inner: number[] = [];
  for (let y = Math.floor(h * 0.08); y < h * 0.92; y++) for (let x = Math.floor(w * 0.04); x < w * 0.96; x++) inner.push(g[y * w + x]);
  if (!inner.length) return fail;
  const t = otsu(inner);
  const ink = new Uint8Array(w * h);
  for (let i = 0; i < ink.length; i++) ink[i] = darkInk ? (g[i] < t ? 1 : 0) : (g[i] > t ? 1 : 0);
  const { labels, comps } = components(ink, w, h, true);
  // descarta moldura (toca a borda), ruído e manchas enormes
  const minN = Math.max(6, w * h * 0.0006);
  let keep = comps.filter(c => c.x0 > 0 && c.y0 > 0 && c.x1 < w - 1 && c.y1 < h - 1 && c.n >= minN && (c.x1 - c.x0) < w * 0.85);
  if (!keep.length) return fail;

  // faixa dos dígitos: linhas com mais tinta (projeção horizontal), maior trecho contínuo
  const keepSet = new Set(keep.map(c => c.id));
  const rows = new Array(h).fill(0);
  for (let i = 0; i < labels.length; i++) if (labels[i] >= 0 && keepSet.has(labels[i])) rows[(i / w) | 0]++;
  const rowThr = Math.max(2, w * 0.015);
  let best: [number, number, number] = [0, -1, 0];
  for (let y = 0; y < h;) {
    if (rows[y] < rowThr) { y++; continue; }
    let y2 = y, sum = 0, gap = 0;
    for (let k = y; k < h; k++) {
      if (rows[k] >= rowThr) { y2 = k; gap = 0; sum += rows[k]; } else if (++gap > h * 0.04) break;
    }
    if (sum > best[2]) best = [y, y2, sum];
    y = y2 + 1;
  }
  const top = best[0], bottom = best[1];
  const H = bottom - top + 1;
  if (H < h * 0.25) return fail;

  // pontos decimais: borrões pequenos na parte de baixo da faixa
  const dots: Array<{ x: number; y: number }> = [];
  keep = keep.filter(c => {
    const cw = c.x1 - c.x0 + 1, chh = c.y1 - c.y0 + 1;
    if (c.y1 < top - 2 || c.y0 > bottom + 2) return false; // fora da faixa (rótulos)
    const cy = (c.y0 + c.y1) / 2;
    const ar = cw / chh;
    // ponto: pequeno, quase quadrado, na parte de baixo
    if (cw < H * 0.22 && chh < H * 0.22 && ar > 0.5 && ar < 1.8 && cy > top + H * 0.72) {
      dots.push({ x: (c.x0 + c.x1) / 2, y: cy });
      return false;
    }
    // segmentos (incl. horizontais curtos e finos); o resto é ruído
    return chh >= H * 0.25 || cw >= H * 0.18;
  });
  if (!keep.length) return fail;
  const ids = new Set(keep.map(c => c.id));
  const px: number[] = [], py: number[] = [];
  for (let i = 0; i < labels.length; i++) if (labels[i] >= 0 && ids.has(labels[i])) { px.push(i % w); py.push((i / w) | 0); }

  // inclinação (itálico): cisalhamento que deixa mais colunas vazias
  const mid = (top + bottom) / 2;
  const pad = Math.ceil(H * 0.4);
  let shear = 0, bestCols = Infinity;
  for (let s = -0.3; s <= 0.3001; s += 0.02) {
    const occ = new Uint8Array(w + 2 * pad);
    for (let k = 0; k < px.length; k++) occ[Math.round(px[k] + s * (py[k] - mid)) + pad] = 1;
    let n = 0;
    for (let i = 0; i < occ.length; i++) n += occ[i];
    if (n < bestCols - 0.5 || (Math.abs(n - bestCols) < 0.5 && Math.abs(s) < Math.abs(shear))) { bestCols = n; shear = s; }
  }
  const W2 = w + 2 * pad;
  const occ = new Uint16Array(W2);
  const sx = px.map((x, k) => Math.round(x + shear * (py[k] - mid)) + pad);
  for (const x of sx) occ[x]++;
  // colunas ocupadas → dígitos
  // (folgas pequenas entre os segmentos do mesmo dígito são unidas; entre dígitos o espaço é maior)
  const maxGap = Math.max(1, Math.round(H * 0.06));
  const runs: Array<[number, number]> = [];
  for (let x = 0; x < W2;) {
    if (!occ[x]) { x++; continue; }
    let x2 = x;
    for (;;) {
      let nx = -1;
      for (let k = 1; k <= maxGap + 1 && x2 + k < W2; k++) if (occ[x2 + k]) { nx = x2 + k; break; }
      if (nx < 0) break;
      x2 = nx;
    }
    runs.push([x, x2]);
    x = x2 + 1;
  }
  // descarta corridas sem altura de dígito (ex.: restos do símbolo da unidade)
  const runInfo = runs.map(([a, b]) => {
    let y0 = h, y1 = -1, n = 0;
    for (let k = 0; k < sx.length; k++) if (sx[k] >= a && sx[k] <= b) { n++; if (py[k] < y0) y0 = py[k]; if (py[k] > y1) y1 = py[k]; }
    return { a, b, y0, y1, n };
  }).filter(r => r.y1 - r.y0 + 1 >= H * 0.45 || ((r.b - r.a + 1) >= H * 0.25 && r.y0 > top + H * 0.3 && r.y1 < top + H * 0.7));
  if (!runInfo.length) return fail;
  const wide = runInfo.map(r => r.b - r.a + 1).filter(x => x >= H * 0.3).sort((x, y) => x - y);
  const medW = wide.length ? wide[Math.floor(wide.length / 2)] : H * 0.55;

  let text = '';
  let conf = 0;
  const digitRight: number[] = [];
  for (const r of runInfo) {
    const rw = r.b - r.a + 1;
    const tall = r.y1 - r.y0 + 1;
    let d: { d: string; score: number };
    if (tall < H * 0.45) d = { d: '-', score: 0.9 };
    else if (rw < Math.max(medW * 0.5, H * 0.12) && tall >= H * 0.7) d = { d: '1', score: 0.9 };
    else {
      // célula do dígito (no espaço corrigido), largura = corrida, altura = faixa
      const cw = rw, cellH = H;
      const cell = new Uint8Array(cw * cellH);
      for (let k = 0; k < sx.length; k++) {
        if (sx[k] < r.a || sx[k] > r.b) continue;
        const yy = py[k] - top;
        if (yy >= 0 && yy < cellH) cell[yy * cw + (sx[k] - r.a)] = 1;
      }
      d = classify(cell, cw, cellH);
    }
    text += d.d;
    conf += d.score;
    digitRight.push(r.b);
  }
  const nDigits = runInfo.length;
  // ponto decimal: após o último dígito cuja borda direita fica à esquerda do ponto
  const dot = dots
    .map(p => ({ x: p.x + shear * (p.y - mid) + pad }))
    .find(p => p.x > runInfo[0].a && p.x < runInfo[runInfo.length - 1].a);
  if (dot) {
    let idx = -1;
    digitRight.forEach((rb, i) => { if (rb < dot.x) idx = i; });
    if (idx >= 0 && idx < nDigits - 1) text = text.slice(0, idx + 1) + '.' + text.slice(idx + 1);
  }
  if (text.includes('?') || !/\d/.test(text)) return { text, value: null, digits: nDigits, confidence: Math.round((conf / nDigits) * 100), band: H / h };
  const clean = text.replace(/^-+(?=-)/, '');
  const value = /^-?\d+(\.\d+)?$/.test(clean) ? Number(clean) : null;
  return { text: clean, value, digits: nDigits, confidence: Math.round((conf / nDigits) * 100), band: H / h };
}

// ---------------------------------------------------------------- localização do visor

/** Regiões candidatas a visor (claras, retangulares, com traços escuros dentro), em px da imagem. */
export function findDisplayCandidates(p: Pixels): Box[] {
  const ds = Math.min(1, 360 / Math.max(p.w, p.h));
  const w = Math.max(1, Math.round(p.w * ds)), h = Math.max(1, Math.round(p.h * ds));
  const { gray: full, sat: fullSat } = toGray(p);
  const gray = new Uint8Array(w * h), sat = new Float32Array(w * h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const k = Math.min(p.h - 1, Math.floor(y / ds)) * p.w + Math.min(p.w - 1, Math.floor(x / ds));
    gray[y * w + x] = full[k];
    sat[y * w + x] = fullSat[k];
  }
  const t = otsu(gray);
  // o vidro do LCD costuma ser cinza-esverdeado de brilho médio (às vezes mais escuro que o painel),
  // mas quase sem saturação: limiares globais e limiares só entre os pixels "neutros"
  const neutral: number[] = [];
  for (let i = 0; i < gray.length; i++) if (sat[i] < 0.15) neutral.push(gray[i]);
  const tn = neutral.length > 50 ? otsu(neutral) : t;
  const tries: Array<[number, number]> = [[t, 0.4], [Math.round(t + (255 - t) * 0.3), 0.4], [tn, 0.15], [Math.round(tn * 0.8), 0.15]];
  const out: Array<{ box: Box; score: number }> = [];
  for (const [thr, maxSat] of tries) {
    const mask = new Uint8Array(w * h);
    for (let i = 0; i < mask.length; i++) mask[i] = gray[i] > thr && sat[i] < maxSat ? 1 : 0;
    const { comps } = components(mask, w, h, false);
    for (const c of comps) {
      const bw = c.x1 - c.x0 + 1, bh = c.y1 - c.y0 + 1;
      const area = bw * bh;
      const aspect = bw / bh;
      if (area < w * h * 0.004 || area > w * h * 0.7 || aspect < 1.3 || aspect > 9 || bh < 8) continue;
      const fill = c.n / area;
      if (fill < 0.45 || fill > 0.97) continue;
      out.push({ box: { x: c.x0 / ds, y: c.y0 / ds, w: bw / ds, h: bh / ds }, score: area * (aspect >= 1.8 && aspect <= 6 ? 1.5 : 1) });
    }
  }
  out.sort((a, b) => b.score - a.score);
  // remove duplicatas (mesma região achada por limiares diferentes)
  const boxes: Box[] = [];
  for (const o of out) {
    const dup = boxes.some(b => Math.abs(b.x - o.box.x) < b.w * 0.05 && Math.abs(b.y - o.box.y) < b.h * 0.08 && Math.abs(b.w - o.box.w) < b.w * 0.08 && Math.abs(b.h - o.box.h) < b.h * 0.1);
    if (!dup) boxes.push(o.box);
    if (boxes.length >= 12) break;
  }
  // a foto pode ser só o visor: tenta também a imagem inteira
  boxes.push({ x: p.w * 0.03, y: p.h * 0.03, w: p.w * 0.94, h: p.h * 0.94 });
  return boxes;
}

/** Lê o visor numa região escolhida (px da imagem). */
/** inks: polaridades a tentar — escuro sobre claro (LCD) e/ou claro sobre escuro (LED). */
export function readBox(p: Pixels, box: Box, gray?: Uint8Array, inks: boolean[] = [true, false]): SevenSegResult | null {
  const g0 = gray ?? toGray(p).gray;
  const b: Box = {
    x: Math.max(0, Math.round(box.x)), y: Math.max(0, Math.round(box.y)),
    w: Math.max(4, Math.min(p.w - Math.max(0, Math.round(box.x)), Math.round(box.w))),
    h: Math.max(4, Math.min(p.h - Math.max(0, Math.round(box.y)), Math.round(box.h)))
  };
  let bestR: ReturnType<typeof decodeDigits> = null;
  const better = (r: NonNullable<typeof bestR>) => !bestR
    || (r.value !== null && bestR.value === null)
    || (r.value !== null && (r.confidence > bestR.confidence + 2 || (Math.abs(r.confidence - bestR.confidence) <= 2 && r.digits > bestR.digits)));
  // a foto raramente está nivelada: tenta pequenos giros
  outer: for (const ang of [0, -3, 3, -6, 6, -9, 9]) {
    const crop = cropGray(g0, p.w, p.h, b, 140, ang);
    for (const dark of inks) {
      const r = decodeDigits(crop.g, crop.w, crop.h, dark);
      if (r && better(r)) bestR = r;
      if (bestR && bestR.value !== null && bestR.confidence >= 97) break outer;
    }
  }
  return bestR ? { ...bestR, box: b } : null;
}

/** Localiza o visor e lê o valor. Retorna null se não encontrar dígitos de 7 segmentos. */
export function readSevenSegment(p: Pixels): SevenSegResult | null {
  const { gray } = toGray(p);
  const cands = findDisplayCandidates(p);
  let best: { r: SevenSegResult; score: number } | null = null;
  for (const [i, box] of cands.entries()) {
    // regiões claras achadas na foto são LCD (dígitos escuros); só a foto inteira tenta também LED (dígitos claros)
    const r = readBox(p, box, gray, i === cands.length - 1 ? [true, false] : [true]);
    // confiança mínima e dígitos ocupando boa parte da altura da região: num visor os dígitos são grandes;
    // em botões e etiquetas o texto é pequeno em relação à área (evita ler "200 mΩ" do botão de escala)
    if (!r || r.value === null || r.confidence < 70 || r.band < 0.4) continue;
    const numeric = r.text.replace(/\D/g, '').length;
    const score = (r.confidence / 100) * r.band * Math.sqrt(Math.min(numeric, 3) / 3);
    if (!best || score > best.score) best = { r, score };
  }
  return best?.r ?? null;
}

/** Carrega uma foto (data URL) em pixels, limitando o maior lado. */
export async function loadImagePixels(dataUrl: string, maxSide = 1600): Promise<Pixels> {
  const img = await new Promise<HTMLImageElement>((resolve, reject) => {
    const i = new Image();
    i.onload = () => resolve(i);
    i.onerror = () => reject(new Error('Não foi possível abrir a foto'));
    i.src = dataUrl;
  });
  const s = Math.min(1, maxSide / Math.max(img.naturalWidth, img.naturalHeight));
  const c = document.createElement('canvas');
  c.width = Math.max(1, Math.round(img.naturalWidth * s));
  c.height = Math.max(1, Math.round(img.naturalHeight * s));
  const ctx = c.getContext('2d', { willReadFrequently: true })!;
  ctx.drawImage(img, 0, 0, c.width, c.height);
  const d = ctx.getImageData(0, 0, c.width, c.height);
  return { w: c.width, h: c.height, data: d.data };
}

/** Recorte de uma região da imagem (px), como nova imagem. */
export function cropPixels(p: Pixels, box: Box): Pixels {
  const x0 = Math.max(0, Math.floor(box.x)), y0 = Math.max(0, Math.floor(box.y));
  const w = Math.max(1, Math.min(p.w - x0, Math.round(box.w))), h = Math.max(1, Math.min(p.h - y0, Math.round(box.h)));
  const data = new Uint8ClampedArray(w * h * 4);
  for (let y = 0; y < h; y++) data.set(p.data.subarray(((y0 + y) * p.w + x0) * 4, ((y0 + y) * p.w + x0 + w) * 4), y * w * 4);
  return { w, h, data };
}

/**
 * Lê dentro de uma região escolhida (moldura da câmera ou retângulo marcado pelo usuário):
 * primeiro procura o visor dentro dela; se não achar, lê a região inteira como visor.
 */
export function readRegion(p: Pixels, box: Box): SevenSegResult | null {
  const sub = cropPixels(p, box);
  const found = readSevenSegment(sub);
  if (found && found.value !== null) {
    return { ...found, box: { x: found.box.x + Math.max(0, Math.floor(box.x)), y: found.box.y + Math.max(0, Math.floor(box.y)), w: found.box.w, h: found.box.h } };
  }
  return readBox(p, box);
}
