// Package saga_test contains end-to-end integration tests for the OTC exercise
// SAGA as specified in SAGA_test.pdf. Tests run against the live Docker stack.
//
// Run with:
//
//	cd saga-tests && go test -v -timeout 180s ./...
//
// Required environment (defaults work with local docker-compose stack):
//
//	TRADING_URL = http://localhost:18088   (trading-service direct port)
//	SAGA_URL    = http://localhost:8095    (saga-orchestrator admin port)
//	TRADING_DB  = postgres://postgres:postgres@localhost:5432/trading
//	BANKING_DB  = postgres://postgres:postgres@localhost:5432/banking_core
package saga_test

import (
	"bytes"
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
)

// ---------------------------------------------------------------------------
// Config
// ---------------------------------------------------------------------------

func tradingURL() string {
	if u := os.Getenv("TRADING_URL"); u != "" {
		return u
	}
	return "http://localhost:80"
}

func sagaURL() string {
	if u := os.Getenv("SAGA_URL"); u != "" {
		return u
	}
	return "http://localhost:8095"
}

// ---------------------------------------------------------------------------
// DB connection
// ---------------------------------------------------------------------------

type dbConns struct {
	trading *pgxpool.Pool
	banking *pgxpool.Pool
}

func openDBs(t *testing.T) *dbConns {
	t.Helper()
	ctx := context.Background()

	tradingDSN := os.Getenv("TRADING_DB")
	if tradingDSN == "" {
		tradingDSN = "postgres://postgres:postgres@localhost:5432/trading"
	}
	bankingDSN := os.Getenv("BANKING_DB")
	if bankingDSN == "" {
		bankingDSN = "postgres://postgres:postgres@localhost:5432/banking_core"
	}

	tp, err := pgxpool.New(ctx, tradingDSN)
	if err != nil {
		t.Skipf("cannot connect to trading DB: %v", err)
	}
	bp, err := pgxpool.New(ctx, bankingDSN)
	if err != nil {
		tp.Close()
		t.Skipf("cannot connect to banking_core DB: %v", err)
	}
	t.Cleanup(func() { tp.Close(); bp.Close() })
	return &dbConns{trading: tp, banking: bp}
}

// ---------------------------------------------------------------------------
// Test seed
// ---------------------------------------------------------------------------

const (
	testBuyerID  = int64(99001)
	testSellerID = int64(99002)
	// listing_id=1 corresponds to AAPL in the seeded market data.
	testListingID  = int64(1)
	testTicker     = "AAPL"
	testQty        = 10
	testStrikeUSD  = 300.0 // price_per_stock; total = 10×300 = 3000 USD
	testBuyerRSD   = 5000000.0 // enough to cover ~3000 USD at any reasonable rate
	testSellerQty  = 10
	// RSD currency_id in banking_core.currency_table
	currencyRSD = int64(8)
)

// seedTest creates test accounts, portfolio rows, and one ACTIVE contract.
// Returns the contract ID. Registers cleanup via t.Cleanup.
func seedTest(t *testing.T, dbs *dbConns, buyerRSD, sellerQty float64) int64 {
	t.Helper()
	ctx := context.Background()
	cid := doSeed(t, ctx, dbs, buyerRSD, sellerQty)
	t.Cleanup(func() { doCleanup(ctx, dbs, cid) })
	return cid
}

