package store

import (
	"context"
	"errors"
	"fmt"
	"time"

	"Banka1Back/notification-service-go/internal/model"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type NotificationDeliveryStore struct {
	db pgxPool
}

func NewNotificationDeliveryStore(db *pgxpool.Pool) *NotificationDeliveryStore {
	return &NotificationDeliveryStore{db: db}
}

// Create persists a new PENDING delivery record.
func (s *NotificationDeliveryStore) Create(ctx context.Context, d *model.NotificationDelivery) error {
	_, err := s.db.Exec(ctx, `
		INSERT INTO notification_deliveries (
			delivery_id, recipient_email, subject, body,
			status, notification_type,
			retry_count, max_retries,
			created_at, updated_at
		) VALUES (
			$1, $2, $3, $4,
			$5, $6,
			$7, $8,
			NOW(), NOW()
		)`,
		d.DeliveryID, d.RecipientEmail, d.Subject, d.Body,
		string(d.Status), d.NotificationType,
		d.AttemptCount, d.MaxRetries,
	)
	if err != nil {
		return fmt.Errorf("Create delivery: %w", err)
	}
	return nil
}

// PersistFailedAudit inserts a terminal FAILED record without initiating retry logic.
func (s *NotificationDeliveryStore) PersistFailedAudit(ctx context.Context, d *model.NotificationDelivery) error {
	if d.Status != model.StatusFailed {
		return fmt.Errorf("PersistFailedAudit: delivery must have status FAILED, got %q", d.Status)
	}
	_, err := s.db.Exec(ctx, `
		INSERT INTO notification_deliveries (
			delivery_id, recipient_email, subject, body,
			status, notification_type,
			retry_count, max_retries, last_error,
			created_at, updated_at
		) VALUES (
			$1, $2, $3, $4,
			$5, $6,
			$7, $8, $9,
			NOW(), NOW()
		)`,
		d.DeliveryID, d.RecipientEmail, d.Subject, d.Body,
		string(d.Status), d.NotificationType,
		d.AttemptCount, d.MaxRetries, d.LastError,
	)
	if err != nil {
		return fmt.Errorf("PersistFailedAudit: %w", err)
	}
	return nil
}

// FindByDeliveryID retrieves a delivery by its UUID primary key.
// Returns (nil, nil) when the record does not exist.
func (s *NotificationDeliveryStore) FindByDeliveryID(ctx context.Context, deliveryID string) (*model.NotificationDelivery, error) {
	var d model.NotificationDelivery
	err := s.db.QueryRow(ctx, `
		SELECT delivery_id, recipient_email, subject, body,
		       status, notification_type,
		       retry_count, max_retries,
		       last_error, next_attempt_at, last_attempt_at, sent_at,
		       created_at, updated_at
		FROM notification_deliveries
		WHERE delivery_id = $1`,
		deliveryID,
	).Scan(
		&d.DeliveryID, &d.RecipientEmail, &d.Subject, &d.Body,
		&d.Status, &d.NotificationType,
		&d.AttemptCount, &d.MaxRetries,
		&d.LastError, &d.NextAttemptAt, &d.LastAttemptAt, &d.SentAt,
		&d.CreatedAt, &d.UpdatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("FindByDeliveryID %q: %w", deliveryID, err)
	}
	return &d, nil
}

// FindAllByStatus returns all deliveries in the given lifecycle status, oldest first.
func (s *NotificationDeliveryStore) FindAllByStatus(ctx context.Context, status model.DeliveryStatus) ([]*model.NotificationDelivery, error) {
	rows, err := s.db.Query(ctx, `
		SELECT delivery_id, recipient_email, subject, body,
		       status, notification_type,
		       retry_count, max_retries,
		       last_error, next_attempt_at, last_attempt_at, sent_at,
		       created_at, updated_at
		FROM notification_deliveries
		WHERE status = $1
		ORDER BY created_at ASC`,
		string(status),
	)
	if err != nil {
		return nil, fmt.Errorf("FindAllByStatus %q: %w", status, err)
	}
	defer rows.Close()
	return scanDeliveries(rows)
}

// FindDueRetries returns up to limit RETRY_SCHEDULED deliveries whose
// nextAttemptAt <= now, ordered by nextAttemptAt ASC.
func (s *NotificationDeliveryStore) FindDueRetries(ctx context.Context, now time.Time, limit int) ([]*model.NotificationDelivery, error) {
	rows, err := s.db.Query(ctx, `
		SELECT delivery_id, recipient_email, subject, body,
		       status, notification_type,
		       retry_count, max_retries,
		       last_error, next_attempt_at, last_attempt_at, sent_at,
		       created_at, updated_at
		FROM notification_deliveries
		WHERE status = 'RETRY_SCHEDULED'
		  AND next_attempt_at <= $1
		  AND retry_count < max_retries
		ORDER BY next_attempt_at ASC
		LIMIT $2`,
		now.UTC(), limit,
	)
	if err != nil {
		return nil, fmt.Errorf("FindDueRetries: %w", err)
	}
	defer rows.Close()
	return scanDeliveries(rows)
}

// MarkProcessing atomically transitions a delivery from PENDING or RETRY_SCHEDULED
// to PROCESSING. Returns ErrDeliveryNotEligible when the row was not updated.
func (s *NotificationDeliveryStore) MarkProcessing(ctx context.Context, deliveryID string) error {
	tag, err := s.db.Exec(ctx, `
		UPDATE notification_deliveries
		SET status = 'PROCESSING', updated_at = NOW()
		WHERE delivery_id = $1
		  AND status IN ('PENDING', 'RETRY_SCHEDULED')
		  AND retry_count < max_retries`,
		deliveryID,
	)
	if err != nil {
		return fmt.Errorf("MarkProcessing %q: %w", deliveryID, err)
	}
	if tag.RowsAffected() == 0 {
		return &model.ErrDeliveryNotEligible{
			DeliveryID: deliveryID,
			Reason:     "record not found, already terminal, or retry budget exhausted",
		}
	}
	return nil
}

// MarkSucceeded transitions a delivery to SUCCEEDED.
func (s *NotificationDeliveryStore) MarkSucceeded(ctx context.Context, deliveryID string, attemptedAt time.Time) error {
	tag, err := s.db.Exec(ctx, `
		UPDATE notification_deliveries
		SET status          = 'SUCCEEDED',
		    sent_at         = $2,
		    last_attempt_at = $2,
		    retry_count     = retry_count + 1,
		    updated_at      = NOW()
		WHERE delivery_id = $1`,
		deliveryID, attemptedAt.UTC(),
	)
	if err != nil {
		return fmt.Errorf("MarkSucceeded %q: %w", deliveryID, err)
	}
	if tag.RowsAffected() == 0 {
		return &model.ErrDeliveryNotFound{DeliveryID: deliveryID}
	}
	return nil
}

// MarkFailedOrRetry increments the attempt counter and resolves the next status:
//   - RETRY_SCHEDULED when retryable=true and budget remains.
//   - FAILED (terminal) in all other cases.
//
// Returns the computed nextAttemptAt for RETRY_SCHEDULED outcomes, zero for FAILED.
func (s *NotificationDeliveryStore) MarkFailedOrRetry(
	ctx context.Context,
	deliveryID string,
	attemptedAt time.Time,
	errMsg string,
	retryable bool,
	retryDelaySecs int,
) (time.Time, error) {
	tx, err := s.db.Begin(ctx)
	if err != nil {
		return time.Time{}, fmt.Errorf("MarkFailedOrRetry begin: %w", err)
	}
	defer tx.Rollback(ctx)

	var attemptCount, maxRetries int
	err = tx.QueryRow(ctx, `
		SELECT retry_count, max_retries
		FROM notification_deliveries
		WHERE delivery_id = $1
		FOR UPDATE`,
		deliveryID,
	).Scan(&attemptCount, &maxRetries)
	if errors.Is(err, pgx.ErrNoRows) {
		return time.Time{}, &model.ErrDeliveryNotFound{DeliveryID: deliveryID}
	}
	if err != nil {
		return time.Time{}, fmt.Errorf("MarkFailedOrRetry lock %q: %w", deliveryID, err)
	}

	newAttemptCount := attemptCount + 1
	trimmedErr := model.TrimError(errMsg)

	var nextAttemptAt time.Time
	var newStatus string

	if retryable && newAttemptCount < maxRetries {
		nextAttemptAt = time.Now().UTC().Add(time.Duration(retryDelaySecs) * time.Second)
		newStatus = string(model.StatusRetryScheduled)
		_, err = tx.Exec(ctx, `
			UPDATE notification_deliveries
			SET status          = $2,
			    retry_count     = $3,
			    last_error      = $4,
			    last_attempt_at = $5,
			    next_attempt_at = $6,
			    updated_at      = NOW()
			WHERE delivery_id = $1`,
			deliveryID, newStatus, newAttemptCount, trimmedErr,
			attemptedAt.UTC(), nextAttemptAt,
		)
	} else {
		newStatus = string(model.StatusFailed)
		_, err = tx.Exec(ctx, `
			UPDATE notification_deliveries
			SET status          = $2,
			    retry_count     = $3,
			    last_error      = $4,
			    last_attempt_at = $5,
			    updated_at      = NOW()
			WHERE delivery_id = $1`,
			deliveryID, newStatus, newAttemptCount, trimmedErr, attemptedAt.UTC(),
		)
	}
	if err != nil {
		return time.Time{}, fmt.Errorf("MarkFailedOrRetry update %q: %w", deliveryID, err)
	}

	if err := tx.Commit(ctx); err != nil {
		return time.Time{}, fmt.Errorf("MarkFailedOrRetry commit %q: %w", deliveryID, err)
	}

	return nextAttemptAt, nil
}

// scanDeliveries scans all rows into a slice of NotificationDelivery pointers.
func scanDeliveries(rows pgx.Rows) ([]*model.NotificationDelivery, error) {
	var deliveries []*model.NotificationDelivery
	for rows.Next() {
		var d model.NotificationDelivery
		err := rows.Scan(
			&d.DeliveryID, &d.RecipientEmail, &d.Subject, &d.Body,
			&d.Status, &d.NotificationType,
			&d.AttemptCount, &d.MaxRetries,
			&d.LastError, &d.NextAttemptAt, &d.LastAttemptAt, &d.SentAt,
			&d.CreatedAt, &d.UpdatedAt,
		)
		if err != nil {
			return nil, err
		}
		deliveries = append(deliveries, &d)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return deliveries, nil
}
