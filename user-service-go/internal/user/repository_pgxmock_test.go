package user

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/pashagolub/pgxmock/v4"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func strptr(s string) *string { return &s }

// anyN returns n AnyArg matchers for pgxmock WithArgs.
func anyN(n int) []any {
	a := make([]any, n)
	for i := range a {
		a[i] = pgxmock.AnyArg()
	}
	return a
}

func newRepoMock(t *testing.T) (*Repository, pgxmock.PgxPoolIface) {
	t.Helper()
	mock, err := pgxmock.NewPool()
	require.NoError(t, err)
	t.Cleanup(mock.Close)
	return &Repository{db: mock}, mock
}

var employeeCols = []string{
	"id", "ime", "prezime", "datum_rodjenja", "pol", "email", "broj_telefona", "adresa",
	"username", "password", "pozicija", "departman", "aktivan", "role",
	"failed_login_attempts", "locked_until", "created_at", "updated_at",
}

func employeeRow() []any {
	return []any{
		int64(1), "Ana", "Anic", time.Now(), "F", "ana@x.com", strptr("+381"), strptr("Adr"),
		"ana", strptr("hash"), "Teller", "Retail", true, "BASIC",
		int(0), (*time.Time)(nil), time.Now(), time.Now(),
	}
}

var clientCols = []string{
	"id", "ime", "prezime", "datum_rodjenja", "pol", "email", "broj_telefona", "adresa",
	"password", "jmbg", "jmbg_encrypted", "aktivan", "role", "created_at", "updated_at",
}

func clientRow() []any {
	return []any{
		int64(1), "Mark", "Markovic", int64(19900101), "M", "m@x.com", strptr("+381"), strptr("Adr"),
		strptr("hash"), strptr("0101990"), (*string)(nil), true, "CLIENT_BASIC", time.Now(), time.Now(),
	}
}

// ---------------------------------------------------------------------------
// QueryRow getters
// ---------------------------------------------------------------------------

func TestRepo_EmployeeByLogin(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectQuery("FROM employees").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows(employeeCols).AddRow(employeeRow()...))

	emp, err := r.EmployeeByLogin(context.Background(), "ana")
	require.NoError(t, err)
	assert.Equal(t, int64(1), emp.ID)
	assert.Equal(t, "ana@x.com", emp.Email)
}

func TestRepo_EmployeeByID_NotFound(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectQuery("FROM employees").WithArgs(anyN(1)...).WillReturnError(pgx.ErrNoRows)

	_, err := r.EmployeeByID(context.Background(), 99)
	assert.ErrorIs(t, err, ErrNotFound)
}

func TestRepo_FirstActiveEmployeeIDByRoleExcluding(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectQuery("FROM employees").WithArgs(anyN(2)...).
		WillReturnRows(pgxmock.NewRows([]string{"id"}).AddRow(int64(7)))

	id, err := r.FirstActiveEmployeeIDByRoleExcluding(context.Background(), "BASIC", 1)
	require.NoError(t, err)
	assert.Equal(t, int64(7), id)
}

func TestRepo_ClientByEmail(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectQuery("FROM clients").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows(clientCols).AddRow(clientRow()...))

	c, err := r.ClientByEmail(context.Background(), "m@x.com")
	require.NoError(t, err)
	assert.Equal(t, int64(1), c.ID)
}

func TestRepo_ClientByID(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectQuery("FROM clients").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows(clientCols).AddRow(clientRow()...))

	c, err := r.ClientByID(context.Background(), 1)
	require.NoError(t, err)
	assert.Equal(t, "m@x.com", c.Email)
}

// ---------------------------------------------------------------------------
// Query (multi-row) methods
// ---------------------------------------------------------------------------

func TestRepo_EmployeePermissions_FromDB(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectQuery("FROM zaposlen_permissions").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows([]string{"permission"}).AddRow("PERM_A").AddRow("PERM_B"))

	perms := r.EmployeePermissions(context.Background(), 1, "BASIC")
	assert.Equal(t, []string{"PERM_A", "PERM_B"}, perms)
}

func TestRepo_EmployeePermissions_FallbackOnError(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectQuery("FROM zaposlen_permissions").WithArgs(anyN(1)...).WillReturnError(errors.New("boom"))

	perms := r.EmployeePermissions(context.Background(), 1, "BASIC")
	assert.NotNil(t, perms) // falls back to role-derived defaults
}

