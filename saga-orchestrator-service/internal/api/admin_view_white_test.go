package api

import (
	"testing"

	"github.com/raf-si-2025/banka-1-go/saga-orchestrator-service/internal/store"
)

func TestNewAdminHandler_Constructs(t *testing.T) {
	t.Parallel()
	if NewAdminHandler(nil, nil) == nil {
		t.Fatal("NewAdminHandler returned nil")
	}
}

func TestMapInstanceToView_DecodesPayloadAndCompensationLog(t *testing.T) {
	t.Parallel()
	inst := store.SagaInstance{
		SagaType:        "OTC_EXERCISE",
		CorrelationID:   "101",
		CurrentStep:     2,
		TotalSteps:      5,
		State:           store.SagaStateInProgress,
		RetryCount:      1,
		Version:         3,
		Payload:         []byte(`{"contractId":101}`),
		CompensationLog: []byte(`{"steps":[]}`),
	}
	v := mapInstanceToView(inst)
	if v.SagaType != "OTC_EXERCISE" || v.CorrelationID != "101" {
		t.Errorf("unexpected view scalars: %+v", v)
	}
	if v.Payload == nil {
		t.Error("expected decoded Payload object, got nil")
	}
	if v.CompensationLog == nil {
		t.Error("expected decoded CompensationLog object, got nil")
	}
}

func TestMapInstanceToView_InvalidJSONLeavesNil(t *testing.T) {
	t.Parallel()
	inst := store.SagaInstance{
		Payload:         []byte("{not-json"),
		CompensationLog: []byte("also-bad"),
	}
	v := mapInstanceToView(inst)
	if v.Payload != nil || v.CompensationLog != nil {
		t.Error("invalid JSON should leave Payload/CompensationLog nil")
	}
}

func TestMapInstancesToView_MapsAll(t *testing.T) {
	t.Parallel()
	list := []store.SagaInstance{
		{CorrelationID: "1"},
		{CorrelationID: "2"},
	}
	out := mapInstancesToView(list)
	if len(out) != 2 {
		t.Fatalf("expected 2 views, got %d", len(out))
	}
}
