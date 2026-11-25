# SPDA 10 — plataforma JVM Dielectric com o módulo SPDA

Pasta criada em 17/11/2026 como **cópia da v7.5** (`jvm-dielectric-lab_v7.5_antes-SPDA_com-selecao-certificados`,
sem a pasta `.git`) com o SPDA do projeto `jvm-spda_v8.0` (versionCode 28) integrado como **módulo**, igual ao Treinamentos.
Os projetos v7.5 e v8.0 **não foram alterados**.

## Como usar
- `executar.bat` ou `npm run dev` → http://localhost:3020 (porta própria, para rodar junto com as outras versões).
- Login normal da plataforma. Depois do login, escolha o módulo **SPDA** (ou troque pelo menu).
- Ligar/desligar por empresa: **Configurações & Backup → Módulos do sistema**. Liberar por usuário: cadastro do usuário.

## O que entrou
- `src/modules/spda/` — o módulo inteiro (regras NBR 5419:2026, checklist, ensaios, laudo, relatório técnico,
  fotos, croqui, OCR, dashboard), sem a casca `standalone/` do app independente.
- `src/modules/spda/host.platform.ts` — clientes, instrumentos, OS, usuário logado e dados da empresa vêm da plataforma.
- `src/modules/spda/index.ts` — manifesto: menu **SPDA** com Dashboard, Inspeções e laudos, Edificações, Captação,
  Descida, Aterramento, DPS, Relatório e Instrumentos (tela comum); Clientes & OS no topo.
- `src/modules/spda/backup.ts` — as inspeções entram no backup completo da plataforma.
- Registro: `src/modules/registry.ts`, `src/modules/storageKeys.ts`, `src/modules/backup.ts`.
- Atalhos do módulo ("Cadastrar cliente", "Ordens de serviço", "Instrumentos") abrem as telas da plataforma.
- Dependências novas: `html-to-image` (PDF do laudo) e `tesseract.js` (leitura do display pela câmera).
- Versão 10.0.0.

## Limitações desta primeira versão
- **Inspeções SPDA ficam só no aparelho/navegador** (IndexedDB), como no app v8: não vão para o Supabase e
  não aparecem em outro computador. Use o backup completo. Sincronizar exige uma tabela nova no Supabase
  (`supabase/modules/spda.sql`), a fazer depois.
- "Gerar OS" a partir da inspeção grava a OS no cadastro da plataforma (sincronizado com o Supabase).
- "Cores do sistema" do app SPDA não foi trazido (a plataforma tem o próprio visual).
- O APK do SPDA continua no projeto `jvm-spda_v8.0`; o app Android da plataforma não foi alterado.

## Verificação
- `npx tsc --noEmit` sem erros; `npx vitest run`: 363 testes passando (inclui os do SPDA).

---

# 10.0.0 — APP JVM SPDA e sincronização do SPDA com a plataforma (17/11/2026)

Dois aplicativos independentes, cada um com o seu módulo, sincronizando com a mesma plataforma (Supabase):
- **APP de Ensaios de EPI** — o app existente (projeto `jvm-ensaios`), sem alteração.
- **APP JVM SPDA** — novo, em `android-spda/`: login da plataforma e só o módulo SPDA.

## Banco (executar uma vez no SQL Editor do Supabase, depois do schema.sql)
- `supabase/modules/spda.sql` (idempotente): tabela `spda_inspections` (registro completo em `payload`),
  controle de conflito entre aparelhos, bloqueio dos dados demo e acesso por empresa + módulo SPDA.
- OS e instrumentos passam a valer para os módulos Ensaios **ou** SPDA (também no `schema.sql`), para quem
  só tem o SPDA conseguir gerar a OS e usar os instrumentos.
- `recriar_banco.sql` regerado (`npm run build:sql`, que agora aceita o arquivo com quebras de linha do Windows).
- `npm run test:db` (PGlite local, nada no Supabase): todos os testes passam, com os novos do SPDA.

## Sincronização das inspeções (`src/modules/spda/sync.ts`)
- Cada gravação/exclusão entra numa fila no aparelho; envio automático (2 s depois, a cada 90 s e ao voltar a internet).
- Fotos e croquis vão para o bucket `jvm-evidencias/<empresa>/spda/<inspeção>/`; no servidor a inspeção guarda o
  endereço, no aparelho a foto continua dentro da inspeção (laudo funciona sem internet). Cada foto é enviada uma vez.
- Recebimento incremental, sem sobrescrever edição pendente; exclusão vira `deleted_at`.
- Conflito (mesma inspeção alterada em dois aparelhos): aviso com "Manter a deste aparelho" / "Usar a da plataforma".
- Barra de situação no topo das telas do SPDA (sincronizado, aguardando envio, sem internet, erro, Sincronizar).
- As inspeções que já estavam no navegador (chave antiga) continuam; passam a ser enviadas quando forem editadas.

## APP JVM SPDA
- `npm run build:app-spda` (= `vite build --mode spda`): mesma plataforma, só login + módulo SPDA, sem PWA,
  caminhos relativos, saída em `android-spda/app/src/main/assets/www`. `src/appTarget.ts` diz qual app está rodando.
