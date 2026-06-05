package client

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestClientServiceClient_AddMarginPermission(t *testing.T) {
	t.Setenv("JWT_SECRET", "secret")

	var authHeader string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		authHeader = r.Header.Get("Authorization")
		assert.Equal(t, http.MethodPut, r.Method)
		assert.Equal(t, "/clients/customers/margin/42", r.URL.Path)
		w.WriteHeader(http.StatusOK)
	}))
	t.Cleanup(server.Close)

	client := &ClientServiceClient{baseURL: server.URL, http: server.Client()}
	err := client.AddMarginPermission(42)
	require.NoError(t, err)
	assert.NotEmpty(t, authHeader)
}

func TestClientServiceClient_AddMarginPermission_ErrorStatus(t *testing.T) {
	t.Parallel()
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadRequest)
	}))
	t.Cleanup(server.Close)

	client := &ClientServiceClient{baseURL: server.URL, http: server.Client()}
	err := client.AddMarginPermission(42)
	require.Error(t, err)
}
