package user

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"

	"banka1/user-service-go/internal/platform"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func newSvcWithTrading(repo UserRepo, tradingURL string) *Service {
	auth := platform.NewJWTService(platform.JWTConfig{
		Secret: "test-secret", Issuer: "test", IDClaim: "id",
		RolesClaim: "roles", PermissionsClaim: "permissions",
	})
	return NewService(repo, auth, &mockPub{}, defaultCfg(),
		platform.ServicesConfig{TradingURL: tradingURL}, platform.EmailConfig{})
}

func TestUpdateEmployee_SupervisorDeactivated_ReassignsFunds(t *testing.T) {
	var called bool
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		called = true
		w.WriteHeader(http.StatusOK)
	}))
	defer srv.Close()

	repo := &mockRepo{
		employeeByIDResult:  Employee{ID: 1, Role: "SUPERVISOR", Aktivan: true, Ime: "S", Prezime: "V"},
		updateEmployeeResult: Employee{ID: 1, Role: "SUPERVISOR", Email: "s@x.com", Ime: "S"},
		firstActiveIDResult: 2,
	}
	svc := newSvcWithTrading(repo, srv.URL)

	inactive := false
	_, err := svc.UpdateEmployee(context.Background(), 1, EmployeeUpdateRequest{Aktivan: &inactive})
	require.NoError(t, err)
	assert.True(t, called, "trading reassign endpoint should be called")
}

func TestUpdateEmployee_RoleChangeToMargin_ReplacesPermissions(t *testing.T) {
	repo := &mockRepo{
		employeeByIDResult:   Employee{ID: 1, Role: "BASIC", Aktivan: true},
		updateEmployeeResult: Employee{ID: 1, Role: "AGENT", Email: "a@x.com"},
	}
	svc := newSvc(repo)
	newRole := "AGENT"
	margin := true
	_, err := svc.UpdateEmployee(context.Background(), 1, EmployeeUpdateRequest{Role: &newRole, Margin: &margin})
	require.NoError(t, err)
}

func TestResolveAuditActorName_FromEmployee(t *testing.T) {
	svc := newSvc(&mockRepo{employeeByIDResult: Employee{ID: 1, Ime: "Ana", Prezime: "Anic"}})
	name := svc.resolveAuditActorName(context.Background(), platform.Principal{ID: 1})
	assert.Equal(t, "Ana Anic", name)
}

func TestResolveAuditActorName_FallbackToEmail(t *testing.T) {
	svc := newSvc(&mockRepo{employeeByIDErr: ErrNotFound})
	name := svc.resolveAuditActorName(context.Background(), platform.Principal{ID: 1, Email: "x@y.com"})
	assert.Equal(t, "x@y.com", name)
}

func TestResolveAuditActorName_FallbackToSubject(t *testing.T) {
	svc := newSvc(&mockRepo{employeeByIDErr: ErrNotFound})
	name := svc.resolveAuditActorName(context.Background(), platform.Principal{ID: 1, Subject: "svc-account"})
	assert.Equal(t, "svc-account", name)
}

func TestResolveAuditActorName_FallbackToUserID(t *testing.T) {
	svc := newSvc(&mockRepo{employeeByIDErr: ErrNotFound})
	name := svc.resolveAuditActorName(context.Background(), platform.Principal{ID: 42})
	assert.Equal(t, "USER_42", name)
}
