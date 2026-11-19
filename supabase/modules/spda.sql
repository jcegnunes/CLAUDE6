-- =========================================================================
-- MÓDULO SPDA - INSPEÇÃO E ENSAIOS DE SPDA (ABNT NBR 5419:2026)
--
-- Módulo independente: cria/altera só a tabela spda_inspections e as regras
-- de acesso abaixo. Usado pela plataforma web (módulo SPDA) e pelo APP JVM SPDA.
-- Requer o schema.sql da plataforma (empresas, usuários e regras de acesso).
--
-- IDEMPOTENTE: execute no SQL Editor do Supabase DEPOIS do schema.sql,
-- quantas vezes for necessário, sem apagar dados.
--
-- Fotos e croquis das inspeções vão para o bucket "jvm-evidencias", na pasta
-- da empresa (mesmas regras das fotos dos ensaios); a inspeção guarda o endereço.
-- =========================================================================

-- -------------------------------------------------------------------------
-- Inspeções (registro completo em payload; colunas para consulta)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.spda_inspections (
  id TEXT PRIMARY KEY,
  company_id TEXT REFERENCES public.companies(id) ON DELETE SET NULL,
  number TEXT,
  laudo_number TEXT,
  client_id TEXT,
  client_name TEXT,
  service_order_id TEXT,
  building_name TEXT,
  city TEXT,
  inspection_date DATE,
  status TEXT DEFAULT 'rascunho',
  result TEXT,
  technician_name TEXT,
  payload JSONB,
  device_id TEXT,
  deleted_at TIMESTAMPTZ,
  base_updated_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_spda_inspections_company ON public.spda_inspections (company_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_spda_inspections_client ON public.spda_inspections (company_id, client_id);

-- -------------------------------------------------------------------------
-- Data de alteração pelo servidor, conflito entre aparelhos, bloqueio de
-- dados de demonstração e acesso por empresa + módulo SPDA
-- -------------------------------------------------------------------------
DO $$
DECLARE
  t TEXT := 'spda_inspections';
  p RECORD;
BEGIN
  EXECUTE format('DROP TRIGGER IF EXISTS jvm_set_updated_at ON public.%I', t);
  EXECUTE format('CREATE TRIGGER jvm_set_updated_at BEFORE INSERT OR UPDATE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.set_updated_at()', t);
  EXECUTE format('DROP TRIGGER IF EXISTS jvm_check_conflict ON public.%I', t);
  EXECUTE format('CREATE TRIGGER jvm_check_conflict BEFORE UPDATE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.jvm_check_conflict()', t);
  EXECUTE format('DROP TRIGGER IF EXISTS jvm_block_demo ON public.%I', t);
  EXECUTE format('CREATE TRIGGER jvm_block_demo BEFORE INSERT OR UPDATE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.jvm_block_demo_data()', t);

  FOR p IN SELECT policyname FROM pg_policies WHERE schemaname = 'public' AND tablename = t LOOP
    EXECUTE format('DROP POLICY %I ON public.%I', p.policyname, t);
  END LOOP;
  EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);
  EXECUTE format('REVOKE ALL ON public.%I FROM anon', t);
  EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON public.%I TO authenticated', t);
  EXECUTE format($p$CREATE POLICY jvm_select ON public.%I FOR SELECT TO authenticated
    USING ((company_id = (SELECT public.jvm_my_company()) OR (SELECT public.jvm_is_master()))
           AND (SELECT public.jvm_can_use_module('spda')))$p$, t);
  EXECUTE format($p$CREATE POLICY jvm_insert ON public.%I FOR INSERT TO authenticated
    WITH CHECK ((company_id = (SELECT public.jvm_my_company()) OR (SELECT public.jvm_is_master()))
                AND (SELECT public.jvm_can_write()) AND (SELECT public.jvm_can_use_module('spda')))$p$, t);
  EXECUTE format($p$CREATE POLICY jvm_update ON public.%I FOR UPDATE TO authenticated
    USING ((company_id = (SELECT public.jvm_my_company()) OR (SELECT public.jvm_is_master()))
           AND (SELECT public.jvm_can_use_module('spda')))
    WITH CHECK ((company_id = (SELECT public.jvm_my_company()) OR (SELECT public.jvm_is_master()))
                AND (SELECT public.jvm_can_write()) AND (SELECT public.jvm_can_use_module('spda')))$p$, t);
  EXECUTE format($p$CREATE POLICY jvm_delete ON public.%I FOR DELETE TO authenticated
    USING (((company_id = (SELECT public.jvm_my_company()) AND (SELECT public.jvm_is_admin()))
           OR (SELECT public.jvm_is_master())) AND (SELECT public.jvm_can_use_module('spda')))$p$, t);
END $$;

-- -------------------------------------------------------------------------
-- Ordens de serviço e instrumentos: comuns aos módulos Ensaios de EPI e SPDA
-- (a inspeção SPDA usa a OS e os instrumentos calibrados da empresa).
-- Mesmas regras do schema.sql, aceitando também quem só tem o módulo SPDA.
-- -------------------------------------------------------------------------
DO $$
DECLARE
  t TEXT;
  m TEXT := '((SELECT public.jvm_can_use_module(''ensaios'')) OR (SELECT public.jvm_can_use_module(''spda'')))';
BEGIN
  FOREACH t IN ARRAY ARRAY['service_orders','lab_instruments']
  LOOP
    EXECUTE format('DROP POLICY IF EXISTS jvm_select ON public.%I', t);
    EXECUTE format('DROP POLICY IF EXISTS jvm_insert ON public.%I', t);
    EXECUTE format('DROP POLICY IF EXISTS jvm_update ON public.%I', t);
    EXECUTE format('DROP POLICY IF EXISTS jvm_delete ON public.%I', t);
    EXECUTE format($p$CREATE POLICY jvm_select ON public.%I FOR SELECT TO authenticated
      USING ((company_id = (SELECT public.jvm_my_company()) OR (SELECT public.jvm_is_master())) AND %s)$p$, t, m);
    EXECUTE format($p$CREATE POLICY jvm_insert ON public.%I FOR INSERT TO authenticated
      WITH CHECK ((company_id = (SELECT public.jvm_my_company()) OR (SELECT public.jvm_is_master()))
                  AND (SELECT public.jvm_can_write()) AND %s)$p$, t, m);
    EXECUTE format($p$CREATE POLICY jvm_update ON public.%I FOR UPDATE TO authenticated
      USING ((company_id = (SELECT public.jvm_my_company()) OR (SELECT public.jvm_is_master())) AND %s)
      WITH CHECK ((company_id = (SELECT public.jvm_my_company()) OR (SELECT public.jvm_is_master()))
                  AND (SELECT public.jvm_can_write()) AND %s)$p$, t, m, m);
    EXECUTE format($p$CREATE POLICY jvm_delete ON public.%I FOR DELETE TO authenticated
      USING (((company_id = (SELECT public.jvm_my_company()) AND (SELECT public.jvm_is_admin()))
             OR (SELECT public.jvm_is_master())) AND %s)$p$, t, m);
  END LOOP;
END $$;
