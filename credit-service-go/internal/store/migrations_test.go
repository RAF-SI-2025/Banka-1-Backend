package store

import (
	"context"
	"os"
	"path/filepath"
	"testing"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/stretchr/testify/require"
)

func TestRunMigrations_AppliesPending(t *testing.T) {
	t.Parallel()
	dir := t.TempDir()
	require.NoError(t, os.WriteFile(filepath.Join(dir, "001-init.sql"), []byte("SELECT 1;"), 0o600))
	require.NoError(t, os.WriteFile(filepath.Join(dir, "002-skip.sql"), []byte("SELECT 2;"), 0o600))

	existsChecks := []bool{false, true}
	queryCalls := 0
	beginCalls := 0

	tx := &stubTx{
		execFn: func(_ context.Context, _ string, _ ...any) (pgconn.CommandTag, error) {
			return pgconn.NewCommandTag("EXEC"), nil
		},
	}

	db := &stubDB{
		execFn: func(_ context.Context, _ string, _ ...any) (pgconn.CommandTag, error) {
			return pgconn.NewCommandTag("CREATE"), nil
		},
		queryRowFn: func(_ context.Context, _ string, _ ...any) pgx.Row {
			value := existsChecks[queryCalls]
			queryCalls++
			return stubRow{values: []any{value}}
		},
		beginFn: func(_ context.Context) (pgx.Tx, error) {
			beginCalls++
			return tx, nil
		},
	}

	err := RunMigrations(context.Background(), db, dir)
	require.NoError(t, err)
	require.Equal(t, 2, queryCalls)
	require.Equal(t, 1, beginCalls)
}