func doSeed(t *testing.T, ctx context.Context, dbs *dbConns, buyerRSD, sellerQty float64) int64 {
	t.Helper()

	// Account numbers must be 18 digits (banking-core validation).
	buyerAcctNum := fmt.Sprintf("111000199001000001")  // test buyer RSD account
	sellerAcctNum := fmt.Sprintf("111000199002000001") // test seller RSD account

	// Buyer account (RSD). Pass account number as separate param to avoid
	// pgx type-inference conflict between bigint (vlasnik) and the text (broj_racuna).
	mustExec(t, dbs.banking, ctx, `
		INSERT INTO account_table
		  (account_type, broj_racuna, ime_vlasnika_racuna, prezime_vlasnika_racuna,
		   naziv_racuna, vlasnik, zaposlen, stanje, raspolozivo_stanje,
		   datum_i_vreme_kreiranja, datum_isteka, currency_id, status,
		   dnevni_limit, mesecni_limit, dnevna_potrosnja, mesecna_potrosnja,
		   account_concrete, version)
		VALUES
		  ('CHECKING', $4, 'Test', 'Buyer', 'Test',
		   $1, 1, $2, $2, NOW(), '2031-12-31', $3, 'ACTIVE',
		   1000000000, 10000000000, 0, 0, 'STANDARDNI', 0)
		ON CONFLICT (broj_racuna) DO UPDATE
		  SET stanje = $2, raspolozivo_stanje = $2
	`, testBuyerID, buyerRSD, currencyRSD, buyerAcctNum)

	// Seller account (RSD, zero balance – only receives)
	mustExec(t, dbs.banking, ctx, `
		INSERT INTO account_table
		  (account_type, broj_racuna, ime_vlasnika_racuna, prezime_vlasnika_racuna,
		   naziv_racuna, vlasnik, zaposlen, stanje, raspolozivo_stanje,
		   datum_i_vreme_kreiranja, datum_isteka, currency_id, status,
		   dnevni_limit, mesecni_limit, dnevna_potrosnja, mesecna_potrosnja,
		   account_concrete, version)
		VALUES
		  ('CHECKING', $3, 'Test', 'Seller', 'Test',
		   $1, 1, 0, 0, NOW(), '2031-12-31', $2, 'ACTIVE',
		   1000000000, 10000000000, 0, 0, 'STANDARDNI', 0)
		ON CONFLICT (broj_racuna) DO UPDATE
		  SET stanje = 0, raspolozivo_stanje = 0
	`, testSellerID, currencyRSD, sellerAcctNum)

	// Seller portfolio (AAPL)
	mustExec(t, dbs.trading, ctx, `
		INSERT INTO portfolio
		  (user_id, listing_id, listing_type, quantity, average_purchase_price, is_public,
		   public_quantity, last_modified, reserved_quantity)
		VALUES ($1, $2, 'STOCK', $3, 1.00, false, 0, NOW(), 0)
		ON CONFLICT (user_id, listing_id) DO UPDATE
		  SET quantity = $3, reserved_quantity = 0
	`, testSellerID, testListingID, int(sellerQty))

	// Buyer portfolio (AAPL, starts empty)
	mustExec(t, dbs.trading, ctx, `
		INSERT INTO portfolio
		  (user_id, listing_id, listing_type, quantity, average_purchase_price, is_public,
		   public_quantity, last_modified, reserved_quantity)
		VALUES ($1, $2, 'STOCK', 0, 0, false, 0, NOW(), 0)
		ON CONFLICT (user_id, listing_id) DO UPDATE
		  SET quantity = 0, reserved_quantity = 0
	`, testBuyerID, testListingID)

	// OTC offer
	settlementDate := time.Now().Add(48 * time.Hour).Format("2006-01-02")
	var offerID int64
	err := dbs.trading.QueryRow(ctx, `
		INSERT INTO otc_offers
		  (stock_ticker, buyer_id, seller_id, amount, price_per_stock, premium,
		   settlement_date, status, last_modified, created_at)
		VALUES ($1, $2, $3, $4, $5, 0, $6::date, 'ACCEPTED', NOW(), NOW())
		RETURNING id
	`, testTicker, testBuyerID, testSellerID, testQty, testStrikeUSD, settlementDate).Scan(&offerID)
	if err != nil {
		t.Fatalf("seed offer: %v", err)
	}

	// Option contract
	var contractID int64
	err = dbs.trading.QueryRow(ctx, `
		INSERT INTO option_contracts
		  (offer_id, stock_ticker, buyer_id, seller_id, amount,
		   price_per_stock, settlement_date, status, created_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7::date, 'ACTIVE', NOW())
		RETURNING id
	`, offerID, testTicker, testBuyerID, testSellerID, testQty, testStrikeUSD, settlementDate).Scan(&contractID)
	if err != nil {
		t.Fatalf("seed contract: %v", err)
	}
	return contractID
}

