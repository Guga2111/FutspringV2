-- Reject incomplete reads, unreviewed dependencies and unsupported schema features.
DO $$
BEGIN
 IF current_setting('server_version_num')::integer / 10000 <> 17 THEN
   RAISE EXCEPTION 'Expected PostgreSQL 17';
 END IF;
 IF to_regclass('public.flyway_schema_history') IS NULL OR
    NOT EXISTS (SELECT FROM public.flyway_schema_history) OR
    EXISTS (SELECT FROM public.flyway_schema_history WHERE NOT success) THEN
   RAISE EXCEPTION 'Missing/failed Flyway history';
 END IF;
 IF EXISTS (SELECT FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
   WHERE n.nspname='public' AND (c.relkind IN ('f','p') OR c.relrowsecurity OR
    (c.relkind IN ('r','v','m') AND NOT has_table_privilege(c.oid,'SELECT')) OR
    (c.relkind='S' AND NOT has_sequence_privilege(c.oid,'SELECT')))) THEN
   RAISE EXCEPTION 'Incomplete read permissions / unsupported RLS, foreign or partitioned table';
 END IF;
 IF EXISTS (SELECT FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace WHERE n.nspname='public') OR
    EXISTS (SELECT FROM pg_depend d
      WHERE (pg_identify_object(d.classid,d.objid,d.objsubid)).schema='public'
      AND (pg_identify_object(d.refclassid,d.refobjid,d.refobjsubid)).schema NOT IN ('public','pg_catalog','information_schema')
      AND d.deptype='n') THEN
   RAISE EXCEPTION 'Schema dependencies require review';
 END IF;
END $$;
