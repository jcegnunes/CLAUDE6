# Avaliação da sincronização APK ↔ Supabase (plataforma web v7.2)

Referência: `jvm-dielectric-lab_v7.2/supabase/schema.sql` e `src/services/supabaseMappers.ts`.
Projeto Supabase: `cdtbzbshylrcprvmjpgc` (mesmo URL e chave pública nos dois sistemas).

## O que já estava correto
- Login pelo Supabase Auth (`/auth/v1/token`), `jvm_login_email`, `jvm_me`, renovação do token.
- Validação pública de certificado pela RPC `jvm_validar_certificado`.
- Nomes das 7 tabelas usadas pelo app.

## Problemas encontrados no APK v6 (antes da correção)

| # | Gravidade | Problema | Efeito |
|---|---|---|---|
| 1 | Bloqueante | O app enviava colunas que não existem no banco (`company_name`, `user_id`, `user_name`, `created_by`, `photo_uris`, `password`, `login`, `registration_council`, `signature_image_uri`, `logo_uri`, `address`, `website`, `crea_company_register`) | O PostgREST recusa o lote inteiro (PGRST204): **clientes, equipamentos, OS, laudos, empresas e usuários não eram gravados** |
| 2 | Bloqueante | Datas enviadas em `dd/MM/yyyy` para colunas `DATE`; vencimento vazio enviado como `""` | Erro de data (lote recusado) ou dia e mês trocados (06/10 gravado como 10/06) |
| 3 | Bloqueante | Linhas do mesmo lote com chaves diferentes (campos omitidos quando vazios) | Lote recusado (PGRST102) |
| 4 | Bloqueante | `users?select=*`: as colunas `password_hash`/`auth_user_id` não são legíveis | Download de usuários sempre falhava |
| 5 | Alta | Tipo do equipamento em maiúsculas (`LUVA_ISOLANTE`); a web usa `luva_isolante` | A web não reconhecia o tipo / a norma |
| 6 | Alta | Exclusão física (DELETE); a web usa `deleted_at` | Técnico não conseguia excluir (só admin); a exclusão não chegava aos outros aparelhos; registros apagados na web voltavam no APK |
| 7 | Alta | O APK não atualizava o `payload`; a web lê o payload antes das colunas | Edições feitas no APK em registros criados na web não apareciam na web |
| 8 | Alta | "Sincronizar" baixava antes de enviar e o envio geral regravava TODOS os registros | Edições offline perdidas; edições feitas na web sobrescritas pelo APK |
| 9 | Média | Laudo e equipamento enviados em paralelo | Falha de chave estrangeira (laudo antes do equipamento) |
| 10 | Média | Download sem paginação | Só os primeiros 1000 registros de cada tabela |
| 11 | Média | `optString` em colunas nulas | Texto "null" aparecendo nos campos |
| 12 | Média | Erros do envio automático descartados em silêncio | Nenhum aviso quando a gravação falhava |

## O que foi corrigido (`SupabaseService.kt` e `DielectricRepository.kt`)
- Mapeamento com as mesmas colunas da web v7.2; nenhuma coluna inexistente.
- Datas convertidas para `yyyy-MM-dd` (envio) e de volta para `dd/MM/yyyy` (download); vazio → `null`.
- Tipos/status em minúsculas (`bota_dieletrica` → `bota_dielétrica`); perfis convertidos para `admin`/`responsavel_tecnico`/`tecnico`.
- Payload da web preservado: os campos do APK são mesclados no payload existente.
- Exclusão lógica (`deleted_at`) e remoção local do que foi excluído na nuvem.
- Envio geral não sobrescreve registros cuja última gravação veio da web (`device_id`); o APK grava `device_id = android-apk`.
- "Sincronizar" agora envia primeiro e depois baixa.
- Equipamento enviado antes do laudo; paginação de 1000 em 1000; falhas registradas no Logcat (`SupabaseService`, `DielectricRepo`).
- Usuários fora do envio geral; a senha nunca é enviada nem baixada.

## Segunda etapa — pendências corrigidas