func doCleanup(ctx context.Context, dbs *dbConns, contractID int64) {
	dbs.trading.Exec(ctx, `DELETE FROM option_contracts WHERE id = $1`, contractID)
	dbs.trading.Exec(ctx, `DELETE FROM otc_offers WHERE buyer_id = $1`, testBuyerID)
	dbs.trading.Exec(ctx, `DELETE FROM portfolio WHERE user_id IN ($1,$2)`, testBuyerID, testSellerID)
	dbs.trading.Exec(ctx, `DELETE FROM stock_reservations WHERE seller_id = $1`, testSellerID)
	dbs.trading.Exec(ctx, `DELETE FROM stock_ownership_transfers WHERE seller_id = $1`, testSellerID)
	dbs.banking.Exec(ctx, `DELETE FROM account_table WHERE vlasnik IN ($1,$2)`, testBuyerID, testSellerID)
}

func mustExec(t *testing.T, pool *pgxpool.Pool, ctx context.Context, sql string, args ...any) {
	t.Helper()
	if _, err := pool.Exec(ctx, sql, args...); err != nil {
		t.Fatalf("DB exec: %v\nSQL: %s", err, sql)
	}
}

// ---------------------------------------------------------------------------
// HTTP helpers
// ---------------------------------------------------------------------------

// doExercise calls POST /options/{contractId}/exercise.
// Returns (correlationId, httpStatus).
func doExercise(t *testing.T, contractID int64, jwt string, headers map[string]string) (string, int) {
	t.Helper()
	url := fmt.Sprintf("%s/options/%d/exercise", tradingURL(), contractID)
	req, _ := http.NewRequest(http.MethodPost, url, bytes.NewReader([]byte("{}")))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+jwt)
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("POST exercise: %v", err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode == http.StatusAccepted {
		var r struct {
			CorrelationID string `json:"correlationId"`
		}
		_ = json.Unmarshal(body, &r)
		return r.CorrelationID, resp.StatusCode
	}
	return "", resp.StatusCode
}

// ---------------------------------------------------------------------------
// Saga polling
// ---------------------------------------------------------------------------

type sagaInstance struct {
	ID          string          `json:"id"`
	CorrelationID string        `json:"correlationId"`
	CurrentStep int             `json:"currentStep"`
	State       string          `json:"state"`
	CompLog     json.RawMessage `json:"compensationLog"`
}

type stepRecord struct {
	Step    string `json:"step"`
	Outcome string `json:"outcome"`
}

type sagaLogView struct {
	Steps []stepRecord      `json:"steps"`
	Refs  map[string]string `json:"refs"`
}

// pollSaga polls until the saga reaches a terminal state or timeout.
func pollSaga(t *testing.T, corrID string, timeout time.Duration) *sagaInstance {
	t.Helper()
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		url := fmt.Sprintf("%s/saga/instances/by-correlation?sagaType=OTC_EXERCISE&correlationId=%s",
			sagaURL(), corrID)
		resp, err := http.Get(url)
		if err != nil || resp.StatusCode == http.StatusNotFound {
			time.Sleep(400 * time.Millisecond)
			continue
		}
		var inst sagaInstance
		_ = json.NewDecoder(resp.Body).Decode(&inst)
		resp.Body.Close()
		if inst.State == "COMPLETED" || inst.State == "COMPENSATED" || inst.State == "FAILED" {
			return &inst
		}
		time.Sleep(400 * time.Millisecond)
	}
	t.Fatalf("saga %s did not reach terminal state within %s", corrID, timeout)
	return nil
}

func parseLog(t *testing.T, inst *sagaInstance) sagaLogView {
	t.Helper()
	if inst.CompLog == nil {
		return sagaLogView{}
	}
	var sl sagaLogView
	if err := json.Unmarshal(inst.CompLog, &sl); err != nil {
		t.Fatalf("parse saga log: %v\nraw: %s", err, inst.CompLog)
	}
	return sl
}

