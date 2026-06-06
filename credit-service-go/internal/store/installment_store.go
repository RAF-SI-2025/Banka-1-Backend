package store

import (
	"context"

	"Banka1Back/credit-service-go/internal/model"

	"github.com/jackc/pgx/v5/pgxpool"
)

type InstallmentStore struct {
	db pgxPool
}

func NewInstallmentStore(db *pgxpool.Pool) *InstallmentStore {
	return &InstallmentStore{db: db}
}

func (s *InstallmentStore) Create(ctx context.Context, installment model.Installment) error {
	query := `
		INSERT INTO installment_table (
			version, deleted, created_at, updated_at,
			loan_id, installment_amount, interest_rate_at_payment,
			currency, expected_due_date, actual_due_date,
			payment_status, retry
		)
		VALUES (
			0, false, NOW(), NOW(),
			$1, $2, $3, $4, $5, $6, $7, $8
		)
	`

	_, err := s.db.Exec(
		ctx,
		query,
		installment.LoanID,
		installment.InstallmentAmount.String(),
		installment.InterestRateAtPayment.String(),
		installment.Currency,
		installment.ExpectedDueDate,
		installment.ActualDueDate,
		installment.PaymentStatus,
		installment.Retry,
	)

	return err
}

func (s *InstallmentStore) FindByLoanID(ctx context.Context, loanID int64) ([]model.Installment, error) {
	query := `
		SELECT id, version, deleted, created_at, updated_at,
		       loan_id, installment_amount, interest_rate_at_payment,
		       currency, expected_due_date, actual_due_date,
		       payment_status, retry
		FROM installment_table
		WHERE deleted = false
		  AND loan_id = $1
		ORDER BY expected_due_date ASC
	`

	rows, err := s.db.Query(ctx, query, loanID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	installments := make([]model.Installment, 0)

	for rows.Next() {
		var installment model.Installment

		err = rows.Scan(
			&installment.ID,
			&installment.Version,
			&installment.Deleted,
			&installment.CreatedAt,
			&installment.UpdatedAt,
			&installment.LoanID,
			&installment.InstallmentAmount,
			&installment.InterestRateAtPayment,
			&installment.Currency,
			&installment.ExpectedDueDate,
			&installment.ActualDueDate,
			&installment.PaymentStatus,
			&installment.Retry,
		)
		if err != nil {
			return nil, err
		}

		installments = append(installments, installment)
	}

	return installments, nil
}

func (s *InstallmentStore) FindDueUnpaid(ctx context.Context) ([]model.Installment, error) {
	query := `
		SELECT id, version, deleted, created_at, updated_at,
		       loan_id, installment_amount, interest_rate_at_payment,
		       currency, expected_due_date, actual_due_date,
		       payment_status, retry
		FROM installment_table
		WHERE deleted = false
		  AND expected_due_date <= CURRENT_DATE
		  AND payment_status <> 'PAID'
		ORDER BY expected_due_date ASC
	`

	rows, err := s.db.Query(ctx, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	installments := make([]model.Installment, 0)

	for rows.Next() {
		var installment model.Installment

		err = rows.Scan(
			&installment.ID,
			&installment.Version,
			&installment.Deleted,
			&installment.CreatedAt,
			&installment.UpdatedAt,
			&installment.LoanID,
			&installment.InstallmentAmount,
			&installment.InterestRateAtPayment,
			&installment.Currency,
			&installment.ExpectedDueDate,
			&installment.ActualDueDate,
			&installment.PaymentStatus,
			&installment.Retry,
		)
		if err != nil {
			return nil, err
		}

		installments = append(installments, installment)
	}

	return installments, nil
}

func (s *InstallmentStore) MarkRetryOrOverdue(ctx context.Context, installment model.Installment) error {
	if installment.PaymentStatus != model.PaymentOverdue && installment.Retry == 0 {
		_, err := s.db.Exec(ctx, `
			UPDATE installment_table
			SET retry = 1,
			    expected_due_date = CURRENT_DATE + INTERVAL '3 days',
			    version = version + 1,
			    updated_at = NOW()
			WHERE id = $1
			  AND deleted = false
		`, installment.ID)

		return err
	}

	_, err := s.db.Exec(ctx, `
		UPDATE installment_table
		SET payment_status = 'OVERDUE',
		    expected_due_date = CURRENT_DATE + INTERVAL '1 day',
		    version = version + 1,
		    updated_at = NOW()
		WHERE id = $1
		  AND deleted = false
	`, installment.ID)

	return err
}

func (s *InstallmentStore) MarkPaid(ctx context.Context, installmentID int64) error {
	_, err := s.db.Exec(ctx, `
		UPDATE installment_table
		SET payment_status = 'PAID',
		    actual_due_date = CURRENT_DATE,
		    version = version + 1,
		    updated_at = NOW()
		WHERE id = $1
		  AND deleted = false
	`, installmentID)

	return err
}
