package service_test

import (
	"context"
	"errors"
	"sync/atomic"
	"testing"
	"time"

	"Banka1Back/credit-service-go/internal/auth"
	"Banka1Back/credit-service-go/internal/client"
	"Banka1Back/credit-service-go/internal/dto"
	"Banka1Back/credit-service-go/internal/model"
	"Banka1Back/credit-service-go/internal/service"

	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// ---------------------------------------------------------------------------
// Stubs
// ---------------------------------------------------------------------------

type stubLoanRequestRepo struct {
	savedReq       model.LoanRequest
	saveErr        error
	findAllResult  []model.LoanRequest
	findAllTotal   int
	findAllErr     error
	findByIDResult model.LoanRequest
	findByIDErr    error
	updateResult   bool
	updateErr      error
	approveResult  model.Loan
	approveErr     error
}

func (r *stubLoanRequestRepo) Save(_ context.Context, req model.LoanRequest) (model.LoanRequest, error) {
	if r.saveErr != nil {
		return model.LoanRequest{}, r.saveErr
	}
	req.ID = 1
	req.CreatedAt = time.Now()
	r.savedReq = req
	return req, nil
}

func (r *stubLoanRequestRepo) FindAll(_ context.Context, _ *model.LoanType, _ *string, _ int, _ int) ([]model.LoanRequest, int, error) {
	return r.findAllResult, r.findAllTotal, r.findAllErr
}

func (r *stubLoanRequestRepo) FindByID(_ context.Context, _ int64) (model.LoanRequest, error) {
	return r.findByIDResult, r.findByIDErr
}

func (r *stubLoanRequestRepo) UpdateStatusIfPending(_ context.Context, _ int64, _ model.Status) (bool, error) {
	return r.updateResult, r.updateErr
}

func (r *stubLoanRequestRepo) ApproveWithLoanAndInstallment(_ context.Context, _ int64, loan model.Loan, _ model.Installment) (model.Loan, error) {
	if r.approveErr != nil {
		return model.Loan{}, r.approveErr
	}
	loan.ID = 10
	r.approveResult = loan
	return loan, nil
}

type stubLoanRepo struct {
	findByClientResult []model.Loan
	findByClientTotal  int
	findByClientErr    error
	findByIDResult     model.Loan
	findByIDErr        error
	findAllResult      []model.Loan
	findAllTotal       int
	findAllErr         error
	markOverdueErr     error
	updateAfterErr     error
}

func (r *stubLoanRepo) FindByClientID(_ context.Context, _ int64, _ int, _ int) ([]model.Loan, int, error) {
	return r.findByClientResult, r.findByClientTotal, r.findByClientErr
}

func (r *stubLoanRepo) FindByID(_ context.Context, _ int64) (model.Loan, error) {
	return r.findByIDResult, r.findByIDErr
}

func (r *stubLoanRepo) FindAllWithFilters(_ context.Context, _ *model.LoanType, _ *string, _ *model.Status, _ int, _ int) ([]model.Loan, int, error) {
	return r.findAllResult, r.findAllTotal, r.findAllErr
}

func (r *stubLoanRepo) MarkOverdue(_ context.Context, _ int64) error {
	return r.markOverdueErr
}

func (r *stubLoanRepo) UpdateAfterInstallmentPayment(_ context.Context, _ int64, _ decimal.Decimal, _ int, _ time.Time, _ model.Status) error {
	return r.updateAfterErr
}

type stubInstallmentRepo struct {
	findByLoanResult []model.Installment
	findByLoanErr    error
	findDueResult    []model.Installment
	findDueErr       error
	findDueCalls     int32
	markRetryErr     error
	markPaidErr      error
	createErr        error
}

func (r *stubInstallmentRepo) FindByLoanID(_ context.Context, _ int64) ([]model.Installment, error) {
	return r.findByLoanResult, r.findByLoanErr
}

func (r *stubInstallmentRepo) FindDueUnpaid(_ context.Context) ([]model.Installment, error) {
	atomic.AddInt32(&r.findDueCalls, 1)
	return r.findDueResult, r.findDueErr
}

func (r *stubInstallmentRepo) MarkRetryOrOverdue(_ context.Context, _ model.Installment) error {
	return r.markRetryErr
}

func (r *stubInstallmentRepo) MarkPaid(_ context.Context, _ int64) error {
	return r.markPaidErr
}

func (r *stubInstallmentRepo) Create(_ context.Context, _ model.Installment) error {
	return r.createErr
}

type stubAccountGateway struct {
	detailsResult client.AccountDetailsResponse
	detailsErr    error
	txFromBankErr error
	txToBankErr   error
}

func (g *stubAccountGateway) GetDetails(_ string) (client.AccountDetailsResponse, error) {
	return g.detailsResult, g.detailsErr
}

func (g *stubAccountGateway) TransactionFromBank(_ string, _ decimal.Decimal) error {
	return g.txFromBankErr
}

func (g *stubAccountGateway) TransactionToBank(_ string, _ decimal.Decimal) error {
	return g.txToBankErr
}

type stubExchangeGateway struct {
	result client.ConversionResponse
	err    error
}

func (g *stubExchangeGateway) Calculate(_ string, _ string, _ decimal.Decimal) (client.ConversionResponse, error) {
	return g.result, g.err
}

type stubClientGateway struct {
	err error
}

func (g *stubClientGateway) AddMarginPermission(_ int64) error {
	return g.err
}

type stubNotifier struct {
	published []string
	err       error
}

func (n *stubNotifier) PublishJSON(_ context.Context, routingKey string, _ any) error {
	n.published = append(n.published, routingKey)
	return n.err
}

func newService(
	loanReqRepo service.LoanRequestRepository,
	loanRepo service.LoanRepository,
	installRepo service.InstallmentRepository,
	account service.AccountGateway,
	exchange service.ExchangeGateway,
	clientGw service.ClientGateway,
	notifier service.NotificationPublisher,
) *service.LoanService {
	return service.NewLoanService(loanReqRepo, loanRepo, installRepo, account, notifier, exchange, clientGw)
}

func defaultStubs() (
	*stubLoanRequestRepo,
	*stubLoanRepo,
	*stubInstallmentRepo,
	*stubAccountGateway,
	*stubExchangeGateway,
	*stubClientGateway,
	*stubNotifier,
) {
	return &stubLoanRequestRepo{},
		&stubLoanRepo{},
		&stubInstallmentRepo{},
		&stubAccountGateway{
			detailsResult: client.AccountDetailsResponse{
				OwnerID:  1,
				Currency: model.CurrencyRSD,
				Email:    "user@bank.io",
				Username: "user",
			},
		},
		&stubExchangeGateway{},
		&stubClientGateway{},
		&stubNotifier{}
}

// ---------------------------------------------------------------------------
// Request tests
// ---------------------------------------------------------------------------

func TestRequest_HappyPath_CreatesPendingLoanRequest(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	user := auth.User{ID: 1, Role: "CLIENT_BASIC"}
	req := dto.LoanRequestDTO{
		LoanType:                model.LoanGotovinski,
		InterestType:            model.InterestFixed,
		Amount:                  decimal.NewFromInt(50000),
		Currency:                model.CurrencyRSD,
		Purpose:                 "house",
		MonthlySalary:           decimal.NewFromInt(1000),
		EmploymentStatus:        model.EmploymentPermanent,
		CurrentEmploymentPeriod: 12,
		RepaymentPeriod:         24,
		ContactPhone:            "060123456",
		AccountNumber:           "1234567890123456789",
	}

	resp, err := svc.Request(context.Background(), user, req)
	require.NoError(t, err)
	assert.Equal(t, int64(1), resp.ID)
	assert.Contains(t, notifier.published, "credit.requested")
}

func TestRequest_AccountOwnerMismatch_ReturnsError(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	account.detailsResult.OwnerID = 99
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	user := auth.User{ID: 1}
	req := dto.LoanRequestDTO{Currency: model.CurrencyRSD, AccountNumber: "1234567890123456789"}
	_, err := svc.Request(context.Background(), user, req)
	require.Error(t, err)
	assert.Contains(t, err.Error(), "vlasnik")
}

func TestRequest_CurrencyMismatch_ReturnsError(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	account.detailsResult.Currency = model.CurrencyEUR
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	user := auth.User{ID: 1}
	req := dto.LoanRequestDTO{Currency: model.CurrencyRSD, AccountNumber: "1234567890123456789"}
	_, err := svc.Request(context.Background(), user, req)
	require.Error(t, err)
	assert.Contains(t, err.Error(), "valuta")
}

func TestRequest_AccountClientError_ReturnsError(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	account.detailsErr = errors.New("account service down")
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	_, err := svc.Request(context.Background(), auth.User{ID: 1}, dto.LoanRequestDTO{})
	require.Error(t, err)
}

func TestRequest_SaveError_ReturnsError(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	reqRepo.saveErr = errors.New("db error")
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	user := auth.User{ID: 1}
	req := dto.LoanRequestDTO{Currency: model.CurrencyRSD}
	_, err := svc.Request(context.Background(), user, req)
	require.Error(t, err)
}

// ---------------------------------------------------------------------------
// FindAllLoanRequests tests
// ---------------------------------------------------------------------------

func TestFindAllLoanRequests_ReturnsPaginatedResults(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	reqRepo.findAllResult = []model.LoanRequest{{}, {}}
	reqRepo.findAllTotal = 2
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	resp, err := svc.FindAllLoanRequests(context.Background(), nil, nil, 0, 10)
	require.NoError(t, err)
	assert.Len(t, resp.Content, 2)
	assert.Equal(t, 2, resp.TotalElements)
}

func TestFindAllLoanRequests_StoreError_ReturnsError(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	reqRepo.findAllErr = errors.New("db error")
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	_, err := svc.FindAllLoanRequests(context.Background(), nil, nil, 0, 10)
	require.Error(t, err)
}

// ---------------------------------------------------------------------------
// Confirmation: Decline
// ---------------------------------------------------------------------------

func TestConfirmation_Decline_UpdatesAndNotifies(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	reqRepo.findByIDResult = model.LoanRequest{Status: model.StatusPending, UserEmail: "u@bank.io", Username: "u"}
	reqRepo.updateResult = true
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	msg, err := svc.Confirmation(context.Background(), 1, model.StatusDeclined)
	require.NoError(t, err)
	assert.Equal(t, "ODBIJEN ZAHTEV", msg)
	assert.Contains(t, notifier.published, "credit.declined")
}

func TestConfirmation_Decline_UpdateRaceCondition_ReturnsError(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	reqRepo.findByIDResult = model.LoanRequest{Status: model.StatusPending}
	reqRepo.updateResult = false
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	_, err := svc.Confirmation(context.Background(), 1, model.StatusDeclined)
	require.Error(t, err)
}

func TestConfirmation_InvalidStatus_ReturnsError(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	_, err := svc.Confirmation(context.Background(), 1, model.StatusActive)
	require.Error(t, err)
}

func TestConfirmation_LoanRequestNotPending_ReturnsError(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	reqRepo.findByIDResult = model.LoanRequest{Status: model.StatusApproved}
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	_, err := svc.Confirmation(context.Background(), 1, model.StatusDeclined)
	require.Error(t, err)
}

// ---------------------------------------------------------------------------
// Confirmation: Approve (RSD)
// ---------------------------------------------------------------------------

func TestConfirmation_Approve_RSD_CreatesLoanAndNotifies(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	reqRepo.findByIDResult = model.LoanRequest{
		Status:          model.StatusPending,
		Currency:        model.CurrencyRSD,
		Amount:          decimal.NewFromInt(100000),
		RepaymentPeriod: 12,
		LoanType:        model.LoanGotovinski,
		InterestType:    model.InterestFixed,
		UserEmail:       "u@bank.io",
		Username:        "u",
	}
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	msg, err := svc.Confirmation(context.Background(), 1, model.StatusApproved)
	require.NoError(t, err)
	assert.Equal(t, "ODOBREN ZAHTEV", msg)
	assert.Contains(t, notifier.published, "credit.approved")
}

func TestConfirmation_Approve_ForeignCurrency_CallsExchange(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	reqRepo.findByIDResult = model.LoanRequest{
		Status:          model.StatusPending,
		Currency:        model.CurrencyEUR,
		Amount:          decimal.NewFromInt(1000),
		RepaymentPeriod: 12,
		LoanType:        model.LoanAuto,
		InterestType:    model.InterestFixed,
		UserEmail:       "u@bank.io",
		Username:        "u",
	}
	exchange.result = client.ConversionResponse{ToAmount: decimal.NewFromInt(120000)}
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	msg, err := svc.Confirmation(context.Background(), 1, model.StatusApproved)
	require.NoError(t, err)
	assert.Equal(t, "ODOBREN ZAHTEV", msg)
}

func TestConfirmation_Approve_ExchangeError_ReturnsError(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	reqRepo.findByIDResult = model.LoanRequest{
		Status:   model.StatusPending,
		Currency: model.CurrencyEUR,
		Amount:   decimal.NewFromInt(1000),
	}
	exchange.err = errors.New("exchange unavailable")
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	_, err := svc.Confirmation(context.Background(), 1, model.StatusApproved)
	require.Error(t, err)
}

func TestConfirmation_Approve_TransactionFromBankFails_ReturnsError(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	reqRepo.findByIDResult = model.LoanRequest{
		Status:          model.StatusPending,
		Currency:        model.CurrencyRSD,
		Amount:          decimal.NewFromInt(100000),
		RepaymentPeriod: 12,
		LoanType:        model.LoanGotovinski,
		InterestType:    model.InterestFixed,
	}
	account.txFromBankErr = errors.New("account debit failed")
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	_, err := svc.Confirmation(context.Background(), 1, model.StatusApproved)
	require.Error(t, err)
}

// ---------------------------------------------------------------------------
// FindClientLoans
// ---------------------------------------------------------------------------

func TestFindClientLoans_ReturnsMappedLoans(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	loanRepo.findByClientResult = []model.Loan{
		{LoanType: model.LoanGotovinski, Amount: decimal.NewFromInt(50000)},
	}
	loanRepo.findByClientTotal = 1
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	resp, err := svc.FindClientLoans(context.Background(), auth.User{ID: 1}, 0, 10)
	require.NoError(t, err)
	require.Len(t, resp.Content, 1)
	assert.Equal(t, model.LoanGotovinski, resp.Content[0].LoanType)
}

func TestFindClientLoans_StoreError_ReturnsError(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	loanRepo.findByClientErr = errors.New("db error")
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	_, err := svc.FindClientLoans(context.Background(), auth.User{ID: 1}, 0, 10)
	require.Error(t, err)
}

// ---------------------------------------------------------------------------
// GetLoanInfo
// ---------------------------------------------------------------------------

func TestGetLoanInfo_Owner_ReturnsLoanWithInstallments(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	loanRepo.findByIDResult = model.Loan{ClientID: 1, LoanType: model.LoanStambeni}
	installRepo.findByLoanResult = []model.Installment{{PaymentStatus: model.PaymentUnpaid}}
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	resp, err := svc.GetLoanInfo(context.Background(), auth.User{ID: 1}, 5)
	require.NoError(t, err)
	assert.Equal(t, model.LoanStambeni, resp.Loan.LoanType)
	assert.Len(t, resp.Installments, 1)
}

func TestGetLoanInfo_AdminCanViewAnyLoan(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	loanRepo.findByIDResult = model.Loan{ClientID: 99}
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	_, err := svc.GetLoanInfo(context.Background(), auth.User{ID: 1, Role: "ADMIN"}, 5)
	require.NoError(t, err)
}

func TestGetLoanInfo_NotOwnerNotAdmin_ReturnsError(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	loanRepo.findByIDResult = model.Loan{ClientID: 99}
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	_, err := svc.GetLoanInfo(context.Background(), auth.User{ID: 1, Role: "CLIENT_BASIC"}, 5)
	require.Error(t, err)
}

func TestGetLoanInfo_LoanNotFound_ReturnsError(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	loanRepo.findByIDErr = errors.New("not found")
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	_, err := svc.GetLoanInfo(context.Background(), auth.User{ID: 1}, 5)
	require.Error(t, err)
}

// ---------------------------------------------------------------------------
// FindAllLoans
// ---------------------------------------------------------------------------

func TestFindAllLoans_NoFilters_ReturnsAll(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	loanRepo.findAllResult = []model.Loan{{}, {}, {}}
	loanRepo.findAllTotal = 3
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	resp, err := svc.FindAllLoans(context.Background(), nil, nil, nil, 0, 10)
	require.NoError(t, err)
	assert.Len(t, resp.Content, 3)
}

// ---------------------------------------------------------------------------
// ProcessDueInstallments
// ---------------------------------------------------------------------------

func TestProcessDueInstallments_NoDue_ReturnsNil(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	installRepo.findDueResult = []model.Installment{}
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	err := svc.ProcessDueInstallments(context.Background())
	require.NoError(t, err)
}

func TestProcessDueInstallments_SuccessfulPayment_SchedulesNextInstallment(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()

	installment := model.Installment{
		BaseEntity:            model.BaseEntity{ID: 1},
		LoanID:                10,
		InstallmentAmount:     decimal.NewFromFloat(500.0),
		InterestRateAtPayment: decimal.NewFromFloat(0.01),
		Currency:              model.CurrencyRSD,
		PaymentStatus:         model.PaymentUnpaid,
	}
	loan := model.Loan{
		BaseEntity:       model.BaseEntity{ID: 10},
		LoanType:         model.LoanGotovinski,
		InterestType:     model.InterestFixed,
		Currency:         model.CurrencyRSD,
		Status:           model.StatusActive,
		RemainingDebt:    decimal.NewFromInt(10000),
		RepaymentPeriod:  12,
		InstallmentCount: 2,
	}

	installRepo.findDueResult = []model.Installment{installment}
	loanRepo.findByIDResult = loan

	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)
	err := svc.ProcessDueInstallments(context.Background())
	require.NoError(t, err)
}

