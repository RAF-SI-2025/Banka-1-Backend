package client_test

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/shopspring/decimal"

	"github.com/raf-si-2025/banka-1-go/saga-orchestrator-service/internal/client"
)

// closedServerURL returns the URL of an httptest server that has already been
// shut down, so any request to it fails at the transport layer (hc.Do error).
func closedServerURL(t *testing.T) string {
	t.Helper()
	srv := httptest.NewServer(http.HandlerFunc(func(http.ResponseWriter, *http.Request) {}))
	url := srv.URL
	srv.Close()
	return url
}

func TestBankingCoreClient_InternalTransfer_NetworkError(t *testing.T) {
	t.Parallel()
	c := client.NewBankingCoreClient(closedServerURL(t), testIssuer(), 2*time.Second)
	_, err := c.InternalTransfer(context.Background(), "111", "222", decimal.RequireFromString("10"), "corr-1")
	if err == nil {
		t.Fatal("expected network error")
	}
	if !errors.Is(err, client.ErrUpstream) {
		t.Errorf("expected ErrUpstream, got %v", err)
	}
}

func TestTradingServiceClient_ReserveStocks_NetworkError(t *testing.T) {
	t.Parallel()
	c := client.NewTradingServiceClient(closedServerURL(t), testIssuer(), 2*time.Second)
	_, err := c.ReserveStocks(context.Background(), 1, "AAPL", 5, "corr-2")
	if err == nil {
		t.Fatal("expected network error")
	}
}

func TestMarketServiceClient_ConvertCurrency_NetworkError(t *testing.T) {
	t.Parallel()
	c := client.NewMarketServiceClient(closedServerURL(t), testIssuer(), 2*time.Second)
	_, err := c.ConvertCurrencyNoCommission(context.Background(), "USD", "RSD", decimal.RequireFromString("100"))
	if err == nil {
		t.Fatal("expected network error")
	}
}
