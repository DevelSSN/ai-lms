#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE EXTENSION IF NOT EXISTS vector;
    CREATE DATABASE ailms;
    CREATE USER ailms WITH PASSWORD '${AILMS_PASSWORD}';
    GRANT ALL PRIVILEGES ON DATABASE ailms TO ailms;
    \c ailms
    CREATE EXTENSION IF NOT EXISTS vector;
    GRANT ALL ON SCHEMA public TO ailms;
EOSQL
