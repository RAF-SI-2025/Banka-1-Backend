package config

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// env-mutating tests can't run in parallel (t.Setenv forbids it).

func TestLoad_Defaults_WhenEnvUnset(t *testing.T) {
	// Clear the vars Load reads so defaults are exercised.
	for _, k := range []string{
		"SERVER_PORT", "SPRING_PROFILES_ACTIVE", "BANKING_CORE_DB_HOST",
		"JWT_SECRET", "BANK_CLIENT_ID", "TRANSFERS_RETRY_MAX_ATTEMPTS",
		"BANKING_CORE_GO_MIGRATIONS_ENABLED", "BANKING_CORE_GO_SEED_DEV_DATA",
		"LIQUIBASE_CONTEXTS",
	} {
		t.Setenv(k, "")
	}

	cfg := Load()
	assert.Equal(t, "8084", cfg.ServerPort)
	assert.Equal(t, "postgres", cfg.DBHost)
	assert.Equal(t, "banking_core", cfg.DBName)
	assert.Equal(t, int64(-1), cfg.BankClientID)
	assert.Equal(t, 3, cfg.TransferRetryMaxAttempts)
	assert.True(t, cfg.MigrationsEnabled)
	assert.Equal(t, time.Hour, cfg.JWTExpiration)
	assert.Equal(t, []string{"http://localhost:4200"}, cfg.CORSAllowedOrigins)
}

func TestLoad_OverridesFromEnv(t *testing.T) {
	t.Setenv("SERVER_PORT", "9999")
	t.Setenv("SPRING_PROFILES_ACTIVE", "dev,test")
	t.Setenv("BANK_CLIENT_ID", "42")
	t.Setenv("BANKA_SECURITY_CORS_ALLOWED_ORIGINS", "https://a.com, https://b.com")
	t.Setenv("BANKING_CORE_GO_MIGRATIONS_ENABLED", "false")

	cfg := Load()
	assert.Equal(t, "9999", cfg.ServerPort)
	assert.Equal(t, []string{"dev", "test"}, cfg.Profiles)
	assert.Equal(t, int64(42), cfg.BankClientID)
	assert.Equal(t, []string{"https://a.com", "https://b.com"}, cfg.CORSAllowedOrigins)
	assert.False(t, cfg.MigrationsEnabled)
}

func TestDatabaseURL_BuildsPostgresDSN(t *testing.T) {
	t.Parallel()
	cfg := Config{DBUser: "u", DBPassword: "p", DBHost: "h", DBPort: "5432", DBName: "db", DBSSLMode: "disable"}
	got := cfg.DatabaseURL()
	assert.Contains(t, got, "postgres://")
	assert.Contains(t, got, "u:p@h:5432/db")
	assert.Contains(t, got, "sslmode=disable")
}

func TestRabbitURL_BuildsAMQPDSN(t *testing.T) {
	t.Parallel()
	cfg := Config{RabbitUsername: "guest", RabbitPassword: "guest", RabbitHost: "rabbit", RabbitPort: "5672"}
	got := cfg.RabbitURL()
	assert.Contains(t, got, "amqp://")
	assert.Contains(t, got, "guest:guest@rabbit:5672")
}

func TestEnv_ReturnsValueThenFallback(t *testing.T) {
	t.Setenv("BC_TEST_ENV", "set")
	assert.Equal(t, "set", env("BC_TEST_ENV", "fb"))
	t.Setenv("BC_TEST_ENV", "")
	assert.Equal(t, "fb", env("BC_TEST_ENV", "fb"))
}

func TestFirstEnv_PrefersFirstThenSecondThenFallback(t *testing.T) {
	t.Setenv("BC_FIRST", "one")
	t.Setenv("BC_SECOND", "two")
	assert.Equal(t, "one", firstEnv("BC_FIRST", "BC_SECOND", "fb"))

	t.Setenv("BC_FIRST", "")
	assert.Equal(t, "two", firstEnv("BC_FIRST", "BC_SECOND", "fb"))

	t.Setenv("BC_SECOND", "")
	assert.Equal(t, "fb", firstEnv("BC_FIRST", "BC_SECOND", "fb"))
}

func TestEnvBool_ParsesAndFallsBack(t *testing.T) {
	t.Setenv("BC_BOOL", "true")
	assert.True(t, envBool("BC_BOOL", false))

	t.Setenv("BC_BOOL", "nonsense")
	assert.False(t, envBool("BC_BOOL", false))

	t.Setenv("BC_BOOL", "")
	assert.True(t, envBool("BC_BOOL", true))
}

func TestEnvInt64_ParsesAndFallsBack(t *testing.T) {
	t.Setenv("BC_I64", "123")
	assert.Equal(t, int64(123), envInt64("BC_I64", 0))

	t.Setenv("BC_I64", "x")
	assert.Equal(t, int64(7), envInt64("BC_I64", 7))

	t.Setenv("BC_I64", "")
	assert.Equal(t, int64(9), envInt64("BC_I64", 9))
}

func TestEnvInt_ParsesAndFallsBack(t *testing.T) {
	t.Setenv("BC_INT", "55")
	assert.Equal(t, 55, envInt("BC_INT", 0))

	t.Setenv("BC_INT", "bad")
	assert.Equal(t, 3, envInt("BC_INT", 3))
}

func TestEnvListDefault_UsesFallbackWhenEmpty(t *testing.T) {
	t.Setenv("BC_LIST", "")
	assert.Equal(t, []string{"d"}, envListDefault("BC_LIST", []string{"d"}))

	t.Setenv("BC_LIST", "a, ,b")
	assert.Equal(t, []string{"a", "b"}, envListDefault("BC_LIST", []string{"d"}))
}

func TestEnvList_EmptyReturnsNil(t *testing.T) {
	t.Setenv("BC_LIST2", "  ")
	assert.Nil(t, envList("BC_LIST2"))
}

func TestSeedDevData_ExplicitBoolWins(t *testing.T) {
	t.Setenv("BANKING_CORE_GO_SEED_DEV_DATA", "false")
	assert.False(t, seedDevData())

	t.Setenv("BANKING_CORE_GO_SEED_DEV_DATA", "true")
	assert.True(t, seedDevData())
}

func TestSeedDevData_LiquibaseContextGating(t *testing.T) {
	t.Setenv("BANKING_CORE_GO_SEED_DEV_DATA", "")

	t.Setenv("LIQUIBASE_CONTEXTS", "")
	assert.True(t, seedDevData(), "no context => run all (dev included)")

	t.Setenv("LIQUIBASE_CONTEXTS", "dev,foo")
	assert.True(t, seedDevData())

	t.Setenv("LIQUIBASE_CONTEXTS", "prod")
	assert.False(t, seedDevData())
}

func TestSeedDevData_InvalidExplicitBoolFallsThrough(t *testing.T) {
	t.Setenv("BANKING_CORE_GO_SEED_DEV_DATA", "notabool")
	t.Setenv("LIQUIBASE_CONTEXTS", "prod")
	require.False(t, seedDevData())
}
