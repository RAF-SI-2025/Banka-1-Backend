package saga_test

import (
	"context"
	"errors"
	"log/slog"
	"os"
	"testing"

	"github.com/shopspring/decimal"

	"github.com/raf-si-2025/banka-1-go/saga-orchestrator-service/internal/events"
	"github.com/raf-si-2025/banka-1-go/saga-orchestrator-service/internal/saga"
	"github.com/raf-si-2025/banka-1-go/saga-orchestrator-service/internal/store"
)

// ---------------------------------------------------------------------------
// injectStore is a SagaStore whose results are fully scripted, so we can drive
// findOrInitialize's error and optimistic-conflict branches deterministically.
// ---------------------------------------------------------------------------

type injectStore struct {
	findResults []*store.SagaInstance
	findErrs    []error
	findIdx     int
	insertErr   error
	updateErr   error
}

func (s *injectStore) FindByTypeAndCorrelation(_ context.Context, _, _ string) (*store.SagaInstance, error) {
	i := s.findIdx
	s.findIdx++
	var inst *store.SagaInstance
	var err error
	if i < len(s.findResults) {
		inst = s.findResults[i]
	}
	if i < len(s.findErrs) {
		err = s.findErrs[i]
	}
	return inst, err
}

func (s *injectStore) Insert(_ context.Context, inst *store.SagaInstance) error {
	if s.insertErr != nil {
		return s.insertErr
	}
	return nil
}

func (s *injectStore) UpdateOptimistic(_ context.Context, _ *store.SagaInstance) error {
	return s.updateErr
}

// flakyUpdateStore delegates to a real fakeStore but fails the Nth
// UpdateOptimistic call, letting us hit the saveLog-failure / emergency-undo
// branches mid-saga.
type flakyUpdateStore struct {
	inner  *fakeStore
	failOn int
	n      int
}

func (s *flakyUpdateStore) FindByTypeAndCorrelation(ctx context.Context, st, c string) (*store.SagaInstance, error) {
	return s.inner.FindByTypeAndCorrelation(ctx, st, c)
}
func (s *flakyUpdateStore) Insert(ctx context.Context, inst *store.SagaInstance) error {
	return s.inner.Insert(ctx, inst)
}
func (s *flakyUpdateStore) UpdateOptimistic(ctx context.Context, inst *store.SagaInstance) error {
	s.n++
	if s.n == s.failOn {
		return errors.New("update failed")
	}
	return s.inner.UpdateOptimistic(ctx, inst)
}

// errPublisher always fails Publish, exercising publishJSON's error branch.
type errPublisher struct{}

func (errPublisher) Publish(_ context.Context, _ string, _ []byte) error {
	return errors.New("broker unavailable")
}

func newOrchWith(st saga.SagaStore, bc saga.BankingCoreActions, td saga.TradingActions, mk saga.MarketActions, pub saga.EventPublisher) *saga.Orchestrator {
	log := slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))
	return saga.NewOrchestratorForTest(st, bc, td, mk, pub, log)
}

func redeemEvt(id string) events.FundRedeemRequested {
	return events.FundRedeemRequested{
		TransactionID:     id,
		Amount:            decimal.RequireFromString("100"),
		FundAccountNumber: "111000300000000003",
		ToAccountNumber:   "111000100000000001",
		FundID:            7,
	}
}

// ---------------------------------------------------------------------------
// findOrInitialize — error & conflict branches (via HandleFundRedeem)
// ---------------------------------------------------------------------------

func TestFindOrInitialize_FindError_PropagatesError(t *testing.T) {
	st := &injectStore{findErrs: []error{errors.New("db down")}}
	orch := newOrchWith(st, newFakeBC(), newFakeTD(), newFakeMK(), &fakePublisher{})

	err := orch.HandleFundRedeem(context.Background(), redeemEvt("c1"))
	if err == nil {
		t.Fatal("expected findOrInitialize error to propagate")
	}
}

