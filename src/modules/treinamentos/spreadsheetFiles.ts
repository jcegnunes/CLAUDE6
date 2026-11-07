/** Arquivos Excel do módulo Treinamentos: modelo para download e leitura. */
import { saveFileLocally } from '../../utils/nativeFileSaver';
import { getCourses, getInstructors } from './repository';
import { MAX_IMPORT_ROWS, OPTIONAL_HEADERS, TEMPLATE_HEADERS } from './spreadsheetImport';
import { parseCsv, SHEET_HEADERS, type SkippedRow } from './reportMerge';

const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

/** Modelo .xlsx: Nome, CPF, Colaborador da Empresa e Filial (+ cursos/instrutores e instruções). */
export async function downloadParticipantsTemplate(): Promise<void> {
  const XLSX = await import('xlsx');
  const ws = XLSX.utils.aoa_to_sheet([
    TEMPLATE_HEADERS,
    ['Maria da Silva', '529.982.247-25', 'Empresa Cliente Ltda', 'Campinas/SP'],
    ['João Souza', '111.444.777-35', 'Empresa Cliente Ltda', 'São Paulo/SP']
  ]);
  ws['!cols'] = [{ wch: 40 }, { wch: 18 }, { wch: 36 }, { wch: 26 }];
  // CPF como texto: o Excel não remove o zero inicial
  for (let r = 2; r <= 3; r++) { const c = ws[`B${r}`]; if (c) c.t = 's'; }

  const courses = getCourses().filter(c => c.active);
  const instructors = getInstructors().filter(i => i.active);
  const lists = XLSX.utils.aoa_to_sheet([
    ['Cursos (sigla)', 'Nome do curso', 'Carga horária'],
    ...courses.map(c => [c.code, c.name, c.workloadHours]),
    [],
    ['Instrutores'],
    ...instructors.map(i => [i.name])
  ]);
  lists['!cols'] = [{ wch: 26 }, { wch: 70 }, { wch: 14 }];

  const help = XLSX.utils.aoa_to_sheet([
    ['Como preencher'],
    ['• Uma pessoa por linha, com Nome, CPF, Colaborador da Empresa (empresa onde a pessoa trabalha) e Filial.'],
    ['• Filial: cidade/unidade da pessoa (ex.: Campinas/SP). É o local impresso junto à data do certificado; vazia = cidade do laboratório.'],
    ['• CPF é recomendado: aparece mascarado no validador do QR Code e evita certificado repetido.'],
    ['• Curso, datas, local, instrutor, presença e nota são escolhidos na tela de importação e valem para todos.'],
    [`• Se precisar, acrescente colunas opcionais (valem só para a linha): ${OPTIONAL_HEADERS.join(', ')}.`],
    [`• Até ${MAX_IMPORT_ROWS} linhas por planilha.`]
  ]);
  help['!cols'] = [{ wch: 120 }];

  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, 'Alunos');
  XLSX.utils.book_append_sheet(wb, lists, 'Cursos e instrutores');
  XLSX.utils.book_append_sheet(wb, help, 'Instruções');
  const bytes = XLSX.write(wb, { bookType: 'xlsx', type: 'array' });
  await saveFileLocally({ filename: 'Modelo_alunos_treinamento.xlsx', data: new Uint8Array(bytes), mimeType: XLSX_MIME, title: 'Modelo de planilha de alunos', category: 'csv' });
}

/** Lê a primeira aba (ou a aba "Alunos"/"Certificados") como objetos por título de coluna. */
export async function readSpreadsheet(file: File): Promise<Array<Record<string, unknown>>> {
  const XLSX = await import('xlsx');
  // datas como número de série do Excel (sem fuso horário); convertidas em parseDateCell
  const wb = XLSX.read(await file.arrayBuffer(), { cellDates: false });
  const sheetName = wb.SheetNames.find(n => /alunos|certificad/i.test(n)) || wb.SheetNames[0];
  if (!sheetName) throw new Error('A planilha está vazia.');
  const rows = XLSX.utils.sheet_to_json<Record<string, unknown>>(wb.Sheets[sheetName], { defval: '', raw: true });
  if (!rows.length) throw new Error('Nenhuma linha encontrada. Use a primeira linha para os títulos das colunas (Nome, CPF, Colaborador da Empresa, Filial).');
  return rows;
}

/** Todas as abas do arquivo como linhas (CSV ou Excel), para a planilha gerada dos relatórios. */
export async function readSheetsAsRows(file: File): Promise<unknown[][][]> {
  const buf = await file.arrayBuffer();
  if (/\.(csv|txt)$/i.test(file.name) || file.type === 'text/csv') {
    let text = new TextDecoder('utf-8').decode(buf);
    // CSV salvo pelo Excel em ANSI: acentos viram "�" em UTF-8
    if (text.includes('�')) text = new TextDecoder('windows-1252').decode(buf);
    return [parseCsv(text)];
  }
  const XLSX = await import('xlsx');
  // datas como número de série do Excel (sem fuso horário); convertidas em parseDateCell
  const wb = XLSX.read(buf, { cellDates: false });
  return wb.SheetNames.map(n => XLSX.utils.sheet_to_json<unknown[]>(wb.Sheets[n], { header: 1, defval: '', raw: true }));
}

/** Planilha de emissão gerada dos relatórios: aba "Alunos" (formato da importação) e "Não incluídos". */
export async function downloadGeneratedSheet(records: Array<Record<string, string>>, skipped: SkippedRow[], filename: string): Promise<void> {
  const XLSX = await import('xlsx');
  const ws = XLSX.utils.aoa_to_sheet([SHEET_HEADERS, ...records.map(r => SHEET_HEADERS.map(h => r[h] ?? ''))]);
  ws['!cols'] = [{ wch: 40 }, { wch: 16 }, { wch: 28 }, { wch: 20 }, { wch: 30 }, { wch: 12 }, { wch: 12 }, { wch: 12 }, { wch: 60 }, { wch: 40 }];
  const out = XLSX.utils.aoa_to_sheet([
    ['Nome', 'CPF', 'Curso no relatório', 'Motivo'],
    ...skipped.map(s => [s.name, s.cpf, s.courseText, s.reason])
  ]);
  out['!cols'] = [{ wch: 40 }, { wch: 16 }, { wch: 70 }, { wch: 36 }];
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, 'Alunos');
  XLSX.utils.book_append_sheet(wb, out, 'Não incluídos');
  const bytes = XLSX.write(wb, { bookType: 'xlsx', type: 'array' });
  await saveFileLocally({ filename, data: new Uint8Array(bytes), mimeType: XLSX_MIME, title: 'Planilha para emissão de certificados', category: 'csv' });
}
