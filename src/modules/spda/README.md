# Módulo SPDA — plataforma JVM Dielectric (SPDA 10)

Inspeção visual e ensaios de continuidade e aterramento de SPDA, com laudo e relatório técnico.
Veio do app independente `jvm-spda_v8.0` (sem a casca `standalone/`). Na plataforma:
- **Cadastros da plataforma** (`host.platform.ts`): empresa, usuário logado, clientes, instrumentos e OS
  são os mesmos dos ensaios de EPI e do Treinamentos; "Gerar OS" cria uma OS no cadastro oficial.
- **Inspeções só no aparelho** (IndexedDB, chave `jvm_spda_demo_inspections`), sem Supabase e sem tabelas novas;
  entram no **backup completo** da plataforma (`backup.ts`, seção `modules.spda`).
- **Menu** (`index.ts`): Dashboard, Inspeções e laudos, Edificações, Captação, Descida, Aterramento, DPS,
  Relatório e Instrumentos; Clientes & OS no topo. Telas com o `DialogHost` em `views/platformScreens.tsx`.
- Não vieram para a plataforma: "Cores do sistema" (`theme.ts`/`AppearanceView.tsx` ficam sem uso) e os
  cadastros/backup próprios do app.

## Arquivos
| Arquivo | Conteúdo |
|---|---|
| `index.ts` | Manifesto do módulo (menu "SPDA (demo)", só admin e responsável técnico) |
| `types.ts` | Tipos: inspeção, checklist, ensaios, instrumentos |
| `checklist.ts` | Itens da inspeção visual por subsistema (documentação, captação, descida, aterramento, equipotencialização/DPS, toque e passo) |
| `ANALISE_CHECKLIST_NBR5419_2026.md` | Comparação do checklist de referência (base 2015) com a NBR 5419:2026 |
| `rules.ts` | Critérios e cálculos (funções puras) |
| `repository.ts` | Gravação local (mesmo padrão de `treinamentos/repository.ts`, sem fila de sincronização) |
| `storageKeys.ts` | Chave do IndexedDB |
| `views/` | Telas: lista, editor (Dados → Inspeção visual → Ensaios → Conclusão) e laudo |
| `__tests__/` | Testes vitest (`npx vitest run src/modules/spda`) |

## Critérios adotados (ABNT NBR 5419:2026)
- **Checklist**: 57 itens em 6 blocos com referência aos itens da 5419-3:2026 / 5419-4:2026 (ver `ANALISE_CHECKLIST_NBR5419_2026.md`).
- **Continuidade (5419-3, Anexo F)**: 1ª verificação das armaduras F.1/F.2/F.3 ≤ 1 Ω; comprobatória captação ↔ BEP F.4.4 ≤ 0,2 Ω;
  descidas não naturais (F.5), anel não natural (F.6) e equipotencialização (5419-4, 9.2.6): valor compatível com material × comprimento
  (o app calcula ρ·L/S e o inspetor adota o limite). Aviso se a corrente de ensaio sair de 1 A a 5 A (F.1.3.1).
- **Resistência de aterramento**: informativa — não é necessária para verificar a eficácia do SPDA (7.1.4); não gera NC.
- **Parecer**: qualquer NC → *não conforme*; checklist incompleto ou ensaio de continuidade sem valor/critério → *incompleto*; senão *conforme*.
- **Periodicidade (7.3.2 f)**: 1 ano (áreas classificadas 0/1/20/21, munição, explosivos, tóxicos, corrosão severa, serviços essenciais) ou 3 anos.
- Parâmetros por nível: Tabela 2 (esfera rolante, malha) e Tabela 5 (descidas, até +20 %).

## Leitura do valor pela câmera (OCR)
- Ícone de câmera ao lado do campo "Medido" (continuidade, em **mΩ**) e "Adotado" (aterramento, em Ω).
- `ocr.ts`: pré-processamento (cinza/contraste) + **tesseract.js** no próprio aparelho; a foto não sai do aparelho.
  Na 1ª leitura o modelo de reconhecimento é baixado da internet (CDN do tesseract) e fica em cache.
- O valor lido é sempre mostrado para confirmação; número sem unidade é lido como mΩ na continuidade, com
  escolha "mΩ / Ω" quando a unidade não é reconhecida. A foto pode ser anexada ao registro do ensaio.
- Dependência adicionada ao projeto: `tesseract.js` (package.json).

## Ligação com o app
No projeto JVM SPDA v8.0 o módulo usa os cadastros do próprio app (`standalone/host.standalone.ts`, via `host.ts`).
Não há ligação com a plataforma de EPI/Treinamento.

## Próximos passos (fora da demo)
Tabelas no Supabase + sincronização, inclusão no backup, PDF via jsPDF com QR Code de validação,
croqui dos pontos medidos e gráfico da curva de queda de potencial.
