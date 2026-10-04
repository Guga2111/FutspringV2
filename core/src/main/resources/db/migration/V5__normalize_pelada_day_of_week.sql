-- peladas.day_of_week must be a java.time.DayOfWeek name (MONDAY..SUNDAY): DailySchedulerService parses
-- it with DayOfWeek.valueOf, and the API now validates it. The web app used to send Portuguese labels
-- ("Segunda", "Terça", "Sabado"...), which made the hourly auto-create job fail for those peladas.
-- Converts Portuguese and lower-case English values; anything unrecognized is left as is (the job logs
-- and skips it). Idempotent.
UPDATE peladas
SET day_of_week = CASE
    WHEN normalized LIKE 'seg%' OR normalized = 'monday' THEN 'MONDAY'
    WHEN normalized LIKE 'ter%' OR normalized = 'tuesday' THEN 'TUESDAY'
    WHEN normalized LIKE 'qua%' OR normalized = 'wednesday' THEN 'WEDNESDAY'
    WHEN normalized LIKE 'qui%' OR normalized = 'thursday' THEN 'THURSDAY'
    WHEN normalized LIKE 'sex%' OR normalized = 'friday' THEN 'FRIDAY'
    WHEN normalized LIKE 'sab%' OR normalized = 'saturday' THEN 'SATURDAY'
    WHEN normalized LIKE 'dom%' OR normalized = 'sunday' THEN 'SUNDAY'
    ELSE day_of_week
END
FROM (
    SELECT id AS pid, translate(lower(trim(day_of_week)), 'áàâãéêíóôõúç', 'aaaaeeiooouc') AS normalized
    FROM peladas
) n
WHERE peladas.id = n.pid
  AND day_of_week NOT IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY');
