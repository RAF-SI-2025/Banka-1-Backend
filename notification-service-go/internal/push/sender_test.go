package push

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"testing"
)

type fakeRT struct {
	status int
	body   string
	err    error
}

func (f *fakeRT) RoundTrip(*http.Request) (*http.Response, error) {
	if f.err != nil {
		return nil, f.err
	}
	return &http.Response{
		StatusCode: f.status,
		Body:       io.NopCloser(strings.NewReader(f.body)),
		Header:     make(http.Header),
	}, nil
}

func testSender(rt http.RoundTripper) *FCMSender {
	return &FCMSender{projectID: "proj", client: &http.Client{Transport: rt}, log: slog.Default()}
}

func TestFCMSender_SendNotification_Success(t *testing.T) {
	s := testSender(&fakeRT{status: 200, body: "{}"})
	if err := s.SendNotification(context.Background(), "device-1", "Title", "Body"); err != nil {
		t.Fatalf("expected success, got %v", err)
	}
}

func TestFCMSender_SendData_Success(t *testing.T) {
	s := testSender(&fakeRT{status: 200, body: "{}"})
	if err := s.SendData(context.Background(), "device-1", map[string]string{"type": "OTP"}); err != nil {
		t.Fatalf("expected success, got %v", err)
	}
}

func TestFCMSender_SendData_EmptyDataInitialised(t *testing.T) {
	s := testSender(&fakeRT{status: 200, body: "{}"})
	if err := s.SendData(context.Background(), "device-1", nil); err != nil {
		t.Fatalf("expected success, got %v", err)
	}
}

func TestFCMSender_Send_Non2xx_ReturnsError(t *testing.T) {
	s := testSender(&fakeRT{status: 401, body: `{"error":"unauthenticated"}`})
	err := s.SendNotification(context.Background(), "d", "t", "b")
	if err == nil {
		t.Fatal("expected error for non-2xx status")
	}
}

func TestFCMSender_Send_TransportError(t *testing.T) {
	s := testSender(&fakeRT{err: errors.New("conn refused")})
	if err := s.SendData(context.Background(), "d", map[string]string{"a": "b"}); err == nil {
		t.Fatal("expected transport error")
	}
}

func TestFCMSender_URL(t *testing.T) {
	s := testSender(&fakeRT{status: 200})
	if !strings.Contains(s.url(), "projects/proj/messages:send") {
		t.Errorf("unexpected url: %s", s.url())
	}
}

func TestSenderConfigFromEnv_MissingCreds(t *testing.T) {
	t.Setenv("GOOGLE_APPLICATION_CREDENTIALS", "")
	if _, err := SenderConfigFromEnv(); err == nil {
		t.Fatal("expected error when creds env missing")
	}
}

func TestSenderConfigFromEnv_MissingProjectID(t *testing.T) {
	t.Setenv("GOOGLE_APPLICATION_CREDENTIALS", "/tmp/creds.json")
	t.Setenv("FCM_PROJECT_ID", "")
	if _, err := SenderConfigFromEnv(); err == nil {
		t.Fatal("expected error when project id missing")
	}
}

func TestSenderConfigFromEnv_Success(t *testing.T) {
	t.Setenv("GOOGLE_APPLICATION_CREDENTIALS", "/tmp/creds.json")
	t.Setenv("FCM_PROJECT_ID", "my-proj")
	cfg, err := SenderConfigFromEnv()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if cfg.ProjectID != "my-proj" {
		t.Errorf("got %q", cfg.ProjectID)
	}
}

func TestNewFCMSender_BadCredsFile(t *testing.T) {
	if _, err := NewFCMSender(SenderConfig{CredentialsFile: "/nonexistent/creds.json"}, slog.Default()); err == nil {
		t.Fatal("expected error reading missing creds file")
	}
}