func TestFindOrInitialize_InsertError_PropagatesError(t *testing.T) {
	st := &injectStore{
		findResults: []*store.SagaInstance{nil},
		insertErr:   errors.New("insert failed"),
	}
	orch := newOrchWith(st, newFakeBC(), newFakeTD(), newFakeMK(), &fakePublisher{})

	err := orch.HandleFundRedeem(context.Background(), redeemEvt("c2"))
	if err == nil {
		t.Fatal("expected insert error to propagate")
	}
}

func TestFindOrInitialize_OptimisticConflict_ReReadsExisting(t *testing.T) {
	// First find: not present -> insert -> conflict -> re-read returns a
	// terminal instance, so the handler should skip and return nil.
	existing := &store.SagaInstance{
		SagaType: "FUND_REDEEM", CorrelationID: "c3", State: store.SagaStateCompleted,
	}
	st := &injectStore{
		findResults: []*store.SagaInstance{nil, existing},
		insertErr:   store.ErrOptimisticLockConflict,
	}
	orch := newOrchWith(st, newFakeBC(), newFakeTD(), newFakeMK(), &fakePublisher{})

	if err := orch.HandleFundRedeem(context.Background(), redeemEvt("c3")); err != nil {
		t.Fatalf("expected nil (terminal skip after re-read), got %v", err)
	}
}

// ---------------------------------------------------------------------------
// publishJSON — publish error is logged, not propagated
// ---------------------------------------------------------------------------

func TestPublishJSON_PublishError_StillCompletes(t *testing.T) {
	fs := newFakeStore()
	orch := newOrchWith(fs, newFakeBC(), newFakeTD(), newFakeMK(), errPublisher{})

	// Happy-path redeem: transfer succeeds, finalize OK, publish fails but is swallowed.
	if err := orch.HandleFundRedeem(context.Background(), redeemEvt("pub-1")); err != nil {
		t.Fatalf("publish failure must not fail the saga: %v", err)
	}
	inst, _ := fs.FindByTypeAndCorrelation(context.Background(), "FUND_REDEEM", "pub-1")
	if inst == nil || inst.State != store.SagaStateCompleted {
		t.Errorf("expected COMPLETED despite publish error, got %v", inst)
	}
}

// ---------------------------------------------------------------------------
// Crash recovery — pre-existing IN_PROGRESS instance re-runs
// ---------------------------------------------------------------------------

func TestHandleFundRedeem_CrashRecovery_RerunsInProgress(t *testing.T) {
	fs := newFakeStore()
	_ = fs.Insert(context.Background(), &store.SagaInstance{
		SagaType: "FUND_REDEEM", CorrelationID: "recov-1", State: store.SagaStateInProgress,
	})
	orch := newOrchWith(fs, newFakeBC(), newFakeTD(), newFakeMK(), &fakePublisher{})

	if err := orch.HandleFundRedeem(context.Background(), redeemEvt("recov-1")); err != nil {
		t.Fatalf("crash recovery re-run failed: %v", err)
	}
	inst, _ := fs.FindByTypeAndCorrelation(context.Background(), "FUND_REDEEM", "recov-1")
	if inst == nil || inst.State != store.SagaStateCompleted {
		t.Errorf("expected COMPLETED after recovery, got %v", inst)
	}
}

func TestHandleOtcPremiumTransfer_CrashRecovery_RerunsInProgress(t *testing.T) {
	fs := newFakeStore()
	_ = fs.Insert(context.Background(), &store.SagaInstance{
		SagaType: "OTC_PREMIUM_TRANSFER", CorrelationID: "301", State: store.SagaStateInProgress,
	})
	orch := newOrchWith(fs, newFakeBC(), newFakeTD(), newFakeMK(), &fakePublisher{})

	evt := events.OtcPremiumTransferRequested{
		ContractID: 301, BuyerID: 1, SellerID: 2,
		Premium: decimal.RequireFromString("20"), Currency: "RSD",
	}
	if err := orch.HandleOtcPremiumTransfer(context.Background(), evt); err != nil {
		t.Fatalf("crash recovery re-run failed: %v", err)
	}
}

