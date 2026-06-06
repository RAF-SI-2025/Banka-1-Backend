package store

import (
	"context"
	"errors"
	"testing"
	"time"

	"Banka1Back/credit-service-go/internal/model"
	"github.com/pashagolub/pgxmock/v4"
	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func newMock(t *testing.T) pgxmock.PgxPoolIface {
	t.Helper()
	m, err := pgxmock.NewPool()
	require.NoError(t, err)
	t.Cleanup(m.Close)
	return m
}

func anyN(n int) []any {
	a := make([]any, n)
	for i := range a {
		a[i] = pgxmock.AnyArg()
	}
	return a
}

func dec(s string) decimal.Decimal { return decimal.RequireFromString(s) }

var baseCols = []string{"id", "version", "deleted", "created_at", "updated_at"}

func baseRow() []any { return []any{int64(1), int64(0), false, time.Now(), time.Now()} }

var loanCols = []string{
	"id", "version", "deleted", "created_at", "updated_at",
	"loan_type", "account_number", "amount", "repayment_period",
	"nominal_interest_rate", "effective_interest_rate", "interest_type",
	"agreement_date", "maturity_date", "installment_amount",
	"next_installment_date", "remaining_debt", "currency", "status",
	"user_email", "username", "client_id", "installment_count",
}

func loanRow() []any {
	return []any{
		int64(1), int64(0), false, time.Now(), time.Now(),
		model.LoanType("CASH"), "111", dec("1000"), 12,
		dec("5"), dec("5.5"), model.InterestType("FIXED"),
		time.Now(), time.Now(), dec("100"),
		time.Now(), dec("900"), model.CurrencyCode("RSD"), model.Status("APPROVED"),
		"e@x.com", "u", int64(7), 10,
	}
}

var loanReqCols = []string{
	"id", "version", "deleted", "created_at", "updated_at",
	"loan_type", "interest_type", "amount", "currency", "purpose",
	"monthly_salary", "employment_status", "current_employment_period",
	"repayment_period", "contact_phone", "account_number", "client_id",
	"status", "user_email", "username",
}

func loanReqRow() []any {
	return []any{
		int64(1), int64(0), false, time.Now(), time.Now(),
		model.LoanType("CASH"), model.InterestType("FIXED"), dec("1000"), model.CurrencyCode("RSD"), "purpose",
		dec("2000"), model.EmploymentStatus("EMPLOYED"), 24,
		12, "060", "111", int64(7),
		model.Status("PENDING"), "e@x.com", "u",
	}
}

var installmentCols = []string{
	"id", "version", "deleted", "created_at", "updated_at",
	"loan_id", "installment_amount", "interest_rate_at_payment",
	"currency", "expected_due_date", "actual_due_date",
	"payment_status", "retry",
}

func installmentRow() []any {
	return []any{
		int64(1), int64(0), false, time.Now(), time.Now(),
		int64(1), dec("100"), dec("5"),
		model.CurrencyCode("RSD"), time.Now(), (*time.Time)(nil),
		model.PaymentStatus("PENDING"), 0,
	}
}

func mockLoan() model.Loan {
	return model.Loan{
		Amount: dec("1000"), NominalInterestRate: dec("5"), EffectiveInterestRate: dec("5.5"),
		InstallmentAmount: dec("100"), RemainingDebt: dec("900"),
		AgreementDate: time.Now(), MaturityDate: time.Now(), NextInstallmentDate: time.Now(),
	}
}

func mockInstallment() model.Installment {
	return model.Installment{
		InstallmentAmount: dec("100"), InterestRateAtPayment: dec("5"), ExpectedDueDate: time.Now(),
	}
}

// ---------------------------------------------------------------------------
// LoanStore
// ---------------------------------------------------------------------------

func TestLoanStore_Create(t *testing.T) {
	m := newMock(t)
	s := &LoanStore{db: m}
	m.ExpectQuery("INSERT INTO loan_table").WithArgs(anyN(18)...).
		WillReturnRows(pgxmock.NewRows(baseCols).AddRow(baseRow()...))

	loan, err := s.Create(context.Background(), mockLoan())
	require.NoError(t, err)
	assert.Equal(t, int64(1), loan.ID)
}

func TestLoanStore_FindByID(t *testing.T) {
	m := newMock(t)
	s := &LoanStore{db: m}
	m.ExpectQuery("FROM loan_table").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows(loanCols).AddRow(loanRow()...))

	loan, err := s.FindByID(context.Background(), 1)
	require.NoError(t, err)
	assert.Equal(t, int64(1), loan.ID)
}

