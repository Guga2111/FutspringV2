-- Run in the exported dump snapshot, or in the isolated restored database.
-- Only counts and multiset fingerprints leave the server; no row contents in the manifest.
SET timezone = 'UTC';
SET extra_float_digits = 3;
SELECT jsonb_build_object(
  'tables', (SELECT jsonb_object_agg(c.relname,
      query_to_xml(format('SELECT count(*) AS rows, coalesce(sum((''x'' || substr(md5(row_to_json(t)::text),1,16))::bit(64)::bigint::numeric),0) AS fingerprint FROM public.%I t', c.relname), false, true, '')::text)
    FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname='public' AND c.relkind='r'),
  -- PostgreSQL reparses expressions during restore (e.g. varchar-array -> text-array
  -- casts), so deparsed CHECK/predicate text is not stable across a valid round trip.
  -- Compare structural inventories; pg_restore itself recreates/enforces the archived DDL.
  'constraints', (SELECT md5(coalesce(string_agg(jsonb_build_array(c.conrelid::regclass::text,
      c.conname,c.contype,c.conkey,c.confrelid::regclass::text,c.confkey,c.confupdtype,c.confdeltype,
      c.confmatchtype,c.condeferrable,c.condeferred,c.convalidated)::text,
      E'\n' ORDER BY c.conrelid::regclass::text, c.conname),''))
    FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname='public'),
  'indexes', (SELECT md5(coalesce(string_agg(jsonb_build_array(t.relname,c.relname,i.indisunique,
      i.indisprimary,i.indisexclusion,i.indisvalid,i.indisready,i.indnullsnotdistinct,i.indkey::text,
      i.indoption::text,i.indpred IS NOT NULL,i.indexprs IS NOT NULL)::text,E'\n' ORDER BY c.relname),''))
    FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid JOIN pg_class t ON t.oid=i.indrelid
    WHERE t.relnamespace='public'::regnamespace),
  'row_security', (SELECT jsonb_object_agg(c.relname,jsonb_build_array(c.relrowsecurity,c.relforcerowsecurity))
    FROM pg_class c WHERE c.relnamespace='public'::regnamespace AND c.relkind='r'),
  'flyway', (SELECT md5(string_agg(row_to_json(h)::text, E'\n' ORDER BY installed_rank)) FROM public.flyway_schema_history h)
);
