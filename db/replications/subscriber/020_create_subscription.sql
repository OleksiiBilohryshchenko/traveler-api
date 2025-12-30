DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_subscription WHERE subname = 'traveler_subscription'
  ) THEN
    EXECUTE format(
      'CREATE SUBSCRIPTION traveler_subscription
       CONNECTION %L
       PUBLICATION traveler_publication',
      'host=postgres port=5432 dbname=' || current_database() ||
      ' user=' || current_setting('replication.user') ||
      ' password=' || current_setting('replication.password')
    );
END IF;
END
$$;