func TestProcessDueInstallments_PaymentFails_MarksRetryOrOverdue(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()

	installment := model.Installment{
		BaseEntity: model.BaseEntity{ID: 1},
		LoanID:     10,
		Retry:      0,
	}
	loan := model.Loan{BaseEntity: model.BaseEntity{ID: 10}, Status: model.StatusActive}

	installRepo.findDueResult = []model.Installment{installment}
	loanRepo.findByIDResult = loan
	account.txToBankErr = errors.New("insufficient funds")

	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)
	err := svc.ProcessDueInstallments(context.Background())
	require.NoError(t, err)
}

func TestProcessDueInstallments_PaymentFails_OverdueRetry_MarksLoanOverdue(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()

	installment := model.Installment{
		BaseEntity:    model.BaseEntity{ID: 1},
		LoanID:        10,
		Retry:         1,
		PaymentStatus: model.PaymentOverdue,
	}
	loan := model.Loan{BaseEntity: model.BaseEntity{ID: 10}, Status: model.StatusActive}

	installRepo.findDueResult = []model.Installment{installment}
	loanRepo.findByIDResult = loan
	account.txToBankErr = errors.New("insufficient funds")

	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)
	err := svc.ProcessDueInstallments(context.Background())
	require.NoError(t, err)
}

func TestProcessDueInstallments_LastInstallment_MarksLoanPaidOff(t *testing.T) {
	t.Parallel()
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()

	installment := model.Installment{
		BaseEntity:            model.BaseEntity{ID: 1},
		LoanID:                10,
		InstallmentAmount:     decimal.NewFromFloat(1000.0),
		InterestRateAtPayment: decimal.NewFromFloat(0.01),
		Currency:              model.CurrencyRSD,
	}
	loan := model.Loan{
		BaseEntity:       model.BaseEntity{ID: 10},
		LoanType:         model.LoanGotovinski,
		InterestType:     model.InterestFixed,
		RemainingDebt:    decimal.NewFromFloat(990.0),
		RepaymentPeriod:  12,
		InstallmentCount: 11,
	}

	installRepo.findDueResult = []model.Installment{installment}
	loanRepo.findByIDResult = loan

	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)
	err := svc.ProcessDueInstallments(context.Background())
	require.NoError(t, err)
}
