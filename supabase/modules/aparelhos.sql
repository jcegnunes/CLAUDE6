-- =============================================================================
-- Licença do APP em um único aparelho (por usuário e por aplicativo)
-- Executar UMA vez no SQL Editor do Supabase (depois de acessos.sql). Pode ser executado de novo.
--
-- O APP JVM SPDA manda um ID fixo do celular (ANDROID_ID, não muda ao reinstalar o app).
-- - 1º login do usuário no APP: o aparelho fica registrado.
-- - Login em outro celular: recusado, com o modelo e a data do aparelho registrado.
-- - Troca de celular: o administrador master libera em Acessos → Usuários → "Liberar aparelho";
--   o próximo login fixa o aparelho novo (o antigo é desconectado na próxima conferência).
-- - Administrador master não tem limite. A plataforma web não tem limite.
-- =============================================================================

CREATE TABLE IF NOT EXISTS public.jvm_user_devices (
  user_id TEXT NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  app TEXT NOT NULL,
  device_id TEXT NOT NULL,
  device_name TEXT,
  registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (user_id, app)
);

-- só pelas funções abaixo
ALTER TABLE public.jvm_user_devices ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.jvm_user_devices FROM PUBLIC, anon, authenticated;

-- Confere (e na primeira vez registra) o aparelho do usuário logado.
-- {ok:true, registered:true|false} ou {ok:false, error, deviceName, registeredAt}
CREATE OR REPLACE FUNCTION public.jvm_device_check(p_app TEXT, p_device_id TEXT, p_device_name TEXT DEFAULT NULL)
RETURNS JSON
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  u public.users%ROWTYPE;
  d public.jvm_user_devices%ROWTYPE;
  v_app TEXT := lower(trim(COALESCE(p_app, '')));
  v_dev TEXT := trim(COALESCE(p_device_id, ''));
BEGIN
  IF auth.uid() IS NULL THEN
    RETURN json_build_object('ok', false, 'error', 'Sessão inválida. Entre novamente.');
  END IF;
  IF v_app = '' OR length(v_dev) < 8 OR length(v_dev) > 128 THEN
    RETURN json_build_object('ok', false, 'error', 'Identificação do aparelho inválida.');
  END IF;
  SELECT * INTO u FROM public.users WHERE auth_user_id = auth.uid() AND deleted_at IS NULL LIMIT 1;
  IF NOT FOUND THEN
    RETURN json_build_object('ok', false, 'error', 'Usuário não cadastrado no sistema.');
  END IF;
  IF u.is_master_admin IS TRUE THEN
    RETURN json_build_object('ok', true, 'registered', false, 'unlimited', true);
  END IF;

  INSERT INTO public.jvm_user_devices (user_id, app, device_id, device_name)
  VALUES (u.id, v_app, v_dev, left(p_device_name, 120))
  ON CONFLICT (user_id, app) DO NOTHING;

  SELECT * INTO d FROM public.jvm_user_devices WHERE user_id = u.id AND app = v_app;
  IF d.device_id = v_dev THEN
    UPDATE public.jvm_user_devices
       SET last_seen_at = NOW(), device_name = COALESCE(left(p_device_name, 120), device_name)
     WHERE user_id = u.id AND app = v_app;
    RETURN json_build_object('ok', true, 'registered', true);
  END IF;
  RETURN json_build_object(
    'ok', false,
    'error', 'Esta licença do aplicativo já está em uso em outro celular'
      || COALESCE(' (' || d.device_name || ')', '')
      || ', desde ' || to_char(d.registered_at AT TIME ZONE 'America/Sao_Paulo', 'DD/MM/YYYY')
      || '. Para usar este celular, peça ao administrador para liberar o aparelho.',
    'deviceName', d.device_name,
    'registeredAt', d.registered_at
  );
END;
$$;

REVOKE ALL ON FUNCTION public.jvm_device_check(TEXT, TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.jvm_device_check(TEXT, TEXT, TEXT) TO authenticated;

-- Master: aparelhos registrados (de todos ou de uma empresa)
CREATE OR REPLACE FUNCTION public.jvm_master_devices(p_company_id TEXT DEFAULT NULL)
RETURNS JSON
LANGUAGE plpgsql STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT public.jvm_is_master() THEN
    RAISE EXCEPTION 'Somente o administrador master gerencia os aparelhos.' USING ERRCODE = '42501';
  END IF;
  RETURN COALESCE((
    SELECT json_agg(json_build_object(
      'userId', d.user_id, 'app', d.app, 'deviceName', d.device_name,
      'registeredAt', d.registered_at, 'lastSeenAt', d.last_seen_at
    ) ORDER BY d.registered_at)
      FROM public.jvm_user_devices d
      JOIN public.users u ON u.id = d.user_id
     WHERE p_company_id IS NULL OR u.company_id = p_company_id
  ), '[]'::json);
END;
$$;

REVOKE ALL ON FUNCTION public.jvm_master_devices(TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.jvm_master_devices(TEXT) TO authenticated;

-- Master: libera o aparelho (o próximo login no APP registra o celular novo)
CREATE OR REPLACE FUNCTION public.jvm_master_release_device(p_user_id TEXT, p_app TEXT)
RETURNS JSON
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  n INT;
BEGIN
  IF NOT public.jvm_is_master() THEN
    RAISE EXCEPTION 'Somente o administrador master libera aparelhos.' USING ERRCODE = '42501';
  END IF;
  DELETE FROM public.jvm_user_devices WHERE user_id = p_user_id AND app = lower(trim(COALESCE(p_app, '')));
  GET DIAGNOSTICS n = ROW_COUNT;
  RETURN json_build_object('ok', true, 'released', n > 0);
END;
$$;

REVOKE ALL ON FUNCTION public.jvm_master_release_device(TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.jvm_master_release_device(TEXT, TEXT) TO authenticated;