// assertSteps checks step records match expected pattern (Step + Outcome).
func assertSteps(t *testing.T, label string, got []stepRecord, want []stepRecord) {
	t.Helper()
	if len(got) != len(want) {
		t.Errorf("[%s] step count: got %d want %d\n  got:  %v\n  want: %v",
			label, len(got), len(want), got, want)
		return
	}
	for i, w := range want {
		g := got[i]
		if g.Step != w.Step || g.Outcome != w.Outcome {
			t.Errorf("[%s] step[%d]: got {%s %s}, want {%s %s}",
				label, i, g.Step, g.Outcome, w.Step, w.Outcome)
		}
	}
}

// ---------------------------------------------------------------------------
// Invariant assertions
// ---------------------------------------------------------------------------

func assertI1Money(t *testing.T, dbs *dbConns, initialRSD float64, label string) {
	t.Helper()
	ctx := context.Background()
	var total float64
	dbs.banking.QueryRow(ctx, `
		SELECT COALESCE(SUM(stanje),0)
		FROM account_table WHERE vlasnik IN ($1,$2) AND currency_id = $3
	`, testBuyerID, testSellerID, currencyRSD).Scan(&total)
	if absF(total-initialRSD) > 1.0 { // allow 1 RSD rounding
		t.Errorf("[%s] I1 money: total=%.2f, want ~%.2f", label, total, initialRSD)
	}
}

func assertI2Stocks(t *testing.T, dbs *dbConns, initialQty int, label string) {
	t.Helper()
	ctx := context.Background()
	var total int
	dbs.trading.QueryRow(ctx, `
		SELECT COALESCE(SUM(quantity+reserved_quantity),0)
		FROM portfolio WHERE user_id IN ($1,$2) AND listing_id = $3
	`, testBuyerID, testSellerID, testListingID).Scan(&total)
	if total != initialQty {
		t.Errorf("[%s] I2 stocks: total=%d, want %d", label, total, initialQty)
	}
}

func assertI3NoReserved(t *testing.T, dbs *dbConns, label string) {
	t.Helper()
	ctx := context.Background()
	var reservedRSD float64
	dbs.banking.QueryRow(ctx, `
		SELECT COALESCE(SUM(raspolozivo_stanje - stanje),0)
		FROM account_table WHERE vlasnik IN ($1,$2)
	`, testBuyerID, testSellerID).Scan(&reservedRSD)
	// raspolozivo_stanje <= stanje always; gap = reserved amount
	if reservedRSD < -1 {
		t.Errorf("[%s] I3 banking reserved: %.2f (unexpected gap)", label, reservedRSD)
	}

	var reservedQty int
	dbs.trading.QueryRow(ctx, `
		SELECT COALESCE(SUM(reserved_quantity),0)
		FROM portfolio WHERE user_id IN ($1,$2) AND listing_id = $3
	`, testBuyerID, testSellerID, testListingID).Scan(&reservedQty)
	if reservedQty != 0 {
		t.Errorf("[%s] I3 stock reserved_quantity=%d, want 0", label, reservedQty)
	}
}

func absF(f float64) float64 {
	if f < 0 {
		return -f
	}
	return f
}

// ---------------------------------------------------------------------------
// JWT helpers
// ---------------------------------------------------------------------------

func devJWT(userID int64, roles ...string) string {
	header := b64url([]byte(`{"alg":"HS256","typ":"JWT"}`))
	now := time.Now().Unix()
	roleJSON, _ := json.Marshal(roles)
	payload := b64url([]byte(fmt.Sprintf(
		`{"id":%d,"roles":%s,"permissions":[],"iat":%d,"exp":%d,"iss":"banka1"}`,
		userID, string(roleJSON), now, now+3600,
	)))
	data := header + "." + payload
	sig := b64url(hmacSHA256Bytes([]byte("dev-jwt-secret-do-not-use-in-prod"), []byte(data)))
	return data + "." + sig
}

