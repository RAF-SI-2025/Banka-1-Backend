package user

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/pashagolub/pgxmock/v4"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func undefinedColumnErr() error { return &pgconn.PgError{Code: "42703"} }

func TestRepo_ClientByPlainJMBG_FallsBackToEncrypted(t *testing.T) {
	r, mock := newRepoMock(t)
	r.jmbg = testJMBG(t)

	// First query (plain jmbg column) fails with undefined-column.
	mock.ExpectQuery("FROM clients").WithArgs(anyN(1)...).WillReturnError(undefinedColumnErr())
	// Fallback scans all encrypted clients; ours decrypts to the target.
	enc, err := r.jmbg.Encrypt("0101990500006")
	require.NoError(t, err)
	mock.ExpectQuery("jmbg_encrypted IS NOT NULL").
		WillReturnRows(pgxmock.NewRows(clientCols).AddRow(
			int64(1), "Mark", "Markovic", int64(19900101), "M", "m@x.com", strptr("+381"), strptr("Adr"),
			strptr("hash"), (*string)(nil), strptr(enc), true, "CLIENT_BASIC", time.Now(), time.Now(),
		))

	c, err := r.ClientByPlainJMBG(context.Background(), "0101990500006")
	require.NoError(t, err)
	assert.Equal(t, int64(1), c.ID)
}

func TestRepo_ClientByPlainJMBG_EncryptedNotFound(t *testing.T) {
	r, mock := newRepoMock(t)
	r.jmbg = testJMBG(t)
	mock.ExpectQuery("FROM clients").WithArgs(anyN(1)...).WillReturnError(undefinedColumnErr())
	mock.ExpectQuery("jmbg_encrypted IS NOT NULL").
		WillReturnRows(pgxmock.NewRows(clientCols)) // no rows

	_, err := r.ClientByPlainJMBG(context.Background(), "9999999999999")
	assert.ErrorIs(t, err, ErrNotFound)
}

func TestRepo_SoftDeleteEmployee_NotFound(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectExec("UPDATE employees").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("UPDATE", 0))
	assert.ErrorIs(t, r.SoftDeleteEmployee(context.Background(), 1), ErrNotFound)
}

func TestRepo_SoftDeleteClient_NotFound(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectExec("UPDATE clients").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("UPDATE", 0))
	assert.ErrorIs(t, r.SoftDeleteClient(context.Background(), 1), ErrNotFound)
}

func TestRepo_UpdateEmployee_ExecError(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectQuery("FROM employees").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows(employeeCols).AddRow(employeeRow()...))
	mock.ExpectExec("UPDATE employees").WithArgs(anyN(10)...).WillReturnError(errors.New("boom"))

	_, err := r.UpdateEmployee(context.Background(), 1, EmployeeUpdateRequest{})
	assert.Error(t, err)
}

func TestRepo_UpdateClient_ExecError(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectQuery("FROM clients").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows(clientCols).AddRow(clientRow()...))
	mock.ExpectExec("UPDATE clients").WithArgs(anyN(7)...).WillReturnError(errors.New("boom"))

	_, err := r.UpdateClient(context.Background(), 1, ClientUpdateRequest{})
	assert.Error(t, err)
}

func TestRepo_CreateClient_UndefinedColumnFallback(t *testing.T) {
	r, mock := newRepoMock(t)
	r.jmbg = testJMBG(t)
	mock.ExpectBegin()
	// First INSERT (jmbg_encrypted column) fails undefined-column -> fallback to jmbg column.
	mock.ExpectQuery("INSERT INTO clients").WithArgs(anyN(9)...).WillReturnError(undefinedColumnErr())
	mock.ExpectQuery("INSERT INTO clients").WithArgs(anyN(9)...).
		WillReturnRows(pgxmock.NewRows([]string{"id"}).AddRow(int64(1)))
	mock.ExpectCommit()
	mock.ExpectQuery("FROM clients").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows(clientCols).AddRow(clientRow()...))

	c, err := r.CreateClient(context.Background(), ClientCreateRequest{
		Ime: "Mark", Email: "m@x.com", JMBG: "0101990500006",
	}, nil)
	require.NoError(t, err)
	assert.Equal(t, int64(1), c.ID)
}

