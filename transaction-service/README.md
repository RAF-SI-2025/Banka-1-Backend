# Transaction Service – Upravljanje bankarskim transakcijama

Mikroservis za kreiranje, izvršenje i praćenje finansijskih transakcija između klijenata banke. Servis je deo Banka 1 backend sistema i dostupan je **klijentima kroz autorizovane API pozive** i **internim servisima** (putem JWT tokena).

---

## Docker Compose

### Opcija 1: Hibridni režim (preporučeno za razvoj)

Pokrenite samo bazu i RabbitMQ u Dockeru:

```bash
cd transaction-service
docker compose up -d postgres rabbitmq
```

Zatim pokrenite aplikaciju iz IntelliJ (`TransactionServiceApplication`). Aplikacija koristi fallback vrednosti iz .env fajla.

### Opcija 2: Puni Docker paket (ceo sistem)

```bash
docker compose -f setup/docker-compose.yml up -d --build transaction-service
```

Servis je dostupan na `http://localhost:8082` (direktno) ili `http://localhost/transactions/` (kroz API gateway).

**Korisne komande:**
```bash
docker compose -f setup/docker-compose.yml logs -f transaction-service   # Praćenje logova
docker compose -f setup/docker-compose.yml down                            # Gašenje svih kontejnera
docker compose -f setup/docker-compose.yml down -v                         # Gašenje + brisanje baze
```

## Environment Variables

Kreirati `.env` fajl u `setup/` folderu (primer u `setup/.env.example`):

| Varijabla | Opis | Primer |
|---|---|---|
| `TRANSACTION_SERVICE_PORT` | Port na kome servis sluša | `8082` |
| `TRANSACTION_SERVICE_DB_HOST` | Hostname baze podataka | `postgres_transaction` |
| `TRANSACTION_SERVICE_DB_PORT` | Interni port baze (unutar Docker mreže) | `5432` |
| `TRANSACTION_SERVICE_DB_EX_PORT` | Eksterni Docker port baze | `5436` |
| `TRANSACTION_SERVICE_DB_NAME` | Naziv baze podataka | `transactiondb` |
| `TRANSACTION_SERVICE_DB_USER` | Korisničko ime baze | `postgres` |
| `TRANSACTION_SERVICE_DB_PASSWORD` | Lozinka baze | `postgres` |
| `JWT_SECRET` | HMAC-SHA256 secret (isti kao ostali servisi) | `my_secret_key` |
| `RABBITMQ_HOST` | Hostname RabbitMQ brokera | `rabbitmq` |
| `RABBITMQ_PORT` | Port RabbitMQ brokera | `5672` |
| `RABBITMQ_USERNAME` | Korisničko ime RabbitMQ | `guest` |
| `RABBITMQ_PASSWORD` | Lozinka RabbitMQ | `guest` |
| `NOTIFICATION_QUEUE` | Naziv RabbitMQ queue-a za notifikacije | `notification-service-queue` |
| `NOTIFICATION_EXCHANGE` | Naziv RabbitMQ exchange-a | `employee.events` |
| `NOTIFICATION_ROUTING_KEY` | Routing key za email notifikacije | `employee.#` |

---

## API Endpoints

Svi endpointi zahtevaju Bearer JWT token zaposlenog u headeru:
```
Authorization: Bearer <token>
```

## Kreiranje transakcije

```
POST /transactions
Content-Type: application/json

{
  "fromAccountNumber": "1234567890123456789",
  "toAccountNumber": "9876543210987654321",
  "amount": 1500.00,
  "recipientName": "Marko Marković",
  "paymentCode": "201",
  "referenceNumber": "REF12345",
  "paymentPurpose": "Uplata računa",
  "verificationSessionId": 42
}
```

**Response:**
```
{
  "message": "Transakcija kreirana",
  "status": "IN_PROGRESS"
}
```
## Pregled transakcija

```
GET /transactions?fromAccountNumber=123&toAccountNumber=&status=COMPLETED&page=0&size=10
```

## Globalna pretraga transakcija

```
GET /transactions/search?query=REF12345&page=0&size=10
```

## Promena statusa transakcija

```
PUT /transactions/{id}/status
Content-Type: application/json

{
  "status": "COMPLETED"
}
```
>Endpoint dostupan isključivo za interne pozive između servisa. JWT mora imati claim roles: "SERVICE".

## Brisanje transakcije (ADMIN)
```
DELETE /transactions/{id}
```

## Baza podataka i Liquibase

Projekat koristi PostgreSQL i Liquibase za migracije šeme. Hibernate je postavljen na validate mod — ne kreira tabele automatski.

Pravila migracija:

NIKADA ne menjati postojeće .sql fajlove koji su već pokrenuti
Za izmenu šeme kreirati novi fajl (npr. 002-dodaj-polje.sql) i prijaviti ga u db.changelog-master.xml

## Pokretanje testova

```bash
./gradlew :transaction-service:test
```

Coverage izveštaj: `transaction-service/build/reports/jacoco/test/html/index.html`