-- =========================================================================
-- CADASTRO PÚBLICO COM APROVAÇÃO ("Criar conta")
--
-- Qualquer pessoa pode PEDIR uma conta pela tela de login (plataforma web ou
-- APP JVM SPDA). O pedido fica "pendente" e NÃO dá acesso a nada. Só um
-- administrador master aprova (cria o usuário administrador da nova empresa,
-- que cadastra a empresa no primeiro acesso) ou recusa.
--
-- - A senha é gravada no pedido já criptografada (bcrypt); ninguém a vê.
-- - A tabela de pedidos não é lida nem gravada diretamente: só pelas funções.
-- - O cadastro público do Supabase Auth continua DESLIGADO.
--
-- IDEMPOTENTE: execute no SQL Editor do Supabase DEPOIS do schema.sql.
-- =========================================================================

CREATE TABLE IF NOT EXISTS public.account_requests (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  email TEXT NOT NULL,
  username TEXT,
  phone TEXT,
  company_name TEXT,
  cnpj TEXT,
  module TEXT,
  password_hash TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'pendente',
  decision_note TEXT,
  decided_by TEXT,
  decided_at TIMESTAMPTZ,
  user_id TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'account_requests_status_check') THEN
    ALTER TABLE public.account_requests
      ADD CONSTRAINT account_requests_status_check CHECK (status IN ('pendente', 'aprovado', 'recusado'));
  END IF;
END $$;

-- um pedido pendente por e-mail
CREATE UNIQUE INDEX IF NOT EXISTS idx_account_requests_pending_email
  ON public.account_requests (lower(email)) WHERE status = 'pendente';
CREATE INDEX IF NOT EXISTS idx_account_requests_status ON public.account_requests (status, created_at);

-- acesso só pelas funções abaixo
ALTER TABLE public.account_requests ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.account_requests FROM PUBLIC, anon, authenticated;

-- -------------------------------------------------------------------------
-- Pedir conta (sem login)
-- -------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.jvm_request_account(
  p_name TEXT,
  p_email TEXT,
  p_password TEXT,
  p_company_name TEXT,
  p_cnpj TEXT DEFAULT NULL,
  p_phone TEXT DEFAULT NULL,
  p_username TEXT DEFAULT NULL,
  p_module TEXT DEFAULT NULL
)
RETURNS JSON
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
  v_email TEXT := lower(trim(COALESCE(p_email, '')));
  v_username TEXT := NULLIF(lower(trim(COALESCE(p_username, ''))), '');
  v_cnpj TEXT := NULLIF(regexp_replace(COALESCE(p_cnpj, ''), '\D', '', 'g'), '');
  v_id TEXT := 'req-' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 16);
BEGIN
  IF length(trim(COALESCE(p_name, ''))) < 3 OR length(p_name) > 120 THEN
    RAISE EXCEPTION 'Informe o seu nome completo.' USING ERRCODE = '22023';
  END IF;
  IF v_email !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' OR length(v_email) > 160 THEN
    RAISE EXCEPTION 'Informe um e-mail válido (ele será usado no login).' USING ERRCODE = '22023';
  END IF;
  IF length(trim(COALESCE(p_company_name, ''))) < 2 OR length(p_company_name) > 160 THEN
    RAISE EXCEPTION 'Informe o nome da empresa.' USING ERRCODE = '22023';
  END IF;
  IF v_cnpj IS NOT NULL AND length(v_cnpj) NOT IN (11, 14) THEN
    RAISE EXCEPTION 'CNPJ (ou CPF) inválido.' USING ERRCODE = '22023';
  END IF;
  IF v_username IS NOT NULL AND v_username !~ '^[a-z0-9._-]{3,40}$' THEN
    RAISE EXCEPTION 'Nome de usuário inválido: use de 3 a 40 letras, números, ponto, hífen ou sublinhado.' USING ERRCODE = '22023';
  END IF;
  PERFORM public.jvm_validate_new_password(p_password);
  IF length(p_password) > 72 THEN
    RAISE EXCEPTION 'A senha deve ter no máximo 72 caracteres.' USING ERRCODE = '22023';
  END IF;

  IF EXISTS (SELECT 1 FROM public.users WHERE lower(email) = v_email) THEN
    RAISE EXCEPTION 'Já existe uma conta com este e-mail. Use "Entrar" ou "Esqueci minha senha".' USING ERRCODE = '23505';
  END IF;
  IF v_username IS NOT NULL AND EXISTS (SELECT 1 FROM public.users WHERE lower(username) = v_username) THEN
    RAISE EXCEPTION 'Este nome de usuário já está em uso.' USING ERRCODE = '23505';
  END IF;
  IF EXISTS (SELECT 1 FROM public.account_requests WHERE lower(email) = v_email AND status = 'pendente') THEN
    RAISE EXCEPTION 'Já existe um pedido de conta com este e-mail aguardando aprovação.' USING ERRCODE = '23505';
  END IF;
  -- proteção contra pedidos em massa
  IF (SELECT count(*) FROM public.account_requests WHERE created_at > NOW() - interval '1 hour') >= 30 THEN
    RAISE EXCEPTION 'Muitos pedidos de conta agora. Tente novamente mais tarde.' USING ERRCODE = '54000';
  END IF;

  INSERT INTO public.account_requests (id, name, email, username, phone, company_name, cnpj, module, password_hash)
  VALUES (v_id, trim(p_name), v_email, v_username, NULLIF(trim(COALESCE(p_phone, '')), ''), trim(p_company_name), v_cnpj,
          NULLIF(trim(COALESCE(p_module, '')), ''), crypt(p_password, gen_salt('bf', 10)));

  RETURN json_build_object('ok', true, 'id', v_id);
