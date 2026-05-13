
#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE banka;
    CREATE DATABASE notification_db;
    CREATE DATABASE clientdb;
    CREATE DATABASE bankingdb;
    CREATE DATABASE accountdb;
    CREATE DATABASE card_db;
    CREATE DATABASE transferdb;
    CREATE DATABASE exchange_db;
    CREATE DATABASE verificationdb;
    CREATE DATABASE transaction_db;
    CREATE DATABASE orderdb;
    CREATE DATABASE stock_db;
    CREATE DATABASE credit_db;
    CREATE DATABASE investment_fund_db;
EOSQL
