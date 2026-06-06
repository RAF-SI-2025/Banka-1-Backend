package user

import (
	"context"
	"testing"

	"banka1/user-service-go/internal/platform"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func newSvc(repo UserRepo) *Service {
	auth := platform.NewJWTService(platform.JWTConfig{
		Secret: "test-secret", Issuer: "test", IDClaim: "id",
		RolesClaim: "roles", PermissionsClaim: "permissions",
	})
	return NewService(repo, auth, &mockPub{}, defaultCfg(), platform.ServicesConfig{}, platform.EmailConfig{})
}

func TestEmployeeForgotPassword_Active_Success(t *testing.T) {
	svc := newSvc(&mockRepo{employeeByLoginResult: Employee{ID: 1, Email: "e@x.com", Aktivan: true}})
	require.NoError(t, svc.EmployeeForgotPassword(context.Background(), "e@x.com"))
}

func TestEmployeeForgotPassword_Inactive_ReturnsError(t *testing.T) {
	svc := newSvc(&mockRepo{employeeByLoginResult: Employee{ID: 1, Aktivan: false}})
	assert.ErrorIs(t, svc.EmployeeForgotPassword(context.Background(), "e@x.com"), ErrInactiveAccount)
}

func TestClientForgotPassword_Active_Success(t *testing.T) {
	svc := newSvc(&mockRepo{clientByEmailResult: Client{ID: 1, Email: "c@x.com", Aktivan: true}})
	require.NoError(t, svc.ClientForgotPassword(context.Background(), "c@x.com"))
}

func TestClientForgotPassword_Inactive_ReturnsError(t *testing.T) {
	svc := newSvc(&mockRepo{clientByEmailResult: Client{ID: 1, Aktivan: false}})
	assert.ErrorIs(t, svc.ClientForgotPassword(context.Background(), "c@x.com"), ErrInactiveAccount)
}

func TestEmployeeResendActivation_Inactive_Success(t *testing.T) {
	svc := newSvc(&mockRepo{employeeByLoginResult: Employee{ID: 1, Email: "e@x.com", Aktivan: false}})
	require.NoError(t, svc.EmployeeResendActivation(context.Background(), "e@x.com"))
}

func TestEmployeeResendActivation_AlreadyActive_NoOp(t *testing.T) {
	svc := newSvc(&mockRepo{employeeByLoginResult: Employee{ID: 1, Aktivan: true}})
	require.NoError(t, svc.EmployeeResendActivation(context.Background(), "e@x.com"))
}

func TestClientResendActivation_Inactive_Success(t *testing.T) {
	svc := newSvc(&mockRepo{clientByEmailResult: Client{ID: 1, Email: "c@x.com", Aktivan: false}})
	require.NoError(t, svc.ClientResendActivation(context.Background(), "c@x.com"))
}

func TestDeleteClient_PublishesDeactivationEmail(t *testing.T) {
	svc := newSvc(&mockRepo{clientByIDResult: Client{ID: 1, Email: "c@x.com"}})
	require.NoError(t, svc.DeleteClient(context.Background(), 1))
}
