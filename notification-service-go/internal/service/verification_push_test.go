package service_test

import (
	"context"
	"testing"

	"Banka1Back/notification-service-go/internal/dto"
	"Banka1Back/notification-service-go/internal/model"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestHandleIncoming_VerificationOTP_SendsDataPush(t *testing.T) {
	t.Parallel()

	push := &stubPushSender{}
	token := &model.FcmToken{Token: "device-otp"}
	svc := newPushService(&stubStore{}, &stubTokenStore{token: token}, push)

	req := &dto.NotificationRequest{
		ClientID:      42,
		UserEmail:     "u@x.com",
		OperationType: "LOGIN",
		SessionID:     "sess-1",
		TemplateVariables: map[string]string{
			"code": "123456",
		},
	}

	err := svc.HandleIncoming(context.Background(), req, model.NotificationTypeVerificationOTP)
	require.NoError(t, err)
	assert.Equal(t, 1, push.dataCalls, "verification OTP should be sent as a data push")
	assert.Equal(t, "VERIFICATION_OTP", push.lastDataMap["type"])
	assert.Equal(t, "123456", push.lastDataMap["code"])
}

func TestHandleIncoming_VerificationOTP_PushSendFailure_NoError(t *testing.T) {
	t.Parallel()

	push := &stubPushSender{sendDataErr: assert.AnError}
	svc := newPushService(&stubStore{}, &stubTokenStore{token: &model.FcmToken{Token: "d"}}, push)

	req := &dto.NotificationRequest{
		ClientID:          42,
		UserEmail:         "u@x.com",
		TemplateVariables: map[string]string{"code": "999"},
	}

	err := svc.HandleIncoming(context.Background(), req, model.NotificationTypeVerificationOTP)
	require.NoError(t, err, "push failure must not propagate")
	assert.Equal(t, 1, push.dataCalls)
}