func TestRepo_EmployeeByRefreshToken_NotFound(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectQuery("FROM refresh_tokens").WithArgs(anyN(1)...).WillReturnError(pgx.ErrNoRows)
	_, err := r.EmployeeByRefreshToken(context.Background(), "tok")
	assert.ErrorIs(t, err, ErrNotFound)
}

func TestRepo_UpdateEmployee_LoadCurrentError(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectQuery("FROM employees").WithArgs(anyN(1)...).WillReturnError(pgx.ErrNoRows)
	_, err := r.UpdateEmployee(context.Background(), 1, EmployeeUpdateRequest{})
	assert.ErrorIs(t, err, ErrNotFound)
}

func TestRepo_UpdateClient_LoadCurrentError(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectQuery("FROM clients").WithArgs(anyN(1)...).WillReturnError(pgx.ErrNoRows)
	_, err := r.UpdateClient(context.Background(), 1, ClientUpdateRequest{})
	assert.ErrorIs(t, err, ErrNotFound)
}

func TestRepo_CreateClient_CommitError(t *testing.T) {
	r, mock := newRepoMock(t)
	r.jmbg = testJMBG(t)
	mock.ExpectBegin()
	mock.ExpectQuery("INSERT INTO clients").WithArgs(anyN(9)...).
		WillReturnRows(pgxmock.NewRows([]string{"id"}).AddRow(int64(1)))
	mock.ExpectCommit().WillReturnError(errors.New("commit failed"))

	_, err := r.CreateClient(context.Background(), ClientCreateRequest{
		Ime: "A", Email: "a@x.com", JMBG: "0101990500006",
	}, nil)
	assert.Error(t, err)
}

func TestRepo_CreateClient_BeginError(t *testing.T) {
	r, mock := newRepoMock(t)
	r.jmbg = testJMBG(t)
	mock.ExpectBegin().WillReturnError(errors.New("no conn"))

	_, err := r.CreateClient(context.Background(), ClientCreateRequest{
		Ime: "A", Email: "a@x.com", JMBG: "0101990500006",
	}, nil)
	assert.Error(t, err)
}

func TestRepo_ConfirmationIDByToken_NotFound(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectQuery("FROM confirmation_token").WithArgs(anyN(1)...).WillReturnError(pgx.ErrNoRows)

	_, err := r.ConfirmationIDByToken(context.Background(), "confirmation_token", "zaposlen_id", "tok")
	assert.ErrorIs(t, err, ErrNotFound)
}

func TestRepo_ActivateClientPassword_InvalidToken(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectExec("UPDATE clients").WithArgs(anyN(3)...).WillReturnResult(pgxmock.NewResult("UPDATE", 0))
	assert.ErrorIs(t, r.ActivateClientPassword(context.Background(), 5, "hash", "pw"), ErrInvalidToken)
}

func TestRepo_CreateEmployee_InsertError(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectBegin()
	mock.ExpectQuery("INSERT INTO employees").WithArgs(anyN(12)...).WillReturnError(errors.New("boom"))
	mock.ExpectRollback()

	_, err := r.CreateEmployee(context.Background(), EmployeeCreateRequest{
		Ime: "A", Prezime: "B", DatumRodjenja: "1990-01-01", Email: "a@x.com", Username: "a",
	}, nil)
	assert.Error(t, err)
}

func TestRepo_ReplaceEmployeePermissions_InsertFails_Rollback(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectBegin()
	mock.ExpectExec("DELETE FROM zaposlen_permissions").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("DELETE", 1))
	mock.ExpectExec("INSERT INTO zaposlen_permissions").WithArgs(anyN(2)...).WillReturnError(errors.New("boom"))
	mock.ExpectRollback()

	assert.Error(t, r.ReplaceEmployeePermissions(context.Background(), 1, []string{"P"}))
}
