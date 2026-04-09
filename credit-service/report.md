# PR Review Report

**Service:** credit-service
**Reviewed by:** AI Code Review Agent
**Date:** 2026-04-09
**PR / Branch:** main (full service review against spec.md)

---

## 1. Service Overview

**Business purpose:** credit-service is a Spring Boot microservice managing the full loan lifecycle for Banka 1 — from client loan request submission through employee approval/decline, automated daily installment collection, and reporting endpoints for both clients and employees.

**Technology stack:** Java 21, Spring Boot 4.0.3, Spring Security with OAuth2 Resource Server (JWT/HMAC-SHA256), Spring Data JPA + Hibernate, PostgreSQL, Liquibase, Spring AMQP (declared but not wired), Spring Web RestClient, Lombok, springdoc-openapi, Jacoco, Checkstyle.

**External dependencies:**
- `account-service` — account validation, fund deposits, installment deductions
- `exchange-service` — currency-to-RSD conversion for interest rate calculation
- `client-service` — declared but all methods commented out; effectively unused
- `verification-service` — declared in RestClientConfig, empty, unused
- RabbitMQ — dependency present, properties configured, zero producer code

**Architecture:** `LoanController` -> `LoanService` interface -> `LoanServiceImplementation`. Three repositories: `LoanRepository`, `LoanRequestRepository`, `InstallmentRepository`. REST clients: `AccountService`, `ExchangeService` (functional), `ClientService`/`VerificationService` (empty shells).

**Authentication:** JWT HMAC-SHA256 validated via `NimbusJwtDecoder`. Method-level security via `@PreAuthorize`. The service generates its own service-to-service JWT through `JWTServiceImplementation` for outbound RestClient calls.

**Deployment:** A local `docker-compose_intelij.yml` exists only for PostgreSQL and RabbitMQ. The `credit-service` container block is fully commented out. No `Dockerfile` exists. The service is not integrated into the main project `docker-compose.yml` or NGINX gateway configuration.

**Observability:** Actuator exposed with all endpoints. `company-observability-starter` is commented out in `build.gradle.kts`. No structured logging with trace/correlation IDs.

---

## 2. Specification Parsed

**Format:** Markdown requirements document (11 issues)