func b64url(b []byte) string {
	s := base64.StdEncoding.EncodeToString(b)
	s = strings.ReplaceAll(s, "+", "-")
	s = strings.ReplaceAll(s, "/", "_")
	s = strings.ReplaceAll(s, "=", "")
	return s
}

func hmacSHA256Bytes(key, data []byte) []byte {
	mac := hmac.New(sha256.New, key)
	mac.Write(data)
	return mac.Sum(nil)
}

// waitHealthy polls until the API gateway liveness probe returns 200, up to timeout.
// Used to ensure services have recovered between infrastructure tests.
func waitHealthy(t *testing.T, timeout time.Duration) {
	t.Helper()
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		resp, err := http.Get("http://localhost/actuator/health/liveness")
		if err == nil && resp.StatusCode == 200 {
			resp.Body.Close()
			return
		}
		if resp != nil {
			resp.Body.Close()
		}
		time.Sleep(1 * time.Second)
	}
	t.Logf("warning: services may not be fully healthy after %s", timeout)
}

// ---------------------------------------------------------------------------
// Docker helpers
// ---------------------------------------------------------------------------

func dockerCompose(args ...string) {
	cmdArgs := append([]string{
		"compose",
		"--env-file", "../setup/.env",
		"-f", "../setup/docker-compose.yml",
	}, args...)
	cmd := exec.Command("docker", cmdArgs...)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	_ = cmd.Run()
}

// ---------------------------------------------------------------------------
// SG-01: Happy path
// ---------------------------------------------------------------------------

func TestSG01_HappyPath(t *testing.T) {
	dbs := openDBs(t)
	cid := seedTest(t, dbs, testBuyerRSD, float64(testSellerQty))

	corrID, status := doExercise(t, cid, devJWT(testBuyerID, "CLIENT_TRADING"), nil)
	if status != http.StatusAccepted {
		t.Fatalf("SG-01: expected 202, got %d", status)
	}
	if corrID != strconv.FormatInt(cid, 10) {
		t.Errorf("SG-01: correlationId=%q, want %q", corrID, strconv.FormatInt(cid, 10))
	}

	inst := pollSaga(t, corrID, 30*time.Second)
	if inst.State != "COMPLETED" {
		t.Errorf("SG-01: state=%q, want COMPLETED", inst.State)
	}
	if inst.CurrentStep != 5 {
		t.Errorf("SG-01: currentStep=%d, want 5", inst.CurrentStep)
	}

	sl := parseLog(t, inst)
	assertSteps(t, "SG-01", sl.Steps, []stepRecord{
		{"F1", "ok"}, {"F2", "ok"}, {"F3", "ok"}, {"F4", "ok"}, {"F5", "ok"},
	})

	// I6: contract is EXERCISED.
	var status2 string
	dbs.trading.QueryRow(context.Background(),
		`SELECT status FROM option_contracts WHERE id = $1`, cid).Scan(&status2)
	if status2 != "EXERCISED" {
		t.Errorf("SG-01: contract status=%q, want EXERCISED", status2)
	}

	assertI2Stocks(t, dbs, testSellerQty, "SG-01")
	assertI3NoReserved(t, dbs, "SG-01")
}

// ---------------------------------------------------------------------------
// SG-02: Pre-saga validation
// ---------------------------------------------------------------------------

func TestSG02a_CallerNotBuyer(t *testing.T) {
	dbs := openDBs(t)
	cid := seedTest(t, dbs, testBuyerRSD, float64(testSellerQty))

	_, status := doExercise(t, cid, devJWT(testSellerID, "CLIENT_TRADING"), nil)
	if status < 400 {
		t.Errorf("SG-02a: expected 4xx, got %d", status)
	}
	// No saga log must be created.
	time.Sleep(500 * time.Millisecond)
	url := fmt.Sprintf("%s/saga/instances/by-correlation?sagaType=OTC_EXERCISE&correlationId=%d",
		sagaURL(), cid)
	resp, _ := http.Get(url)
	if resp.StatusCode != http.StatusNotFound {
		t.Errorf("SG-02a: expected no saga log (404 from admin), got %d", resp.StatusCode)
	}
}

