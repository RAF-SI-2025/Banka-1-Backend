# Verification Service – Upravljanje 2FA verifikacionim sesijama

Mikroservis za potvrdu osetljivih operacija klijenata (plaćanja, transferi, promene limita, zahtevi za kartice i kredite). Servis generiše jednokratne verifikacione kodove, šalje ih na klijentov mobilni uređaj i validira ih pre izvršenja akcije. Deo Banka 1 backend sistema, izgrađen na **Spring Boot 4.0.3** sa **PostgreSQL** bazom i **Liquibase** migracijama.

**Verifikacioni flow:** Kod važi **5 minuta** — nakon isteka sesija se poništava. Nakon **3 neuspešna pokušaja** transakcija se automatski otkazuje.

---

## Docker Compose

### Opcija 1: Hibridni režim (preporučeno za razvoj)

Pokrenite samo infrastrukturu u Dockeru:

```bash
cd verification-service
cp .env.example .env   # popuniti vrednosti po potrebi
docker compose up -d postgres_verification rabbitmq
```

Zatim pokrenite aplikaciju iz IntelliJ (`VerificationServiceApplication`). Aplikacija učitava vrednosti iz `.env` fajla.

### Opcija 2: Puni Docker paket (ceo sistem)

```bash
docker compose -f setup/docker-compose.yml up -d --build verification-service
```

Servis je dostupan na `http://localhost:8086` (direktno) ili `http://localhost/verification/` (kroz API gateway).

**Korisne komande:**
```bash
docker compose -f setup/docker-compose.yml logs -f verification-service   # Praćenje logova
docker compose -f setup/docker-compose.yml down                           # Gašenje svih kontejnera
docker compose -f setup/docker-compose.yml down -v                        # Gašenje + brisanje baze
```

---

## Environment Variables

Kreirati `.env` fajl u `setup/` folderu (primer u `setup/.env.example`):

| Varijabla | Opis | Primer |
|---|---|---|
| `VERIFICATION_SERVER_PORT` | Port na kome servis sluša | `8086` |
| `VERIFICATION_DB_HOST` | Hostname baze podataka | `postgres_verification` |
| `VERIFICATION_DB_INTERNAL_PORT` | Interni port baze (unutar Docker mreže) | `5432` |
| `VERIFICATION_DB_EXTERNAL_PORT` | Eksterni Docker port baze | `5438` |
| `VERIFICATION_DB_NAME` | Naziv baze podataka | `verificationdb` |
| `VERIFICATION_DB_USER` | Korisničko ime baze | `postgres` |
| `VERIFICATION_DB_PASSWORD` | Lozinka baze | `postgres` |
| `JWT_SECRET` | HMAC-SHA256 secret (isti kao ostali servisi) | `my_secret_key` |
| `RABBITMQ_HOST` | Hostname RabbitMQ brokera | `rabbitmq` |
| `RABBITMQ_PORT` | Port RabbitMQ brokera | `5672` |
| `RABBITMQ_USERNAME` | Korisničko ime RabbitMQ | `guest` |
| `RABBITMQ_PASSWORD` | Lozinka RabbitMQ | `guest` |
| `NOTIFICATION_QUEUE` | Naziv RabbitMQ queue-a za notifikacije | `notification-service-queue` |
| `NOTIFICATION_EXCHANGE` | Naziv RabbitMQ exchange-a | `employee.events` |
| `NOTIFICATION_ROUTING_KEY` | Routing key za notifikacije | `employee.#` |

---

## API Endpoints

> Endpointi će biti implementirani u sledećim sub-issues.

Planirani endpointi prema specifikaciji:

### Generisanje verifikacionog koda

```
POST /api/verification/generate
Authorization: Bearer <jwt>
```

### Validacija koda

```
POST /api/verification/validate
```

### Status verifikacione sesije

```
GET /api/verification/{sessionId}/status
Authorization: Bearer <jwt>
```

---

## Baza podataka i Liquibase

Projekat koristi PostgreSQL i Liquibase za migracije šeme. Hibernate je postavljen na `validate` mod — ne kreira tabele automatski.

**Pravila migracija:**
- NIKADA ne menjati postojeće `.sql` fajlove koji su već pokrenuti
- Za izmenu šeme kreirati novi numerisani fajl (npr. `002-dodaj-polje.sql`) i prijaviti ga u `db.changelog-master.xml`

---

## Pokretanje testova

```bash
./gradlew :verification-service:test
```

Coverage izveštaj: `verification-service/build/reports/jacoco/test/html/index.html`
