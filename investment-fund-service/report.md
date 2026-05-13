# PR Review Report

**Service:** investment-fund-service  
**Reviewed by:** AI Code Review Agent  
**Date:** 2026-05-13  
**Branch:** main

---

## 1. Service Overview

### Technology Stack
- Python 3.12, FastAPI 0.115, SQLAlchemy 2.0 (async), asyncpg, PostgreSQL
- Redis caching (60 s TTL), APScheduler 3.10 (daily snapshot job)
- JWT auth (HS256) via Starlette BaseHTTPMiddleware
- External deps: banking-service (8084), employee-service (8081), order-service (8088)

### Architecture
Standard 4-layer layout: `Router → Service → Repository → ORM`. All dependencies injected via `Depends`.

---

## 2. Specification Requirements Checklist

| # | Requirement | Status | Notes |
|---|-------------|--------|-------|
| R1 | Fund entity core stored fields | ✅ | All present in InvestmentFund model |
| R2 | FundValue derived | ✅ | `compute_vrednost_fonda` in FundValuationService |
| R3 | Profit derived (FundValue − sum of positions) | ❌ | Wrong: uses sum of all-time inflow transactions, not positions |
| R4 | ClientFundTransaction entity | ✅ | All required fields present |
| R5 | ClientFundPosition core fields | ✅ | All stored fields present |
| R6 | FundShare derived | ✅ | `compute_procenat_fonda` |
| R7 | CurrentPositionValue derived | ✅ | `compute_trenutna_vrednost_pozicije` |
| R8 | Discovery page with value/profit | ❌ | `GET /funds` returns `FundResponse` without `vrednost_fonda` or `profit` |
| R9 | Filtering and sorting on discovery page | ❌ | `GET /funds` accepts no filter/sort parameters |
| R10 | Client invest with minimumContribution check | ✅ | Enforced in FundInvestmentService |
| R11 | Supervisors create funds | ✅ | `POST /funds` with 409 on duplicate name |
| R12 | Supervisor deposits on behalf of bank | ⚠️ | No validation that supervisors use a bank account; clients can pass bank account IDs |
| R13 | Fund Detail all stored fields | ✅ | FundDetailResponse includes all spec fields except securities |
| R14 | Fund Detail securities list | ❌ | No `securities` field in FundDetailResponse; portfolio data discarded after valuation |
| R15 | Supervisor sell button per security | ❌ | No sell-per-security endpoint |
| R16 | Fund performance endpoint | ✅ | `GET /funds/{id}/performance` with period param |
| R17 | Create Fund with all fields | ✅ | `POST /funds` |
| R18 | Auto-create RSD bank account | ✅ | `banking_client.create_fund_account` called |
| R19 | Client list own positions | ✅ | `GET /positions` returns client's positions |
| R20 | Client portfolio: share %, monetary share, achieved profit | ⚠️ | Share % and current value present; "achieved profit = monetary share − invested amount" missing |
| R21 | Client deposit and withdraw | ⚠️ | Work but no "withdraw all" convenience option |
| R22 | Supervisor "My Funds" list | ❌ | No endpoint filters funds by `menadzer_id` |
| R23 | Supervisor portfolio view (value + liquidity per managed fund) | ❌ | No dedicated endpoint |
| R24 | Actuary Performances endpoint | ❌ | Not implemented anywhere |
| R25 | Bank Fund Positions with manager name | ⚠️ | Endpoint exists but returns `menadzer_id` only — no human-readable name/surname |
| R26 | Bank Profit Portal deposit/withdraw actions | ❌ | Only GET exists on `/bank-profit` |
| R27 | Account type enforcement (own vs bank) | ⚠️ | No ownership validation on source/destination accounts |
| R28 | minimumContribution validation | ✅ | Enforced in FundInvestmentService |
| R29 | Create/update position after investment | ✅ | `position_repo.upsert` called on success |
| R30 | Covered redemption → immediate transfer | ✅ | Direct banking transfer on covered path |
| R31 | Uncovered redemption → liquidation + notification | ❌ | Liquidation starts but `complete_liquidation` is never triggered; no notification |
| R32 | Supervisor/bank withdrawal — no commission | ❌ | Commission is always `Decimal("0")` for everyone — no role check |
| R33 | Client withdrawal — charge commission | ❌ | Commission is always `Decimal("0")` — never charged |
| R34 | Bank as investor linked to bank owner client | ⚠️ | `BANK_KLIJENT_ID = -1` sentinel used, not the actual bank owner client ID |
| R35 | Fund ownership transfer on permission revoke | ❌ | No event consumer for this |
| R36 | Securities portal buy routing integration | ❌ | No endpoint in this service |
| R37 | Daily performance snapshots | ❌ | Model exists but scheduler job is wired to `_noop_snapshot` stub — never executes |
| R38 | Client notification on liquidation | ❌ | No notification mechanism of any kind |
| R39 | Fund account belongs to bank | ✅ | `banking_client.create_fund_account` |