func TestLoanStore_FindByClientID(t *testing.T) {
	m := newMock(t)
	s := &LoanStore{db: m}
	m.ExpectQuery("FROM loan_table").WithArgs(anyN(3)...).
		WillReturnRows(pgxmock.NewRows(loanCols).AddRow(loanRow()...))
	m.ExpectQuery("COUNT").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows([]string{"count"}).AddRow(1))

	loans, total, err := s.FindByClientID(context.Background(), 7, 0, 10)
	require.NoError(t, err)
	assert.Len(t, loans, 1)
	assert.Equal(t, 1, total)
}

func TestLoanStore_FindAllWithFilters(t *testing.T) {
	m := newMock(t)
	s := &LoanStore{db: m}
	m.ExpectQuery("FROM loan_table").WithArgs(anyN(5)...).
		WillReturnRows(pgxmock.NewRows(loanCols).AddRow(loanRow()...))
	m.ExpectQuery("COUNT").WithArgs(anyN(3)...).
		WillReturnRows(pgxmock.NewRows([]string{"count"}).AddRow(1))

	loans, total, err := s.FindAllWithFilters(context.Background(), nil, nil, nil, 0, 10)
	require.NoError(t, err)
	assert.Len(t, loans, 1)
	assert.Equal(t, 1, total)
}

func TestLoanStore_MarkOverdue(t *testing.T) {
	m := newMock(t)
	s := &LoanStore{db: m}
	m.ExpectExec("UPDATE loan_table").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	require.NoError(t, s.MarkOverdue(context.Background(), 1))
}

func TestLoanStore_UpdateAfterInstallmentPayment(t *testing.T) {
	m := newMock(t)
	s := &LoanStore{db: m}
	m.ExpectExec("UPDATE loan_table").WithArgs(anyN(5)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	require.NoError(t, s.UpdateAfterInstallmentPayment(context.Background(), 1, dec("500"), 5, time.Now(), model.Status("ACTIVE")))
}

func TestLoanStore_Create_Error(t *testing.T) {
	m := newMock(t)
	s := &LoanStore{db: m}
	m.ExpectQuery("INSERT INTO loan_table").WithArgs(anyN(18)...).WillReturnError(errors.New("boom"))
	_, err := s.Create(context.Background(), mockLoan())
	assert.Error(t, err)
}

// ---------------------------------------------------------------------------
// LoanRequestStore
// ---------------------------------------------------------------------------

func TestLoanRequestStore_Save(t *testing.T) {
	m := newMock(t)
	s := &LoanRequestStore{db: m}
	m.ExpectQuery("INSERT INTO loan_request_table").WithArgs(anyN(15)...).
		WillReturnRows(pgxmock.NewRows(baseCols).AddRow(baseRow()...))

	req, err := s.Save(context.Background(), model.LoanRequest{Amount: dec("1000"), MonthlySalary: dec("2000")})
	require.NoError(t, err)
	assert.Equal(t, int64(1), req.ID)
}

func TestLoanRequestStore_FindByID(t *testing.T) {
	m := newMock(t)
	s := &LoanRequestStore{db: m}
	m.ExpectQuery("FROM loan_request_table").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows(loanReqCols).AddRow(loanReqRow()...))

	req, err := s.FindByID(context.Background(), 1)
	require.NoError(t, err)
	assert.Equal(t, int64(1), req.ID)
}

func TestLoanRequestStore_FindAll(t *testing.T) {
	m := newMock(t)
	s := &LoanRequestStore{db: m}
	m.ExpectQuery("FROM loan_request_table").WithArgs(anyN(4)...).
		WillReturnRows(pgxmock.NewRows(loanReqCols).AddRow(loanReqRow()...))
	m.ExpectQuery("COUNT").WithArgs(anyN(2)...).
		WillReturnRows(pgxmock.NewRows([]string{"count"}).AddRow(1))

	reqs, total, err := s.FindAll(context.Background(), nil, nil, 0, 10)
	require.NoError(t, err)
	assert.Len(t, reqs, 1)
	assert.Equal(t, 1, total)
}

