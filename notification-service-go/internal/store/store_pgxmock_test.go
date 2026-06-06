package store

import (
	"context"
	"testing"
	"time"

	"Banka1Back/notification-service-go/internal/model"
	"github.com/jackc/pgx/v5"
	"github.com/pashagolub/pgxmock/v4"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func newMock(t *testing.T) pgxmock.PgxPoolIface {
	t.Helper()
	m, err := pgxmock.NewPool()
	require.NoError(t, err)
	t.Cleanup(m.Close)
	return m
}

func anyN(n int) []any {
	a := make([]any, n)
	for i := range a {
		a[i] = pgxmock.AnyArg()
	}
	return a
}

var deliveryCols = []string{
	"delivery_id", "recipient_email", "subject", "body",
	"status", "notification_type", "retry_count", "max_retries",
	"last_error", "next_attempt_at", "last_attempt_at", "sent_at",
	"created_at", "updated_at",
}

func deliveryRow() []any {
	return []any{
		"d-1", "to@x.com", "subj", "body",
		model.DeliveryStatus("PENDING"), "TYPE", 0, 3,
		(*string)(nil), (*time.Time)(nil), (*time.Time)(nil), (*time.Time)(nil),
		time.Now(), time.Now(),
	}
}

// ---------------------------------------------------------------------------
// FcmTokenStore
// ---------------------------------------------------------------------------

func TestFcmTokenStore_Upsert(t *testing.T) {
	m := newMock(t)
	s := &FcmTokenStore{db: m}
	m.ExpectExec("INSERT INTO fcm_tokens").WithArgs(anyN(3)...).WillReturnResult(pgxmock.NewResult("INSERT", 1))
	require.NoError(t, s.Upsert(context.Background(), &model.FcmToken{ClientId: 1, Token: "tok"}))
}

func TestFcmTokenStore_FindByClientId_Found(t *testing.T) {
	m := newMock(t)
	s := &FcmTokenStore{db: m}
	m.ExpectQuery("FROM fcm_tokens").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows([]string{"id", "client_id", "fcm_token", "updated_at"}).
			AddRow(int64(1), int64(7), "tok", time.Now()))

	tok, err := s.FindByClientId(context.Background(), 7)
	require.NoError(t, err)
	require.NotNil(t, tok)
	assert.Equal(t, "tok", tok.Token)
}

func TestFcmTokenStore_FindByClientId_NotFound(t *testing.T) {
	m := newMock(t)
	s := &FcmTokenStore{db: m}
	m.ExpectQuery("FROM fcm_tokens").WithArgs(anyN(1)...).WillReturnError(pgx.ErrNoRows)

	tok, err := s.FindByClientId(context.Background(), 7)
	require.NoError(t, err)
	assert.Nil(t, tok)
}

func TestFcmTokenStore_DeleteByClientId(t *testing.T) {
	m := newMock(t)
	s := &FcmTokenStore{db: m}
	m.ExpectExec("DELETE FROM fcm_tokens").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("DELETE", 1))
	require.NoError(t, s.DeleteByClientId(context.Background(), 7))
}

// ---------------------------------------------------------------------------
// NotificationDeliveryStore
// ---------------------------------------------------------------------------

func TestDeliveryStore_Create(t *testing.T) {
	m := newMock(t)
	s := &NotificationDeliveryStore{db: m}
	m.ExpectExec("INSERT INTO notification_deliveries").WithArgs(anyN(8)...).WillReturnResult(pgxmock.NewResult("INSERT", 1))
	require.NoError(t, s.Create(context.Background(), &model.NotificationDelivery{DeliveryID: "d-1"}))
}

func TestDeliveryStore_PersistFailedAudit(t *testing.T) {
	m := newMock(t)
	s := &NotificationDeliveryStore{db: m}
	m.ExpectExec("INSERT INTO notification_deliveries").WithArgs(anyN(9)...).WillReturnResult(pgxmock.NewResult("INSERT", 1))
	require.NoError(t, s.PersistFailedAudit(context.Background(), &model.NotificationDelivery{DeliveryID: "d-1", Status: model.StatusFailed}))
}

func TestDeliveryStore_PersistFailedAudit_WrongStatus(t *testing.T) {
	m := newMock(t)
	s := &NotificationDeliveryStore{db: m}
	err := s.PersistFailedAudit(context.Background(), &model.NotificationDelivery{Status: model.DeliveryStatus("PENDING")})
	assert.Error(t, err)
}

func TestDeliveryStore_FindByDeliveryID_Found(t *testing.T) {
	m := newMock(t)
	s := &NotificationDeliveryStore{db: m}
	m.ExpectQuery("FROM notification_deliveries").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows(deliveryCols).AddRow(deliveryRow()...))

	d, err := s.FindByDeliveryID(context.Background(), "d-1")
	require.NoError(t, err)
	require.NotNil(t, d)
	assert.Equal(t, "d-1", d.DeliveryID)
}