func TestSG02b_ContractNotFound(t *testing.T) {
	_, status := doExercise(t, 999999999, devJWT(testBuyerID, "CLIENT_TRADING"), nil)
	if status != http.StatusNotFound {
		t.Errorf("SG-02b: expected 404, got %d", status)
	}
}

func TestSG02c_ContractNotActive(t *testing.T) {
	dbs := openDBs(t)
	cid := seedTest(t, dbs, testBuyerRSD, float64(testSellerQty))
	dbs.trading.Exec(context.Background(),
		`UPDATE option_contracts SET status='EXERCISED' WHERE id=$1`, cid)

	_, status := doExercise(t, cid, devJWT(testBuyerID, "CLIENT_TRADING"), nil)
	if status < 400 {
		t.Errorf("SG-02c: expected 4xx, got %d", status)
	}
}

func TestSG02d_SettlementDatePast(t *testing.T) {
	dbs := openDBs(t)
	cid := seedTest(t, dbs, testBuyerRSD, float64(testSellerQty))
	dbs.trading.Exec(context.Background(),
		`UPDATE option_contracts SET settlement_date=NOW()-INTERVAL '1 day' WHERE id=$1`, cid)

	_, status := doExercise(t, cid, devJWT(testBuyerID, "CLIENT_TRADING"), nil)
	if status < 400 {
		t.Errorf("SG-02d: expected 4xx, got %d", status)
	}
}

// ---------------------------------------------------------------------------
// SG-03: F1 fails — buyer has insufficient funds
// ---------------------------------------------------------------------------

func TestSG03_InsufficientFunds(t *testing.T) {
	dbs := openDBs(t)
	// 1 RSD — far less than any reasonable conversion of 10×300 USD.
	cid := seedTest(t, dbs, 1.0, float64(testSellerQty))

	corrID, status := doExercise(t, cid, devJWT(testBuyerID, "CLIENT_TRADING"), nil)
	if status != http.StatusAccepted {
		t.Fatalf("SG-03: expected 202, got %d", status)
	}

	inst := pollSaga(t, corrID, 30*time.Second)
	if inst.State != "COMPENSATED" {
		t.Errorf("SG-03: state=%q, want COMPENSATED", inst.State)
	}
	if inst.CurrentStep != 1 {
		t.Errorf("SG-03: currentStep=%d, want 1", inst.CurrentStep)
	}
	sl := parseLog(t, inst)
	assertSteps(t, "SG-03", sl.Steps, []stepRecord{{"F1", "err"}})
	assertI2Stocks(t, dbs, testSellerQty, "SG-03")
}

// ---------------------------------------------------------------------------
// SG-04: F2 fails — seller has insufficient stocks
// ---------------------------------------------------------------------------

func TestSG04_InsufficientStocks(t *testing.T) {
	dbs := openDBs(t)
	// Seed 3 stocks (< 10 required).
	cid := seedTest(t, dbs, testBuyerRSD, 3.0)

	corrID, _ := doExercise(t, cid, devJWT(testBuyerID, "CLIENT_TRADING"), nil)
	inst := pollSaga(t, corrID, 30*time.Second)

	if inst.State != "COMPENSATED" {
		t.Errorf("SG-04: state=%q, want COMPENSATED", inst.State)
	}
	if inst.CurrentStep != 2 {
		t.Errorf("SG-04: currentStep=%d, want 2", inst.CurrentStep)
	}
	sl := parseLog(t, inst)
	assertSteps(t, "SG-04", sl.Steps, []stepRecord{
		{"F1", "ok"}, {"F2", "err"}, {"C1", "ok"},
	})
	assertI2Stocks(t, dbs, 3, "SG-04")
	assertI3NoReserved(t, dbs, "SG-04")
}

// ---------------------------------------------------------------------------
// SG-05: F3 force-fail → compensate C2, C1
// ---------------------------------------------------------------------------

