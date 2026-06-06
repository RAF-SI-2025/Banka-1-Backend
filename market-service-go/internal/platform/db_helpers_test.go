package platform

import (
	"strings"
	"testing"
)

func TestDatabaseURLFor_BuildsDSN(t *testing.T) {
	t.Parallel()
	cfg := Config{DBUser: "u", DBPassword: "p", DBHost: "h", DBPort: "5432", DBSSLMode: "disable"}
	got := databaseURLFor(cfg, "mydb")
	if !strings.Contains(got, "postgres://u:p@h:5432/mydb") || !strings.Contains(got, "sslmode=disable") {
		t.Fatalf("unexpected DSN: %s", got)
	}
}

func TestQuoteIdentifier_EscapesQuotes(t *testing.T) {
	t.Parallel()
	if got := quoteIdentifier("my_db"); got != `"my_db"` {
		t.Errorf("got %s", got)
	}
	if got := quoteIdentifier(`a"b`); got != `"a""b"` {
		t.Errorf("got %s", got)
	}
}
