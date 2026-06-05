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

func sampleInstallment(id int64, retry int, status model.PaymentStatus) model.Installment {
	now := time.Date(2024, 3, 1, 0, 0, 0, 0, time.UTC)
	return model.Installment{
		BaseEntity: model.BaseEntity{
			ID:        id,
			Version:   1,
			Deleted:   false,
			CreatedAt: now,
			UpdatedAt: now.Add(time.Hour),
		},
		LoanID:                5,
		InstallmentAmount:     decimal.NewFromInt(1000),
		InterestRateAtPayment: decimal.NewFromFloat(0.02),
		Currency:              model.CurrencyRSD,
		ExpectedDueDate:       now,
		ActualDueDate:         nil,
		PaymentStatus:         status,
		Retry:                 retry,
	}
}

func installmentRow(installment model.Installment) []any {
	return []any{
		installment.ID,
		installment.Version,
		installment.Deleted,
		installment.CreatedAt,
		installment.UpdatedAt,
		installment.LoanID,
		installment.InstallmentAmount,
		installment.InterestRateAtPayment,
		installment.Currency,
		installment.ExpectedDueDate,
		installment.ActualDueDate,
		installment.PaymentStatus,
		installment.Retry,
	}
}

func TestInstallmentStore_Create(t *testing.T) {
	t.Parallel()
	db := &stubDB{
		execFn: func(_ context.Context, _ string, _ ...any) (pgconn.CommandTag, error) {
			return pgconn.NewCommandTag("INSERT 1"), nil
		},
	}
	store := NewInstallmentStore(db)

	err := store.Create(context.Background(), sampleInstallment(1, 0, model.PaymentUnpaid))
	require.NoError(t, err)
}

func TestInstallmentStore_FindByLoanID(t *testing.T) {
	t.Parallel()
	instA := sampleInstallment(1, 0, model.PaymentUnpaid)
	instB := sampleInstallment(2, 1, model.PaymentOverdue)

	db := &stubDB{
		queryFn: func(_ context.Context, _ string, _ ...any) (pgx.Rows, error) {
			return &stubRows{rows: [][]any{installmentRow(instA), installmentRow(instB)}}, nil
		},
	}
	store := NewInstallmentStore(db)

	installments, err := store.FindByLoanID(context.Background(), instA.LoanID)
	require.NoError(t, err)
	assert.Len(t, installments, 2)
}

func TestInstallmentStore_FindDueUnpaid(t *testing.T) {
	t.Parallel()
	inst := sampleInstallment(1, 0, model.PaymentUnpaid)
	db := &stubDB{
		queryFn: func(_ context.Context, _ string, _ ...any) (pgx.Rows, error) {
			return &stubRows{rows: [][]any{installmentRow(inst)}}, nil
		},
	}
	store := NewInstallmentStore(db)

	installments, err := store.FindDueUnpaid(context.Background())
	require.NoError(t, err)
	assert.Len(t, installments, 1)
}

func TestInstallmentStore_MarkRetryOrOverdue_Retry(t *testing.T) {
	t.Parallel()
	db := &stubDB{
		execFn: func(_ context.Context, _ string, _ ...any) (pgconn.CommandTag, error) {
			return pgconn.NewCommandTag("UPDATE 1"), nil
		},
	}
	store := NewInstallmentStore(db)

	err := store.MarkRetryOrOverdue(context.Background(), sampleInstallment(3, 0, model.PaymentUnpaid))
	require.NoError(t, err)
}

func TestInstallmentStore_MarkRetryOrOverdue_Overdue(t *testing.T) {
	t.Parallel()
	db := &stubDB{
		execFn: func(_ context.Context, _ string, _ ...any) (pgconn.CommandTag, error) {
			return pgconn.NewCommandTag("UPDATE 1"), nil
		},
	}
	store := NewInstallmentStore(db)

	err := store.MarkRetryOrOverdue(context.Background(), sampleInstallment(4, 1, model.PaymentOverdue))
	require.NoError(t, err)
}

func TestInstallmentStore_MarkPaid(t *testing.T) {
	t.Parallel()
	db := &stubDB{
		execFn: func(_ context.Context, _ string, _ ...any) (pgconn.CommandTag, error) {
			return pgconn.NewCommandTag("UPDATE 1"), nil
		},
	}
	store := NewInstallmentStore(db)

	err := store.MarkPaid(context.Background(), 12)
	require.NoError(t, err)
}