- `android-spda/`: casca Android do app SPDA v8 (câmera, fotos em Download/JVM_SPDA, PDF), pacote novo
  `br.com.jvmengenharia.jvmspda`, versão 10.0.0 (versionCode 1). `compilar-apk.bat` → `JVM-SPDA.apk`.
- Usuário sem o módulo SPDA: aviso "Sem acesso ao módulo SPDA" e botão Sair.

## Verificação
- `npx tsc --noEmit` sem erros; `npx vitest run`: 370 testes; `npm run test:db`: todos passam.
- Não testado com login real nem no celular.

---

# 10.0.1 — SPDA no visual do APP de Ensaios de EPI (17/11/2026) — APK versionCode 2

Plataforma web (módulo SPDA) e APP JVM SPDA:
- Caixas de preenchimento no padrão do app de EPI (Material): altas (48 px), contorno cinza `#94A3B8`, foco
  azul-marinho `#0A2540`, nome do campo na borda da caixa, texto 16 px no celular (sem zoom ao tocar).
- Botões principais laranja `#F97316` (44 px de altura); secundários com contorno azul-marinho; confirmações iguais.
- Etapa ativa do passo a passo em azul-marinho. Laudos e relatórios impressos não mudam.

Só no APP JVM SPDA:
- Barra superior azul-marinho com "JVM SPDA" centralizado, logotipo em quadrado laranja, ☰ (menu) e Sincronizar.
- Barra inferior branca: Painel, Inspeções, **Nova inspeção** (laranja, abre o diálogo de nova inspeção), Relatório e Menu.
- Menu sempre em gaveta (também no tablet); fundo `#F1F5F9`.
- `npm run dev -- --mode spda` (porta 3021 na configuração "spda-10-app") abre o modo app no navegador.

Arquivos: `src/modules/spda/views/ui.tsx`, `dialogs.tsx`, `GuidedFlow.tsx`, `navigation.ts`, `SpdaModuleView.tsx`,
`src/components/SpdaAppBars.tsx` (novo), `src/App.tsx`, `src/index.css`.
APK anterior (versionCode 1) guardado como `android-spda/JVM-SPDA_anterior.apk`.

## 10.0.2 (APK versionCode 3) — ajustes conferidos no navegador (modo app, celular)
- Telas do SPDA sem o selo "DEMO" e sem o aviso "dados somente neste aparelho" (agora sincroniza com a plataforma).
- Nome do campo na borda da caixa também nos quadros cinza (fundo do nome acompanha o quadro).
- Rodapé do menu do app mostra "JVM SPDA • ABNT NBR 5419:2026".
- Pendente de decisão: laudo, relatório técnico e relatório por subsistema ainda trazem "VERSÃO DE DEMONSTRAÇÃO".

## 10.0.3 (APK versionCode 4) — botões de PDF e Word dos laudos e relatórios, no padrão do app de EPI
Em todos os documentos (laudo, relatório técnico, por subsistema, fotográfico, continuidade e demais):
**Visualizar PDF**, **Compartilhar PDF**, **Exportar Word (.doc)** e **Compartilhar Word** (botões grandes, 2 × 2).
- **APP JVM SPDA:** o PDF é gerado pelo motor de impressão do Android direto em arquivo (sem a janela de
  impressão) — PDF **vetorial**, texto nítido e selecionável, A4 com margens, igual em qualidade ao PDF do app de
  Ensaios de EPI. Visualizar = salva em Download/JVM_SPDA e abre no leitor de PDF; Compartilhar = WhatsApp, e-mail…
  (`android/print/SpdaPdfWriter.java` + `AndroidBridge.createPdf`).
- **Plataforma web:** Visualizar PDF abre a visualização de impressão do navegador ("Salvar como PDF" gera o PDF
  vetorial); Compartilhar PDF/Word usa o compartilhar do aparelho quando o navegador permite e, senão, baixa o arquivo
  (o PDF baixado na web continua sendo de imagens, agora em resolução maior: 3×, JPEG 95 %).
- Correção: o compartilhamento de arquivos (FileProvider) do APP usava o identificador do app demo antigo — falhava
  ao compartilhar e impedia instalar ao lado do demo. Agora segue o pacote do app.
- Impressão de todos os relatórios em A4 com as margens do modelo (11/13/9/13 mm).

## 10.0.4 (APK versionCode 5) — fotos sem o nome do executor
- Legendas das fotos sem o nome de quem tirou a foto: relatório fotográfico, laudo (registro fotográfico) e foto
  ampliada na tela. Ficam a legenda e a data/hora. O nome continua gravado no registro (não é exibido).

## 10.0.5 (APK versionCode 6) — Ordens de Serviço no módulo SPDA
- Na tela **Ordens de Serviço** aberta pelo módulo SPDA (plataforma web e APP JVM SPDA) não aparece a lista de
  EPI/EPC do cliente: nem na abertura/edição da OS ("Item 4 • Equipamentos do Cliente para Ensaio"), nem nos detalhes
  da OS, nem a contagem de equipamentos na lista. No módulo Ensaios de EPI continua igual.
- Descrição padrão da OS no módulo SPDA (nova OS pela tela de OS e "Gerar nova OS" a partir da inspeção):
  "Inspeção visual e ensaios de continuidade e de resistência de aterramento dos subsistemas que compõem o SPDA."
  (`src/modules/spda/osText.ts`). OS já existentes não mudam.

