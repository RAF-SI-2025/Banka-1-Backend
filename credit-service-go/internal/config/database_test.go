package config

import (
	"testing"

	"github.com/stretchr/testify/require"
)

func TestNewDatabasePool_InvalidConnection(t *testing.T) {
	t.Setenv("CREDIT_DB_HOST", "127.0.0.1")
	t.Setenv("CREDIT_DB_PORT", "1")
	t.Setenv("CREDIT_DB_NAME", "credit")
	t.Setenv("CREDIT_DB_USER", "user")
	t.Setenv("CREDIT_DB_PASSWORD", "pass")
	t.Setenv("CREDIT_DB_SSLMODE", "disable")

	pool, err := NewDatabasePool()
	require.Error(t, err)
	require.Nil(t, pool)
}
