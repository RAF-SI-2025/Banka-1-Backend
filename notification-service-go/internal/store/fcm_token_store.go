package store

import (
	"context"
	"errors"
	"fmt"
	"time"

	"Banka1Back/notification-service-go/internal/model"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

// pgxPool is the subset of *pgxpool.Pool used by the stores. Declaring it as an
// interface lets unit tests inject a mock pool; *pgxpool.Pool satisfies it.
type pgxPool interface {
	Begin(ctx context.Context) (pgx.Tx, error)
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
}

type FcmTokenStore struct {
	db pgxPool
}

func NewFcmTokenStore(db *pgxpool.Pool) *FcmTokenStore {
	return &FcmTokenStore{db: db}
}

// Upsert inserts or updates the FCM token for the given client.
func (s *FcmTokenStore) Upsert(ctx context.Context, token *model.FcmToken) error {
	token.UpdatedAt = time.Now().UTC()
	_, err := s.db.Exec(ctx, `
		INSERT INTO fcm_tokens (client_id, fcm_token, updated_at)
		VALUES ($1, $2, $3)
		ON CONFLICT (client_id)
		DO UPDATE SET fcm_token = EXCLUDED.fcm_token,
		              updated_at = EXCLUDED.updated_at`,
		token.ClientId, token.Token, token.UpdatedAt,
	)
	if err != nil {
		return fmt.Errorf("FcmTokenStore.Upsert clientId=%d: %w", token.ClientId, err)
	}
	return nil
}

// FindByClientId retrieves the active FCM token for the given clientId.
// Returns (nil, nil) when no token is registered.
func (s *FcmTokenStore) FindByClientId(ctx context.Context, clientId int64) (*model.FcmToken, error) {
	var t model.FcmToken
	err := s.db.QueryRow(ctx, `
		SELECT id, client_id, fcm_token, updated_at
		FROM fcm_tokens
		WHERE client_id = $1`,
		clientId,
	).Scan(&t.ID, &t.ClientId, &t.Token, &t.UpdatedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("FcmTokenStore.FindByClientId %d: %w", clientId, err)
	}
	return &t, nil
}

// DeleteByClientId removes the FCM token for the given clientId.
// No-op when no matching row exists.
func (s *FcmTokenStore) DeleteByClientId(ctx context.Context, clientId int64) error {
	_, err := s.db.Exec(ctx, `DELETE FROM fcm_tokens WHERE client_id = $1`, clientId)
	if err != nil {
		return fmt.Errorf("FcmTokenStore.DeleteByClientId %d: %w", clientId, err)
	}
	return nil
}