func TestLoanRequestStore_UpdateStatusIfPending(t *testing.T) {
	m := newMock(t)
	s := &LoanRequestStore{db: m}
	m.ExpectExec("UPDATE loan_request_table").WithArgs(anyN(2)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	ok, err := s.UpdateStatusIfPending(context.Background(), 1, model.Status("APPROVED"))
	require.NoError(t, err)
	assert.True(t, ok)
}

func TestLoanRequestStore_CreateLoanWithFirstInstallment(t *testing.T) {
	m := newMock(t)
	s := &LoanRequestStore{db: m}
	m.ExpectBegin()
	m.ExpectQuery("INSERT INTO loan_table").WithArgs(anyN(18)...).
		WillReturnRows(pgxmock.NewRows(baseCols).AddRow(baseRow()...))
	m.ExpectExec("INSERT INTO installment_table").WithArgs(anyN(8)...).WillReturnResult(pgxmock.NewResult("INSERT", 1))
	m.ExpectCommit()

	loan, err := s.CreateLoanWithFirstInstallment(context.Background(), mockLoan(), mockInstallment())
	require.NoError(t, err)
	assert.Equal(t, int64(1), loan.ID)
}

func TestLoanRequestStore_ApproveWithLoanAndInstallment(t *testing.T) {
	m := newMock(t)
	s := &LoanRequestStore{db: m}
	m.ExpectBegin()
	m.ExpectExec("UPDATE loan_request_table").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	m.ExpectQuery("INSERT INTO loan_table").WithArgs(anyN(18)...).
		WillReturnRows(pgxmock.NewRows(baseCols).AddRow(baseRow()...))
	m.ExpectExec("INSERT INTO installment_table").WithArgs(anyN(8)...).WillReturnResult(pgxmock.NewResult("INSERT", 1))
	m.ExpectCommit()

	loan, err := s.ApproveWithLoanAndInstallment(context.Background(), 1, mockLoan(), mockInstallment())
	require.NoError(t, err)
	assert.Equal(t, int64(1), loan.ID)
}

func TestLoanRequestStore_ApproveWithLoanAndInstallment_NotPending(t *testing.T) {
	m := newMock(t)
	s := &LoanRequestStore{db: m}
	m.ExpectBegin()
	m.ExpectExec("UPDATE loan_request_table").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("UPDATE", 0))
	m.ExpectRollback()

	_, err := s.ApproveWithLoanAndInstallment(context.Background(), 1, mockLoan(), mockInstallment())
	assert.Error(t, err)
}

// ---------------------------------------------------------------------------
// InstallmentStore
// ---------------------------------------------------------------------------

func TestInstallmentStore_Create(t *testing.T) {
	m := newMock(t)
	s := &InstallmentStore{db: m}
	m.ExpectExec("INSERT INTO installment_table").WithArgs(anyN(8)...).WillReturnResult(pgxmock.NewResult("INSERT", 1))
	require.NoError(t, s.Create(context.Background(), mockInstallment()))
}

func TestInstallmentStore_FindByLoanID(t *testing.T) {
	m := newMock(t)
	s := &InstallmentStore{db: m}
	m.ExpectQuery("FROM installment_table").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows(installmentCols).AddRow(installmentRow()...))

	items, err := s.FindByLoanID(context.Background(), 1)
	require.NoError(t, err)
	assert.Len(t, items, 1)
}

func TestInstallmentStore_FindDueUnpaid(t *testing.T) {
	m := newMock(t)
	s := &InstallmentStore{db: m}
	m.ExpectQuery("FROM installment_table").
		WillReturnRows(pgxmock.NewRows(installmentCols).AddRow(installmentRow()...))

	items, err := s.FindDueUnpaid(context.Background())
	require.NoError(t, err)
	assert.Len(t, items, 1)
}

func TestInstallmentStore_MarkRetryOrOverdue_FirstRetryMock(t *testing.T) {
	m := newMock(t)
	s := &InstallmentStore{db: m}
	m.ExpectExec("retry = 1").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	require.NoError(t, s.MarkRetryOrOverdue(context.Background(), model.Installment{PaymentStatus: model.PaymentStatus("DUE"), Retry: 0}))
}

func TestInstallmentStore_MarkRetryOrOverdueMock(t *testing.T) {
	m := newMock(t)
	s := &InstallmentStore{db: m}
	m.ExpectExec("OVERDUE").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	require.NoError(t, s.MarkRetryOrOverdue(context.Background(), model.Installment{Retry: 1}))
}

func TestInstallmentStore_MarkPaid(t *testing.T) {
	m := newMock(t)
	s := &InstallmentStore{db: m}
	m.ExpectExec("PAID").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	require.NoError(t, s.MarkPaid(context.Background(), 1))
}

// ---------------------------------------------------------------------------
// Constructors
// ---------------------------------------------------------------------------

func TestConstructors(t *testing.T) {
	t.Parallel()
	assert.NotNil(t, NewLoanStore(nil))
	assert.NotNil(t, NewLoanRequestStore(nil))
	assert.NotNil(t, NewInstallmentStore(nil))
}
