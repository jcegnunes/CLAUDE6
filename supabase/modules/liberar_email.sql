-- =============================================================================
-- Usuário excluído libera o e-mail (e o nome de usuário) para um novo cadastro
-- Executar UMA vez no SQL Editor do Supabase (depois de liberacao.sql). Pode ser executado de novo.
--
-- Ao excluir (master, empresa ou sincronização), o registro continua no banco (histórico:
-- laudos, ensaios e certificados mantêm o nome), mas:
--   - o e-mail vira "excluido.<id>@sem-email.local" (o original fica em deleted_email, só consulta);
--   - o nome de usuário é apagado;
--   - a conta de login antiga troca de e-mail junto (jvm_sync_auth) e continua bloqueada.
-- Assim "Criar conta", o cadastro pela empresa e a aprovação do master aceitam o mesmo e-mail:
-- é criado um usuário NOVO (sem herdar perfil, assinatura nem empresa do antigo).
-- No fim, libera também os e-mails de quem já tinha sido excluído.
-- =============================================================================

ALTER TABLE public.users ADD COLUMN IF NOT EXISTS deleted_email TEXT;

CREATE OR REPLACE FUNCTION public.jvm_release_deleted_email()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
  IF NEW.deleted_at IS NOT NULL
     AND COALESCE(NEW.email, '') NOT LIKE 'excluido.%@sem-email.local' THEN
    NEW.deleted_email := COALESCE(NEW.deleted_email, NEW.email);
    NEW.email := 'excluido.' || NEW.id || '@sem-email.local';
    NEW.username := NULL;
    -- sem conta de login vinculada: não cria uma para o endereço interno
    IF NEW.auth_user_id IS NULL THEN
      NEW.password_hash := NULL;
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

-- ordem alfabética dos gatilhos: depois de jvm_guard_users/jvm_hash_password e ANTES de jvm_sync_auth
-- (que leva o e-mail novo para a conta de login e a mantém bloqueada)
DROP TRIGGER IF EXISTS jvm_release_email ON public.users;
CREATE TRIGGER jvm_release_email
  BEFORE INSERT OR UPDATE ON public.users
  FOR EACH ROW EXECUTE FUNCTION public.jvm_release_deleted_email();

-- quem já foi excluído antes deste script
UPDATE public.users
   SET email = email
 WHERE deleted_at IS NOT NULL
   AND COALESCE(email, '') NOT LIKE 'excluido.%@sem-email.local';
