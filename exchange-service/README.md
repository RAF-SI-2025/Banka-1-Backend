# Exchange Service

Spring Boot microservice for exchange-rate retrieval and currency conversion support used by Transaction, Transfer, and Loan flows.

## Docker Compose

Run the service with its local database:

```bash
docker compose -f exchange-service/docker-compose.yml up -d --build
```

Run it from the shared stack:

```bash
docker compose -f setup/docker-compose.yml up -d postgres_exchange exchange-service api-gateway
```

## Environment Variables

Primary variables are defined in `setup/.env.example`.

| Variable | Default | Purpose |
| --- | --- | --- |
| `EXCHANGE_SERVER_PORT` | `8085` | HTTP port for the service |
| `EXCHANGE_SERVICE_HOST` | `0.0.0.0` | Bind address inside the container |
| `EXCHANGE_DB_HOST` | `postgres_exchange` | PostgreSQL host |
| `EXCHANGE_DB_PORT` | `5432` | PostgreSQL internal port |
| `EXCHANGE_DB_EX_PORT` | `5437` | Exposed PostgreSQL port for local access |
| `EXCHANGE_DB_NAME` | `exchange_db` | Service database name |
| `EXCHANGE_DB_USER` | `postgres` | PostgreSQL username |
| `EXCHANGE_DB_PASSWORD` | `postgres` | PostgreSQL password |
| `JWT_SECRET` | required | Shared HMAC key used by `security-lib` |

## Endpoints/Events

- `GET /info` returns a minimal service metadata payload directly on the service.
- `GET /api/exchange/info` returns the same payload through the API Gateway.
- `GET /actuator/health`, `/actuator/health/liveness`, and `/actuator/health/readiness` expose runtime health checks.
- No events are published or consumed in this setup-only iteration.

```json
{
  "service": "exchange-service",
  "status": "UP",
  "gatewayPrefix": "/api/exchange"
}
```

## OpenAPI

Static API contract: `exchange-service/docs/openapi.yml`

## Notes

- API Gateway route: `/api/exchange/**`
- Database: dedicated PostgreSQL schema in `exchange_db`
- Observability: integrated through `company-observability-starter`
- Security: integrated through `security-lib`
