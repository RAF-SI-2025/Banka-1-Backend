package store

import (
	"context"
	"errors"

	"Banka1Back/credit-service-go/internal/model"
)

type LoanRequestStore struct {
	db dbPool
}

func NewLoanRequestStore(db dbPool) *LoanRequestStore {
	return &LoanRequestStore{db: db}
}

func (s *LoanRequestStore) Save(ctx context.Context, loanRequest model.LoanRequest) (model.LoanRequest, error) {
	query := `
		INSERT INTO loan_request_table (
			version,
			deleted,
			created_at,
			updated_at,
			loan_type,
			interest_type,
			amount,
			currency,
			purpose,
			monthly_salary,
			employment_status,
			current_employment_period,
			repayment_period,
			contact_phone,
			account_number,
			client_id,
			status,
			user_email,
			username
		)
		VALUES (
			0,
			false,
			NOW(),
			NOW(),
			$1, $2, $3, $4, $5, $6, $7, $8, $9,
			$10, $11, $12, $13, $14, $15
		)
		RETURNING id, version, deleted, created_at, updated_at
	`

	err := s.db.QueryRow(
		ctx,
		query,
		loanRequest.LoanType,
		loanRequest.InterestType,
		loanRequest.Amount.String(),
		loanRequest.Currency,
		loanRequest.Purpose,
		loanRequest.MonthlySalary.String(),
		loanRequest.EmploymentStatus,
		loanRequest.CurrentEmploymentPeriod,
		loanRequest.RepaymentPeriod,
		loanRequest.ContactPhone,
		loanRequest.AccountNumber,
		loanRequest.ClientID,
		loanRequest.Status,
		loanRequest.UserEmail,
		loanRequest.Username,
	).Scan(
		&loanRequest.ID,
		&loanRequest.Version,
		&loanRequest.Deleted,
		&loanRequest.CreatedAt,
		&loanRequest.UpdatedAt,
	)

	if err != nil {
		return model.LoanRequest{}, err
	}

	return loanRequest, nil
}

func (s *LoanRequestStore) FindAll(
	ctx context.Context,
	loanType *model.LoanType,
	accountNumber *string,
	page int,
	size int,
) ([]model.LoanRequest, int, error) {
	offset := page * size

	query := `
		SELECT id, version, deleted, created_at, updated_at,
		       loan_type, interest_type, amount, currency, purpose,
		       monthly_salary, employment_status, current_employment_period,
		       repayment_period, contact_phone, account_number, client_id,
		       status, user_email, username
		FROM loan_request_table
		WHERE deleted = false
		  AND ($1::text IS NULL OR loan_type = $1)
		  AND ($2::text IS NULL OR account_number = $2)
		ORDER BY created_at DESC
		LIMIT $3 OFFSET $4
	`

	rows, err := s.db.Query(ctx, query, loanType, accountNumber, size, offset)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	requests := make([]model.LoanRequest, 0)

	for rows.Next() {
		var request model.LoanRequest

		err = rows.Scan(
			&request.ID,
			&request.Version,
			&request.Deleted,
			&request.CreatedAt,
			&request.UpdatedAt,
			&request.LoanType,
			&request.InterestType,
			&request.Amount,
			&request.Currency,
			&request.Purpose,
			&request.MonthlySalary,
			&request.EmploymentStatus,
			&request.CurrentEmploymentPeriod,
			&request.RepaymentPeriod,
			&request.ContactPhone,
			&request.AccountNumber,
			&request.ClientID,
			&request.Status,
			&request.UserEmail,
			&request.Username,
		)
		if err != nil {
			return nil, 0, err
		}

		requests = append(requests, request)
	}

	countQuery := `
		SELECT COUNT(*)
		FROM loan_request_table
		WHERE deleted = false
		  AND ($1::text IS NULL OR loan_type = $1)
		  AND ($2::text IS NULL OR account_number = $2)
	`

	var total int
	err = s.db.QueryRow(ctx, countQuery, loanType, accountNumber).Scan(&total)
	if err != nil {
		return nil, 0, err
	}

	return requests, total, nil
}

func (s *LoanRequestStore) UpdateStatusIfPending(
	ctx context.Context,
	id int64,
	status model.Status,
) (bool, error) {
	query := `
		UPDATE loan_request_table
		SET status = $1,
		    version = version + 1,
		    updated_at = NOW()
		WHERE id = $2
		  AND status = 'PENDING'
		  AND deleted = false
	`

	result, err := s.db.Exec(ctx, query, status, id)
	if err != nil {
		return false, err
	}

	return result.RowsAffected() == 1, nil
}

func (s *LoanRequestStore) FindByID(ctx context.Context, id int64) (model.LoanRequest, error) {
	query := `
		SELECT id, version, deleted, created_at, updated_at,
		       loan_type, interest_type, amount, currency, purpose,
		       monthly_salary, employment_status, current_employment_period,
		       repayment_period, contact_phone, account_number, client_id,
		       status, user_email, username
		FROM loan_request_table
		WHERE id = $1 AND deleted = false
	`

	var request model.LoanRequest

	err := s.db.QueryRow(ctx, query, id).Scan(
		&request.ID,
		&request.Version,
		&request.Deleted,
		&request.CreatedAt,
		&request.UpdatedAt,
		&request.LoanType,
		&request.InterestType,
		&request.Amount,
		&request.Currency,
		&request.Purpose,
		&request.MonthlySalary,
		&request.EmploymentStatus,
		&request.CurrentEmploymentPeriod,
		&request.RepaymentPeriod,
		&request.ContactPhone,
		&request.AccountNumber,
		&request.ClientID,
		&request.Status,
		&request.UserEmail,
		&request.Username,
	)

	if err != nil {
		return model.LoanRequest{}, err
	}

	return request, nil
}