func TestSG05_F3Fail(t *testing.T) {
	dbs := openDBs(t)
	cid := seedTest(t, dbs, testBuyerRSD, float64(testSellerQty))

	corrID, _ := doExercise(t, cid, devJWT(testBuyerID, "CLIENT_TRADING"), map[string]string{
		"X-Saga-Force-Fail": "F3",
	})
	inst := pollSaga(t, corrID, 30*time.Second)

	if inst.State != "COMPENSATED" {
		t.Errorf("SG-05: state=%q, want COMPENSATED", inst.State)
	}
	sl := parseLog(t, inst)
	assertSteps(t, "SG-05", sl.Steps, []stepRecord{
		{"F1", "ok"}, {"F2", "ok"}, {"F3", "err"}, {"C2", "ok"}, {"C1", "ok"},
	})
	assertI2Stocks(t, dbs, testSellerQty, "SG-05")
	assertI3NoReserved(t, dbs, "SG-05")
}

// ---------------------------------------------------------------------------
// SG-06: F4 force-fail → compensate C3, C2, C1
// ---------------------------------------------------------------------------

func TestSG06_F4Fail(t *testing.T) {
	dbs := openDBs(t)
	cid := seedTest(t, dbs, testBuyerRSD, float64(testSellerQty))

	corrID, _ := doExercise(t, cid, devJWT(testBuyerID, "CLIENT_TRADING"), map[string]string{
		"X-Saga-Force-Fail": "F4",
	})
	inst := pollSaga(t, corrID, 30*time.Second)

	if inst.State != "COMPENSATED" {
		t.Errorf("SG-06: state=%q, want COMPENSATED", inst.State)
	}
	sl := parseLog(t, inst)
	assertSteps(t, "SG-06", sl.Steps, []stepRecord{
		{"F1", "ok"}, {"F2", "ok"}, {"F3", "ok"},
		{"F4", "err"}, {"C3", "ok"}, {"C2", "ok"}, {"C1", "ok"},
	})
	assertI2Stocks(t, dbs, testSellerQty, "SG-06")
	assertI3NoReserved(t, dbs, "SG-06")
}

// ---------------------------------------------------------------------------
// SG-07: F5 force-fail → compensate C4, C3, C2, C1
// ---------------------------------------------------------------------------

func TestSG07_F5Fail(t *testing.T) {
	dbs := openDBs(t)
	cid := seedTest(t, dbs, testBuyerRSD, float64(testSellerQty))

	corrID, _ := doExercise(t, cid, devJWT(testBuyerID, "CLIENT_TRADING"), map[string]string{
		"X-Saga-Force-Fail": "F5",
	})
	inst := pollSaga(t, corrID, 30*time.Second)

	if inst.State != "COMPENSATED" {
		t.Errorf("SG-07: state=%q, want COMPENSATED", inst.State)
	}
	sl := parseLog(t, inst)
	assertSteps(t, "SG-07", sl.Steps, []stepRecord{
		{"F1", "ok"}, {"F2", "ok"}, {"F3", "ok"}, {"F4", "ok"},
		{"F5", "err"}, {"C4", "ok"}, {"C3", "ok"}, {"C2", "ok"}, {"C1", "ok"},
	})
	assertI2Stocks(t, dbs, testSellerQty, "SG-07")
	assertI3NoReserved(t, dbs, "SG-07")
}

// ---------------------------------------------------------------------------
// SG-08: Compensator fails once then succeeds
// ---------------------------------------------------------------------------

func TestSG08_CompensatorRetry(t *testing.T) {
	dbs := openDBs(t)
	cid := seedTest(t, dbs, testBuyerRSD, float64(testSellerQty))

	corrID, _ := doExercise(t, cid, devJWT(testBuyerID, "CLIENT_TRADING"), map[string]string{
		"X-Saga-Force-Fail":            "F3",
		"X-Saga-Compensate-Fail":       "C2",
		"X-Saga-Compensate-Fail-Times": "1",
	})
	inst := pollSaga(t, corrID, 30*time.Second)

	if inst.State != "COMPENSATED" {
		t.Errorf("SG-08: state=%q, want COMPENSATED", inst.State)
	}
	sl := parseLog(t, inst)
	// F1 ok, F2 ok, F3 err, C2 err, C2 ok, C1 ok — 6 records.
	assertSteps(t, "SG-08", sl.Steps, []stepRecord{
		{"F1", "ok"}, {"F2", "ok"}, {"F3", "err"},
		{"C2", "err"}, {"C2", "ok"}, {"C1", "ok"},
	})
	assertI2Stocks(t, dbs, testSellerQty, "SG-08")
	assertI3NoReserved(t, dbs, "SG-08")
}