## 10.0.6 (APK versionCode 7) — o módulo SPDA só vê as OS do SPDA
- A OS ganhou o campo `module` (gravado no registro e sincronizado no `payload`, sem mudar o banco).
  OS abertas pelo módulo SPDA (tela de OS ou "Gerar nova OS" na inspeção) ficam com `module = 'spda'`.
- No módulo SPDA (plataforma web e APP JVM SPDA) só aparecem as OS do SPDA: tela Ordens de Serviço, escolha da OS
  na nova inspeção, dados da inspeção e Dashboard. As OS dos Ensaios de EPI não aparecem.
- OS antigas sem marca: contam como do SPDA só se a descrição for de SPDA (as geradas pelas inspeções antes desta
  versão); ao serem editadas pelo módulo SPDA passam a ficar marcadas. As demais são dos Ensaios de EPI.
- O módulo Ensaios de EPI continua vendo todas as OS (inclusive as do SPDA).

## 10.0.7 (só plataforma web) — os Ensaios de EPI não veem as OS do SPDA
- No módulo Ensaios de EPI as OS do SPDA não aparecem: tela Ordens de Serviço, Dashboard dos ensaios, Emissão de
  Relatório, Novo Ensaio (escolha da OS) e modo de campo Android da plataforma (`withoutSpdaOrders` em osText.ts).
- Laudos/PDFs, backup e sincronização continuam com todas as OS (só procuram a OS pelo id).
- O app nativo de Ensaios de EPI (projeto jvm-ensaios) não foi alterado: ele continua listando todas as OS que baixa.
- O APK do SPDA não precisa de nova versão (o módulo de ensaios não existe nele).

## App de Ensaios de EPI (Android nativo) — sem as OS do SPDA (17/11/2026)
- Nova pasta `app-ensaios-epi/`: cópia do projeto `jvm-ensaios-v6.1` (o original não foi alterado).
- O app não baixa as OS do módulo SPDA e esconde as que já tinha baixado. Nada é apagado (nem no Supabase nem no
  celular). APK `app-ensaios-epi/JVM-Ensaios-EPI.apk` (6.1.261117.2204), mesmo pacote e mesma chave do app atual:
  instala por cima mantendo os dados. Ver `app-ensaios-epi/LEIA-ME.txt`.

## 10.0.7 (APK versionCode 8) — botões da OS no módulo SPDA
- Na tela Ordens de Serviço do módulo SPDA, **Executar Ensaios** (no cartão e nos detalhes da OS) abre a inspeção
  SPDA dessa OS no passo a passo; se a OS ainda não tem inspeção, abre a Nova inspeção já com a OS escolhida.
  Antes abria o ensaio de EPI.
- **Relatório** abre a tela Relatórios do SPDA com a inspeção dessa OS escolhida (antes: emissão de relatório de EPI).
- No módulo Ensaios de EPI os botões continuam iguais. Conferido no navegador com a OS-2611-0383 / SPDA-2026-0001.

## 10.0.8 (APK versionCode 9) — Salvar no fim da inspeção
- Na última etapa do passo a passo (Conclusão e laudo), o botão "Encerrar passo a passo" virou **Salvar**
  (no rodapé e na barra de etapas). Ao salvar: grava a inspeção, encerra o passo a passo e abre o
  **relatório simplificado dos ensaios** (todas as edificações e subsistemas com medição), com os botões
  Visualizar/Compartilhar PDF e Word.

## 10.0.9 (APK versionCode 10) — campos ao tocar e lançamento do aterramento no celular
- Caixas com o nome na borda (altura, comprimento, largura, pavimentos…): ao tocar na caixa, a borda mais grossa
  não passa mais por cima do nome do campo.
- Resistência de aterramento: a tabela larga (com colunas escondidas na lateral do celular) virou um cartão por
  ponto, com os campos em grade que quebra na tela: Ponto · Método · Passo das leituras; Dist. E → C · S1 · S (62 %) · S2
  (com a posição de cada leitura); Adotado (com leitura pela câmera) · Projeto · Situação; gráfico do patamar,
  fotos, observações e botões. No computador os campos ficam lado a lado.

## 10.0.10 (APK versionCode 11) — leitura pela câmera em S1, S e S2
- Resistência de aterramento (queda de potencial): o ícone da câmera (ler o valor no visor do instrumento) fica ao lado
  de cada leitura — S1, S (62 %) e S2 — e grava o valor lido na própria caixa (com a foto, se tirada).
- O valor Adotado não tem mais o ícone da câmera (é preenchido à mão ou, se vazio, vale a leitura S a 62 %).

## 10.0.11 (APK versionCode 12) — "Sem acesso ao módulo SPDA" com o motivo
- A tela de bloqueio do APP JVM SPDA diz o motivo exato e onde liberar:
  módulo desligado para a empresa (Configurações & Backup → Módulos do sistema); perfil sem acesso (cliente);
  ou o cadastro do usuário libera só outros módulos (ex.: "Ensaios de EPI + Treinamentos" — marcado antes de o
  SPDA existir) → Configurações & Backup → Usuários, marcar SPDA. Depois: Sair e entrar de novo.

