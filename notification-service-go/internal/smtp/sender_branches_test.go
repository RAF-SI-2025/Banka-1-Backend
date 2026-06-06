package smtp

import (
	"errors"
	"net/textproto"
	"strings"
	"testing"

	"Banka1Back/notification-service-go/internal/config"
)

func TestClassifyError_AuthCode535(t *testing.T) {
	err := classifyError(&textproto.Error{Code: 535, Msg: "auth failed"})
	var authErr *MailAuthError
	if !errors.As(err, &authErr) {
		t.Fatalf("expected MailAuthError, got %T", err)
	}
}

func TestClassifyError_Permanent5xx(t *testing.T) {
	err := classifyError(&textproto.Error{Code: 550, Msg: "mailbox unavailable"})
	var permErr *PermanentSMTPError
	if !errors.As(err, &permErr) {
		t.Fatalf("expected PermanentSMTPError, got %T", err)
	}
	if permErr.Code != 550 {
		t.Errorf("code=%d", permErr.Code)
	}
}

func TestClassifyError_Transient4xx_PassThrough(t *testing.T) {
	in := &textproto.Error{Code: 452, Msg: "try later"}
	out := classifyError(in)
	if out != in {
		t.Errorf("expected pass-through for 4xx, got %v", out)
	}
}

func TestClassifyError_StringHeuristic_Auth(t *testing.T) {
	err := classifyError(errors.New("server said 535 bad credentials"))
	var authErr *MailAuthError
	if !errors.As(err, &authErr) {
		t.Fatalf("expected MailAuthError from string heuristic, got %T", err)
	}
}

func TestClassifyError_Generic_PassThrough(t *testing.T) {
	in := errors.New("some random network blip")
	if out := classifyError(in); out != in {
		t.Errorf("expected pass-through, got %v", out)
	}
}

func TestBuildRFC2822Message_ContainsHeadersAndBody(t *testing.T) {
	msg := buildRFC2822Message("from@x.com", "to@y.com", "Subj", "line1\nline2")
	for _, want := range []string{"From: from@x.com", "To: to@y.com", "Subject: Subj", "MIME-Version: 1.0", "line1\r\nline2"} {
		if !strings.Contains(msg, want) {
			t.Errorf("message missing %q\n%s", want, msg)
		}
	}
}

func TestNewSender_PopulatesFromConfig(t *testing.T) {
	s := NewSender(config.SMTPConfig{Host: "smtp.x.com", Port: 587, Username: "u@x.com", Password: "p"})
	if s == nil {
		t.Fatal("NewSender returned nil")
	}
}
