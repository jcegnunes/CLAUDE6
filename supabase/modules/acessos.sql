-- =========================================================================
-- MÓDULO GESTÃO DE ACESSOS (só o administrador master)
--
-- - Validade do acesso por empresa (companies.access_expires_at): vencida, os
--   usuários da empresa não entram (jvm_me) e o banco não mostra nem grava dados
--   dela (jvm_my_company devolve vazio). Master nunca é bloqueado.
-- - Módulos por empresa (lab_info.enabledModules) valem também NO BANCO
--   (jvm_can_use_module), não só no menu.
-- - Funções do master: empresas (módulos e validade) e usuários (bloqueio,
--   perfil e módulos liberados). Os pedidos de conta ficam em cadastro.sql.
--
-- As três funções de acesso abaixo (jvm_my_company, jvm_can_use_module e
-- jvm_me) são as MESMAS do schema.sql desta versão: executar qualquer um dos
-- dois arquivos deixa o banco igual.
--
-- IDEMPOTENTE: execute no SQL Editor do Supabase DEPOIS do schema.sql.
-- =========================================================================

ALTER TABLE public.companies ADD COLUMN IF NOT EXISTS access_expires_at DATE;

-- -------------------------------------------------------------------------
-- Funções de acesso (iguais às do schema.sql)
-- -------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.jvm_my_company()
RETURNS TEXT
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public
AS $$
  -- empresa com acesso vencido: o usuário não enxerga nem grava dados dela (master nunca vence)
  SELECT u.company_id FROM public.users u
    LEFT JOIN public.companies c ON c.id = u.company_id
   WHERE u.auth_user_id = auth.uid() AND u.deleted_at IS NULL AND u.active IS NOT FALSE
     AND (u.is_master_admin IS TRUE OR c.access_expires_at IS NULL OR c.access_expires_at >= CURRENT_DATE)
   LIMIT 1
$$;

CREATE OR REPLACE FUNCTION public.jvm_can_use_module(p_module TEXT)
RETURNS BOOLEAN
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public
AS $$
  -- liberado para o usuário (admin: todos; vazio: todos) E ligado para a empresa (lab_info.enabledModules)
  SELECT public.jvm_is_master() OR COALESCE((
    SELECT (u.role = 'admin' OR u.allowed_modules IS NULL OR p_module = ANY (u.allowed_modules))
           AND COALESCE(c.lab_info -> 'enabledModules' -> p_module, 'true'::jsonb) <> 'false'::jsonb
      FROM public.users u
      LEFT JOIN public.companies c ON c.id = u.company_id
     WHERE u.auth_user_id = auth.uid() AND u.deleted_at IS NULL AND u.active IS NOT FALSE
     LIMIT 1), false)
$$;

CREATE OR REPLACE FUNCTION public.jvm_me()
RETURNS JSON
LANGUAGE plpgsql STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  u public.users%ROWTYPE;
  v_exp DATE;
BEGIN
  IF auth.uid() IS NULL THEN
    RETURN json_build_object('ok', false, 'error', 'Sessão inválida. Entre novamente.');
  END IF;
  SELECT * INTO u FROM public.users
   WHERE auth_user_id = auth.uid() AND deleted_at IS NULL
   LIMIT 1;
  IF NOT FOUND THEN
    RETURN json_build_object('ok', false, 'error', 'Usuário não cadastrado no sistema. Contate o administrador.');
  END IF;
  IF u.active IS FALSE THEN
    RETURN json_build_object('ok', false, 'error', 'Usuário inativo. Contate o administrador.');
  END IF;
  IF u.is_master_admin IS NOT TRUE AND u.company_id IS NOT NULL THEN
    SELECT access_expires_at INTO v_exp FROM public.companies WHERE id = u.company_id;
    IF v_exp IS NOT NULL AND v_exp < CURRENT_DATE THEN
      RETURN json_build_object('ok', false, 'error',
        'O acesso da empresa venceu em ' || to_char(v_exp, 'DD/MM/YYYY') || '. Contate o administrador da plataforma para renovar.');
    END IF;
  END IF;
  RETURN json_build_object('ok', true, 'user', to_jsonb(u) - 'password_hash');
END;
$$;