END;
$$;

REVOKE ALL ON FUNCTION public.jvm_request_account(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.jvm_request_account(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) TO anon, authenticated;

-- -------------------------------------------------------------------------
-- Pedidos (só administrador master)
-- -------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.jvm_list_account_requests(p_status TEXT DEFAULT 'pendente')
RETURNS JSON
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT public.jvm_is_master() THEN
    RAISE EXCEPTION 'Somente o administrador master vê os pedidos de conta.' USING ERRCODE = '42501';
  END IF;
  RETURN COALESCE((
    SELECT json_agg(json_build_object(
      'id', r.id, 'name', r.name, 'email', r.email, 'username', r.username, 'phone', r.phone,
      'companyName', r.company_name, 'cnpj', r.cnpj, 'module', r.module, 'status', r.status,
      'decisionNote', r.decision_note, 'decidedAt', r.decided_at, 'createdAt', r.created_at
    ) ORDER BY r.created_at DESC)
      FROM public.account_requests r
     WHERE p_status IS NULL OR r.status = p_status
  ), '[]'::json);
END;
$$;

REVOKE ALL ON FUNCTION public.jvm_list_account_requests(TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.jvm_list_account_requests(TEXT) TO authenticated;

-- Aprovar (cria o usuário administrador da nova empresa, sem empresa vinculada:
-- no primeiro acesso ele cadastra a empresa) ou recusar.
CREATE OR REPLACE FUNCTION public.jvm_decide_account_request(p_id TEXT, p_approve BOOLEAN, p_note TEXT DEFAULT NULL)
RETURNS JSON
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  r public.account_requests%ROWTYPE;
  v_me TEXT;
  v_user_id TEXT;
BEGIN
  IF NOT public.jvm_is_master() THEN
    RAISE EXCEPTION 'Somente o administrador master aprova pedidos de conta.' USING ERRCODE = '42501';
  END IF;
  SELECT id INTO v_me FROM public.users WHERE auth_user_id = auth.uid() LIMIT 1;
  SELECT * INTO r FROM public.account_requests WHERE id = p_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Pedido não encontrado.' USING ERRCODE = 'P0002';
  END IF;
  IF r.status <> 'pendente' THEN
    RAISE EXCEPTION 'Este pedido já foi %.', r.status USING ERRCODE = '22023';
  END IF;

  IF NOT p_approve THEN
    UPDATE public.account_requests
       SET status = 'recusado', decision_note = NULLIF(trim(COALESCE(p_note, '')), ''), decided_by = v_me, decided_at = NOW()
     WHERE id = p_id;
    RETURN json_build_object('ok', true, 'status', 'recusado');
  END IF;

  IF EXISTS (SELECT 1 FROM public.users WHERE lower(email) = lower(r.email)) THEN
    RAISE EXCEPTION 'Já existe um usuário com o e-mail %.', r.email USING ERRCODE = '23505';
  END IF;
  IF r.username IS NOT NULL AND EXISTS (SELECT 1 FROM public.users WHERE lower(username) = lower(r.username)) THEN
    RAISE EXCEPTION 'O nome de usuário % já está em uso.', r.username USING ERRCODE = '23505';
  END IF;

  v_user_id := 'usr-' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 16);
  -- libera a criação da conta de login nesta transação (ver jvm_sync_auth_user)
  PERFORM set_config('jvm.trusted_auth_sync', 'on', true);
  INSERT INTO public.users (id, company_id, name, email, username, role, phone, active, is_master_admin, password_hash)
  VALUES (v_user_id, NULL, r.name, lower(r.email), r.username, 'admin', r.phone, true, false, r.password_hash);
  PERFORM set_config('jvm.trusted_auth_sync', 'off', true);

  UPDATE public.account_requests
     SET status = 'aprovado', decision_note = NULLIF(trim(COALESCE(p_note, '')), ''), decided_by = v_me,
         decided_at = NOW(), user_id = v_user_id,
         password_hash = '-'  -- a senha agora vale só na conta de login
   WHERE id = p_id;

  RETURN json_build_object('ok', true, 'status', 'aprovado', 'userId', v_user_id);
END;
$$;

REVOKE ALL ON FUNCTION public.jvm_decide_account_request(TEXT, BOOLEAN, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.jvm_decide_account_request(TEXT, BOOLEAN, TEXT) TO authenticated;