## 10.0.12 (APK versionCode 13) — "Criar conta" com aprovação do master
- **Tela de login** (plataforma web e APP JVM SPDA): botão **Criar conta**. A pessoa informa nome, e-mail (login),
  telefone, empresa, CNPJ, nome de usuário (opcional) e senha. O pedido fica **aguardando aprovação** e não dá acesso.
- **Configurações & Backup → Pedidos de conta** (só o administrador **master**): Pendentes / Aprovados / Recusados,
  com Aprovar e Recusar (observação opcional). Aprovar cria o usuário **administrador** da nova empresa, sem empresa
  vinculada: no primeiro acesso ele preenche "Cadastre sua empresa". Depois ele cria os usuários da empresa dele.
- Banco: `supabase/modules/cadastro.sql` (executar uma vez no SQL Editor, depois do schema.sql). Tabela
  `account_requests` sem acesso direto; funções `jvm_request_account` (sem login), `jvm_list_account_requests` e
  `jvm_decide_account_request` (só master). Senha guardada já criptografada (bcrypt); um pedido pendente por e-mail;
  no máximo 30 pedidos por hora. O cadastro público do Supabase Auth continua desligado.
- `npm run test:db`: 16 testes novos do cadastro (todos passam). `recriar_banco.sql` regerado.

## 10.0.13 (APK versionCode 14) — módulo Gestão de Acessos (só o administrador master)
Novo módulo da plataforma, como o SPDA e o Treinamentos, que só aparece para o **master** (não aparece para
nenhum cliente, não entra em "Módulos do sistema" nem no cadastro de usuários):
- **Pedidos de conta**: os pedidos do "Criar conta" (aprovar / recusar). Saiu de Configurações & Backup.
- **Empresas e validade**: todas as empresas, com os módulos liberados (Ensaios de EPI, Treinamentos, SPDA) e a
  **validade do acesso** (data, +1 mês, +1 ano, sem vencimento). Avisos de "vence em 30 dias" e "vencida".
- **Usuários e bloqueio**: usuários de todas as empresas (filtro por empresa, busca, só bloqueados): bloquear ou
  desbloquear o acesso (encerra as sessões), perfil e módulos liberados.

Banco — `supabase/modules/acessos.sql` (executar uma vez no SQL Editor, depois do schema.sql):
- `companies.access_expires_at`. Empresa vencida: o login é recusado com "O acesso da empresa venceu em dd/mm/aaaa"
  (plataforma e apps) e o banco não mostra nem grava dados dela. Master nunca é bloqueado. Os dados são mantidos.
- Módulo desligado para a empresa passa a valer também no banco (não só no menu). "Ensaios de EPI" também pode ser
  desligado por empresa.
- Funções do master: `jvm_master_companies`, `jvm_master_set_company`, `jvm_master_users`, `jvm_master_set_user`.
- As funções de acesso (`jvm_my_company`, `jvm_can_use_module`, `jvm_me`) foram atualizadas igualmente no
  `schema.sql` desta versão. `npm run test:db`: todos passam (com os novos de validade, módulos e usuários).

## 10.0.14 (APK versionCode 15) — "Criar conta": módulos de interesse, empresa e CNPJ opcionais
- O pedido de conta informa os **módulos de interesse** (SPDA, Ensaios de EPI, Treinamentos — pelo menos um;
  no APP JVM SPDA o SPDA já vem marcado). **Empresa** e **CNPJ/CPF** passaram a ser **opcionais**: a conta é
  liberada para o e-mail de quem pediu (pessoa física, profissional autônomo…).
- Pedidos de conta (Gestão de Acessos): mostra os módulos de interesse de cada pedido.
- Aprovado, o usuário fica com os módulos pedidos; no primeiro acesso, a empresa que ele cadastrar começa **só com
  esses módulos ligados** (o master muda depois em Gestão de Acessos → Empresas e validade).
- "Cadastre sua empresa" (1º acesso): CNPJ ou CPF opcional; razão social pode ser o nome da pessoa. A empresa sem
  CNPJ é reconhecida como cadastrada pela razão social (antes a tela voltava sempre).
- Banco: **executar de novo `supabase/modules/cadastro.sql`** no SQL Editor (troca a função `jvm_request_account`:
  módulos em lista, empresa opcional; acrescenta a coluna `modules`). Testes do banco: todos passam.

## 10.0.15 (APK versionCode 16) — excluir empresa e usuário (Gestão de Acessos)
- **Empresas e validade** e **Usuários e bloqueio** ganharam o botão **Excluir**, com confirmação na própria tela
  ("Confirmar exclusão" / "Cancelar").
- Exclusão lógica: a empresa/usuário some das listas e o acesso é bloqueado na hora (sessões encerradas), mas os
  dados ficam guardados no banco. Excluir a empresa exclui também todos os usuários dela.
- Travas: não exclui você mesmo, outro administrador master, nem a empresa de um master (ou a sua).
  O e-mail de usuário excluído continua reservado (para reaproveitar o e-mail, prefira Bloquear).
- Banco: **executar de novo `supabase/modules/acessos.sql`** (funções `jvm_master_delete_user` e
  `jvm_master_delete_company`). Testes do banco: todos passam (10 novos).

## 10.0.16 (APK versionCode 17) — módulos só pelo master e Configurações por módulo
- **Módulos do sistema** (Configurações & Backup): para quem não é master, mostra só os módulos **liberados** para a
  empresa e para o usuário, sem caixa de marcar ("Para liberar outros, fale com o administrador da plataforma").
  Só o master liga/desliga (aqui na própria empresa ou em Gestão de Acessos para as demais).
