#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE ailms;
    CREATE USER ailms WITH PASSWORD '${AILMS_PASSWORD}';
    GRANT ALL PRIVILEGES ON DATABASE ailms TO ailms;
    \c ailms
    GRANT ALL ON SCHEMA public TO ailms;
EOSQL
