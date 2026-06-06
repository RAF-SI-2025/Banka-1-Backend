package httpapi

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"Banka1Back/notification-service-go/internal/model"
)

type fakeLookup struct {
	token *model.FcmToken
	err   error
}

func (f *fakeLookup) FindByClientId(_ context.Context, _ int64) (*model.FcmToken, error) {
	return f.token, f.err
}

type fakePusher struct {
	sent map[string]string
	err  error
}

func (f *fakePusher) SendData(_ context.Context, _ string, data map[string]string) error {
	f.sent = data
	return f.err
}

func testPushServer(reg TokenRegistry, lookup TokenLookup, pusher PushSender) http.Handler {
	mux := http.NewServeMux()
	NewFcmHandler(reg, nil).WithTestPush(lookup, pusher).Register(mux)
	return mux
}

func doTestPush(srv http.Handler, body string) *httptest.ResponseRecorder {
	req := httptest.NewRequest(http.MethodPost, "/notifications/fcm/test", strings.NewReader(body))
	w := httptest.NewRecorder()
	srv.ServeHTTP(w, req)
	return w
}

func TestTestPush_Success(t *testing.T) {
	lookup := &fakeLookup{token: &model.FcmToken{Token: "device-1"}}
	pusher := &fakePusher{}
	srv := testPushServer(&fakeRegistry{}, lookup, pusher)

	w := doTestPush(srv, `{"clientId":1,"type":"VERIFICATION_OTP","code":"123"}`)
	if w.Code != http.StatusOK {
		t.Fatalf("status=%d body=%s", w.Code, w.Body.String())
	}
	if pusher.sent["code"] != "123" || pusher.sent["type"] != "VERIFICATION_OTP" {
		t.Errorf("unexpected push data: %v", pusher.sent)
	}
}

func TestTestPush_DefaultType(t *testing.T) {
	pusher := &fakePusher{}
	srv := testPushServer(&fakeRegistry{}, &fakeLookup{token: &model.FcmToken{Token: "d"}}, pusher)
	w := doTestPush(srv, `{"clientId":1}`)
	if w.Code != http.StatusOK {
		t.Fatalf("status=%d", w.Code)
	}
	if pusher.sent["type"] != "VERIFICATION_OTP" {
		t.Errorf("expected default type, got %v", pusher.sent)
	}
}

func TestTestPush_NotConfigured_503(t *testing.T) {
	mux := http.NewServeMux()
	NewFcmHandler(&fakeRegistry{}, nil).Register(mux) // no WithTestPush
	w := doTestPush(mux, `{"clientId":1}`)
	if w.Code != http.StatusServiceUnavailable {
		t.Fatalf("status=%d, want 503", w.Code)
	}
}

func TestTestPush_BadJSON_400(t *testing.T) {
	srv := testPushServer(&fakeRegistry{}, &fakeLookup{}, &fakePusher{})
	w := doTestPush(srv, `{bad`)
	if w.Code != http.StatusBadRequest {
		t.Fatalf("status=%d, want 400", w.Code)
	}
}

func TestTestPush_MissingClientId_400(t *testing.T) {
	srv := testPushServer(&fakeRegistry{}, &fakeLookup{}, &fakePusher{})
	w := doTestPush(srv, `{"type":"X"}`)
	if w.Code != http.StatusBadRequest {
		t.Fatalf("status=%d, want 400", w.Code)
	}
}

func TestTestPush_NoTokenRegistered_400(t *testing.T) {
	srv := testPushServer(&fakeRegistry{}, &fakeLookup{token: nil}, &fakePusher{})
	w := doTestPush(srv, `{"clientId":1}`)
	if w.Code != http.StatusBadRequest {
		t.Fatalf("status=%d, want 400", w.Code)
	}
}

func TestTestPush_LookupError_500(t *testing.T) {
	srv := testPushServer(&fakeRegistry{}, &fakeLookup{err: errors.New("db down")}, &fakePusher{})
	w := doTestPush(srv, `{"clientId":1}`)
	if w.Code != http.StatusInternalServerError {
		t.Fatalf("status=%d, want 500", w.Code)
	}
}

func TestTestPush_SendError_502(t *testing.T) {
	srv := testPushServer(&fakeRegistry{}, &fakeLookup{token: &model.FcmToken{Token: "d"}}, &fakePusher{err: errors.New("fcm down")})
	w := doTestPush(srv, `{"clientId":1}`)
	if w.Code != http.StatusBadGateway {
		t.Fatalf("status=%d, want 502", w.Code)
	}
}
