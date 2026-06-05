package service_test

import (
	"context"
	"sync/atomic"
	"testing"
	"time"

	"Banka1Back/credit-service-go/internal/service"

	"github.com/stretchr/testify/require"
)

func TestStartInstallmentScheduler_InitialRun(t *testing.T) {
	reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier := defaultStubs()
	svc := newService(reqRepo, loanRepo, installRepo, account, exchange, clientGw, notifier)

	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(cancel)

	service.StartInstallmentScheduler(ctx, svc)

	require.Eventually(t, func() bool {
		return atomic.LoadInt32(&installRepo.findDueCalls) > 0
	}, time.Second, 10*time.Millisecond)
}