**Compliance summary:** 16 fully implemented · 9 partially implemented · 11 not implemented

---

## 3. Endpoint Inventory

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/funds` | Any | Returns all funds; no filter/sort |
| POST | `/funds` | SUPERVISOR/ADMIN | Create fund |
| GET | `/funds/{fund_id}` | Any | Fund detail — missing securities |
| PATCH | `/funds/{fund_id}` | SUPERVISOR/ADMIN | Update fund |
| POST | `/funds/{fund_id}/invest` | CLIENT/SUPERVISOR/ADMIN | Invest |
| POST | `/funds/{fund_id}/redeem` | CLIENT/SUPERVISOR/ADMIN | Redeem |
| GET | `/funds/{fund_id}/performance` | Any | Historical snapshots — always empty (broken scheduler) |
| GET | `/positions` | CLIENT/SUPERVISOR/ADMIN | Own positions |
| GET | `/positions/{position_id}` | Any | Single position |
| GET | `/transactions` | CLIENT/SUPERVISOR/ADMIN | Own transactions |
| GET | `/transactions/{transaction_id}` | Any | Single transaction |
| GET | `/bank-profit/fund-positions` | SUPERVISOR/ADMIN | Bank positions |
| GET | `/health` | Public | Health check |

**Missing endpoints:** supervisor managed-funds list, actuary performances, bank-profit invest/redeem actions, sell-security-from-fund.

---

## 4. Findings

### Critical

**[C1] Performance history is permanently broken**  
`services/scheduler.py` — The APScheduler daily job is wired to `_noop_snapshot`, a function that does nothing. Its own docstring says "in production wire to FundPerformanceService.take_daily_snapshot". The `fund_performance_snapshots` table remains empty in every deployment. `GET /funds/{id}/performance` always returns an empty array.

**Fix:** Replace `_noop_snapshot` in the scheduler registration with an async job that acquires a DB session, constructs `FundPerformanceService`, and calls `take_daily_snapshot()`.

---

**[C2] Profit formula is financially incorrect**  
`services/fund_valuation_service.py:35–38` — Profit is computed as `FundValue − sum(COMPLETED inflow transactions)`. The spec requires `FundValue − sum(ukupan_ulozeni_iznos from positions)`. These diverge after any redemption: the inflow transactions accumulate permanently, but the positions table reflects the net invested balance. After a redemption, profit is understated by the withdrawn amount. This is a financial reporting bug that worsens with every redemption.

**Fix:** Change `compute_profit` to use `position_repo.sum_ulozeni_iznos_by_fund(fund.id)` instead of `tx_repo.sum_inflows_by_fund`.

---

**[C3] Liquidation SAGA never completes — clients never receive their money**  
`services/fund_liquidation_service.py:26–52` — `start_liquidation` fires MARKET SELL orders on the order-service, but `complete_liquidation` and `poll_pending_liquidations` are defined yet called from nowhere in the application (no scheduler job, no webhook, no endpoint). Pending liquidation transactions remain PENDING forever. Clients who trigger an uncovered redemption never receive their funds.

**Fix:** Register `poll_pending_liquidations` as a periodic APScheduler job (every 2–5 min) using a service-generated JWT. Alternatively, integrate an order-service webhook that fires when a sell order fills, triggering `complete_liquidation`.

---

### High

**[H1] Commission is never charged**  
`services/fund_investment_service.py:45`, `services/fund_redemption_service.py:47` — Commission is hard-coded to `Decimal("0")` for all callers. The spec requires: client withdrawals pay commission; supervisor/bank withdrawals are free. This is a financial business rule violation.

**Fix:** In `FundRedemptionService.redeem`, detect caller role and compute the appropriate commission rate for clients.

---

**[H2] Client notification on liquidation is missing**  
The spec states clients must be notified when liquidation is triggered. `FundLiquidationService.start_liquidation` sends no notification. Clients have no visibility that their request is in progress.

**Fix:** After triggering liquidation, call a notification service with the client's ID and estimated timeline.

---

**[H3] Fund Detail does not return the securities list**  
`FundDetailResponse` has no `securities` field. Order-service portfolio data is fetched internally for valuation and then discarded. Clients and supervisors cannot see what the fund holds.

**Fix:** Add `securities: List[SecurityItem]` to `FundDetailResponse`. Pass the fetched portfolio holdings through to the response in `FundRouter.get_fund_detail`.

---

**[H4] No filtering or sorting on GET /funds**  
Discovery page spec explicitly requires filtering and sorting. `GET /funds` accepts no query parameters. Unworkable at scale.

**Fix:** Add optional query params: `name_contains`, `min_contribution_max`, `sort_by` (name, value, profit, minimum_contribution), `sort_order` (asc/desc). Implement in `InvestmentFundRepository.find_all`.

---

**[H5] No supervisor "My Funds" endpoint**  
Supervisors must be able to see the funds they manage. `GET /funds` returns all funds with no `manager_id` filter. No endpoint exists for this view.

**Fix:** Add `manager_id` as an optional filter to `GET /funds`, or add `GET /funds/managed-by-me` using `token.id`.

---

**[H6] Actuary Performances endpoint missing**  
`GET /bank-profit/actuary-performances` (R24) is not implemented. No actuary profit calculation exists anywhere.

**Fix:** Implement the endpoint (supervisor-only). Fetch actuaries from employee-service, aggregate profit contributions from transaction data.

---

**[H7] Bank Profit Portal has no deposit/withdraw actions**  
`BankProfitRouter` has only one GET endpoint. Supervisors cannot deposit to or withdraw from a fund on behalf of the bank from this portal.

**Fix:** Add `POST /bank-profit/funds/{fund_id}/invest` and `POST /bank-profit/funds/{fund_id}/redeem` (supervisor-only), delegating to the existing investment/redemption services with `BANK_KLIJENT_ID`.

---

**[H8] No account ownership validation**  
`POST /funds/{id}/invest` and `POST /funds/{id}/redeem` accept any `source_account_id` / `destination_account_id`. No verification that the account belongs to the authenticated caller. A client can drain any account or redirect funds to any destination.

**Fix:** After fetching account details from banking-service, assert `account.owner_id == token.id`. Return 403 on mismatch.

---

### Medium

**[M1] Withdrawal validates against cost basis, not market value**  
`services/fund_redemption_service.py:36` — Validates `position.ukupan_ulozeni_iznos < request.iznos`. A client whose position gained value is blocked from withdrawing more than their cost basis, which is financially wrong. The limit should be the computed `trenutna_vrednost_pozicije`.

---

**[M2] Post-transfer DB failure leaves money without a position record**  
`services/fund_investment_service.py:47–48` — If `update_likvidna_sredstva` or `position_repo.upsert` throws after a successful banking transfer, the transaction is marked FAILED even though money already moved. The fund holds unrecorded money and the client has no position.

**Fix:** Log a CRITICAL alert requiring manual reconciliation instead of marking the transaction FAILED. Alternatively, issue a compensating banking transfer.

---

**[M3] `datetime.utcnow()` deprecated and timezone-inconsistent**  
`datetime.utcnow()` is deprecated in Python 3.12 and returns a naive datetime while columns are declared `DateTime(timezone=True)`.

**Fix:** Replace all occurrences with `datetime.now(timezone.utc)`.

---

**[M4] Hardcoded default JWT secret**  
`config/settings.py:27` — `jwt_secret` has a publicly known default value. If `JWT_SECRET` is unset in a deployment environment the service accepts JWTs forged with the known secret.

**Fix:** Remove the default, making `jwt_secret` a required field with no default so the service fails at startup when unconfigured.

---

**[M5] Bank investor position is never recorded under BANK_KLIJENT_ID**  
`bank_profit_router.py:14` — `BANK_KLIJENT_ID = -1` is defined but when a supervisor calls `POST /funds/{id}/invest`, the position is stored under the supervisor's own `token.id`. `GET /bank-profit/fund-positions` therefore always returns an empty list.

**Fix:** When a supervisor uses a bank account as the source, record the resulting position under `BANK_KLIJENT_ID`.

---

**[M6] No "withdraw all" convenience option**  
The spec requires an option to withdraw an entire position. `RedeemRequest` requires an explicit `iznos`. Clients must compute their market value from a separate API call.

**Fix:** Add `withdraw_all: bool = False` to `RedeemRequest`. When True, compute `iznos` as the full current position value inside the service.

---

**[M7] N+1 queries in position list for privileged users**  
`routers/position_router.py:35–40` — Iterates over all funds and calls `find_by_fund_id` in a loop for unfiltered privileged requests.

**Fix:** Add `find_all()` to `ClientFundPositionRepository` returning all positions in one query.

---

**[M8] Redis TTL config value ignored**  
`services/fund_valuation_service.py:73` — Hard-codes TTL as `60` instead of `settings.redis_ttl_seconds`.

**Fix:** Inject `settings.redis_ttl_seconds` and use it.

---

**[M9] No structured logging**  
Zero log statements in production code. Multiple `except Exception: pass/continue` sites silently swallow failures (valuation, liquidation, performance snapshot).

**Fix:** Add structured logging throughout. At minimum, every `except` block must log with exception details and context.

---

**[M10] `entrypoint.sh` does not run migrations**  
`Dockerfile` CMD runs `alembic upgrade head && uvicorn ...`. `entrypoint.sh` (used in docker-compose) waits for PostgreSQL and then starts uvicorn directly, skipping migrations. A fresh deployment via docker-compose starts with no schema.

**Fix:** Add `alembic upgrade head` to `entrypoint.sh` before `exec uvicorn`.

---

### Low

**[L1] `BANK_KLIJENT_ID` defined in router file**  
Should be in a central `constants.py` module.

**[L2] No router-level tests**  
Test suite covers services and schemas but has zero tests for routers. Authorization guards, HTTP response codes, and serialization are untested.

**[L3] No repository integration tests**  
The PostgreSQL-specific upsert (`pg_insert(...).on_conflict_do_update(...)`) references constraint name `uq_client_fund`. A rename would silently break it — only catchable with real DB integration tests.

**[L4] Duplicate period-days mapping**  
`services/fund_performance_service.py` — `_period_to_days` static method and an identical inline dict in `get_performance` are two sources of truth for the same data.

**[L5] JWT forwarding inconsistency**  
Banking-service calls use a generated service JWT; employee-service and order-service forward the caller's token. Inconsistent — standardize on service-to-service JWTs for all downstream calls.

**[L6] `get_fund_valuation_service` does not wrap `get_redis` in `Depends`**  
`dependencies.py:107` — inconsistent dependency wiring, harder to mock in tests.

---

## 5. Summary

**Overall Assessment: REQUEST CHANGES — HIGH RISK**

| Severity | Count |
|---|---|
| Critical | 3 |
| High | 8 |
| Medium | 10 |
| Low | 6 |

### Must-Fix Before Merge

1. **(C1)** Wire APScheduler snapshot job to `FundPerformanceService.take_daily_snapshot()` — currently a `_noop_snapshot`, making the entire performance history feature dead code.
2. **(C2)** Fix profit formula: use `sum(ukupan_ulozeni_iznos)` from positions table, not historical inflow transactions.
3. **(C3)** Implement SAGA completion: `complete_liquidation`/`poll_pending_liquidations` are never called — clients never receive money from uncovered redemptions.
4. **(H1)** Implement commission logic: clients pay commission on withdrawal; bank/supervisor do not.
5. **(H2)** Add client notification when liquidation is triggered.
6. **(H3)** Return securities list in `FundDetailResponse`.
7. **(H4)** Add filtering and sorting to `GET /funds`.
8. **(H5)** Add supervisor managed-funds view.
9. **(H6)** Implement `GET /bank-profit/actuary-performances`.
10. **(H7)** Add `POST /bank-profit/funds/{id}/invest` and `.../redeem`.
11. **(H8)** Validate account ownership on invest/redeem.

### Unimplemented Spec Requirements

- **R9** — Discovery page filtering and sorting
- **R14** — Fund Detail securities list (Ticker, Price, Change, Volume, initialMarginCost, acquisitionDate)
- **R15** — Supervisor sell-security button per security
- **R22/R23** — Supervisor "My Funds" list and view
- **R24** — Actuary Performances page
- **R26** — Bank Profit Portal deposit/withdraw actions
- **R32/R33** — Commission logic (not charged to anyone)
- **R35** — Fund ownership transfer when admin removes isSupervisor permission
- **R36** — Securities portal buy-for-fund integration
- **R38** — Client notification on liquidation trigger