REVOKE ALL ON FUNCTION public.jvm_me() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.jvm_me() TO authenticated;
REVOKE ALL ON FUNCTION public.jvm_my_company() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.jvm_my_company() TO authenticated;
REVOKE ALL ON FUNCTION public.jvm_can_use_module(TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.jvm_can_use_module(TEXT) TO authenticated;

-- -------------------------------------------------------------------------
-- Empresas (master)
-- -------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.jvm_master_companies()
RETURNS JSON
LANGUAGE plpgsql STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT public.jvm_is_master() THEN
    RAISE EXCEPTION 'Somente o administrador master gerencia as empresas.' USING ERRCODE = '42501';
  END IF;
  RETURN COALESCE((
    SELECT json_agg(json_build_object(
      'id', c.id, 'name', c.name, 'legalName', c.legal_name, 'cnpj', c.cnpj, 'city', c.city, 'state', c.state,
      'email', c.email, 'phone', c.phone, 'createdAt', c.created_at, 'accessExpiresAt', c.access_expires_at,
      'enabledModules', COALESCE(c.lab_info -> 'enabledModules', '{}'::jsonb),
      'users', (SELECT count(*) FROM public.users u WHERE u.company_id = c.id AND u.deleted_at IS NULL),
      'usersWithLogin', (SELECT count(*) FROM public.users u WHERE u.company_id = c.id AND u.deleted_at IS NULL AND u.auth_user_id IS NOT NULL)
    ) ORDER BY lower(c.name))
      FROM public.companies c
     WHERE c.deleted_at IS NULL
  ), '[]'::json);
END;
$$;

REVOKE ALL ON FUNCTION public.jvm_master_companies() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.jvm_master_companies() TO authenticated;

-- módulos ligados (ex.: {"spda": true, "treinamentos": false}) e validade (NULL = sem vencimento)
CREATE OR REPLACE FUNCTION public.jvm_master_set_company(p_company_id TEXT, p_enabled_modules JSONB, p_expires_at DATE)
RETURNS JSON
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_mods JSONB := COALESCE(p_enabled_modules, '{}'::jsonb);
BEGIN
  IF NOT public.jvm_is_master() THEN
    RAISE EXCEPTION 'Somente o administrador master gerencia as empresas.' USING ERRCODE = '42501';
  END IF;
  IF jsonb_typeof(v_mods) <> 'object' THEN
    RAISE EXCEPTION 'Módulos inválidos.' USING ERRCODE = '22023';
  END IF;
  UPDATE public.companies
     SET lab_info = COALESCE(lab_info, '{}'::jsonb)
                    || jsonb_build_object('enabledModules', COALESCE(lab_info -> 'enabledModules', '{}'::jsonb) || v_mods),
         access_expires_at = p_expires_at
   WHERE id = p_company_id AND deleted_at IS NULL;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Empresa não encontrada.' USING ERRCODE = 'P0002';
  END IF;
  RETURN json_build_object('ok', true);
END;
$$;

REVOKE ALL ON FUNCTION public.jvm_master_set_company(TEXT, JSONB, DATE) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.jvm_master_set_company(TEXT, JSONB, DATE) TO authenticated;

-- -------------------------------------------------------------------------
-- Usuários (master)
-- -------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.jvm_master_users(p_company_id TEXT DEFAULT NULL)
RETURNS JSON
LANGUAGE plpgsql STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT public.jvm_is_master() THEN
    RAISE EXCEPTION 'Somente o administrador master gerencia os usuários.' USING ERRCODE = '42501';
  END IF;
  RETURN COALESCE((
    SELECT json_agg(json_build_object(
      'id', u.id, 'name', u.name, 'email', u.email, 'username', u.username, 'role', u.role,
      'active', u.active IS NOT FALSE, 'isMasterAdmin', u.is_master_admin IS TRUE,
      'hasLogin', u.auth_user_id IS NOT NULL, 'allowedModules', u.allowed_modules,
      'companyId', u.company_id, 'companyName', COALESCE(c.name, u.company_name), 'createdAt', u.created_at
    ) ORDER BY lower(COALESCE(c.name, u.company_name, '')), lower(u.name))
      FROM public.users u
      LEFT JOIN public.companies c ON c.id = u.company_id
     WHERE u.deleted_at IS NULL AND (p_company_id IS NULL OR u.company_id = p_company_id)
  ), '[]'::json);
END;
$$;

REVOKE ALL ON FUNCTION public.jvm_master_users(TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.jvm_master_users(TEXT) TO authenticated;

-- bloquear/desbloquear, perfil e módulos liberados (NULL = todos). Bloquear encerra as sessões abertas.
CREATE OR REPLACE FUNCTION public.jvm_master_set_user(p_user_id TEXT, p_active BOOLEAN, p_role TEXT, p_allowed_modules TEXT[])
RETURNS JSON
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  u public.users%ROWTYPE;
BEGIN
  IF NOT public.jvm_is_master() THEN
    RAISE EXCEPTION 'Somente o administrador master gerencia os usuários.' USING ERRCODE = '42501';
  END IF;
  SELECT * INTO u FROM public.users WHERE id = p_user_id AND deleted_at IS NULL;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Usuário não encontrado.' USING ERRCODE = 'P0002';
  END IF;
  IF p_role NOT IN ('admin', 'responsavel_tecnico', 'tecnico', 'administrativo', 'cliente') THEN
    RAISE EXCEPTION 'Perfil inválido: %', p_role USING ERRCODE = '22023';
  END IF;
  IF u.auth_user_id = auth.uid() AND p_active IS FALSE THEN
    RAISE EXCEPTION 'Você não pode bloquear o seu próprio acesso.' USING ERRCODE = '22023';
  END IF;
  UPDATE public.users
     SET active = COALESCE(p_active, true), role = p_role, allowed_modules = p_allowed_modules
   WHERE id = p_user_id;
  RETURN json_build_object('ok', true);
END;
$$;

REVOKE ALL ON FUNCTION public.jvm_master_set_user(TEXT, BOOLEAN, TEXT, TEXT[]) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.jvm_master_set_user(TEXT, BOOLEAN, TEXT, TEXT[]) TO authenticated;
