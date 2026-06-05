package client

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestExchangeClient_Calculate(t *testing.T) {
	t.Parallel()
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "/calculate", r.URL.Path)
		w.Header().Set("Content-Type", "application/json")
		require.NoError(t, json.NewEncoder(w).Encode(ConversionResponse{
			FromCurrency: "USD",
			ToCurrency:   "RSD",
			FromAmount:   decimal.NewFromInt(10),
			ToAmount:     decimal.NewFromInt(1000),
			Rate:         decimal.NewFromFloat(100),
			Commission:   decimal.NewFromFloat(0.02),
		}))
	}))
	t.Cleanup(server.Close)

	client := &ExchangeClient{baseURL: server.URL, http: server.Client()}
	resp, err := client.Calculate("USD", "RSD", decimal.NewFromInt(10))
	require.NoError(t, err)
	assert.Equal(t, "USD", resp.FromCurrency)
	assert.True(t, resp.ToAmount.Equal(decimal.NewFromInt(1000)))
}