- Banco (`acessos.sql`): só o master muda os módulos de uma empresa — o administrador da empresa não consegue ligar
  módulos nem por versões antigas do app (a gravação mantém os módulos que estavam). A empresa criada no 1º acesso de
  uma conta aprovada começa só com os módulos liberados.
- **Configurações & Backup por módulo**: "Parâmetros de Emissão de Certificados & Laudos" (NR-10), "Gerador de APK" e
  "Impressora Niimbot" só aparecem no módulo Ensaios de EPI. Dados da empresa, RT, usuários, portal e backup continuam
  em todos.
- **Parâmetros do SPDA** (Configuração do Sistema, com o SPDA aberto): prefixo da numeração das inspeções/laudos
  (padrão SPDA- → SPDA-2026-0001) e validade padrão do laudo (12/24/36 meses) para as inspeções novas. Ficam nos dados
  da empresa e sincronizam.
- Banco: **executar de novo `supabase/modules/acessos.sql`**. Testes: app 378, banco todos passam.

## 10.0.17 (APK versionCode 18) — login direto na plataforma e cadastro como pessoa física
- **Sem a tela "Cadastre sua empresa"** depois do login:
  - conta aprovada pelo "Criar conta": a aprovação **já cria a empresa** no banco com os dados do pedido (empresa/CNPJ
    ou, sem empresa, **pessoa física** no nome da pessoa com o CPF) e só os módulos pedidos ligados;
  - usuário antigo ainda sem empresa: o app cria sozinho um cadastro mínimo (pessoa física, no nome dele).
  Endereço, responsável técnico e logotipo são completados depois em Configurações & Backup.
- **Configurações & Backup → Dados da empresa**: "Tipo de cadastro: Pessoa jurídica (CNPJ) / Pessoa física (CPF)".
  Pessoa física: "Nome completo", campo CPF (sem a busca da Receita) e "Registro profissional (CREA/CFT)".
- Banco: **executar de novo `supabase/modules/cadastro.sql`** (a aprovação passa a criar a empresa). Testes do banco:
  todos passam (empresa criada na aprovação, pessoa jurídica, pessoa física sem documento e com CPF).

## 10.0.18 (APK versionCode 19) — Configurações em branco para empresa nova; telas do master
- **Empresa nova abre em branco** em Configurações & Backup (sem logotipo, RT, endereço… da empresa usada antes no
  mesmo aparelho). Antes, ao entrar com outra empresa, a plataforma partia dos dados da anterior. Agora os dados ficam
  marcados com a empresa dona (`ownerCompanyId`); ao trocar, começam em branco e os da empresa chegam do banco.
  Só a conexão com o banco é mantida.
- **Portal Público de Validação & Sincronização**, **Backup & Registros Fotográficos** e **Recarregar Dados do Banco**
  só aparecem para o administrador master.
- Teste novo: troca de empresa no aparelho (dados em branco; mesma empresa mantém os dados). 380 testes passando.

## 10.0.19 (APK versionCode 20) — o SPDA abre no Painel do SPDA
- Ao entrar na plataforma com o módulo SPDA, abria o painel dos **Ensaios de EPI** (com botões de EPI): o login, a troca
  de usuário e a empresa nova pediam a tela "dashboard", que é a dos ensaios. Agora:
  - a tela inicial de cada módulo é o painel dele (SPDA → **Painel do SPDA**; Treinamentos → painel dos treinamentos);
  - se algo pedir o painel dos Ensaios de EPI com outro módulo aberto, vai para o painel desse módulo.

## 10.0.20 (APK versionCode 21) — botão Sair no APP JVM SPDA
- Barra superior do APP: ícone **Sair** (à direita do Sincronizar). Abre um quadro com o nome do usuário e a empresa,
  aviso se houver alterações aguardando envio, e os botões **Cancelar** / **Sair do sistema** (logoff; volta à tela
  de login).

## 10.0.21 (plataforma web) — baixar o aplicativo de cada módulo
- Menu lateral da plataforma: botão laranja **Baixar o aplicativo**, conforme o módulo aberto:
  - **SPDA** → APP JVM SPDA (`apps/JVM-SPDA.apk`);
  - **Ensaios de EPI** → APP JVM Ensaios de EPI (`apps/JVM-Ensaios-EPI.apk`; instala por cima do app atual, mantendo os dados).
  Abre um quadro com **QR Code** (apontar a câmera do celular), o botão de download do .apk e o passo a passo de instalação.
  Não aparece dentro do próprio APP.
- Os APKs ficam em `public/apps/` e vão para o site ao publicar com `publicar.bat`. Os dois `compilar-apk.bat`
  (android-spda e app-ensaios-epi) copiam o APK novo para lá automaticamente.
- O APK do SPDA não leva essa pasta dentro dele (o build do app apaga `www/apps`); o modo offline (PWA) não guarda os APKs
  e não intercepta os links `/apps/`. `public/_headers`: `/apps/*` com tipo de arquivo de APK e download.
- Teste novo (APKs presentes e endereços). 384 testes passando.

