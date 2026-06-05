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

func sampleLoanRequest(id int64) model.LoanRequest {
	now := time.Date(2024, 4, 1, 0, 0, 0, 0, time.UTC)
	return model.LoanRequest{
		BaseEntity: model.BaseEntity{
			ID:        id,
			Version:   1,
			Deleted:   false,
			CreatedAt: now,
			UpdatedAt: now.Add(time.Hour),
		},
		LoanType:                model.LoanGotovinski,
		InterestType:            model.InterestFixed,
		Amount:                  decimal.NewFromInt(50000),
		Currency:                model.CurrencyRSD,
		Purpose:                 "renovation",
		MonthlySalary:           decimal.NewFromInt(1000),
		EmploymentStatus:        model.EmploymentPermanent,
		CurrentEmploymentPeriod: 12,
		RepaymentPeriod:         24,
		ContactPhone:            "060123456",
		AccountNumber:           "ACC-909",
		ClientID:                11,
		Status:                  model.StatusPending,
		UserEmail:               "user@bank.rs",
		Username:                "user",
	}
}

func loanRequestRow(request model.LoanRequest) []any {
	return []any{
		request.ID,
		request.Version,
		request.Deleted,
		request.CreatedAt,
		request.UpdatedAt,
		request.LoanType,
		request.InterestType,
		request.Amount,
		request.Currency,
		request.Purpose,
		request.MonthlySalary,
		request.EmploymentStatus,
		request.CurrentEmploymentPeriod,
		request.RepaymentPeriod,
		request.ContactPhone,
		request.AccountNumber,
		request.ClientID,
		request.Status,
		request.UserEmail,
		request.Username,
	}
}

func TestLoanRequestStore_Save(t *testing.T) {
	t.Parallel()
	now := time.Date(2024, 4, 2, 0, 0, 0, 0, time.UTC)
	db := &stubDB{
		queryRowFn: func(_ context.Context, _ string, _ ...any) pgx.Row {
			return stubRow{values: []any{int64(10), int64(2), false, now, now}}
		},
	}
	store := NewLoanRequestStore(db)

	created, err := store.Save(context.Background(), sampleLoanRequest(0))
	require.NoError(t, err)
	assert.Equal(t, int64(10), created.ID)
	assert.Equal(t, int64(2), created.Version)
}

func TestLoanRequestStore_FindAll(t *testing.T) {
	t.Parallel()
	request := sampleLoanRequest(3)
	db := &stubDB{
		queryFn: func(_ context.Context, _ string, _ ...any) (pgx.Rows, error) {
			return &stubRows{rows: [][]any{loanRequestRow(request)}}, nil
		},
		queryRowFn: func(_ context.Context, _ string, _ ...any) pgx.Row {
			return stubRow{values: []any{1}}
		},
	}
	store := NewLoanRequestStore(db)

	requests, total, err := store.FindAll(context.Background(), nil, nil, 0, 10)
	require.NoError(t, err)
	assert.Len(t, requests, 1)
	assert.Equal(t, 1, total)
}

func TestLoanRequestStore_UpdateStatusIfPending(t *testing.T) {
	t.Parallel()
	db := &stubDB{
		execFn: func(_ context.Context, _ string, _ ...any) (pgconn.CommandTag, error) {
			return pgconn.NewCommandTag("UPDATE 1"), nil
		},
	}
	store := NewLoanRequestStore(db)

	updated, err := store.UpdateStatusIfPending(context.Background(), 9, model.StatusApproved)
	require.NoError(t, err)
	assert.True(t, updated)
}

func TestLoanRequestStore_UpdateStatusIfPending_NotUpdated(t *testing.T) {
	t.Parallel()
	db := &stubDB{
		execFn: func(_ context.Context, _ string, _ ...any) (pgconn.CommandTag, error) {
			return pgconn.NewCommandTag("UPDATE 0"), nil
		},
	}
	store := NewLoanRequestStore(db)

	updated, err := store.UpdateStatusIfPending(context.Background(), 9, model.StatusApproved)
	require.NoError(t, err)
	assert.False(t, updated)
}