func TestDeliveryStore_FindByDeliveryID_NotFound(t *testing.T) {
	m := newMock(t)
	s := &NotificationDeliveryStore{db: m}
	m.ExpectQuery("FROM notification_deliveries").WithArgs(anyN(1)...).WillReturnError(pgx.ErrNoRows)

	d, err := s.FindByDeliveryID(context.Background(), "d-1")
	require.NoError(t, err)
	assert.Nil(t, d)
}

func TestDeliveryStore_FindAllByStatus(t *testing.T) {
	m := newMock(t)
	s := &NotificationDeliveryStore{db: m}
	m.ExpectQuery("FROM notification_deliveries").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows(deliveryCols).AddRow(deliveryRow()...))

	list, err := s.FindAllByStatus(context.Background(), model.DeliveryStatus("PENDING"))
	require.NoError(t, err)
	assert.Len(t, list, 1)
}

func TestDeliveryStore_FindDueRetries(t *testing.T) {
	m := newMock(t)
	s := &NotificationDeliveryStore{db: m}
	m.ExpectQuery("FROM notification_deliveries").WithArgs(anyN(2)...).
		WillReturnRows(pgxmock.NewRows(deliveryCols).AddRow(deliveryRow()...))

	list, err := s.FindDueRetries(context.Background(), time.Now(), 10)
	require.NoError(t, err)
	assert.Len(t, list, 1)
}

func TestDeliveryStore_MarkProcessing_Success(t *testing.T) {
	m := newMock(t)
	s := &NotificationDeliveryStore{db: m}
	m.ExpectExec("UPDATE notification_deliveries").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	require.NoError(t, s.MarkProcessing(context.Background(), "d-1"))
}

func TestDeliveryStore_MarkProcessing_NotEligible(t *testing.T) {
	m := newMock(t)
	s := &NotificationDeliveryStore{db: m}
	m.ExpectExec("UPDATE notification_deliveries").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("UPDATE", 0))
	assert.Error(t, s.MarkProcessing(context.Background(), "d-1"))
}

func TestDeliveryStore_MarkSucceeded(t *testing.T) {
	m := newMock(t)
	s := &NotificationDeliveryStore{db: m}
	m.ExpectExec("UPDATE notification_deliveries").WithArgs(anyN(2)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	require.NoError(t, s.MarkSucceeded(context.Background(), "d-1", time.Now()))
}

func TestDeliveryStore_MarkSucceeded_NotFound(t *testing.T) {
	m := newMock(t)
	s := &NotificationDeliveryStore{db: m}
	m.ExpectExec("UPDATE notification_deliveries").WithArgs(anyN(2)...).WillReturnResult(pgxmock.NewResult("UPDATE", 0))
	assert.Error(t, s.MarkSucceeded(context.Background(), "d-1", time.Now()))
}

func TestDeliveryStore_MarkFailedOrRetry_Retry(t *testing.T) {
	m := newMock(t)
	s := &NotificationDeliveryStore{db: m}
	m.ExpectBegin()
	m.ExpectQuery("FOR UPDATE").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows([]string{"retry_count", "max_retries"}).AddRow(0, 3))
	m.ExpectExec("UPDATE notification_deliveries").WithArgs(anyN(6)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	m.ExpectCommit()

	next, err := s.MarkFailedOrRetry(context.Background(), "d-1", time.Now(), "boom", true, 60)
	require.NoError(t, err)
	assert.False(t, next.IsZero())
}

func TestDeliveryStore_MarkFailedOrRetry_Terminal(t *testing.T) {
	m := newMock(t)
	s := &NotificationDeliveryStore{db: m}
	m.ExpectBegin()
	m.ExpectQuery("FOR UPDATE").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows([]string{"retry_count", "max_retries"}).AddRow(2, 3))
	m.ExpectExec("UPDATE notification_deliveries").WithArgs(anyN(5)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	m.ExpectCommit()

	next, err := s.MarkFailedOrRetry(context.Background(), "d-1", time.Now(), "boom", false, 60)
	require.NoError(t, err)
	assert.True(t, next.IsZero())
}

func TestDeliveryStore_MarkFailedOrRetry_NotFound(t *testing.T) {
	m := newMock(t)
	s := &NotificationDeliveryStore{db: m}
	m.ExpectBegin()
	m.ExpectQuery("FOR UPDATE").WithArgs(anyN(1)...).WillReturnError(pgx.ErrNoRows)
	m.ExpectRollback()

	_, err := s.MarkFailedOrRetry(context.Background(), "d-1", time.Now(), "boom", true, 60)
	assert.Error(t, err)
}

func TestNotificationStore_Constructors(t *testing.T) {
	t.Parallel()
	assert.NotNil(t, NewFcmTokenStore(nil))
	assert.NotNil(t, NewNotificationDeliveryStore(nil))
}
