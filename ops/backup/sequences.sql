-- Mutates ONLY the disposable drill target. Detect stale sequences and prove nextval works.
DO $$
DECLARE s record; maximum bigint; following bigint;
BEGIN
 FOR s IN SELECT seq.oid AS sequence_oid, tbl.relname AS table_name, a.attname AS column_name
   FROM pg_class seq JOIN pg_depend d ON d.objid=seq.oid AND d.classid='pg_class'::regclass AND d.deptype IN ('a','i')
   JOIN pg_class tbl ON tbl.oid=d.refobjid
   JOIN pg_attribute a ON a.attrelid=tbl.oid AND a.attnum=d.refobjsubid
   JOIN pg_namespace n ON n.oid=tbl.relnamespace WHERE seq.relkind='S' AND n.nspname='public'
 LOOP
   EXECUTE format('SELECT max(%I) FROM public.%I', s.column_name,s.table_name) INTO maximum;
   following := nextval(s.sequence_oid::regclass);
   IF following <= coalesce(maximum,0) THEN RAISE EXCEPTION 'Stale sequence'; END IF;
 END LOOP;
 IF EXISTS (SELECT FROM pg_constraint WHERE connamespace='public'::regnamespace AND NOT convalidated) THEN
   RAISE EXCEPTION 'Unvalidated constraint';
 END IF;
END $$;

-- Exercise actual constraints, not only their catalog inventory. Each failing
-- statement rolls back in its own subtransaction; unexpected errors fail the drill.
DO $$
BEGIN
 BEGIN
   INSERT INTO public.pelada_members(pelada_id,user_id) VALUES(-1,-1);
   RAISE EXCEPTION 'Foreign key did not reject an orphan';
 EXCEPTION WHEN foreign_key_violation THEN NULL;
 END;
 BEGIN
   INSERT INTO public.users(email,password,username) VALUES(NULL,'synthetic','synthetic');
   RAISE EXCEPTION 'NOT NULL did not reject an invalid user';
 EXCEPTION WHEN not_null_violation THEN NULL;
 END;
 BEGIN
   INSERT INTO public.user_tours(user_id,tour) VALUES(-1,'INVALID_BACKUP_PROBE');
   RAISE EXCEPTION 'CHECK did not reject an invalid tour';
 EXCEPTION WHEN check_violation THEN NULL;
 END;
 IF EXISTS (SELECT FROM public.users) THEN
   BEGIN
     INSERT INTO public.users(email,password,username)
       SELECT email,'synthetic','synthetic' FROM public.users LIMIT 1;
     RAISE EXCEPTION 'Unique index did not reject a duplicate';
   EXCEPTION WHEN unique_violation THEN NULL;
   END;
 END IF;
END $$;
