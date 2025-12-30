#!/bin/sh
set -e

echo "▶ PostgreSQL init orchestrator started"

# --------------------------------------------------
# Run project schema migrations
# --------------------------------------------------
if [ -d "/docker-entrypoint-migrations" ]; then
  echo "▶ Running schema migrations"
  for file in /docker-entrypoint-migrations/*.sql; do
    if [ -f "$file" ]; then
      echo "▶ Executing migration: $file"
      psql -v ON_ERROR_STOP=1 \
        --username "$POSTGRES_USER" \
        --dbname "$POSTGRES_DB" \
        -f "$file"
    fi
  done
fi

# --------------------------------------------------
# Create replication user from ENV (SECURITY TASK)
# --------------------------------------------------
if [ -n "$REPLICATION_USER" ] && [ -n "$REPLICATION_PASSWORD" ]; then
  echo "▶ Ensuring replication user exists (from ENV)"

  psql -v ON_ERROR_STOP=1 \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" <<-EOSQL
DO \$\$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = '${REPLICATION_USER}'
  ) THEN
    CREATE ROLE ${REPLICATION_USER}
      WITH LOGIN
      PASSWORD '${REPLICATION_PASSWORD}'
      REPLICATION;
  END IF;
END
\$\$;
EOSQL
fi

# --------------------------------------------------
# Run replication scripts (publisher or subscriber)
# --------------------------------------------------
if [ -d "/docker-entrypoint-replications" ]; then
  echo "▶ Running replication scripts"
  for file in /docker-entrypoint-replications/*; do
    if [ -f "$file" ]; then
      case "$file" in
        *.sql)
          echo "▶ Executing SQL replication script: $file"
          psql -v ON_ERROR_STOP=1 \
            --username "$POSTGRES_USER" \
            --dbname "$POSTGRES_DB" \
            -f "$file"
          ;;
        *.sh)
          echo "▶ Executing SH replication script: $file"
          sh "$file"
          ;;
        *)
          echo "▶ Skipping file: $file"
          ;;
      esac
    fi
  done
fi

echo "▶ PostgreSQL init orchestrator finished"