func (s *LoanRequestStore) CreateLoanWithFirstInstallment(
	ctx context.Context,
	loan model.Loan,
	installment model.Installment,
) (model.Loan, error) {
	tx, err := s.db.Begin(ctx)
	if err != nil {
		return model.Loan{}, err
	}
	defer tx.Rollback(ctx)

	loanQuery := `
		INSERT INTO loan_table (
			version, deleted, created_at, updated_at,
			loan_type, account_number, amount, repayment_period,
			nominal_interest_rate, effective_interest_rate, interest_type,
			agreement_date, maturity_date, installment_amount,
			next_installment_date, remaining_debt, currency, status,
			user_email, username, client_id, installment_count
		)
		VALUES (
			0, false, NOW(), NOW(),
			$1, $2, $3, $4, $5, $6, $7,
			$8, $9, $10, $11, $12, $13, $14,
			$15, $16, $17, $18
		)
		RETURNING id, version, deleted, created_at, updated_at
	`

	err = tx.QueryRow(
		ctx,
		loanQuery,
		loan.LoanType,
		loan.AccountNumber,
		loan.Amount.String(),
		loan.RepaymentPeriod,
		loan.NominalInterestRate.String(),
		loan.EffectiveInterestRate.String(),
		loan.InterestType,
		loan.AgreementDate,
		loan.MaturityDate,
		loan.InstallmentAmount.String(),
		loan.NextInstallmentDate,
		loan.RemainingDebt.String(),
		loan.Currency,
		loan.Status,
		loan.UserEmail,
		loan.Username,
		loan.ClientID,
		loan.InstallmentCount,
	).Scan(
		&loan.ID,
		&loan.Version,
		&loan.Deleted,
		&loan.CreatedAt,
		&loan.UpdatedAt,
	)
	if err != nil {
		return model.Loan{}, err
	}

	installmentQuery := `
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

	_, err = tx.Exec(
		ctx,
		installmentQuery,
		loan.ID,
		installment.InstallmentAmount.String(),
		installment.InterestRateAtPayment.String(),
		installment.Currency,
		installment.ExpectedDueDate,
		installment.ActualDueDate,
		installment.PaymentStatus,
		installment.Retry,
	)
	if err != nil {
		return model.Loan{}, err
	}

	err = tx.Commit(ctx)
	if err != nil {
		return model.Loan{}, err
	}

	return loan, nil
}

func (s *LoanRequestStore) ApproveWithLoanAndInstallment(
	ctx context.Context,
	requestID int64,
	loan model.Loan,
	installment model.Installment,
) (model.Loan, error) {
	tx, err := s.db.Begin(ctx)
	if err != nil {
		return model.Loan{}, err
	}
	defer tx.Rollback(ctx)

	result, err := tx.Exec(ctx, `
		UPDATE loan_request_table
		SET status = 'APPROVED',
		    version = version + 1,
		    updated_at = NOW()
		WHERE id = $1
		  AND status = 'PENDING'
		  AND deleted = false
	`, requestID)
	if err != nil {
		return model.Loan{}, err
	}

	if result.RowsAffected() != 1 {
		return model.Loan{}, errors.New("loan request ne postoji ili nije u PENDING statusu")
	}

	err = tx.QueryRow(ctx, `
		INSERT INTO loan_table (
			version, deleted, created_at, updated_at,
			loan_type, account_number, amount, repayment_period,
			nominal_interest_rate, effective_interest_rate, interest_type,
			agreement_date, maturity_date, installment_amount,
			next_installment_date, remaining_debt, currency, status,
			user_email, username, client_id, installment_count
		)
		VALUES (
			0, false, NOW(), NOW(),
			$1, $2, $3, $4, $5, $6, $7,
			$8, $9, $10, $11, $12, $13, $14,
			$15, $16, $17, $18
		)
		RETURNING id, version, deleted, created_at, updated_at
	`,
		loan.LoanType,
		loan.AccountNumber,
		loan.Amount.String(),
		loan.RepaymentPeriod,
		loan.NominalInterestRate.String(),
		loan.EffectiveInterestRate.String(),
		loan.InterestType,
		loan.AgreementDate,
		loan.MaturityDate,
		loan.InstallmentAmount.String(),
		loan.NextInstallmentDate,
		loan.RemainingDebt.String(),
		loan.Currency,
		loan.Status,
		loan.UserEmail,
		loan.Username,
		loan.ClientID,
		loan.InstallmentCount,
	).Scan(
		&loan.ID,
		&loan.Version,
		&loan.Deleted,
		&loan.CreatedAt,
		&loan.UpdatedAt,
	)
	if err != nil {
		return model.Loan{}, err
	}

	_, err = tx.Exec(ctx, `
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
	`,
		loan.ID,
		installment.InstallmentAmount.String(),
		installment.InterestRateAtPayment.String(),
		installment.Currency,
		installment.ExpectedDueDate,
		installment.ActualDueDate,
		installment.PaymentStatus,
		installment.Retry,
	)
	if err != nil {
		return model.Loan{}, err
	}

	err = tx.Commit(ctx)
	if err != nil {
		return model.Loan{}, err
	}

	return loan, nil
}
