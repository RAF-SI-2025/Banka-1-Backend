package scheduler_test

import (
	"log/slog"
	"testing"
	"time"

	"github.com/raf-si-2025/banka-1-go/saga-orchestrator-service/internal/scheduler"
)

// New is the production constructor; it just wires fields, so a nil pool/store
// is fine for this construction-only check.
func TestNew_ConstructsScheduler(t *testing.T) {
	t.Parallel()
	cfg := scheduler.CleanupConfig{
		Interval:             time.Minute,
		StuckCutoff:          time.Hour,
		IdempotencyRetention: 24 * time.Hour,
	}
	s := scheduler.New(nil, nil, cfg, slog.Default())
	if s == nil {
		t.Fatal("New returned nil")
	}
}
