package store

import (
	"testing"
)

func TestSafeJSON_EmptyReturnsNil(t *testing.T) {
	t.Parallel()
	if safeJSON(nil) != nil {
		t.Error("safeJSON(nil) should be nil")
	}
	if safeJSON([]byte{}) != nil {
		t.Error("safeJSON(empty) should be nil")
	}
}

func TestSafeJSON_NonEmptyReturnsBytes(t *testing.T) {
	t.Parallel()
	b := []byte(`{"a":1}`)
	got := safeJSON(b)
	gotBytes, ok := got.([]byte)
	if !ok || string(gotBytes) != string(b) {
		t.Errorf("safeJSON should return the original bytes, got %v", got)
	}
}

func TestNewSagaInstanceStore_Constructs(t *testing.T) {
	t.Parallel()
	if NewSagaInstanceStore(nil) == nil {
		t.Fatal("NewSagaInstanceStore returned nil")
	}
}
