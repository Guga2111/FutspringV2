BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
\o /work/snapshot.json
SELECT json_build_object('id', pg_export_snapshot(), 'time', transaction_timestamp(), 'server', version());
\o
SELECT pg_sleep(1800);
ROLLBACK;
