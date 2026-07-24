ALTER TABLE IF EXISTS t_job
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deadline TIMESTAMP;

DO $$
BEGIN
    IF to_regclass('public.t_job') IS NOT NULL THEN
        UPDATE t_job
        SET published_at = COALESCE(published_at, created_at)
        WHERE published_at IS NULL;
    END IF;
END
$$;