// ---------------------------------------------------------------------------
// OtcPremiumTransfer — account resolution & conversion failure branches
// ---------------------------------------------------------------------------

func TestHandleOtcPremiumTransfer_ResolveBuyerFails(t *testing.T) {
	fs := newFakeStore()
	orch := newOrchWith(fs, newFakeBC(), newFakeTD(), newFakeMK(), &fakePublisher{})

	evt := events.OtcPremiumTransferRequested{
		ContractID: 310, BuyerID: 999, SellerID: 2, // 999 not in fake account map
		Premium: decimal.RequireFromString("10"), Currency: "RSD",
	}
	if err := orch.HandleOtcPremiumTransfer(context.Background(), evt); err == nil {
		t.Fatal("expected buyer account resolution to fail")
	}
	inst, _ := fs.FindByTypeAndCorrelation(context.Background(), "OTC_PREMIUM_TRANSFER", "310")
	if inst == nil || inst.State != store.SagaStateFailed {
		t.Errorf("expected FAILED, got %v", inst)
	}
}

func TestHandleOtcPremiumTransfer_ResolveSellerFails(t *testing.T) {
	fs := newFakeStore()
	orch := newOrchWith(fs, newFakeBC(), newFakeTD(), newFakeMK(), &fakePublisher{})

	evt := events.OtcPremiumTransferRequested{
		ContractID: 311, BuyerID: 1, SellerID: 999, // seller missing
		Premium: decimal.RequireFromString("10"), Currency: "RSD",
	}
	if err := orch.HandleOtcPremiumTransfer(context.Background(), evt); err == nil {
		t.Fatal("expected seller account resolution to fail")
	}
}

func TestHandleOtcPremiumTransfer_ConversionFails(t *testing.T) {
	fs := newFakeStore()
	mk := newFakeMK()
	mk.err = errors.New("fx service down")
	orch := newOrchWith(fs, newFakeBC(), newFakeTD(), mk, &fakePublisher{})

	evt := events.OtcPremiumTransferRequested{
		ContractID: 312, BuyerID: 1, SellerID: 2,
		Premium: decimal.RequireFromString("10"), Currency: "USD", // non-RSD => conversion
	}
	if err := orch.HandleOtcPremiumTransfer(context.Background(), evt); err == nil {
		t.Fatal("expected currency conversion to fail")
	}
}

// ---------------------------------------------------------------------------
// FundSubscribe & FundRedeemWithLiquidation — crash recovery branches
// ---------------------------------------------------------------------------

func TestHandleFundSubscribe_CrashRecovery_RerunsInProgress(t *testing.T) {
	fs := newFakeStore()
	_ = fs.Insert(context.Background(), &store.SagaInstance{
		SagaType: "FUND_SUBSCRIBE", CorrelationID: "sub-recov-1", State: store.SagaStateInProgress,
	})
	orch := newOrchWith(fs, newFakeBC(), newFakeTD(), newFakeMK(), &fakePublisher{})

	evt := events.FundSubscribeRequested{
		TransactionID:     "sub-recov-1",
		Amount:            decimal.RequireFromString("100"),
		FromAccountNumber: "111000100000000001",
		FundAccountNumber: "111000300000000003",
		FundID:            7,
	}
	if err := orch.HandleFundSubscribe(context.Background(), evt); err != nil {
		t.Fatalf("crash recovery re-run failed: %v", err)
	}
	inst, _ := fs.FindByTypeAndCorrelation(context.Background(), "FUND_SUBSCRIBE", "sub-recov-1")
	if inst == nil || inst.State != store.SagaStateCompleted {
		t.Errorf("expected COMPLETED, got %v", inst)
	}
}