// ---------------------------------------------------------------------------
// SG-09a: banking-core service paused → F1 unavailable
// ---------------------------------------------------------------------------

func TestSG09a_BankingCorePaused(t *testing.T) {
	dbs := openDBs(t)
	cid := seedTest(t, dbs, testBuyerRSD, float64(testSellerQty))
	t.Cleanup(func() { dockerCompose("unpause", "banking-core-service") })

	// Inject 1s delay at F1 to create a reliable window: exercise is called,
	// orchestrator starts and enters the delay, we pause banking-core, then
	// the delay expires and F1 tries to call the paused service → timeout.
	corrID, status := doExercise(t, cid, devJWT(testBuyerID, "CLIENT_TRADING"), map[string]string{
		"X-Saga-Inject-Delay": "F1:1000",
	})
	if status != http.StatusAccepted {
		t.Fatalf("SG-09a: expected 202, got %d", status)
	}

	// Give the orchestrator 300ms to start the delay, then pause banking-core.
	time.Sleep(300 * time.Millisecond)
	dockerCompose("pause", "banking-core-service")

	// Banking-core is paused when F1 calls it after the 1s delay expires.
	// Orchestrator times out (SAGA_SERVICES_TIMEOUT=5s) then compensates.
	// No compensators needed (F1 failed before creating a reservation).
	inst := pollSaga(t, corrID, 20*time.Second)
	dockerCompose("unpause", "banking-core-service")

	if inst.State != "COMPENSATED" {
		t.Errorf("SG-09a: state=%q, want COMPENSATED", inst.State)
	}
	if inst.CurrentStep != 1 {
		t.Errorf("SG-09a: currentStep=%d, want 1", inst.CurrentStep)
	}
	assertI3NoReserved(t, dbs, "SG-09a")
}

// ---------------------------------------------------------------------------
// SG-11: Coordinator killed mid-flight
// ---------------------------------------------------------------------------

func TestSG11_CoordinatorKilledMidFlight(t *testing.T) {
	// Wait for banking-core to recover from SG-09a's pause/unpause cycle.
	waitHealthy(t, 30*time.Second)

	dbs := openDBs(t)
	cid := seedTest(t, dbs, testBuyerRSD, float64(testSellerQty))
	t.Cleanup(func() {
		// Ensure coordinator is back up on any exit path.
		dockerCompose("start", "trading-service")
	})

	// Inject 5s delay at F3 to give us a window to kill the coordinator.
	corrID, status := doExercise(t, cid, devJWT(testBuyerID, "CLIENT_TRADING"), map[string]string{
		"X-Saga-Inject-Delay": "F3:5000",
	})
	if status != http.StatusAccepted {
		t.Fatalf("SG-11: expected 202, got %d", status)
	}

	// Kill coordinator mid-flight.
	time.Sleep(1 * time.Second)
	dockerCompose("kill", "-s", "KILL", "trading-service")
	time.Sleep(500 * time.Millisecond)
	dockerCompose("up", "-d", "trading-service")

	// Wait for recovery: coordinator restarts and resumes the saga.
	inst := pollSaga(t, corrID, 90*time.Second)
	if inst.State != "COMPLETED" && inst.State != "COMPENSATED" {
		t.Errorf("SG-11: state=%q, want COMPLETED or COMPENSATED", inst.State)
	}
	assertI2Stocks(t, dbs, testSellerQty, "SG-11")
	assertI3NoReserved(t, dbs, "SG-11")
}
