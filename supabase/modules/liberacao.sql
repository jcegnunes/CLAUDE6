-- =============================================================================
-- Novos acessos só com liberação do administrador master
-- Executar UMA vez no SQL Editor do Supabase (depois de acessos.sql). Pode ser executado de novo.
--
-- Quando alguém que NÃO é master:
--   - cadastra um usuário com acesso (senha) — jvm_admin_create_user;
--   - dá acesso a um usuário que não tinha login — jvm_admin_set_password;
--   - reativa um usuário inativo (tela da empresa, troca de senha ou sincronização);
-- o usuário fica INATIVO e "aguardando liberação do master" (não consegue entrar).
-- O master libera em Acessos → Usuários (Desbloquear acesso + Salvar).
-- Usuários que já existem não mudam. Bloquear continua permitido ao admin da empresa.
-- =============================================================================

ALTER TABLE public.users ADD COLUMN IF NOT EXISTS awaiting_master BOOLEAN NOT NULL DEFAULT false;

CREATE OR REPLACE FUNCTION public.jvm_master_approval()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
  -- SQL Editor (sem login) e o administrador master decidem
  IF auth.uid() IS NULL OR public.jvm_is_master() THEN
    IF TG_OP = 'UPDATE' AND NEW.active IS TRUE AND OLD.active IS NOT TRUE THEN
      NEW.awaiting_master := false; -- liberado
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    -- usuário novo com senha = com acesso ao sistema
    IF COALESCE(NEW.password_hash, '') <> '' THEN
      NEW.active := false;
      NEW.awaiting_master := true;
    ELSE
      NEW.awaiting_master := false;
    END IF;
    RETURN NEW;
  END IF;

  NEW.awaiting_master := OLD.awaiting_master; -- só o master muda
  IF NEW.active IS TRUE AND OLD.active IS NOT TRUE THEN
    -- reativação pedida pela empresa: fica aguardando o master
    NEW.active := false;
    NEW.awaiting_master := true;
  ELSIF OLD.auth_user_id IS NULL
        AND COALESCE(NEW.password_hash, '') <> ''
        AND NEW.password_hash IS DISTINCT FROM OLD.password_hash THEN
    -- primeiro acesso de um usuário que não tinha login
    NEW.active := false;
    NEW.awaiting_master := true;
  END IF;
  RETURN NEW;
END;
$$;

-- "jvm_aa_" roda antes dos outros gatilhos (ordem alfabética): o jvm_sync_auth já vê o usuário inativo
DROP TRIGGER IF EXISTS jvm_aa_master_approval ON public.users;
CREATE TRIGGER jvm_aa_master_approval
  BEFORE INSERT OR UPDATE ON public.users
  FOR EACH ROW EXECUTE FUNCTION public.jvm_master_approval();

-- Lista do master com "aguardando liberação"
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
      'companyId', u.company_id, 'companyName', COALESCE(c.name, u.company_name), 'createdAt', u.created_at,
      'awaitingMaster', u.awaiting_master IS TRUE
    ) ORDER BY u.awaiting_master DESC, lower(COALESCE(c.name, u.company_name, '')), lower(u.name))
      FROM public.users u
      LEFT JOIN public.companies c ON c.id = u.company_id
     WHERE u.deleted_at IS NULL AND (p_company_id IS NULL OR u.company_id = p_company_id)
  ), '[]'::json);
END;
$$;

REVOKE ALL ON FUNCTION public.jvm_master_users(TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.jvm_master_users(TEXT) TO authenticated;