func TestHandleFundRedeemWithLiquidation_CrashRecovery_RerunsInProgress(t *testing.T) {
	fs := newFakeStore()
	_ = fs.Insert(context.Background(), &store.SagaInstance{
		SagaType: "FUND_LIQUIDATION_FOR_REDEMPTION", CorrelationID: "liq-recov-1", State: store.SagaStateInProgress,
	})
	orch := newOrchWith(fs, newFakeBC(), newFakeTD(), newFakeMK(), &fakePublisher{})

	evt := events.FundRedeemWithLiquidationRequested{
		TransactionID:     "liq-recov-1",
		Amount:            decimal.RequireFromString("5000"),
		FundID:            9,
		FundAccountNumber: "111000300000000003",
		ToAccountNumber:   "111000100000000001",
	}
	if err := orch.HandleFundRedeemWithLiquidation(context.Background(), evt); err != nil {
		t.Fatalf("crash recovery re-run failed: %v", err)
	}
}

// ---------------------------------------------------------------------------
// OtcExercise — crash recovery and retry branches
// ---------------------------------------------------------------------------

func otcExerciseEvt(id int64) events.OtcExerciseRequested {
	return events.OtcExerciseRequested{
		ContractID:      id,
		BuyerID:         1,
		SellerID:        2,
		StockTicker:     "AAPL",
		Amount:          10,
		PricePerStock:   decimal.RequireFromString("150.00"),
		Premium:         decimal.RequireFromString("5.00"),
		PremiumCurrency: "USD",
	}
}

func TestHandleOtcExercise_CrashRecovery_RerunsInProgress(t *testing.T) {
	fs := newFakeStore()
	_ = fs.Insert(context.Background(), &store.SagaInstance{
		SagaType: "OTC_EXERCISE", CorrelationID: "401", State: store.SagaStateInProgress,
	})
	orch := newOrchWith(fs, newFakeBC(), newFakeTD(), newFakeMK(), &fakePublisher{})

	if err := orch.HandleOtcExercise(context.Background(), otcExerciseEvt(401)); err != nil {
		t.Fatalf("crash recovery re-run failed: %v", err)
	}
	inst, _ := fs.FindByTypeAndCorrelation(context.Background(), "OTC_EXERCISE", "401")
	if inst == nil || inst.State != store.SagaStateCompleted {
		t.Errorf("expected COMPLETED after recovery, got %v", inst)
	}
}

func TestHandleOtcExercise_RetryIncrementsRetryCount(t *testing.T) {
	fs := newFakeStore()
	_ = fs.Insert(context.Background(), &store.SagaInstance{
		SagaType: "OTC_EXERCISE", CorrelationID: "402", State: store.SagaStateStarted,
	})
	orch := newOrchWith(fs, newFakeBC(), newFakeTD(), newFakeMK(), &fakePublisher{})

	if err := orch.HandleOtcExercise(context.Background(), otcExerciseEvt(402)); err != nil {
		t.Fatalf("retry run failed: %v", err)
	}
	inst, _ := fs.FindByTypeAndCorrelation(context.Background(), "OTC_EXERCISE", "402")
	if inst == nil || inst.RetryCount != 1 {
		t.Errorf("expected RetryCount=1, got %v", inst)
	}
}

func TestHandleOtcExercise_SaveLogFailsAfterF1_EmergencyRelease(t *testing.T) {
	st := &flakyUpdateStore{inner: newFakeStore(), failOn: 2} // #1 advanceState ok, #2 saveLog after F1 fails
	orch := newOrchWith(st, newFakeBC(), newFakeTD(), newFakeMK(), &fakePublisher{})

	if err := orch.HandleOtcExercise(context.Background(), otcExerciseEvt(410)); err == nil {
		t.Fatal("expected saveLog-after-F1 failure")
	}
}

func TestHandleOtcExercise_SaveLogFailsAfterF2_EmergencyRelease(t *testing.T) {
	st := &flakyUpdateStore{inner: newFakeStore(), failOn: 3} // #3 saveLog after F2 fails
	orch := newOrchWith(st, newFakeBC(), newFakeTD(), newFakeMK(), &fakePublisher{})

	if err := orch.HandleOtcExercise(context.Background(), otcExerciseEvt(411)); err == nil {
		t.Fatal("expected saveLog-after-F2 failure")
	}
}