| Pendência | Correção |
|---|---|
| Login offline aceitava `123`/`admin` para qualquer usuário; usuários de demonstração (SeedData) | Login offline só para quem já entrou online neste aparelho, pelo e-mail/usuário exato, com senha guardada como hash PBKDF2 (`util/PasswordHasher.kt`). Senha recusada pelo servidor não tenta o modo offline. Usuários de demonstração não são mais criados. Conta de login sem cadastro ativo em `public.users` não entra (antes o app inventava um perfil). A migração apaga as senhas locais antigas (texto puro). |
| Fotos e assinaturas não iam para a nuvem | Enviadas ao bucket `jvm-evidencias/<empresa>/<laudo>/` antes do laudo; `photos`, `technician_signature` e `tech_responsible_signature` gravados no formato da web. O vínculo arquivo local → URL fica em `remoteMedia`. Laudo com mídia que falhou continua pendente e é reenviado. Fotos/assinaturas de laudos da web aparecem no APK pelas URLs. |
| Numeração podia repetir números da web | ENS/LAU/CERT/OS vêm de faixas reservadas pela RPC `jvm_reserve_numbers` (20 por vez, renovadas ao entrar/sincronizar), guardadas na tabela `number_blocks` para uso offline. Sem faixa e sem internet, usa a contagem local (aviso no Logcat). |
| Edição offline podia ser perdida | Coluna `syncStatus` (Room 11 → 12): todo salvamento fica `pending` até chegar ao Supabase; o envio manda só o que está pendente e o download não substitui registros pendentes. Exclusões offline ficam na fila `pending_deletes`. Registros anteriores à atualização ficam `legacy` (enviados uma vez sem sobrescrever o que a web alterou). |
| **Novo:** o app apagava todos os clientes/equipamentos/laudos locais a cada abertura (`seedDatabaseIfEmpty`) | Removido: ensaios offline não enviados eram perdidos ao reabrir o app. |
| **Novo:** validação de certificado no APK mostrava os campos vazios | A resposta de `jvm_validar_certificado` (campos em `payload`) agora é lida corretamente. |
| **Novo:** sessão restaurada não sincronizava | Ao abrir o app com sessão salva (e no login), envia o pendente e baixa da nuvem. |
| Código de validação curto (6 caracteres) | 10 caracteres aleatórios. |

## Pendências restantes
1. Tipo `ATERRAMENTO_TEMPORARIO` não existe na web (vai como `aterramento_temporario`; a web precisa incluir o tipo).
2. Normas e relatórios consolidados continuam sem sincronização pelo APK (o modelo de normas do APK é diferente do da web).
3. Números reservados e não usados (assistente aberto e cancelado) ficam como "buracos" na sequência — não há duplicidade.
4. Código não compilado nesta máquina (sem Gradle): compile no Android Studio. Atualizar sobre uma instalação existente para testar a migração 11 → 12 (o banco tem `fallbackToDestructiveMigration`: se a migração falhar, os dados locais são apagados — faça backup antes).

## Como testar
1. Abra o projeto no Android Studio e gere o APK (o código não foi compilado aqui: não há Gradle nesta máquina).
2. Entre com um usuário que já tem login na plataforma (perfil técnico ou superior, módulo Ensaios liberado).
3. Cadastre um cliente, um equipamento e um ensaio com data de dia > 12 (ex.: 25/10/2026).
4. No Supabase → Table Editor, confira `clients`, `equipment` e `test_records` (`device_id = android-apk`, `test_date = 2026-10-25`).
5. Na plataforma web, use "Sincronizar" e confira se os três registros aparecem; edite o ensaio na web e sincronize o APK.
6. Exclua o equipamento no APK e confira `deleted_at` preenchido e o registro sumindo da web.
7. Fotos: faça um ensaio com 2 fotos e assinatura; confira os arquivos em Storage → `jvm-evidencias/<empresa>/<laudo>/` e as fotos no laudo da web.
8. Offline: modo avião, faça login (mesma senha do último login online), crie um ensaio, exclua um equipamento; volte a internet e sincronize. Confira o ensaio (número LAU-… sem repetir os da web) e o `deleted_at` do equipamento.
9. Senha errada com internet não entra; usuário `admin`/`123` não entra.
