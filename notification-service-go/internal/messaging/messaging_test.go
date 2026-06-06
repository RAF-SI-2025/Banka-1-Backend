package messaging

import (
	"context"
	"errors"
	"log/slog"
	"testing"

	amqp091 "github.com/rabbitmq/amqp091-go"

	"Banka1Back/notification-service-go/internal/config"
	"Banka1Back/notification-service-go/internal/dto"
	"Banka1Back/notification-service-go/internal/model"
)

// ---------------------------------------------------------------------------
// Dispatcher.Handle
// ---------------------------------------------------------------------------

type fakeDelivery struct {
	incomingErr   error
	unsupportErr  error
	incomingCalls int
	unsupportCall int
}

func (f *fakeDelivery) HandleIncoming(_ context.Context, _ *dto.NotificationRequest, _ model.NotificationType) error {
	f.incomingCalls++
	return f.incomingErr
}

func (f *fakeDelivery) PersistUnsupportedAudit(_ context.Context, _ *dto.NotificationRequest, _ string) error {
	f.unsupportCall++
	return f.unsupportErr
}

func newDispatcher(d DeliveryHandler) *Dispatcher {
	return NewDispatcher(d, slog.Default())
}

func TestDispatcher_IgnoredRoutingKey_NoOp(t *testing.T) {
	fd := &fakeDelivery{}
	err := newDispatcher(fd).Handle(context.Background(), &dto.NotificationRequest{}, "card.create")
	if err != nil || fd.incomingCalls != 0 || fd.unsupportCall != 0 {
		t.Fatalf("ignored key should be a no-op; err=%v calls=%d/%d", err, fd.incomingCalls, fd.unsupportCall)
	}
}

func TestDispatcher_UnknownKey_PersistsAudit(t *testing.T) {
	fd := &fakeDelivery{}
	err := newDispatcher(fd).Handle(context.Background(), &dto.NotificationRequest{UserEmail: "e@x.com"}, "totally.unknown")
	if err != nil || fd.unsupportCall != 1 {
		t.Fatalf("unknown key should persist audit; err=%v calls=%d", err, fd.unsupportCall)
	}
}

func TestDispatcher_UnknownKey_AuditError(t *testing.T) {
	fd := &fakeDelivery{unsupportErr: errors.New("db down")}
	if err := newDispatcher(fd).Handle(context.Background(), &dto.NotificationRequest{}, "totally.unknown"); err == nil {
		t.Fatal("expected audit error to propagate")
	}
}

func TestDispatcher_KnownKey_ValidPayload_Delegates(t *testing.T) {
	fd := &fakeDelivery{}
	err := newDispatcher(fd).Handle(context.Background(), &dto.NotificationRequest{UserEmail: "e@x.com"}, "employee.created")
	if err != nil || fd.incomingCalls != 1 {
		t.Fatalf("valid known key should delegate; err=%v calls=%d", err, fd.incomingCalls)
	}
}

func TestDispatcher_KnownKey_HandleIncomingError(t *testing.T) {
	fd := &fakeDelivery{incomingErr: errors.New("smtp down")}
	if err := newDispatcher(fd).Handle(context.Background(), &dto.NotificationRequest{UserEmail: "e@x.com"}, "employee.created"); err == nil {
		t.Fatal("expected HandleIncoming error to propagate")
	}
}

func TestDispatcher_InvalidPayload_NonPushOnly_Errors(t *testing.T) {
	fd := &fakeDelivery{}
	// employee.created is not push-only; blank email fails validation -> error.
	if err := newDispatcher(fd).Handle(context.Background(), &dto.NotificationRequest{UserEmail: ""}, "employee.created"); err == nil {
		t.Fatal("expected validation error for non-push-only type")
	}
}

func TestDispatcher_InvalidPayload_PushOnly_PassesThrough(t *testing.T) {
	fd := &fakeDelivery{}
	// order.created is push-only; blank email is tolerated and still delegated.
	err := newDispatcher(fd).Handle(context.Background(), &dto.NotificationRequest{UserEmail: ""}, "order.created")
	if err != nil || fd.incomingCalls != 1 {
		t.Fatalf("push-only invalid payload should pass through; err=%v calls=%d", err, fd.incomingCalls)
	}
}

// ---------------------------------------------------------------------------
// Consumer.process
// ---------------------------------------------------------------------------

type fakeAck struct {
	acked  bool
	nacked bool
}

func (f *fakeAck) Ack(_ uint64, _ bool) error            { f.acked = true; return nil }
func (f *fakeAck) Nack(_ uint64, _ bool, _ bool) error   { f.nacked = true; return nil }
func (f *fakeAck) Reject(_ uint64, _ bool) error         { return nil }

type fakeHandler struct{ err error }

func (f *fakeHandler) Handle(_ context.Context, _ *dto.NotificationRequest, _ string) error {
	return f.err
}

func newConsumer(h MessageHandler) *Consumer {
	return NewConsumer(config.Config{}, h, slog.Default())
}

func TestConsumer_Process_Success_Acks(t *testing.T) {
	ack := &fakeAck{}
	c := newConsumer(&fakeHandler{})
	c.process(context.Background(), amqp091.Delivery{
		Acknowledger: ack,
		RoutingKey:   "employee.created",
		Body:         []byte(`{"userEmail":"e@x.com"}`),
	})
	if !ack.acked {
		t.Error("expected Ack on success")
	}
}

func TestConsumer_Process_BadJSON_Nacks(t *testing.T) {
	ack := &fakeAck{}
	c := newConsumer(&fakeHandler{})
	c.process(context.Background(), amqp091.Delivery{Acknowledger: ack, Body: []byte(`{bad json`)})
	if !ack.nacked {
		t.Error("expected Nack on bad JSON")
	}
}

func TestConsumer_Process_HandlerError_Nacks(t *testing.T) {
	ack := &fakeAck{}
	c := newConsumer(&fakeHandler{err: errors.New("boom")})
	c.process(context.Background(), amqp091.Delivery{
		Acknowledger: ack,
		RoutingKey:   "employee.created",
		Body:         []byte(`{"userEmail":"e@x.com"}`),
	})
	if !ack.nacked {
		t.Error("expected Nack on handler error")
	}
}

func TestTruncate(t *testing.T) {
	t.Parallel()
	if got := truncate("short", 10); got != "short" {
		t.Errorf("got %q", got)
	}
	if got := truncate("abcdef", 3); got != "abc…" {
		t.Errorf("got %q", got)
	}
}