## 10.0.22 (APK versionCode 22) — Sair do sistema pelo nome do usuário (igual ao app de EPI)
- Barra superior do APP JVM SPDA: no lugar do ícone Sair, o **nome do usuário** com as iniciais num círculo laranja.
  Ao tocar, abre o quadro com nome completo, perfil e empresa, e a opção **Sair do Sistema** (vermelha).
  Tocar em Sair do Sistema pede a confirmação (Cancelar / Sair), com aviso se houver alterações aguardando envio.
  Tocar fora do quadro fecha.

## 10.0.23 (plataforma web) — menu do usuário sem "Instalar App Android"
- Ao clicar no nome do usuário (barra superior da plataforma), a opção **Instalar App Android** foi retirada:
  o aplicativo do módulo é baixado pelo botão **Baixar o aplicativo** do menu lateral.

## 10.0.24 (APK versionCode 23 + plataforma web) — login vence a cada 7 dias; login offline no APP
- **Novo login a cada 7 dias** (APP JVM SPDA e plataforma web): passados 7 dias do último login com senha, o sistema
  volta à tela de login com o aviso "Por segurança, a cada 7 dias é preciso entrar novamente com usuário e senha."
  Vale ao abrir e também com o sistema aberto (confere a cada minuto e ao voltar para a tela).
  Trocar de empresa ou de perfil não renova o prazo; só digitar a senha de novo (online ou offline).
  Os dados no aparelho que ainda não foram enviados continuam lá.
- **Login offline**: sem internet, entra quem já entrou com internet neste aparelho nos últimos 30 dias (senha conferida
  pela prova PBKDF2 guardada no aparelho). Novo: com sinal fraco (conectado mas o banco não responde), quem tem
  acesso offline no aparelho entra offline depois de 15 s, em vez de ficar preso esperando.
- Testes novos (`authSession.test.ts`): prazo de 7 dias, troca de empresa, login offline, sinal fraco. 389 testes passando.

## 10.0.25 (APK versionCode 24 + plataforma web) — usuário bloqueado sai na hora
- Com internet, o sistema aberto confere no banco (jvm_me) a cada minuto, ao voltar para a tela e ao reconectar,
  se o usuário continua ativo:
  - **bloqueado ou excluído** → sai na hora ("Acesso bloqueado: Usuário inativo…") e a **senha offline dele é apagada
    neste aparelho** (não entra mais nem sem internet);
  - **acesso da empresa vencido** → sai com o aviso da validade;
  - sem internet ou erro de rede → não faz nada (o trabalho de campo continua).
- Ao tentar entrar com internet e o banco responder "inativo", a senha offline daquele login também é apagada.
- Limite que continua: um celular que nunca se conecta não fica sabendo do bloqueio; nele vale o prazo do login offline
  (30 dias desde o último login com internet) e o banco recusa qualquer envio desse usuário.
- Testes novos (bloqueio com o sistema aberto, login recusado, validade vencida, sem internet). 393 testes passando.

## 10.0.26 (APK versionCode 25) — miliohmímetro INBRAT INMD 1 Pro pelo Bluetooth
- Protocolo levantado do registro HCI de um ensaio feito com o Inbrat Connect: `android-spda/PROTOCOLO_INBRAT_INMD1PRO.md`
  (nome do ensaio → partida → resultado em ~6 s com resistência em mΩ e corrente em A). Conferido com o visor: 352,2 mΩ.
- **Cadastro do instrumento** (Instrumentos → editar): seção **Comunicação Bluetooth** → "INBRAT INMD 1 Pro" →
  **Parear** (busca 10 s; o aparelho aparece como "IN433A", marcado INBRAT). Grava nome e endereço do aparelho no
  instrumento (vai para o Supabase dentro do payload; nada muda no banco). Na plataforma web mostra o pareado e permite
  remover; o pareamento é pelo APP.
- **Ensaio de continuidade** (Captação/Descida/Aterramento/DPS/Ensaios, ao editar): botão azul **Bluetooth** ao lado
  do valor medido. Conecta, dá a partida, mostra o valor (mΩ), a corrente e o nº do ensaio no instrumento; **Usar valor**
  grava a resistência e a corrente no ensaio e registra a origem ("Lido pelo Bluetooth: INBRAT … nº … ensaio nº …").
  Fora de escala (pontas abertas, 3000 mΩ) ou erro: avisa e não grava. Digitar o valor à mão apaga a marca de origem.
  Usa o instrumento pareado com o mesmo nº de série do instrumento de continuidade da inspeção (ou o primeiro pareado).
- Android: `InbratBle.java` (busca e medição BLE) + ponte `bleScan/bleMeasure/bleCancel` no MainActivity; pede as
  permissões de Bluetooth (dispositivos próximos) e localização na primeira vez.
- Testes novos com os bytes reais do ensaio (352,244 mΩ / 1,143 A; 3000 mΩ = fora de escala). 399 testes passando.

## 10.0.27 (APK versionCode 26 + plataforma web) — app abrindo em branco
- **Causa principal:** o `index.html` carregava a fonte do Google Fonts no `<head>` antes do app. O WebView só executa
  o app depois de baixar esse estilo: com internet ruim (sinal fraco, Wi-Fi sem resposta) o download ficava pendurado
  e o app ficava em branco (sem internet nenhuma falhava rápido e abria). Agora a fonte é carregada pelo `main.tsx`
  depois que o app abre (`src/utils/bootWatchdog.ts`); sem ela, usa a fonte do sistema.