func TestRepo_ClientPermissions_FromDB(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectQuery("FROM client_permissions").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows([]string{"permission"}).AddRow("CLIENT_OTC_TRADE"))

	perms := r.ClientPermissions(context.Background(), 1, "CLIENT_BASIC")
	assert.Equal(t, []string{"CLIENT_OTC_TRADE"}, perms)
}

func TestRepo_OTCTradingClientIDs(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectQuery("FROM clients").
		WillReturnRows(pgxmock.NewRows([]string{"id"}).AddRow(int64(3)).AddRow(int64(5)))

	ids, err := r.OTCTradingClientIDs(context.Background())
	require.NoError(t, err)
	assert.Equal(t, []int64{3, 5}, ids)
}

// ---------------------------------------------------------------------------
// Exec methods
// ---------------------------------------------------------------------------

func TestRepo_ResetEmployeeLoginFailures(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectExec("UPDATE employees").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	require.NoError(t, r.ResetEmployeeLoginFailures(context.Background(), 1))
}

func TestRepo_RegisterFailedEmployeeLogin_LocksAtMax(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectExec("UPDATE employees").WithArgs(anyN(3)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	emp := Employee{ID: 1, FailedLoginAttempts: 2}
	require.NoError(t, r.RegisterFailedEmployeeLogin(context.Background(), emp, 3, time.Minute))
}

func TestRepo_DeleteRefreshToken(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectExec("refresh_tokens").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	require.NoError(t, r.DeleteRefreshToken(context.Background(), "tok"))
}

func TestRepo_AddClientMarginPermission(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectExec("client_permissions").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("INSERT", 1))
	require.NoError(t, r.AddClientMarginPermission(context.Background(), 1))
}

func TestRepo_SoftDeleteEmployee(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectExec("UPDATE employees").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	require.NoError(t, r.SoftDeleteEmployee(context.Background(), 1))
}

func TestRepo_SoftDeleteClient(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectExec("UPDATE clients").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	require.NoError(t, r.SoftDeleteClient(context.Background(), 1))
}

// ---------------------------------------------------------------------------
// Transaction methods
// ---------------------------------------------------------------------------

func TestRepo_ReplaceEmployeePermissions(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectBegin()
	mock.ExpectExec("DELETE FROM zaposlen_permissions").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("DELETE", 1))
	mock.ExpectExec("INSERT INTO zaposlen_permissions").WithArgs(anyN(2)...).WillReturnResult(pgxmock.NewResult("INSERT", 1))
	mock.ExpectCommit()

	require.NoError(t, r.ReplaceEmployeePermissions(context.Background(), 1, []string{"PERM_A"}))
}

func TestRepo_ReplaceEmployeePermissions_DeleteFails_Rollback(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectBegin()
	mock.ExpectExec("DELETE FROM zaposlen_permissions").WithArgs(anyN(1)...).WillReturnError(errors.New("boom"))
	mock.ExpectRollback()

	assert.Error(t, r.ReplaceEmployeePermissions(context.Background(), 1, []string{"PERM_A"}))
}

func TestRepo_CreateEmployee(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectBegin()
	mock.ExpectQuery("INSERT INTO employees").WithArgs(anyN(12)...).
		WillReturnRows(pgxmock.NewRows([]string{"id"}).AddRow(int64(1)))
	mock.ExpectExec("INSERT INTO zaposlen_permissions").WithArgs(anyN(2)...).WillReturnResult(pgxmock.NewResult("INSERT", 1))
	mock.ExpectCommit()
	mock.ExpectQuery("FROM employees").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows(employeeCols).AddRow(employeeRow()...))

	emp, err := r.CreateEmployee(context.Background(), EmployeeCreateRequest{
		Ime: "Ana", Prezime: "Anic", DatumRodjenja: "1990-01-01", Pol: "F",
		Email: "ana@x.com", Username: "ana", Pozicija: "Teller", Departman: "Retail",
	}, []string{"PERM_A"})
	require.NoError(t, err)
	assert.Equal(t, int64(1), emp.ID)
}

func TestRepo_CreateEmployee_BadDOB(t *testing.T) {
	r, _ := newRepoMock(t)
	_, err := r.CreateEmployee(context.Background(), EmployeeCreateRequest{DatumRodjenja: "not-a-date"}, nil)
	assert.ErrorIs(t, err, ErrBadRequest)
}
