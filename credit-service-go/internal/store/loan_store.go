package store

import (
	"context"
	"time"

	"Banka1Back/credit-service-go/internal/model"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/shopspring/decimal"
)

// pgxPool is the subset of *pgxpool.Pool used by the stores. Declaring it as an
// interface lets unit tests inject a mock pool; *pgxpool.Pool satisfies it.
type pgxPool interface {
	Begin(ctx context.Context) (pgx.Tx, error)
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
}

type LoanStore struct {
	db pgxPool
}

func NewLoanStore(db *pgxpool.Pool) *LoanStore {
	return &LoanStore{db: db}
}

func (s *LoanStore) Create(ctx context.Context, loan model.Loan) (model.Loan, error) {
	query := `
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

	err := s.db.QueryRow(
		ctx,
		query,
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

	return loan, nil
}

func (s *LoanStore) FindByClientID(ctx context.Context, clientID int64, page int, size int) ([]model.Loan, int, error) {
	offset := page * size

	query := `
		SELECT id, version, deleted, created_at, updated_at,
		       loan_type, account_number, amount, repayment_period,
		       nominal_interest_rate, effective_interest_rate, interest_type,
		       agreement_date, maturity_date, installment_amount,
		       next_installment_date, remaining_debt, currency, status,
		       user_email, username, client_id, installment_count
		FROM loan_table
		WHERE deleted = false
		  AND client_id = $1
		ORDER BY amount DESC
		LIMIT $2 OFFSET $3
	`

	rows, err := s.db.Query(ctx, query, clientID, size, offset)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	loans := make([]model.Loan, 0)

	for rows.Next() {
		var loan model.Loan

		err = rows.Scan(
			&loan.ID,
			&loan.Version,
			&loan.Deleted,
			&loan.CreatedAt,
			&loan.UpdatedAt,
			&loan.LoanType,
			&loan.AccountNumber,
			&loan.Amount,
			&loan.RepaymentPeriod,
			&loan.NominalInterestRate,
			&loan.EffectiveInterestRate,
			&loan.InterestType,
			&loan.AgreementDate,
			&loan.MaturityDate,
			&loan.InstallmentAmount,
			&loan.NextInstallmentDate,
			&loan.RemainingDebt,
			&loan.Currency,
			&loan.Status,
			&loan.UserEmail,
			&loan.Username,
			&loan.ClientID,
			&loan.InstallmentCount,
		)
		if err != nil {
			return nil, 0, err
		}

		loans = append(loans, loan)
	}

	countQuery := `
		SELECT COUNT(*)
		FROM loan_table
		WHERE deleted = false
		  AND client_id = $1
	`

	var total int
	err = s.db.QueryRow(ctx, countQuery, clientID).Scan(&total)
	if err != nil {
		return nil, 0, err
	}

	return loans, total, nil
}

func (s *LoanStore) FindByID(ctx context.Context, id int64) (model.Loan, error) {
	query := `
		SELECT id, version, deleted, created_at, updated_at,
		       loan_type, account_number, amount, repayment_period,
		       nominal_interest_rate, effective_interest_rate, interest_type,
		       agreement_date, maturity_date, installment_amount,
		       next_installment_date, remaining_debt, currency, status,
		       user_email, username, client_id, installment_count
		FROM loan_table
		WHERE id = $1
		  AND deleted = false
	`

	var loan model.Loan

	err := s.db.QueryRow(ctx, query, id).Scan(
		&loan.ID,
		&loan.Version,
		&loan.Deleted,
		&loan.CreatedAt,
		&loan.UpdatedAt,
		&loan.LoanType,
		&loan.AccountNumber,
		&loan.Amount,
		&loan.RepaymentPeriod,
		&loan.NominalInterestRate,
		&loan.EffectiveInterestRate,
		&loan.InterestType,
		&loan.AgreementDate,
		&loan.MaturityDate,
		&loan.InstallmentAmount,
		&loan.NextInstallmentDate,
		&loan.RemainingDebt,
		&loan.Currency,
		&loan.Status,
		&loan.UserEmail,
		&loan.Username,
		&loan.ClientID,
		&loan.InstallmentCount,
	)

	if err != nil {
		return model.Loan{}, err
	}

	return loan, nil
}

func (s *LoanStore) FindAllWithFilters(
	ctx context.Context,
	loanType *model.LoanType,
	accountNumber *string,
	status *model.Status,
	page int,
	size int,
) ([]model.Loan, int, error) {
	offset := page * size

	query := `
		SELECT id, version, deleted, created_at, updated_at,
		       loan_type, account_number, amount, repayment_period,
		       nominal_interest_rate, effective_interest_rate, interest_type,
		       agreement_date, maturity_date, installment_amount,
		       next_installment_date, remaining_debt, currency, status,
		       user_email, username, client_id, installment_count
		FROM loan_table
		WHERE deleted = false
		  AND ($1::text IS NULL OR loan_type = $1)
		  AND ($2::text IS NULL OR account_number = $2)
		  AND ($3::text IS NULL OR status = $3)
		ORDER BY account_number ASC
		LIMIT $4 OFFSET $5
	`

	rows, err := s.db.Query(ctx, query, loanType, accountNumber, status, size, offset)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	loans := make([]model.Loan, 0)

	for rows.Next() {
		var loan model.Loan

		err = rows.Scan(
			&loan.ID,
			&loan.Version,
			&loan.Deleted,
			&loan.CreatedAt,
			&loan.UpdatedAt,
			&loan.LoanType,
			&loan.AccountNumber,
			&loan.Amount,
			&loan.RepaymentPeriod,
			&loan.NominalInterestRate,
			&loan.EffectiveInterestRate,
			&loan.InterestType,
			&loan.AgreementDate,
			&loan.MaturityDate,
			&loan.InstallmentAmount,
			&loan.NextInstallmentDate,
			&loan.RemainingDebt,
			&loan.Currency,
			&loan.Status,
			&loan.UserEmail,
			&loan.Username,
			&loan.ClientID,
			&loan.InstallmentCount,
		)
		if err != nil {
			return nil, 0, err
		}

		loans = append(loans, loan)
	}

	countQuery := `
		SELECT COUNT(*)
		FROM loan_table
		WHERE deleted = false
		  AND ($1::text IS NULL OR loan_type = $1)
		  AND ($2::text IS NULL OR account_number = $2)
		  AND ($3::text IS NULL OR status = $3)
	`

	var total int
	err = s.db.QueryRow(ctx, countQuery, loanType, accountNumber, status).Scan(&total)
	if err != nil {
		return nil, 0, err
	}

	return loans, total, nil
}

func (s *LoanStore) MarkOverdue(ctx context.Context, loanID int64) error {
	_, err := s.db.Exec(ctx, `
		UPDATE loan_table
		SET status = 'OVERDUE',
		    next_installment_date = CURRENT_DATE + INTERVAL '1 day',
		    version = version + 1,
		    updated_at = NOW()
		WHERE id = $1
		  AND deleted = false
	`, loanID)

	return err
}

func (s *LoanStore) UpdateAfterInstallmentPayment(
	ctx context.Context,
	loanID int64,
	remainingDebt decimal.Decimal,
	installmentCount int,
	nextInstallmentDate time.Time,
	status model.Status,
) error {
	_, err := s.db.Exec(ctx, `
		UPDATE loan_table
		SET remaining_debt = $1,
		    installment_count = $2,
		    next_installment_date = $3,
		    status = $4,
		    version = version + 1,
		    updated_at = NOW()
		WHERE id = $5
		  AND deleted = false
	`, remainingDebt.String(), installmentCount, nextInstallmentDate, status, loanID)

	return err
}