| # | Requirement | Source | Type |
|---|---|---|---|
| R1a | Create credit-service module in settings.gradle | Issue #1 | Infra |
| R1b | Add service to setup/docker-compose.yml with Dockerfile | Issue #1 | Infra |
| R1c | Register /credits/ route in api-gateway NGINX config | Issue #1 | Infra |
| R1d | Configure application.properties (DB, RabbitMQ, JWT) | Issue #1 | Config |
| R1e | Add security-lib dependency | Issue #1 | Dependency |
| R1f | Add company-observability-starter | Issue #1 | Dependency |
| R1g | Configure RabbitMQ producer (exchange: employee.events) | Issue #1 | Messaging |
| R1h | RestClient adapters for account-service, client-service, exchange-service | Issue #1 | Integration |
| R1i | Health endpoint available | Issue #1 | Infra |
| R2a | Loan entity with all specified fields | Issue #2 | Entity |
| R2b | Enum types: LoanType (5 values), LoanStatus, InterestType | Issue #2 | Entity |
| R2c | Liquibase migration for Loan | Issue #2 | DB |
| R2d | Validations: amount > 0, repaymentPeriod within allowed values per type | Issue #2 | Validation |
| R2e | Separate unique loanNumber field (auto-generated, distinct from id) | Issue #2 | Entity |
| R3a | Installment entity with all specified fields | Issue #3 | Entity |
| R3b | FK relation to Loan | Issue #3 | Entity |
| R3c | Liquibase migration for Installment | Issue #3 | DB |
| R3d | Stores full history + 1 future installment | Issue #3 | Business |
| R4a | LoanRequest entity with all specified fields | Issue #4 | Entity |
| R4b | Liquibase migration for LoanRequest | Issue #4 | DB |
| R4c | Validation: allowed repaymentPeriod values per loan type | Issue #4 | Validation |
| R4d | Validate account currency matches loan currency via account-service | Issue #4 | Business rule |
| R5a | POST /api/loans/requests endpoint | Issue #5 | Endpoint |
| R5b | Extract clientId from JWT, verify account ownership | Issue #5 | Business rule |
| R5c | Create LoanRequest with PENDING status | Issue #5 | Business rule |
| R5d | Return 201 / 400 / 403 / 404 on appropriate conditions | Issue #5 | HTTP contract |
| R5e | Unit and integration tests | Issue #5 | Testing |
| R6a | Interest rate service: base rate from 7-tier RSD amount table | Issue #6 | Service |
| R6b | Convert non-RSD amounts to RSD via exchange-service before lookup | Issue #6 | Integration |
| R6c | Add bank margin per loan type | Issue #6 | Business rule |
| R6d | Fixed: monthly = (base + margin) / 12 | Issue #6 | Formula |
| R6e | Variable: monthly = (base + offset + margin) / 12 | Issue #6 | Formula |
| R6f | Unit tests for every amount range | Issue #6 | Testing |
| R7a | PUT /api/loans/requests/{id}/approve | Issue #7 | Endpoint |
| R7b | PUT /api/loans/requests/{id}/decline | Issue #7 | Endpoint |
| R7c | Approve: set APPROVED, create Loan entity | Issue #7 | Business rule |
| R7d | Approve: calculate interest rate via Issue #6 | Issue #7 | Business rule |
| R7e | Approve: installment formula A = P*r(1+r)^n / ((1+r)^n - 1) | Issue #7 | Formula |
| R7f | Approve: credit amount to client account via account-service | Issue #7 | Integration |
| R7g | Approve: generate first future Installment | Issue #7 | Business rule |
| R7h | Approve: nextInstallmentDate = today + 1 month | Issue #7 | Business rule |
| R7i | Publish credit.approved RabbitMQ event | Issue #7 | Messaging |
| R7j | Decline: set DECLINED, publish credit.declined event | Issue #7 | Messaging |
| R7k | Only employees can access approve/decline (JWT role check) | Issue #7 | Security |
| R7l | Tests | Issue #7 | Testing |
| R8a | GET /api/loans/client | Issue #8 | Endpoint |
| R8b | Filter by JWT clientId | Issue #8 | Business rule |
| R8c | Sort descending by amount | Issue #8 | Business rule |
| R8d | Pagination | Issue #8 | Feature |
| R8e | Tests | Issue #8 | Testing |
| R9a | GET /api/loans/{loanNumber} | Issue #9 | Endpoint |
| R9b | Return all loan fields + full installment history (history + 1 future) | Issue #9 | Response |
| R9c | Only loan owner (client) or employee can access | Issue #9 | Security |
| R9d | Tests | Issue #9 | Testing |
| R10a | Daily cron: find loans where nextInstallmentDate == today | Issue #10 | Cron |
| R10b | Deduct installment from account via account-service | Issue #10 | Integration |
| R10c | Success: mark PAID, reduce debt, advance date, new installment, PAID_OFF when done | Issue #10 | Business rule |
| R10d | Failure: publish credit.installment_failed, retry in 72h | Issue #10 | Messaging/Retry |
| R10e | Retry also fails: +0.05% rate penalty, set OVERDUE | Issue #10 | Penalty |
| R10f | Monthly cron for variable rate: random offset [-1.50%, +1.50%] | Issue #10 | Cron |
| R10g | Tests | Issue #10 | Testing |
| R11a | GET /api/loans/requests with filters (loanType, accountNumber) sorted by date | Issue #11 | Endpoint |
| R11b | GET /api/loans/all with filters (loanType, accountNumber, loanStatus) sorted by accountNumber | Issue #11 | Endpoint |
| R11c | Pagination on both | Issue #11 | Feature |
| R11d | Only employees have access | Issue #11 | Security |
| R11e | Tests | Issue #11 | Testing |

