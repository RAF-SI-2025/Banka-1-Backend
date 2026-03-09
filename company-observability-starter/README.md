# Company Observability Starter

`company-observability-starter` je Spring Boot starter biblioteka za standardizovano observability ponašanje u mikroservisima.

Biblioteka omogućava:
- JSON logging
- Correlation ID podršku
- HTTP request logging
- Global exception handling
- Distributed tracing integraciju
- Maskiranje osetljivih podataka u logovima
- Opciono dodavanje `userId` iz JWT-a u MDC

---

## Funkcionalnosti

### 1. JSON logging
Logovi se ispisuju u JSON formatu i sadrže standardizovana polja kao što su:
- `timestamp`
- `level`
- `serviceName`
- `traceId`
- `spanId`
- `correlationId`
- `thread`
- `logger`
- `message`
- `stacktrace` 

### 2. Correlation ID
Biblioteka:
- čita `X-Correlation-Id` iz request header-a
- generiše novi UUID ako header ne postoji
- vraća isti `X-Correlation-Id` u response
- ubacuje `correlationId` u MDC

### 3. HTTP request logging
Za svaki HTTP request loguju se:
- HTTP method
- URI
- status
- trajanje u milisekundama

### 4. Global exception handling
Sve neobrađene greške se:
- loguju sa stacktrace-om
- vraćaju klijentu kao generičan JSON odgovor bez internih detalja

### 5. Distributed tracing
Biblioteka podržava tracing integraciju preko Micrometer + OpenTelemetry mehanizma.

### 6. Maskiranje osetljivih podataka
Pre logovanja maskiraju se:
- `password`
- `token`
- `Authorization` header

### 7. Opciono: `userId` iz JWT-a u MDC
Ako servis koristi Spring Security / JWT, moguće je uključiti dodavanje `userId` vrednosti u MDC i logove.

---

## Tehnologije

- Java 21
- Spring Boot 4.0.3
- Gradle
- Spring Web MVC
- Spring Boot Actuator
- Micrometer Tracing
- OpenTelemetry
- Logstash Logback Encoder

---

## Struktura biblioteke

Biblioteka je organizovana kroz nekoliko paketa:
- `config` – auto-configuration i properties
- `bootstrap` – rano učitavanje logback konfiguracije
- `web` – filteri i global exception handler
- `service` – servisna logika biblioteke
- `domain` – pomoćni modeli i interfejsi

---

## Kako koristiti biblioteku


Biblioteka se koristi unutar istog repozitorijuma kao lokalni Gradle modul, bez objavljivanja na Maven Central ili GitHub Packages.

U root `settings.gradle` fajlu potrebno je uključiti modul biblioteke i module servisa:

```gradle
rootProject.name = 'banka1-system' npr

include 'company-observability-starter'
include 'user-service'
include 'notification-service'
```

Servis koji želi da koristi biblioteku treba da doda dependency na lokalni modul:

```gradle
dependencies {
    implementation project(':company-observability-starter')
}
```

Servis koji koristi biblioteku treba da ima definisano ime aplikacije u application.yml:

```yaml
spring:
    application:
        name: user-service
```

Ako servis koristi distributed tracing i želi da se svi requestovi sample-uju tokom testiranja, može dodatno da uključi:
```yaml
management:
  tracing:
    sampling:
      probability: 1.0
```

Opcionalno dodavanje userId iz JWT-a u MDC uključuje se samo u servisima koji koriste Spring Security / JWT autentikaciju:

```yaml
company:
  observability:
    user-id-mdc-enabled: true
```

Ako servis ne koristi JWT, ovu opciju nije potrebno uključivati.
### Pokretanje servisa

Pošto se biblioteka nalazi u istom repozitorijumu, nije potrebno posebno publish-ovanje. Dovoljno je pokrenuti željeni servis iz root projekta:

```
./gradlew :user-service:bootRun
```
ili build-ovati ceo repo:

``` 
./gradlew build
```