package user

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestEmployeeLogoutHandler_Success_Returns204(t *testing.T) {
	h := testHandlers(&mockRepo{})
	req := httptest.NewRequest(http.MethodDelete, "/employees/auth/logout", jsonBody(LogoutRequest{RefreshToken: "tok"}))
	w := httptest.NewRecorder()
	h.employeeLogout(w, req)
	assert.Equal(t, http.StatusNoContent, w.Code)
}

func TestEmployeeLogoutHandler_BadJSON_Returns400(t *testing.T) {
	h := testHandlers(&mockRepo{})
	req := httptest.NewRequest(http.MethodDelete, "/employees/auth/logout", jsonBody("not-an-object"))
	w := httptest.NewRecorder()
	h.employeeLogout(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestDeleteEmployeeHandler_Success_Returns204(t *testing.T) {
	h := testHandlers(&mockRepo{})
	req := httptest.NewRequest(http.MethodDelete, "/employees/employees/5", nil)
	w := httptest.NewRecorder()
	h.deleteEmployee(w, req)
	assert.Equal(t, http.StatusNoContent, w.Code)
}

func TestDeleteEmployeeHandler_InvalidID_Returns400(t *testing.T) {
	h := testHandlers(&mockRepo{})
	req := httptest.NewRequest(http.MethodDelete, "/employees/employees/abc", nil)
	w := httptest.NewRecorder()
	h.deleteEmployee(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestAddMarginPermissionHandler_Success_Returns200(t *testing.T) {
	h := testHandlers(&mockRepo{})
	req := httptest.NewRequest(http.MethodPut, "/clients/customers/margin/5", nil)
	w := httptest.NewRecorder()
	h.addMarginPermission(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
}

func TestDeleteClientHandler_Success_Returns204(t *testing.T) {
	h := testHandlers(&mockRepo{})
	req := httptest.NewRequest(http.MethodDelete, "/clients/customers/5", nil)
	w := httptest.NewRecorder()
	h.deleteClient(w, req)
	assert.Equal(t, http.StatusNoContent, w.Code)
}

func TestUpdateEmployeeHandler_Success_Returns200(t *testing.T) {
	repo := &mockRepo{updateEmployeeResult: Employee{ID: 5, Email: "e@x.com"}}
	h := testHandlers(repo)
	req := httptest.NewRequest(http.MethodPut, "/employees/employees/5", jsonBody(EmployeeUpdateRequest{}))
	w := httptest.NewRecorder()
	h.updateEmployee(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
}

func TestEmployeeActivateHandler_Success_Returns200(t *testing.T) {
	h := testHandlers(&mockRepo{})
	req := httptest.NewRequest(http.MethodPost, "/employees/auth/activate",
		jsonBody(ActivateRequest{ID: 5, ConfirmationToken: "tok", Token: "tok", Password: "NewPass123"}))
	w := httptest.NewRecorder()
	h.employeeActivate(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
}
