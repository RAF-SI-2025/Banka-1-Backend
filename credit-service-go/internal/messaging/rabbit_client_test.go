package messaging

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNewRabbitClient_ErrorDisables(t *testing.T) {
	t.Setenv("RABBITMQ_HOST", "127.0.0.1")
	t.Setenv("RABBITMQ_PORT", "1")

	client, err := NewRabbitClient()
	require.Error(t, err)
	assert.False(t, client.enabled)
	client.Close()
}

func TestRabbitClient_PublishJSON_Disabled(t *testing.T) {
	client := &RabbitClient{enabled: false}

	err := client.PublishJSON(context.Background(), "routing", map[string]string{"a": "b"})
	require.NoError(t, err)
}

func TestGetenv_Fallback(t *testing.T) {
	value := getenv("MISSING_ENV", "fallback")
	assert.Equal(t, "fallback", value)
}