func TestLoanRequestStore_FindByID(t *testing.T) {
	t.Parallel()
	request := sampleLoanRequest(4)
	db := &stubDB{
		queryRowFn: func(_ context.Context, _ string, _ ...any) pgx.Row {
			return stubRow{values: loanRequestRow(request)}
		},
	}
	store := NewLoanRequestStore(db)

	found, err := store.FindByID(context.Background(), request.ID)
	require.NoError(t, err)
	assert.Equal(t, request.ID, found.ID)
	assert.Equal(t, request.AccountNumber, found.AccountNumber)
}

func TestLoanRequestStore_CreateLoanWithFirstInstallment(t *testing.T) {
	t.Parallel()
	loan := sampleLoan(0)
	installment := sampleInstallment(0, 0, model.PaymentUnpaid)
	loan.CreatedAt = time.Date(2024, 5, 1, 0, 0, 0, 0, time.UTC)

	tx := &stubTx{
		queryRowFn: func(_ context.Context, _ string, _ ...any) pgx.Row {
			return stubRow{values: []any{int64(12), int64(1), false, loan.CreatedAt, loan.CreatedAt}}
		},
		execFn: func(_ context.Context, _ string, _ ...any) (pgconn.CommandTag, error) {
			return pgconn.NewCommandTag("INSERT 1"), nil
		},
	}

	db := &stubDB{
		beginFn: func(_ context.Context) (pgx.Tx, error) {
			return tx, nil
		},
	}
	store := NewLoanRequestStore(db)

	created, err := store.CreateLoanWithFirstInstallment(context.Background(), loan, installment)
	require.NoError(t, err)
	assert.Equal(t, int64(12), created.ID)
}

func TestLoanRequestStore_ApproveWithLoanAndInstallment(t *testing.T) {
	t.Parallel()
	loan := sampleLoan(0)
	installment := sampleInstallment(0, 0, model.PaymentUnpaid)

	execCalls := 0
	tx := &stubTx{
		queryRowFn: func(_ context.Context, _ string, _ ...any) pgx.Row {
			return stubRow{values: []any{int64(20), int64(1), false, loan.CreatedAt, loan.CreatedAt}}
		},
		execFn: func(_ context.Context, _ string, _ ...any) (pgconn.CommandTag, error) {
			execCalls++
			if execCalls == 1 {
				return pgconn.NewCommandTag("UPDATE 1"), nil
			}
			return pgconn.NewCommandTag("INSERT 1"), nil
		},
	}

	db := &stubDB{
		beginFn: func(_ context.Context) (pgx.Tx, error) {
			return tx, nil
		},
	}
	store := NewLoanRequestStore(db)

	created, err := store.ApproveWithLoanAndInstallment(context.Background(), 9, loan, installment)
	require.NoError(t, err)
	assert.Equal(t, int64(20), created.ID)
}

func TestLoanRequestStore_ApproveWithLoanAndInstallment_NotPending(t *testing.T) {
	t.Parallel()
	loan := sampleLoan(0)
	installment := sampleInstallment(0, 0, model.PaymentUnpaid)

	tx := &stubTx{
		execFn: func(_ context.Context, _ string, _ ...any) (pgconn.CommandTag, error) {
			return pgconn.NewCommandTag("UPDATE 0"), nil
		},
	}

	db := &stubDB{
		beginFn: func(_ context.Context) (pgx.Tx, error) {
			return tx, nil
		},
	}
	store := NewLoanRequestStore(db)

	_, err := store.ApproveWithLoanAndInstallment(context.Background(), 9, loan, installment)
	require.Error(t, err)
	assert.EqualError(t, err, "loan request ne postoji ili nije u PENDING statusu")
}
