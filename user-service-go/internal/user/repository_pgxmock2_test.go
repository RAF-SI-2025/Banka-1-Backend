package user

import (
	"context"
	"testing"
	"time"

	"banka1/user-service-go/internal/platform"
	"github.com/pashagolub/pgxmock/v4"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func testJMBG(t *testing.T) *platform.JMBGCrypto {
	t.Helper()
	c, err := platform.NewJMBGCrypto(platform.JMBGConfig{
		AESKeyBase64: "VGhpc0lzQURldk9ubHkzMkJ5dGVBRVNLZXktMTIzNDU=",
	})
	require.NoError(t, err)
	return c
}

// ---------------------------------------------------------------------------
// Refresh tokens & confirmations
// ---------------------------------------------------------------------------

func TestRepo_StoreEmployeeRefreshToken(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectExec("INSERT INTO refresh_tokens").WithArgs(anyN(3)...).WillReturnResult(pgxmock.NewResult("INSERT", 1))
	require.NoError(t, r.StoreEmployeeRefreshToken(context.Background(), 1, "tok", time.Now().Add(time.Hour)))
}

func TestRepo_EmployeeByRefreshToken(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectQuery("FROM refresh_tokens").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows(employeeCols).AddRow(employeeRow()...))

	emp, err := r.EmployeeByRefreshToken(context.Background(), "tok")
	require.NoError(t, err)
	assert.Equal(t, int64(1), emp.ID)
}

func TestRepo_ConfirmationIDByToken(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectQuery("FROM confirmation_token").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows([]string{"id"}).AddRow(int64(5)))

	id, err := r.ConfirmationIDByToken(context.Background(), "confirmation_token", "zaposlen_id", "tok")
	require.NoError(t, err)
	assert.Equal(t, int64(5), id)
}

func TestRepo_UpsertEmployeeConfirmation(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectExec("INSERT INTO confirmation_token").WithArgs(anyN(3)...).WillReturnResult(pgxmock.NewResult("INSERT", 1))
	require.NoError(t, r.UpsertEmployeeConfirmation(context.Background(), 1, "hash", time.Now()))
}

func TestRepo_UpsertClientConfirmation(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectExec("INSERT INTO client_confirmation_token").WithArgs(anyN(3)...).WillReturnResult(pgxmock.NewResult("INSERT", 1))
	require.NoError(t, r.UpsertClientConfirmation(context.Background(), 1, "hash", time.Now()))
}

func TestRepo_ActivateEmployeePassword(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectExec("UPDATE employees").WithArgs(anyN(3)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	mock.ExpectExec("UPDATE confirmation_token").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	require.NoError(t, r.ActivateEmployeePassword(context.Background(), 5, "hash", "pw"))
}

func TestRepo_ActivateEmployeePassword_InvalidToken(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectExec("UPDATE employees").WithArgs(anyN(3)...).WillReturnResult(pgxmock.NewResult("UPDATE", 0))
	assert.ErrorIs(t, r.ActivateEmployeePassword(context.Background(), 5, "hash", "pw"), ErrInvalidToken)
}

func TestRepo_ActivateClientPassword(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectExec("UPDATE clients").WithArgs(anyN(3)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	mock.ExpectExec("UPDATE client_confirmation_token").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	require.NoError(t, r.ActivateClientPassword(context.Background(), 5, "hash", "pw"))
}

// ---------------------------------------------------------------------------
// Search (queryEmployees / queryClients)
// ---------------------------------------------------------------------------

func TestRepo_SearchEmployees(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectQuery("COUNT").WillReturnRows(pgxmock.NewRows([]string{"count"}).AddRow(1))
	mock.ExpectQuery("FROM employees").WithArgs(anyN(2)...).
		WillReturnRows(pgxmock.NewRows(employeeCols).AddRow(employeeRow()...))

	emps, total, err := r.SearchEmployees(context.Background(), SearchQuery{Size: 10})
	require.NoError(t, err)
	assert.Equal(t, 1, total)
	assert.Len(t, emps, 1)
}

func TestRepo_SearchClients(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectQuery("COUNT").WillReturnRows(pgxmock.NewRows([]string{"count"}).AddRow(2))
	mock.ExpectQuery("FROM clients").WithArgs(anyN(2)...).
		WillReturnRows(pgxmock.NewRows(clientCols).AddRow(clientRow()...))

	clients, total, err := r.SearchClients(context.Background(), SearchQuery{Size: 10})
	require.NoError(t, err)
	assert.Equal(t, 2, total)
	assert.Len(t, clients, 1)
}

// ---------------------------------------------------------------------------
// Update / CreateClient
// ---------------------------------------------------------------------------

func TestRepo_UpdateEmployee(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectQuery("FROM employees").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows(employeeCols).AddRow(employeeRow()...))
	mock.ExpectExec("UPDATE employees").WithArgs(anyN(10)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	mock.ExpectQuery("FROM employees").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows(employeeCols).AddRow(employeeRow()...))

	newName := "Updated"
	emp, err := r.UpdateEmployee(context.Background(), 1, EmployeeUpdateRequest{Ime: &newName})
	require.NoError(t, err)
	assert.Equal(t, int64(1), emp.ID)
}

func TestRepo_UpdateClient(t *testing.T) {
	r, mock := newRepoMock(t)
	mock.ExpectQuery("FROM clients").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows(clientCols).AddRow(clientRow()...))
	mock.ExpectExec("UPDATE clients").WithArgs(anyN(7)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	mock.ExpectQuery("FROM clients").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows(clientCols).AddRow(clientRow()...))

	newEmail := "new@x.com"
	c, err := r.UpdateClient(context.Background(), 1, ClientUpdateRequest{Email: &newEmail})
	require.NoError(t, err)
	assert.Equal(t, int64(1), c.ID)
}

func TestRepo_CreateClient(t *testing.T) {
	r, mock := newRepoMock(t)
	r.jmbg = testJMBG(t)
	mock.ExpectBegin()
	mock.ExpectQuery("INSERT INTO clients").WithArgs(anyN(9)...).
		WillReturnRows(pgxmock.NewRows([]string{"id"}).AddRow(int64(1)))
	mock.ExpectExec("INSERT INTO client_permissions").WithArgs(anyN(2)...).WillReturnResult(pgxmock.NewResult("INSERT", 1))
	mock.ExpectCommit()
	mock.ExpectQuery("FROM clients").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows(clientCols).AddRow(clientRow()...))

	c, err := r.CreateClient(context.Background(), ClientCreateRequest{
		Ime: "Mark", Prezime: "Markovic", DatumRodjenja: 19900101, Pol: "M",
		Email: "m@x.com", JMBG: "0101990500006",
	}, []string{"CLIENT_OTC_TRADE"})
	require.NoError(t, err)
	assert.Equal(t, int64(1), c.ID)
}

func TestRepo_ClientByPlainJMBG(t *testing.T) {
	r, mock := newRepoMock(t)
	r.jmbg = testJMBG(t)
	mock.ExpectQuery("FROM clients").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows(clientCols).AddRow(clientRow()...))

	c, err := r.ClientByPlainJMBG(context.Background(), "0101990500006")
	require.NoError(t, err)
	assert.Equal(t, int64(1), c.ID)
}

func TestRepo_NewRepository_Constructs(t *testing.T) {
	t.Parallel()
	assert.NotNil(t, NewRepository(nil, nil))
}