---

## 3. Endpoint Inventory

| # | Method | Path | Handler | File | Line |
|---|---|---|---|---|---|
| 1 | POST | /api/loans/requests | LoanController.requests() | LoanController.java | 33 |
| 2 | PUT | /api/loans/requests/{id}/approve | LoanController.approve() | LoanController.java | 40 |
| 3 | PUT | /api/loans/requests/{id}/decline | LoanController.decline() | LoanController.java | 45 |
| 4 | GET | /api/loans/client | LoanController.find() | LoanController.java | 54 |
| 5 | GET | /api/loans/{loanNumber} | LoanController.info() | LoanController.java | 61 |
| 6 | GET | /api/loans/requests | LoanController.findAllLoanRequest() | LoanController.java | 68 |
| 7 | GET | /api/loans/all | LoanController.findAllLoans() | LoanController.java | 83 |
| 8 | CRON daily 00:00 | (scheduled) | LoanServiceImplementation.cronForRates() | LoanServiceImplementation.java | 180 |
| 9 | CRON monthly 1st 00:00 | (scheduled) | LoanServiceImplementation.generateReferenceRate() | LoanServiceImplementation.java | 232 |

---

## 4. Specification Compliance

| Req | Status | Notes |
|---|---|---|
| R1a — settings.gradle module | ✅ | settings.gradle.kts present |
| R1b — docker-compose.yml | ❌ | credit-service container block fully commented out; no Dockerfile |
| R1c — NGINX gateway /credits/ | ❌ | No evidence of gateway registration |
| R1d — application.properties | ✅ | DB, RabbitMQ, JWT configured via env-variable placeholders |
| R1e — security-lib dependency | ✅ | com.banka1:security-lib:0.0.1-SNAPSHOT in build.gradle.kts |
| R1f — company-observability-starter | ❌ | Commented out in build.gradle.kts |
| R1g — RabbitMQ producer | ❌ | No RabbitConfig class, no RabbitTemplate, no message publishing anywhere |
| R1h — RestClient adapters | ⚠️ | AccountService and ExchangeService functional; ClientService all methods commented out; VerificationService empty |
| R1i — Health endpoint | ✅ | Actuator with health probes configured |
| R2a — Loan entity fields | ⚠️ | All required fields present except separate unique loanNumber (see R2e) |
| R2b — Enum types | ✅ | LoanType (5 values + margin), Status, InterestType all present |
| R2c — Liquibase migration for Loan | ✅ | loan_table in 001-initial-schema.sql |
| R2d — Validations: amount > 0 | ✅ | @Positive on Loan.amount and LoanRequestDto.amount |
| R2e — Separate unique loanNumber field | ❌ | Commented out with TODO; id is reused as loanNumber |
| R3a — Installment entity fields | ✅ | All required fields present |
| R3b — FK relation to Loan | ✅ | @ManyToOne with DB constraint |
| R3c — Liquibase migration for Installment | ✅ | installment_table with FK in schema |
| R3d — History + 1 future installment | ✅ | Design stores all installments; UNPAID = future |
| R4a — LoanRequest entity fields | ✅ | All spec fields present (plus userEmail/username, acceptable) |
| R4b — Liquibase migration for LoanRequest | ✅ | loan_request_table created |
| R4c — repaymentPeriod validation per type | ✅ | Validated in request() via modulo/max checks |
| R4d — Account currency matches loan currency | ✅ | Checked via account-service call in request() |
| R5a — POST /api/loans/requests | ✅ | Endpoint exists, returns 201 on success |
| R5b — JWT clientId + account ownership | ⚠️ | JWT ownership check is correct, but clientId is also accepted from DTO body and saved directly (impersonation risk — see Critical #3) |
| R5c — Create LoanRequest with PENDING status | ✅ | Status.PENDING set explicitly |
| R5d — 201/400/403/404 responses | ⚠️ | 201 and 400 work; missing account returns 400 not 404 as required |
| R5e — Tests | ❌ | Only context-load test exists |
| R6a — Base rate by RSD amount | ✅ | Binary search across 6 thresholds implementing 7-tier table |
| R6b — Currency conversion via exchange-service | ✅ | Non-RSD converted before rate lookup |
| R6c — Bank margin per loan type | ✅ | Margin in LoanType enum, added to base rate |
| R6d — Fixed rate formula | ✅ | Divides by 1200 (annual % / 12 months / 100) to get monthly decimal |
| R6e — Variable rate formula | ✅ | referenceRate added to numerator before division |
| R6f — Unit tests for each range | ❌ | No tests at all |
| R7a — PUT approve | ✅ | Endpoint exists |
| R7b — PUT decline | ✅ | Endpoint exists |
| R7c — Create Loan on approval | ⚠️ | Loan created but clientId never set on Loan entity (will cause DB constraint violation) |
| R7d — Interest rate via Issue #6 | ✅ | interestRate() called during approval |
| R7e — Installment formula | ✅ | Correctly implemented with BigDecimal |
| R7f — Credit amount to account | ✅ | accountService.transactionFromBank() called |
| R7g — Generate first future installment | ✅ | One UNPAID Installment saved after approval |
| R7h — nextInstallmentDate = today + 1 month | ✅ | agreementDate.plusMonths(1) |
| R7i — Publish credit.approved event | ❌ | TODO comment only |
| R7j — Decline: publish credit.declined event | ❌ | TODO comment only |
| R7k — Only employees can access | ⚠️ | @PreAuthorize("hasRole('BASIC')") — covers basic employees; unclear if AGENT/SUPERVISOR/ADMIN roles also need access |
| R7l — Tests | ❌ | No tests |
| R8a — GET /api/loans/client | ✅ | Endpoint exists |
| R8b — Filter by JWT clientId | ✅ | findByClientIdOrderByAmountDesc() uses JWT-extracted id |
| R8c — Sort desc by amount | ✅ | Enforced by Spring Data method name |
| R8d — Pagination | ✅ | PageRequest with configurable page/size, validated with @Min/@Max |
| R8e — Tests | ❌ | No tests |
| R9a — GET /api/loans/{loanNumber} | ✅ | Endpoint exists |
| R9b — All loan fields + installment history | ✅ | LoanInfoResponseDto with LoanResponseDto + List<InstallmentResponseDto> |
| R9c — Only owner or employee can access | ❌ | Bug: ownership check compares JWT user ID to the loanNumber path variable, not to loan.clientId (see Critical #1) |
| R9d — Tests | ❌ | No tests |
| R10a — Daily cron: find due installments | ⚠️ | Query uses <= today not == today; may reprocess accumulated overdue installments |
| R10b — Deduct installment from account | ✅ | accountService.transactionFromBank() called in cron |
| R10c — Success: PAID/reduce debt/advance/PAID_OFF | ⚠️ | PAID_OFF status transition never happens (no code sets it) |
| R10d — Failure: RabbitMQ event + 72h retry | ⚠️ | Retry scheduling present but uses 24h on second attempt (not 72h); RabbitMQ event NOT sent |
| R10e — Penalty +0.05% on OVERDUE | ✅ | interestRate() adds 0.05% when status is OVERDUE |
| R10f — Monthly variable rate cron | ✅ | generateReferenceRate() runs monthly on 1st |
| R10g — Tests | ❌ | No tests |
| R11a — GET /api/loans/requests with filters | ⚠️ | Exists but crashes with NullPointerException when loanType is omitted (see Critical #2) |
| R11b — GET /api/loans/all with filters | ⚠️ | Exists but crashes with NullPointerException when loanType or loanStatus omitted |
| R11c — Pagination on both | ✅ | PageRequest used |
| R11d — Only employees | ✅ | @PreAuthorize("hasRole('BASIC')") on both |
| R11e — Tests | ❌ | No tests |

---

## 5. Code Review Findings

### Critical

**[CRITICAL] Authorization bypass in GET /api/loans/{loanNumber} — LoanServiceImplementation.java:254**

The ownership check is:
```java
((Number) jwt.getClaim(appPropertiesId)).longValue() == id
```
`id` is the `loanNumber` path variable (the loan's database primary key), not the loan's `clientId`. A client with user ID 5 passes this check for loan #5 regardless of who actually owns that loan. Any authenticated client can access any loan whose number happens to match their user ID.

**Fix:** Load the Loan first, extract `loan.getClientId()`, and compare that to the JWT user ID. Return 403 if neither owner nor employee condition is satisfied.

---

**[CRITICAL] NullPointerException on optional filter params — LoanController.java:71-78 and 87-101**

`vrstaKredita` and `loanStatus` are declared `required = false` but the controller immediately calls `LoanType.valueOf(vrstaKredita)` and `Status.valueOf(loanStatus)`. When either param is omitted, `valueOf(null)` throws `NullPointerException`, which the `catch (Exception e)` block re-throws as `IllegalArgumentException("Los loanType")`. Both filter parameters become de-facto mandatory, breaking the entire employee portal when no filter is supplied.

**Fix:**
```java
LoanType loanType = (vrstaKredita != null) ? LoanType.valueOf(vrstaKredita) : null;
Status status = (loanStatus != null) ? Status.valueOf(loanStatus) : null;
```

---

**[CRITICAL] clientId accepted from untrusted request body — LoanRequestDto.java:49 / LoanServiceImplementation.java:128**

`LoanRequestDto` includes a `clientId` field sent by the client. Although account ownership is verified via the JWT, the persisted `LoanRequest.clientId` uses `loanRequestDto.getClientId()` — the body value — not the JWT-extracted user ID. A malicious client can submit any `clientId` value and have it stored and used for all downstream operations.

**Fix:** Remove `clientId` from `LoanRequestDto`. Derive it exclusively from `jwt.getClaim(appPropertiesId)` in the `request()` method.

---

### High

**[HIGH] RabbitMQ messaging completely absent — LoanServiceImplementation.java:175, 193**

Three required RabbitMQ events (`credit.approved`, `credit.declined`, `credit.installment_failed`) are not implemented — only TODO comments exist. No `RabbitConfig` class, no `RabbitTemplate`, no `convertAndSend()` calls anywhere. Clients receive no email notifications for any credit event.

**Fix:** Create a `RabbitConfig` class declaring the `employee.events` topic exchange. Inject `RabbitTemplate` and publish events at the three marked TODO locations.

---

**[HIGH] PAID_OFF status never set — LoanServiceImplementation.java:216**

The cron job's success path checks:
```java
if(x.getLoan().getRemainingDebt().compareTo(BigDecimal.ZERO) > 0 && x.getLoan().getInstallmentCount() < x.getLoan().getRepaymentPeriod())
```
When both conditions are false (loan fully repaid), the code falls through with no action. Loans that are fully paid off remain `ACTIVE` indefinitely.

**Fix:** Add an `else` branch setting `loan.setStatus(Status.PAID_OFF)`.

---

**[HIGH] clientId not set on Loan entity — LoanServiceImplementation.java:153-171**

The `Loan` entity has a `clientId` field with a NOT NULL database constraint. During approval, `loan.setClientId()` is never called. This will cause a database constraint violation on every loan approval, making the approve endpoint entirely non-functional.

**Fix:** Add `loan.setClientId(loanRequest.getClientId())` before `loanRepository.save(loan)`.

---

**[HIGH] No Dockerfile and docker-compose entry commented out**

The spec requires the service to start in docker-compose. No `Dockerfile` exists and the service container block in `docker-compose_intelij.yml` is fully commented out. The service cannot be deployed as part of the banking platform.

---

**[HIGH] Missing 404 for non-existent account — LoanServiceImplementation.java:120-122**

When account-service returns null for an account, `IllegalArgumentException` is thrown, which GlobalExceptionHandler maps to HTTP 400. The spec requires 404. This also obscures communication failures (account-service down) from legitimate "not found" cases.

**Fix:** Throw `NoSuchElementException` (already mapped to 404 in GlobalExceptionHandler) for a missing account.

---

**[HIGH] Retry timing uses 24h instead of 72h on second attempt — LoanServiceImplementation.java:201**

The spec says to retry after 72h. On the first failure the code adds 3 days (correct). On the second failure (`retry == 1`) the code adds only 1 day (`today.plusDays(1)`), setting status to OVERDUE and penalizing the rate immediately without the promised 72-hour retry window.

---

**[HIGH] credit-service not registered in api-gateway**

The spec requires registering the `/credits/` route in the NGINX gateway config. No evidence of this having been done. All client traffic reaching the gateway cannot be routed to credit-service.

---

### Medium

**[MEDIUM] confirmation() fetches LoanRequest twice with TOCTOU window — LoanServiceImplementation.java:134-147**

`updateStatus()` is called first (modifying query, only updates if PENDING), then `findById(id)` is called again for the APPROVED path. Two separate round-trips with a small race window: two concurrent approval requests could both pass the PENDING check. Optimistic locking (`@Version`) will catch the second one but `ObjectOptimisticLockingFailureException` is not handled in `GlobalExceptionHandler` and will fall through to the generic 500.

**Fix:** Add a handler for `ObjectOptimisticLockingFailureException` returning HTTP 409 Conflict.

---

**[MEDIUM] ExchangeService.calculate() does not pass commission=false — ExchangeService.java:21-33**

The spec explicitly states conversion without commission ("bez provizije"). No commission-suppression parameter is passed. The conversion may apply a fee, inflating the RSD amount and placing loans in a lower rate tier than they should be.

**Fix:** Verify the exchange-service API contract and add the appropriate `commission=false` (or equivalent) query parameter.

---

**[MEDIUM] Interest rates stored as monthly decimal fractions, not annual percentages**

The `nominalInterestRate` and `effectiveInterestRate` fields store values like `0.000521` (the monthly decimal). The spec implies these represent annual percentage rates (e.g., 6.25%). The frontend receives raw decimal fractions and must multiply by 1200 to display meaningful values — this is an undocumented contract.

**Fix:** Store rates as annual percentages (e.g., 6.25) and convert to decimal internally at calculation time.

---

**[MEDIUM] GET /api/loans/requests returns raw JPA entity — LoanController.java:69**

`Page<LoanRequest>` (the JPA entity) is returned directly from the employee portal endpoint. This leaks internal fields (version, deleted, updatedAt, userEmail, etc.) and tightly couples the API contract to the persistence model.

**Fix:** Create a `LoanRequestListDto` and map to it before returning.

---

**[MEDIUM] monthlySalary has no @NotNull or @Positive constraint — LoanRequestDto.java:35**

A client can submit a null or negative monthly salary without triggering any validation error.

**Fix:** Add `@NotNull @Positive` to `monthlySalary` in `LoanRequestDto`.

---

**[MEDIUM] Cron query uses <= today instead of == today — InstallmentRepository.java:13**

`findInstallmentByExpectedDueDateLessThanEqualAndPaymentStatusNot(today, PAID)` returns all non-PAID installments with due date up to and including today. On days following a failed attempt, the retried installment (with an updated `expectedDueDate`) will still be found along with any other older non-PAID installments, potentially processing them multiple times in unexpected ways.

---

**[MEDIUM] Column naming inconsistency in SQL migration — 001-initial-schema.sql:24, 53**

`userEmail` and `username` are defined as camelCase column names in the SQL migration. PostgreSQL folds unquoted identifiers to lowercase, so they become `useremail` and `username` in the DB. JPA maps field `userEmail` to column `useremail` by default — this may work but is non-standard and inconsistent with the rest of the schema which uses snake_case.

**Fix:** Use `user_email` as the column name and annotate with `@Column(name = "user_email")`.

---

**[MEDIUM] Thread-safety: referenceRate initialized with random value at startup — LoanServiceImplementation.java:57**

```java
private BigDecimal referenceRate = BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(...))
```
This means the reference rate is random at every service startup rather than persisted. After a restart during the month, the rate changes, silently altering all variable-rate loan calculations without any audit trail.

**Fix:** Persist the current reference rate in a database table or cache so it survives restarts.

---

**[MEDIUM] company-observability-starter commented out — build.gradle.kts:29**

Required by Issue #1. No distributed tracing, no custom metrics. Monitoring is unavailable.

---

### Low

**[LOW] @EnableScheduling not verified — CreditServiceApplication.java**

`@Scheduled` cron jobs require `@EnableScheduling` on a configuration class. If `security-lib` does not provide it, the cron jobs will register silently but never execute. Verify or add `@EnableScheduling` explicitly.

---

**[LOW] Installment uses FetchType.EAGER for Loan back-reference — Installment.java:27**

Every time the cron fetches all due installments, each `Installment` eagerly loads its parent `Loan`. For large datasets this can produce significant memory pressure. Consider a named JPQL query with an explicit JOIN FETCH instead.

---

**[LOW] BankPaymentDto null field semantics are unclear — LoanServiceImplementation.java:152, 188**

On approval `fromAccountNumber` is null; on installment collection `toAccountNumber` is null. The intent is inferable from context but the account-service contract for these null fields is undocumented. A mismatch in interpretation would cause silent financial errors.

---

**[LOW] Hardcoded Belgrade timezone in cron expressions — LoanServiceImplementation.java:180, 232**

`zone = "Europe/Belgrade"` is hardcoded. Externalize to `application.properties`.

---

**[LOW] Spring Boot 4.0.3 is a pre-GA release — build.gradle.kts:4**

Spring Boot 4.x is not yet generally available. Using a milestone/pre-release version in a production banking system carries risk of API changes and missing security patches. Consider using the latest stable Spring Boot 3.x release.

---

**[LOW] @Component name copied from another service — GlobalExceptionHandler.java:24**

```java
@Component("transactionServiceGlobalExceptionHandler")
```
This was clearly copied from transaction-service. While Spring resolves it correctly, the name is misleading.

---

**[LOW] ClientService and VerificationService are empty shells**

Both exist as registered Spring beans with no implemented methods. They add startup overhead without providing value. Either implement them or remove them.

---

### Info

**[INFO] Single Status enum for both LoanRequest and Loan**

The spec implies separate status enumerations (LoanRequest: PENDING/APPROVED/DECLINED; Loan: PENDING/APPROVED/DECLINED/ACTIVE/OVERDUE/PAID_OFF). A single enum is used for both, making it possible to (incorrectly) set a LoanRequest to ACTIVE. The TODO comment acknowledges this. Consider splitting into `LoanRequestStatus` and `LoanStatus`.

**[INFO] InterestRateStore should be a record**

This is a simple two-field value object. A Java record would eliminate the boilerplate constructor and be more idiomatic in Java 21.

**[INFO] approve/decline return plain String response**

`ResponseEntity<String>` with "ODOBREN ZAHTEV" / "ODBIJEN ZAHTEV" is not a structured response. Consider a DTO with the updated request status and relevant IDs.

**[INFO] @NotBlank on Loan entity fields is decorative**

`@NotBlank` on `Loan.accountNumber` (a JPA entity) will never be triggered — Bean Validation on entities fires only if explicitly invoked. Validation belongs on the DTO layer, not the entity.

**[INFO] Read-only service methods should use @Transactional(readOnly = true)**

`find()`, `info()`, `findAllLoanRequest()`, and `findAllLoans()` are read-only. Adding `readOnly = true` enables database-level optimizations and avoids Hibernate dirty-checking overhead.

---

## 6. Summary

**Overall Assessment: REQUEST CHANGES**

**Risk Level: HIGH**

### Issue Counts

| Severity | Count |
|---|---|
| Critical | 3 |
| High | 8 |
| Medium | 9 |
| Low | 7 |
| Info | 5 |

### Spec / Requirements Compliance

| Status | Count |
|---|---|
| Fully implemented | 28 |
| Partially implemented | 11 |
| Not implemented | 15 |
| Undocumented (not in spec) | 2 |

### Must-Fix Before Merge

1. **[CRITICAL]** Authorization bypass in `GET /api/loans/{loanNumber}` — JWT user ID compared to loanNumber, not loan.clientId. Any client can read any loan.
2. **[CRITICAL]** NullPointerException when optional filter params are omitted in `GET /api/loans/requests` and `GET /api/loans/all` — employee portal crashes without filters.
3. **[CRITICAL]** clientId accepted from untrusted request body — malicious client can persist any clientId.
4. **[HIGH]** RabbitMQ messaging entirely absent — no approval, decline, or installment-failure notifications.
5. **[HIGH]** PAID_OFF status never set — fully repaid loans stay ACTIVE forever.
6. **[HIGH]** clientId not set on Loan entity — NOT NULL constraint violation crashes every loan approval.
7. **[HIGH]** No Dockerfile and docker-compose entry commented out — service cannot be deployed.
8. **[HIGH]** credit-service not registered in NGINX api-gateway.
9. **[HIGH]** Non-existent account returns 400, not the required 404.
10. **[HIGH]** Second installment retry uses 24h window, not the required 72h.

### Recommended Fixes

1. Remove `clientId` from `LoanRequestDto`, derive from JWT only.
2. Add `loan.setClientId(loanRequest.getClientId())` in the approval flow.
3. Add commission-suppression parameter to `ExchangeService.calculate()`.
4. Store interest rates as annual percentages, convert to decimal at calculation time.
5. Return `LoanRequestListDto` from `GET /api/loans/requests`, not the raw entity.
6. Add `@NotNull @Positive` to `LoanRequestDto.monthlySalary`.
7. Handle `ObjectOptimisticLockingFailureException` in `GlobalExceptionHandler` with HTTP 409.
8. Persist `referenceRate` to database so it survives restarts.
9. Fix SQL migration column names to snake_case (`user_email`).
10. Add `@EnableScheduling` explicitly on a configuration class.

### Unimplemented Requirements

- **R1b** — credit-service Dockerfile and docker-compose entry
- **R1c** — NGINX api-gateway /credits/ route
- **R1f** — company-observability-starter dependency
- **R1g** — RabbitMQ producer (RabbitConfig, RabbitTemplate, all three publish calls)
- **R2e** — Separate unique `loanNumber` field on Loan entity (commented out)
- **R5e** — Unit and integration tests for POST /api/loans/requests
- **R6f** — Unit tests for each interest rate amount range
- **R7i** — Publish `credit.approved` RabbitMQ event
- **R7j** — Publish `credit.declined` RabbitMQ event
- **R7l** — Tests for approve/decline endpoints
- **R8e** — Tests for GET /api/loans/client
- **R9d** — Tests for GET /api/loans/{loanNumber}
- **R10d** (partial) — `credit.installment_failed` RabbitMQ event not published
- **R10g** — Tests for cron job
- **R11e** — Tests for GET /api/loans/requests and GET /api/loans/all

---

**Inline summary:** Overall assessment is REQUEST CHANGES at HIGH risk. There are 3 critical findings (authorization bypass, employee portal NullPointerException crash, clientId impersonation), 8 high-severity findings (missing RabbitMQ, PAID_OFF never set, clientId not saved on Loan causing every approval to crash, no deployment artifacts, wrong HTTP codes, incorrect retry timing), and 15 unimplemented specification requirements. The service has a solid structural foundation — entities, repositories, interest rate calculation, and most business logic are well-designed — but the authorization bug on loan detail access, the employee portal crash on unfiltered requests, the missing clientId on Loan entity, and the complete absence of RabbitMQ integration must all be fixed before this can be safely merged.
