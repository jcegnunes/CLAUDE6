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