- **Tela de carregamento** no lugar do branco (logo + "Carregando…"). Se o app não abrir em 20 s: aviso e botão
  **Reiniciar o app** (no APP reinicia o processo inteiro — o mesmo efeito de reiniciar o celular; na web recarrega).
- **Armazenamento do celular travado** (ex.: o Android encerrou o app no meio de uma gravação — o relatório de bug de
  20/11 mostra o app encerrado por "one-time permission revoked"): abrir o banco com limite de 10 s e ler os dados com
  limite de 30 s. Se travar, o app NÃO abre vazio (abriria sem os dados e as gravações apagariam o que está guardado):
  mostra o aviso com **Reiniciar o app**. IndexedDB indisponível de verdade continua usando o localStorage.
- Android: `RestartActivity` (processo separado que encerra o principal e abre o app de novo), ponte `restartApp()`,
  e `onRenderProcessGone`: se o WebView cair (falta de memória), reinicia o app em vez de ficar em branco.
- Testes novos (banco que não abre, leitura que não termina, tela de carregamento, fonte). 404 testes passando.

## 10.0.28 (APK versionCode 27 + plataforma web + SQL) — licença do APP em um único celular
- **Executar uma vez no Supabase:** `supabase/modules/aparelhos.sql` (tabela `jvm_user_devices` e as funções
  `jvm_device_check`, `jvm_master_devices`, `jvm_master_release_device`). Já entra no `recriar_banco.sql`.
  Sem o script o app funciona normalmente, sem o limite.
- **APP JVM SPDA:** o 1º login de cada usuário registra o celular (ID fixo do Android — `ANDROID_ID` com hash —, que não
  muda ao reinstalar o app; muda só com restauração de fábrica). Login em outro celular: recusado com a mensagem
  "Esta licença do aplicativo já está em uso em outro celular (modelo), desde dd/mm/aaaa. Para usar este celular, peça ao
  administrador para liberar o aparelho." e o acesso offline daquele usuário é apagado nesse celular.
- **Troca de celular:** Acessos → Usuários → "Celular do APP JVM SPDA" → **Liberar aparelho** (só o master, com
  confirmação na tela). O próximo login no APP registra o celular novo; o antigo é desconectado na conferência
  seguinte (a cada minuto com internet).
- Sem limite: administrador master e a plataforma web. A licença é por aplicativo (`spda`).
- Limites: APKs anteriores a esta versão não mandam o ID do celular (não são limitados) — distribuir só esta versão
  em diante; celular sem internet não é conferido (vale o prazo do login offline).
- Testes: banco (PGlite: registra, recusa outro celular, usuário não apaga o próprio registro, admin de empresa não
  libera, master vê/libera, master sem limite) e login (recusa, desconexão do antigo, web sem limite). 409 testes.

## 10.0.29 (plataforma web + SQL) — novos acessos só com liberação do master
- **Executar uma vez no Supabase:** `supabase/modules/liberacao.sql` (coluna `users.awaiting_master`, gatilho
  `jvm_aa_master_approval` e `jvm_master_users` com "aguardando liberação"). Já entra no `recriar_banco.sql`.
- Regra no banco (vale para qualquer caminho: tela, troca de senha, sincronização): quando quem NÃO é master
  cadastra um usuário com acesso, dá acesso a quem não tinha login ou reativa um usuário inativo, o usuário fica
  **inativo e aguardando o master** (login bloqueado). Usuários que já existem não mudam.
- Corrigido: "Trocar senha" pelo admin da empresa reativava o usuário (podia desbloquear quem o master bloqueou).
- Admin da empresa continua podendo **bloquear**. Na tela dele: "Inativo — liberação pelo master", botão
  "Pedir reativação" e avisos de que o acesso depende do master.
- Master (Acessos → Usuários): aviso "N usuário(s) aguardando a sua liberação", filtro "Só aguardando liberação",
  selo **Aguardando liberação**; libera com **Desbloquear acesso** + **Salvar**.
- Testes de banco (PGlite): nasce inativo e com login bloqueado, empresa não ativa pela tabela nem pela senha,
  dar acesso também aguarda, master vê e libera, empresa bloqueia e reativação volta a aguardar.

## 10.0.30 (SQL + plataforma web) — usuário excluído libera o e-mail para novo cadastro
- **Executar uma vez no Supabase:** `supabase/modules/liberar_email.sql` (coluna `users.deleted_email` e gatilho
  `jvm_release_email`). Já entra no `recriar_banco.sql`. No fim, libera também os e-mails de quem já tinha sido excluído.
- Ao excluir um usuário (master, empresa ou sincronização), o registro continua no banco (histórico: laudos, ensaios e
  certificados mantêm o nome), mas o e-mail vira `excluido.<id>@sem-email.local` (o original fica em `deleted_email`),
  o nome de usuário é apagado e a conta de login antiga troca de e-mail junto e continua bloqueada.
- "Criar conta", o cadastro pela empresa e a aprovação do master aceitam o mesmo e-mail de novo: é criado um usuário
  **novo**, com conta de login nova (não herda perfil, assinatura nem empresa do antigo) e segue a liberação pelo master.