// flakyReverseTD fails ReverseOwnership the first time, then succeeds — used to
// exercise compensateWithRetry's failure-then-retry path.
type flakyReverseTD struct {
	*fakeTD
	reverseFails int
	n            int
}

func (f *flakyReverseTD) ReverseOwnership(ctx context.Context, id, corr string) error {
	f.n++
	if f.n <= f.reverseFails {
		return errors.New("reverse temporarily unavailable")
	}
	return f.fakeTD.ReverseOwnership(ctx, id, corr)
}

func TestHandleOtcExercise_CompensationContextCancelled_SagaFailed(t *testing.T) {
	fs := newFakeStore()
	orch := newOrchWith(fs, newFakeBC(), newFakeTD(), newFakeMK(), &fakePublisher{})

	ctx, cancel := context.WithCancel(context.Background())
	cancel() // compensateWithRetry should bail out on ctx.Done immediately

	evt := otcExerciseEvt(420)
	evt.FaultInjection = &events.FaultInjection{ForceFailStep: "F5"} // forward steps 1-4 done, F5 fails
	_ = orch.HandleOtcExercise(ctx, evt)

	inst, _ := fs.FindByTypeAndCorrelation(context.Background(), "OTC_EXERCISE", "420")
	if inst == nil || inst.State != store.SagaStateFailed {
		t.Errorf("expected FAILED (compensation could not run under cancelled ctx), got %v", inst)
	}
}

func TestHandleOtcExercise_CompensatorRetriesThenSucceeds(t *testing.T) {
	fs := newFakeStore()
	td := &flakyReverseTD{fakeTD: newFakeTD(), reverseFails: 1}
	orch := newOrchWith(fs, newFakeBC(), td, newFakeMK(), &fakePublisher{})

	evt := otcExerciseEvt(421)
	evt.FaultInjection = &events.FaultInjection{ForceFailStep: "F5"}
	_ = orch.HandleOtcExercise(context.Background(), evt)

	inst, _ := fs.FindByTypeAndCorrelation(context.Background(), "OTC_EXERCISE", "421")
	// C4 ReverseOwnership failed once then succeeded; all compensators eventually OK.
	if inst == nil || inst.State != store.SagaStateCompensated {
		t.Errorf("expected COMPENSATED after retry, got %v", inst)
	}
}

func TestHandleOtcExercise_ConversionFails_SagaFails(t *testing.T) {
	fs := newFakeStore()
	mk := newFakeMK()
	mk.err = errors.New("fx down")
	orch := newOrchWith(fs, newFakeBC(), newFakeTD(), mk, &fakePublisher{})

	if err := orch.HandleOtcExercise(context.Background(), otcExerciseEvt(403)); err == nil {
		t.Fatal("expected currency conversion failure")
	}
}

func TestHandleOtcPremiumTransfer_EmptyCurrencyDefaultsToUSD(t *testing.T) {
	fs := newFakeStore()
	orch := newOrchWith(fs, newFakeBC(), newFakeTD(), newFakeMK(), &fakePublisher{})

	evt := events.OtcPremiumTransferRequested{
		ContractID: 313, BuyerID: 1, SellerID: 2,
		Premium: decimal.RequireFromString("10"), Currency: "", // defaults to USD -> conversion path
	}
	if err := orch.HandleOtcPremiumTransfer(context.Background(), evt); err != nil {
		t.Fatalf("empty currency should default to USD and succeed: %v", err)
	}
	inst, _ := fs.FindByTypeAndCorrelation(context.Background(), "OTC_PREMIUM_TRANSFER", "313")
	if inst == nil || inst.State != store.SagaStateCompleted {
		t.Errorf("expected COMPLETED, got %v", inst)
	}
}
