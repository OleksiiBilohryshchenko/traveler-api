DO $$
BEGIN
   IF NOT EXISTS (
      SELECT 1 FROM pg_publication WHERE pubname = 'traveler_publication'
   ) THEN
      CREATE PUBLICATION traveler_publication
      FOR ALL TABLES;
END IF;
END
$$;
