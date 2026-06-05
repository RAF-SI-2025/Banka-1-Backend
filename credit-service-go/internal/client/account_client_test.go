package client

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"Banka1Back/credit-service-go/internal/model"

	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestAccountClient_GetDetails_Success(t *testing.T) {
	t.Setenv("JWT_SECRET", "secret")

	var authHeader string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		authHeader = r.Header.Get("Authorization")
		assert.Equal(t, "/internal/accounts/ACC123/details", r.URL.Path)
		w.Header().Set("Content-Type", "application/json")
		require.NoError(t, json.NewEncoder(w).Encode(AccountDetailsResponse{
			OwnerID:  7,
			Currency: model.CurrencyRSD,
			Email:    "user@bank.rs",
			Username: "user",
		}))
	}))
	t.Cleanup(server.Close)

	client := &AccountClient{baseURL: server.URL, http: server.Client()}
	resp, err := client.GetDetails("ACC123")
	require.NoError(t, err)
	assert.Equal(t, int64(7), resp.OwnerID)
	assert.NotEmpty(t, authHeader)
}

func TestAccountClient_GetDetails_ErrorStatus(t *testing.T) {
	t.Parallel()
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadRequest)
	}))
	t.Cleanup(server.Close)

	client := &AccountClient{baseURL: server.URL, http: server.Client()}
	_, err := client.GetDetails("ACC123")
	require.Error(t, err)
}

func TestAccountClient_TransactionFromBank_ErrorStatus(t *testing.T) {
	t.Parallel()
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	t.Cleanup(server.Close)

	client := &AccountClient{baseURL: server.URL, http: server.Client()}
	err := client.TransactionFromBank("ACC123", decimal.NewFromInt(100))
	require.Error(t, err)
}

func TestAccountClient_TransactionToBank_SendsPayload(t *testing.T) {
	t.Setenv("JWT_SECRET", "secret")

	var payload BankPaymentRequest
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, http.MethodPost, r.Method)
		assert.Equal(t, "/internal/accounts/transactionFromBank", r.URL.Path)
		require.NoError(t, json.NewDecoder(r.Body).Decode(&payload))
		w.WriteHeader(http.StatusOK)
	}))
	t.Cleanup(server.Close)

	client := &AccountClient{baseURL: server.URL, http: server.Client()}
	err := client.TransactionToBank("ACC-999", decimal.NewFromInt(250))
	require.NoError(t, err)
	require.NotNil(t, payload.FromBankNumber)
	assert.Equal(t, "ACC-999", *payload.FromBankNumber)
	assert.Nil(t, payload.ToBankNumber)
	assert.True(t, payload.Amount.Equal(decimal.NewFromInt(250)))
}
