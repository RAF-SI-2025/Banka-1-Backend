package service

import (
	"context"
	"database/sql"
	"testing"
	"time"

	"banka1/banking-core-service-go/internal/account"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func newMarginSvc(t *testing.T) (*MarginAccountService, sqlmock.Sqlmock) {
	t.Helper()
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	t.Cleanup(func() { _ = db.Close() })
	return NewMarginAccountService(db, account.NumberGenerator{}), mock
}

func i64(v int64) *int64 { return &v }

func marginRows() *sqlmock.Rows {
	return sqlmock.NewRows([]string{
		"id", "account_number", "initial_margin", "loan_value", "maintenance_margin",
		"bank_participation", "active", "user_id", "company_id", "created_at",
	}).AddRow(int64(1), "111000110000000312", "1000", "0", "500", "0.5", true, int64(5), nil, time.Now())
}

func TestMargin_CreateForUser_Success(t *testing.T) {
	s, mock := newMarginSvc(t)
	mock.ExpectBegin()
	mock.ExpectQuery("user_margin_accounts").WillReturnRows(sqlmock.NewRows([]string{"exists"}).AddRow(false))
	mock.ExpectQuery("INSERT INTO margin_accounts").WillReturnRows(sqlmock.NewRows([]string{"id"}).AddRow(int64(1)))
	mock.ExpectExec("INSERT INTO user_margin_accounts").WillReturnResult(sqlmock.NewResult(1, 1))
	mock.ExpectCommit()
	mock.ExpectQuery("FROM margin_accounts").WillReturnRows(marginRows())

	resp, err := s.CreateForUser(context.Background(), CreateUserMarginAccountRequest{
		EmployeeID: i64(1), UserID: i64(5),
		InitialMargin: decimal.NewFromInt(1000), MaintenanceMargin: decimal.NewFromInt(500),
		BankParticipation: decimal.RequireFromString("0.5"),
	})
	require.NoError(t, err)
	require.NotNil(t, resp.UserID)
	assert.Equal(t, int64(5), *resp.UserID)
}

func TestMargin_CreateForUser_MissingIDs(t *testing.T) {
	s, _ := newMarginSvc(t)
	_, err := s.CreateForUser(context.Background(), CreateUserMarginAccountRequest{})
	assert.Error(t, err)
}

func TestMargin_CreateForUser_InvalidMargins(t *testing.T) {
	s, _ := newMarginSvc(t)
	_, err := s.CreateForUser(context.Background(), CreateUserMarginAccountRequest{
		EmployeeID: i64(1), UserID: i64(5),
		InitialMargin: decimal.Zero, MaintenanceMargin: decimal.NewFromInt(500),
		BankParticipation: decimal.RequireFromString("0.5"),
	})
	assert.Error(t, err)
}

func TestMargin_CreateForUser_AlreadyExists(t *testing.T) {
	s, mock := newMarginSvc(t)
	mock.ExpectBegin()
	mock.ExpectQuery("user_margin_accounts").WillReturnRows(sqlmock.NewRows([]string{"exists"}).AddRow(true))

	_, err := s.CreateForUser(context.Background(), CreateUserMarginAccountRequest{
		EmployeeID: i64(1), UserID: i64(5),
		InitialMargin: decimal.NewFromInt(1000), MaintenanceMargin: decimal.NewFromInt(500),
		BankParticipation: decimal.RequireFromString("0.5"),
	})
	assert.Error(t, err)
}

func TestMargin_CreateForCompany_Success(t *testing.T) {
	s, mock := newMarginSvc(t)
	mock.ExpectBegin()
	mock.ExpectQuery("company_margin_accounts").WillReturnRows(sqlmock.NewRows([]string{"exists"}).AddRow(false))
	mock.ExpectQuery("INSERT INTO margin_accounts").WillReturnRows(sqlmock.NewRows([]string{"id"}).AddRow(int64(1)))
	mock.ExpectExec("INSERT INTO company_margin_accounts").WillReturnResult(sqlmock.NewResult(1, 1))
	mock.ExpectCommit()
	mock.ExpectQuery("FROM margin_accounts").WillReturnRows(sqlmock.NewRows([]string{
		"id", "account_number", "initial_margin", "loan_value", "maintenance_margin",
		"bank_participation", "active", "user_id", "company_id", "created_at",
	}).AddRow(int64(1), "111000110000000312", "1000", "0", "500", "0.5", true, nil, int64(9), time.Now()))

	resp, err := s.CreateForCompany(context.Background(), CreateCompanyMarginAccountRequest{
		EmployeeID: i64(1), CompanyID: i64(9),
		InitialMargin: decimal.NewFromInt(1000), MaintenanceMargin: decimal.NewFromInt(500),
		BankParticipation: decimal.RequireFromString("0.5"),
	})
	require.NoError(t, err)
	require.NotNil(t, resp.CompanyID)
	assert.Equal(t, int64(9), *resp.CompanyID)
}

func TestMargin_FindByUserID_Success(t *testing.T) {
	s, mock := newMarginSvc(t)
	mock.ExpectQuery("FROM margin_accounts").WillReturnRows(marginRows())
	resp, err := s.FindByUserID(context.Background(), 5)
	require.NoError(t, err)
	assert.Equal(t, "111000110000000312", resp.AccountNumber)
}

func TestMargin_FindByUserID_NotFound(t *testing.T) {
	s, mock := newMarginSvc(t)
	mock.ExpectQuery("FROM margin_accounts").WillReturnError(sqlNoRows())
	_, err := s.FindByUserID(context.Background(), 5)
	assert.Error(t, err)
}

func TestMargin_FindByCompanyID_Success(t *testing.T) {
	s, mock := newMarginSvc(t)
	mock.ExpectQuery("FROM margin_accounts").WillReturnRows(sqlmock.NewRows([]string{
		"id", "account_number", "initial_margin", "loan_value", "maintenance_margin",
		"bank_participation", "active", "user_id", "company_id", "created_at",
	}).AddRow(int64(1), "111000110000000312", "1000", "0", "500", "0.5", true, nil, int64(9), time.Now()))
	resp, err := s.FindByCompanyID(context.Background(), 9)
	require.NoError(t, err)
	require.NotNil(t, resp.CompanyID)
}
