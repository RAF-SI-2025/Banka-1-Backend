package store

import (
	"context"
	"testing"
	"time"

	"Banka1Back/credit-service-go/internal/model"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func sampleLoan(id int64) model.Loan {
	now := time.Date(2024, 1, 1, 0, 0, 0, 0, time.UTC)
	return model.Loan{
		BaseEntity: model.BaseEntity{
			ID:        id,
			Version:   2,
			Deleted:   false,
			CreatedAt: now,
			UpdatedAt: now.Add(2 * time.Hour),
		},
		LoanType:              model.LoanGotovinski,
		AccountNumber:         "ACC-123",
		Amount:                decimal.NewFromInt(100000),
		RepaymentPeriod:       12,
		NominalInterestRate:   decimal.NewFromFloat(0.04),
		EffectiveInterestRate: decimal.NewFromFloat(0.05),
		InterestType:          model.InterestFixed,
		AgreementDate:         now,
		MaturityDate:          now.AddDate(1, 0, 0),
		InstallmentAmount:     decimal.NewFromInt(8000),
		NextInstallmentDate:   now.AddDate(0, 1, 0),
		RemainingDebt:         decimal.NewFromInt(100000),
		Currency:              model.CurrencyRSD,
		Status:                model.StatusActive,
		UserEmail:             "user@bank.rs",
		Username:              "user",
		ClientID:              99,
		InstallmentCount:      1,
	}
}

func loanRow(loan model.Loan) []any {
	return []any{
		loan.ID,
		loan.Version,
		loan.Deleted,
		loan.CreatedAt,
		loan.UpdatedAt,
		loan.LoanType,
		loan.AccountNumber,
		loan.Amount,
		loan.RepaymentPeriod,
		loan.NominalInterestRate,
		loan.EffectiveInterestRate,
		loan.InterestType,
		loan.AgreementDate,
		loan.MaturityDate,
		loan.InstallmentAmount,
		loan.NextInstallmentDate,
		loan.RemainingDebt,
		loan.Currency,
		loan.Status,
		loan.UserEmail,
		loan.Username,
		loan.ClientID,
		loan.InstallmentCount,
	}
}

func TestLoanStore_Create(t *testing.T) {
	t.Parallel()
	loan := sampleLoan(0)
	now := time.Date(2024, 2, 1, 0, 0, 0, 0, time.UTC)

	db := &stubDB{
		queryRowFn: func(_ context.Context, _ string, _ ...any) pgx.Row {
			return stubRow{values: []any{int64(10), int64(1), false, now, now}}
		},
	}
	store := NewLoanStore(db)

	created, err := store.Create(context.Background(), loan)
	require.NoError(t, err)
	assert.Equal(t, int64(10), created.ID)
	assert.Equal(t, int64(1), created.Version)
	assert.Equal(t, now, created.CreatedAt)
	assert.Equal(t, loan.Amount, created.Amount)
}

func TestLoanStore_FindByClientID(t *testing.T) {
	t.Parallel()
	loanA := sampleLoan(1)
	loanB := sampleLoan(2)

	db := &stubDB{
		queryFn: func(_ context.Context, _ string, _ ...any) (pgx.Rows, error) {
			return &stubRows{rows: [][]any{loanRow(loanA), loanRow(loanB)}}, nil
		},
		queryRowFn: func(_ context.Context, _ string, _ ...any) pgx.Row {
			return stubRow{values: []any{2}}
		},
	}
	store := NewLoanStore(db)

	loans, total, err := store.FindByClientID(context.Background(), loanA.ClientID, 0, 10)
	require.NoError(t, err)
	assert.Len(t, loans, 2)
	assert.Equal(t, 2, total)
}

func TestLoanStore_FindByID(t *testing.T) {
	t.Parallel()
	loan := sampleLoan(7)
	db := &stubDB{
		queryRowFn: func(_ context.Context, _ string, _ ...any) pgx.Row {
			return stubRow{values: loanRow(loan)}
		},
	}
	store := NewLoanStore(db)

	found, err := store.FindByID(context.Background(), loan.ID)
	require.NoError(t, err)
	assert.Equal(t, loan.ID, found.ID)
	assert.Equal(t, loan.AccountNumber, found.AccountNumber)
}

func TestLoanStore_FindAllWithFilters(t *testing.T) {
	t.Parallel()
	loan := sampleLoan(5)
	db := &stubDB{
		queryFn: func(_ context.Context, _ string, _ ...any) (pgx.Rows, error) {
			return &stubRows{rows: [][]any{loanRow(loan)}}, nil
		},
		queryRowFn: func(_ context.Context, _ string, _ ...any) pgx.Row {
			return stubRow{values: []any{1}}
		},
	}
	store := NewLoanStore(db)

	loans, total, err := store.FindAllWithFilters(context.Background(), nil, nil, nil, 0, 10)
	require.NoError(t, err)
	assert.Len(t, loans, 1)
	assert.Equal(t, 1, total)
}

func TestLoanStore_MarkOverdue(t *testing.T) {
	t.Parallel()
	db := &stubDB{
		execFn: func(_ context.Context, _ string, _ ...any) (pgconn.CommandTag, error) {
			return pgconn.NewCommandTag("UPDATE 1"), nil
		},
	}
	store := NewLoanStore(db)

	err := store.MarkOverdue(context.Background(), 10)
	require.NoError(t, err)
}

func TestLoanStore_UpdateAfterInstallmentPayment(t *testing.T) {
	t.Parallel()
	db := &stubDB{
		execFn: func(_ context.Context, _ string, _ ...any) (pgconn.CommandTag, error) {
			return pgconn.NewCommandTag("UPDATE 1"), nil
		},
	}
	store := NewLoanStore(db)

	err := store.UpdateAfterInstallmentPayment(
		context.Background(),
		12,
		decimal.NewFromInt(5000),
		2,
		time.Date(2024, 5, 1, 0, 0, 0, 0, time.UTC),
		model.StatusActive,
	)
	require.NoError(t, err)
}