- Textos de exclusão atualizados (antes diziam que o e-mail ficava reservado).
- Testes de banco: e-mail liberado, conta antiga bloqueada com o endereço interno, novo cadastro com o mesmo e-mail
  cria usuário e login novos, pedido de conta com e-mail de excluído aceito, registro excluído não volta a ocupar o e-mail.

## 10.0.31 (APK versionCode 28 + plataforma web) — nível de proteção (NP) pode ficar sem valor
- No cadastro de edificações (APP e plataforma), o campo **Nível de proteção (NP)** agora aceita **"Sem nível definido"**
  e nova edificação já nasce sem NP (antes vinha "Nível III" por padrão).
- Sem NP: não há mínimo de descidas a calcular e os parâmetros da NBR (esfera rolante, malha, espaçamento) aparecem como
  "a definir" em vez de quebrar a tela. O resumo da inspeção usa o NP mais restritivo entre as edificações **que têm NP**;
  se nenhuma tiver, fica "Não definido". Relatórios, laudo técnico e descrições dos subsistemas tratam o NP vazio.
- Testes novos (nova edificação sem NP, sem cálculo de descidas, resumo ignora edificação sem NP). 412 testes passando.

## 10.0.32 (APK versionCode 29) — fotos e arquivos na pasta "JVM SPDA"
- No APP, a pasta de downloads passou a se chamar **JVM SPDA** (antes `JVM_SPDA`, com sublinhado), igual ao nome do app.
- Confirmado o que já existia: no APP, cada foto tirada ou anexada é gravada também em
  **Download/JVM SPDA/<OS>/<edificação>/<subsistema>/**, com o nome "legenda_AAAAMMDD-HHMMSS.jpg" (sem OS: nº da inspeção).
  Os PDFs e documentos continuam em Download/JVM SPDA. Na plataforma web (navegador) não há essa gravação automática.

## 10.0.33 (plataforma web) — versão do app no botão "Baixar o aplicativo"
- No menu lateral, o botão "Baixar o aplicativo" e o quadro de download agora mostram a **versão** do aplicativo
  (ex.: "Android · versão 10.0.32").
- A versão fica num arquivo ao lado do APK (`public/apps/JVM-SPDA.version.txt` e `JVM-Ensaios-EPI.version.txt`), que os
  dois `compilar-apk.bat` gravam automaticamente a cada compilação (lendo do output-metadata.json do Android). O modal lê
  esse arquivo; se não houver, o botão apenas não mostra a versão. `public/_headers`: `/apps/*.txt` servido como texto.
- Teste atualizado (arquivos de versão presentes em public/apps). 412 testes passando.

## 10.0.34 (plataforma web) — checklist ao editar o laudo e assinaturas por cadastro (EPI)
- **Editar laudo de EPI:** o checklist do laudo voltou a aparecer. Se o laudo veio sem o checklist salvo (laudos antigos
  ou sincronizados sem itens), carrega o checklist padrão do tipo de equipamento para edição, mantendo as respostas
  já salvas quando existirem.
- **Laudo e relatórios (técnico consolidado e resumo de lote):** o nome e a **assinatura** do Analista Executor e do
  Responsável Técnico são buscados no cadastro (Usuários / Configurações) ignorando maiúsculas e espaços no nome, e a
  imagem da assinatura é embutida no PDF. O laudo já casava por id; agora o nome também casa de forma tolerante, então a
  assinatura cadastrada aparece mesmo com pequena diferença de digitação. RT sem correspondência usa a assinatura do
  Responsável Técnico do menu Configurações.

## 10.0.35 (plataforma web) — assinaturas na tela da Ordem de Serviço
- Na tela de detalhes da OS, abaixo do escopo/ART, agora aparecem **Analista Executor** e **Responsável Técnico** com o
  **nome e a imagem da assinatura** do cadastro (Usuários / Configurações): o executor pela técnico da OS (id ou nome),
  o RT pelo responsável da OS, ou o Responsável Técnico do menu Configurações. Sem assinatura cadastrada, mostra
  "Assinatura não cadastrada". É só exibição na tela (sem PDF da OS).

## 10.0.36 (plataforma web + APP Ensaios de EPI) — cópias da etiqueta digitáveis
- **Plataforma web (etiqueta Niimbot):** no quadro de impressão, nova caixa **"Cópias por etiqueta"** (digitável, 1 a 99,
  com − e +). A impressão direta no driver térmico sai com cada etiqueta repetida esse número de vezes; o botão mostra o
  total de etiquetas.
- **APP de Ensaios de EPI:** o seletor de cópias (antes fixo em 1/2/3) virou uma **caixa para digitar** a quantidade
  (1 a 99), com − e +. APK recompilado (versão 6.1.261125.1646).
- Correção: os dois `compilar-apk.bat` passaram a gravar o arquivo de versão do APK via PowerShell direto (o modo
  anterior gravava texto inválido no arquivo da versão do app de EPI).

## 10.0.37 (plataforma web) — remover a faixa "App Android JVM" do menu lateral
- Saiu do menu lateral a faixa antiga "App Android JVM — Modo Campo & PWA" (botões "Abrir Modo Android" e
  "Gerar APK / Instalar App"). O download do aplicativo fica no botão **Baixar o aplicativo** (que mostra a versão).
