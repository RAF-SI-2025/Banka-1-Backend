# Order Service

Mikroservis koji pokriva upravljanje aktuarima, berzanskim nalozima, portfoliom klijenata i praćenjem poreza.

For shared project setup, git hooks, and infrastructure details, see the [root README](../README.md).

## Current Scope

This module currently implements the service foundation (Subissue #1). Business logic will be added in subsequent issues.

Implemented now:

- Spring Boot application skeleton with JWT authentication
- Liquibase database migration support
- RabbitMQ producer for order and tax notifications
- RestClient adapters for all dependent services: account-service, employee-service, client-service, exchange-service, stock-service
- Actuator health endpoints

Planned later:

- ActuaryInfo entity and actuary management endpoints
- Order entity and order execution logic
- Portfolio tracking endpoints
- Tax tracking and collection

## Docker Compose

To run the service locally with its own database and RabbitMQ:

```bash
cd order-service
docker compose up --build
```

To run the full system including all services:

```bash
docker compose -f setup/docker-compose.yml up -d
```

## Environment Variables

| Variable | Description | Default |
|---|---|---|
| `ORDER_SERVER_PORT` | Port the service listens on | `8088` |
| `ORDER_DB_HOST` | PostgreSQL host | `localhost` |
| `ORDER_DB_PORT` | PostgreSQL port | `5432` |
| `ORDER_DB_NAME` | Database name | `orderdb` |
| `ORDER_DB_USER` | Database user | `postgres` |
| `ORDER_DB_PASSWORD` | Database password | `postgres` |
| `JWT_SECRET` | Shared HMAC secret for JWT signing and verification | — |
| `RABBITMQ_HOST` | RabbitMQ host | `localhost` |
| `RABBITMQ_PORT` | RabbitMQ port | `5672` |
| `RABBITMQ_USERNAME` | RabbitMQ user | `guest` |
| `RABBITMQ_PASSWORD` | RabbitMQ password | `guest` |
| `NOTIFICATION_EXCHANGE` | RabbitMQ exchange name | `employee.events` |
| `NOTIFICATION_QUEUE` | RabbitMQ queue name | `notification-service-queue` |
| `ACCOUNT_SERVICE_HOST` | account-service hostname | `localhost` |
| `ACCOUNT_SERVER_PORT` | account-service port | `8084` |
| `EMPLOYEE_SERVICE_HOST` | employee-service hostname | `localhost` |
| `USER_SERVER_PORT` | employee-service port | `8081` |
| `CLIENT_SERVICE_HOST` | client-service hostname | `localhost` |
| `CLIENT_SERVER_PORT` | client-service port | `8083` |
| `EXCHANGE_SERVICE_HOST` | exchange-service hostname | `localhost` |
| `EXCHANGE_SERVER_PORT` | exchange-service port | `8085` |
| `STOCK_SERVICE_HOST` | stock-service hostname | `localhost` |
| `STOCK_SERVICE_PORT` | stock-service port | `8090` |

## API Gateway

The service is reachable via the API Gateway at:

```
http://localhost/order/
```

## Health Check

```
GET /actuator/health/liveness
```

```json
{ "status": "UP" }
```

## Events

The service publishes notifications to the `employee.events` RabbitMQ exchange using the following routing keys:

| Routing Key | Trigger |
|---|---|
| `order.approved` | A brokerage order has been approved and executed |
| `order.declined` | A brokerage order has been declined |
| `tax.collected` | Tax has been collected from a portfolio transaction |
