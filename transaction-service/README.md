# Transaction Service

Manages internal bank transactions and transfers between accounts.

## Docker Compose

```bash
docker compose -f setup/docker-compose.yml up -d transaction-service
```

## Environment Variables

| Variable | Description |
|---|---|
| `TRANSACTION_SERVER_PORT` | HTTP port (default `8087`) |
| `POSTGRES_HOST` | PostgreSQL host |
| `POSTGRES_PORT` | PostgreSQL port |
| `POSTGRES_DB` | Database name |
| `POSTGRES_USER` | Database user |
| `POSTGRES_PASSWORD` | Database password |
| `JWT_SECRET` | Shared HMAC JWT secret |

Copy `setup/.env.example` to `setup/.env` and fill in the values.

## API Endpoints

Swagger UI is available at `http://localhost:8087/swagger-ui.html` when the service is running.

Example endpoint:

```text
GET /transactions
```
