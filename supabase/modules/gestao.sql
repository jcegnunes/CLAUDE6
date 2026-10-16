-- =========================================================================
-- MÓDULO GESTÃO ADM - CONTAS A PAGAR/RECEBER, FLUXO DE CAIXA E PRECIFICAÇÃO
--
-- Módulo independente: só cria/altera as tabelas gestao_* e as funções
-- jvm_gestao_*. Pode ser executado e alterado sem mexer nos ensaios.
-- Requer o schema.sql da plataforma (empresas, usuários e regras de acesso).
--
-- Dados financeiros: só administrador e perfil administrativo leem e gravam
-- as contas; a precificação e a configuração de custos também ficam
-- visíveis para o Responsável Técnico (que monta o preço do serviço).
--
-- IDEMPOTENTE: execute no SQL Editor do Supabase DEPOIS do schema.sql,
-- quantas vezes for necessário, sem apagar dados.
-- =========================================================================

-- -------------------------------------------------------------------------
-- Contas a pagar e a receber (uma linha por parcela)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.gestao_entries (
  id TEXT PRIMARY KEY,
  company_id TEXT REFERENCES public.companies(id) ON DELETE SET NULL,
  kind TEXT NOT NULL CHECK (kind IN ('pagar', 'receber')),
  description TEXT,
  category TEXT,
  counterparty_name TEXT,
  client_id TEXT,
  issue_date DATE,
  due_date DATE,
  amount NUMERIC(14,2),
  paid_amount NUMERIC(14,2),
  status TEXT DEFAULT 'aberto',
  pricing_id TEXT,
  service_order_id TEXT,
  payload JSONB,
  device_id TEXT,
  deleted_at TIMESTAMPTZ,
  base_updated_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- -------------------------------------------------------------------------
-- Precificação de serviços (custos, margem, impostos e preço)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.gestao_pricings (
  id TEXT PRIMARY KEY,
  company_id TEXT REFERENCES public.companies(id) ON DELETE SET NULL,
  number TEXT,
  title TEXT,
  client_id TEXT,
  client_name TEXT,
  pricing_date DATE,
  total_cost NUMERIC(14,2),
  final_price NUMERIC(14,2),
  status TEXT DEFAULT 'rascunho',
  payload JSONB,
  device_id TEXT,
  deleted_at TIMESTAMPTZ,
  base_updated_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- -------------------------------------------------------------------------
-- Cadastro de serviços (catálogo usado nos orçamentos)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.gestao_services (
  id TEXT PRIMARY KEY,
  company_id TEXT REFERENCES public.companies(id) ON DELETE SET NULL,
  code TEXT,
  name TEXT,
  category TEXT,
  mode TEXT,
  unit_price NUMERIC(14,2),
  active BOOLEAN DEFAULT TRUE,
  payload JSONB,
  device_id TEXT,
  deleted_at TIMESTAMPTZ,
  base_updated_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- -------------------------------------------------------------------------
-- Orçamentos (vários serviços: da precificação, do cadastro ou digitados)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.gestao_budgets (
  id TEXT PRIMARY KEY,
  company_id TEXT REFERENCES public.companies(id) ON DELETE SET NULL,
  number TEXT,
  title TEXT,
  client_id TEXT,
  client_name TEXT,
  budget_date DATE,
  total NUMERIC(14,2),
  status TEXT DEFAULT 'rascunho',
  payload JSONB,
  device_id TEXT,
  deleted_at TIMESTAMPTZ,
  base_updated_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Conta a receber gerada por orçamento aprovado
ALTER TABLE public.gestao_entries ADD COLUMN IF NOT EXISTS budget_id TEXT;

-- -------------------------------------------------------------------------
-- Configuração de custos da empresa (mão de obra, refeição, deslocamento...)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.gestao_settings (
  id TEXT PRIMARY KEY,
  company_id TEXT REFERENCES public.companies(id) ON DELETE SET NULL,
  payload JSONB,
  device_id TEXT,
  deleted_at TIMESTAMPTZ,
  base_updated_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_gestao_entries_company ON public.gestao_entries (company_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_gestao_entries_due ON public.gestao_entries (company_id, kind, due_date);
CREATE INDEX IF NOT EXISTS idx_gestao_pricings_company ON public.gestao_pricings (company_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_gestao_settings_company ON public.gestao_settings (company_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_gestao_services_company ON public.gestao_services (company_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_gestao_budgets_company ON public.gestao_budgets (company_id, updated_at);

-- -------------------------------------------------------------------------
-- Perfis com acesso: 'financeiro' (contas) ou 'precificacao'
-- -------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.jvm_gestao_can(p_area TEXT)
RETURNS BOOLEAN
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public
AS $$
  SELECT public.jvm_can_use_module('gestao') AND (
    public.jvm_is_master()
    OR COALESCE(public.jvm_my_role() IN ('admin', 'administrativo'), false)
    OR (p_area = 'precificacao' AND COALESCE(public.jvm_my_role() = 'responsavel_tecnico', false))
  )
$$;
REVOKE ALL ON FUNCTION public.jvm_gestao_can(TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.jvm_gestao_can(TEXT) TO authenticated;

-- -------------------------------------------------------------------------
-- Data de alteração definida pelo servidor, conflito entre aparelhos,
-- bloqueio de dados de demonstração e regras de acesso por empresa
-- (as mesmas funções usadas pelas demais tabelas da plataforma)
-- -------------------------------------------------------------------------
DO $$
DECLARE
  t TEXT;
  v_read TEXT;
  v_write TEXT;
  p RECORD;
BEGIN
  FOREACH t IN ARRAY ARRAY['gestao_entries','gestao_pricings','gestao_settings','gestao_services','gestao_budgets']
  LOOP
    EXECUTE format('DROP TRIGGER IF EXISTS jvm_set_updated_at ON public.%I', t);
    EXECUTE format('CREATE TRIGGER jvm_set_updated_at BEFORE INSERT OR UPDATE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.set_updated_at()', t);
    EXECUTE format('DROP TRIGGER IF EXISTS jvm_check_conflict ON public.%I', t);
    EXECUTE format('CREATE TRIGGER jvm_check_conflict BEFORE UPDATE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.jvm_check_conflict()', t);
    EXECUTE format('DROP TRIGGER IF EXISTS jvm_block_demo ON public.%I', t);
    EXECUTE format('CREATE TRIGGER jvm_block_demo BEFORE INSERT OR UPDATE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.jvm_block_demo_data()', t);

    -- contas: só financeiro; precificação, serviços e orçamentos: também o RT;
    -- configuração de custos: RT lê, financeiro grava
    v_read := CASE WHEN t = 'gestao_entries' THEN 'financeiro' ELSE 'precificacao' END;
    v_write := CASE WHEN t IN ('gestao_pricings', 'gestao_services', 'gestao_budgets') THEN 'precificacao' ELSE 'financeiro' END;

    FOR p IN SELECT policyname FROM pg_policies WHERE schemaname = 'public' AND tablename = t LOOP
      EXECUTE format('DROP POLICY %I ON public.%I', p.policyname, t);
    END LOOP;
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('REVOKE ALL ON public.%I FROM anon', t);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON public.%I TO authenticated', t);
    EXECUTE format($p$CREATE POLICY jvm_select ON public.%I FOR SELECT TO authenticated
      USING ((company_id = (SELECT public.jvm_my_company()) OR (SELECT public.jvm_is_master()))
             AND (SELECT public.jvm_gestao_can(%L)))$p$, t, v_read);
    EXECUTE format($p$CREATE POLICY jvm_insert ON public.%I FOR INSERT TO authenticated
      WITH CHECK ((company_id = (SELECT public.jvm_my_company()) OR (SELECT public.jvm_is_master()))
                  AND (SELECT public.jvm_gestao_can(%L)))$p$, t, v_write);
    EXECUTE format($p$CREATE POLICY jvm_update ON public.%I FOR UPDATE TO authenticated
      USING ((company_id = (SELECT public.jvm_my_company()) OR (SELECT public.jvm_is_master()))
             AND (SELECT public.jvm_gestao_can(%L)))
      WITH CHECK ((company_id = (SELECT public.jvm_my_company()) OR (SELECT public.jvm_is_master()))
                  AND (SELECT public.jvm_gestao_can(%L)))$p$, t, v_write, v_write);
    EXECUTE format($p$CREATE POLICY jvm_delete ON public.%I FOR DELETE TO authenticated
      USING (((company_id = (SELECT public.jvm_my_company()) AND (SELECT public.jvm_is_admin()))
             OR (SELECT public.jvm_is_master())) AND (SELECT public.jvm_can_use_module('gestao')))$p$, t);
  END LOOP;
END $$;

-- -------------------------------------------------------------------------
-- NUMERAÇÃO das precificações (PRC-AAMM-0001) e dos orçamentos (ORC-AAMM-0001)
-- Mesma regra dos ensaios: cada aparelho reserva uma faixa quando online.
-- -------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.jvm_gestao_reserve_numbers(p_kind TEXT, p_period TEXT, p_quantity INTEGER, p_local_max BIGINT DEFAULT 0)
RETURNS JSON
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_company TEXT := public.jvm_my_company();
  v_kind TEXT;
  v_used BIGINT := 0;
  v_last BIGINT;
BEGIN
  IF v_company IS NULL OR NOT public.jvm_gestao_can('precificacao') THEN
    RAISE EXCEPTION 'Sem permissão para reservar numeração.' USING ERRCODE = '42501';
  END IF;
  IF p_kind NOT IN ('pricing', 'budget') THEN
    RAISE EXCEPTION 'Tipo de numeração inválido: %', p_kind USING ERRCODE = '22023';
  END IF;
  IF p_period IS NULL OR p_period !~ '^\d{4}$' THEN
    RAISE EXCEPTION 'Período inválido: %', p_period USING ERRCODE = '22023';
  END IF;
  IF p_quantity IS NULL OR p_quantity < 1 OR p_quantity > 200 THEN
    RAISE EXCEPTION 'Quantidade inválida: %', p_quantity USING ERRCODE = '22023';
  END IF;

  v_kind := 'gestao_' || p_kind;
  IF p_kind = 'budget' THEN
    SELECT COALESCE(max(public.jvm_number_sequence(number, p_period)), 0) INTO v_used
      FROM public.gestao_budgets WHERE company_id = v_company;
  ELSE
    SELECT COALESCE(max(public.jvm_number_sequence(number, p_period)), 0) INTO v_used
      FROM public.gestao_pricings WHERE company_id = v_company;
  END IF;

  INSERT INTO public.number_sequences (company_id, kind, last_value)
  VALUES (v_company, v_kind, 0)
  ON CONFLICT (company_id, kind) DO NOTHING;

  SELECT last_value INTO v_last
    FROM public.number_sequences
   WHERE company_id = v_company AND kind = v_kind
   FOR UPDATE;

  v_last := GREATEST(v_last, v_used, COALESCE(p_local_max, 0));

  UPDATE public.number_sequences
     SET last_value = v_last + p_quantity, updated_at = NOW()
   WHERE company_id = v_company AND kind = v_kind;

  RETURN json_build_object('start', v_last + 1, 'end', v_last + p_quantity);
END;
$$;

REVOKE ALL ON FUNCTION public.jvm_gestao_reserve_numbers(TEXT, TEXT, INTEGER, BIGINT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.jvm_gestao_reserve_numbers(TEXT, TEXT, INTEGER, BIGINT) TO authenticated;

-- =========================================================================
-- FIM DO MÓDULO GESTÃO ADM
-- =========================================================================
