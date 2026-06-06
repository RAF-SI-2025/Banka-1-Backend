package market

import (
	"context"
	"errors"
	"log/slog"
	"testing"
	"time"

	"banka1/market-service-go/internal/platform"
	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type fakeCache struct {
	snap   *StockPriceSnapshot
	getErr error
	setErr error
	setN   int
}

func (f *fakeCache) Get(context.Context, string) (*StockPriceSnapshot, error) {
	return f.snap, f.getErr
}
func (f *fakeCache) Set(context.Context, string, StockPriceSnapshot, time.Duration) error {
	f.setN++
	return f.setErr
}

func newFeed() *PriceFeedService {
	s := NewPriceFeedService(platform.Config{}, slog.Default())
	s.SetClient(nil)
	return s
}

func TestPriceFeed_CacheHit(t *testing.T) {
	t.Parallel()
	s := newFeed()
	want := StockPriceSnapshot{Ticker: "AAPL", CurrentPrice: decimal.RequireFromString("99")}
	s.SetCache(&fakeCache{snap: &want})
	got, ok := s.GetSingle(context.Background(), "aapl")
	require.True(t, ok)
	assert.Equal(t, "AAPL", got.Ticker)
	assert.True(t, got.CurrentPrice.Equal(decimal.RequireFromString("99")))
}

func TestPriceFeed_CacheMiss_DefaultSnapshot(t *testing.T) {
	t.Parallel()
	s := newFeed()
	s.SetCache(&fakeCache{snap: nil}) // miss; no client -> default stub snapshot
	got, ok := s.GetSingle(context.Background(), "TSLA")
	require.True(t, ok)
	assert.Equal(t, "TSLA", got.Ticker)
	assert.True(t, got.CurrentPrice.GreaterThan(decimal.Zero))
}

func TestPriceFeed_CacheGetError_FallsThrough(t *testing.T) {
	t.Parallel()
	s := newFeed()
	s.SetCache(&fakeCache{getErr: errors.New("redis down")})
	_, ok := s.GetSingle(context.Background(), "MSFT")
	assert.True(t, ok)
}

func TestPriceFeed_InMemoryCacheRoundTrip(t *testing.T) {
	t.Parallel()
	s := newFeed()
	s.SetCache(nil) // no external cache -> uses in-memory map
	first, _ := s.GetSingle(context.Background(), "NVDA")
	second, ok := s.GetSingle(context.Background(), "NVDA") // in-memory hit
	require.True(t, ok)
	assert.Equal(t, first.Ticker, second.Ticker)
}

func TestPriceFeed_GetCurrent_SkipsBlanks(t *testing.T) {
	t.Parallel()
	s := newFeed()
	s.SetCache(nil)
	out := s.GetCurrent(context.Background(), []string{"AAPL", "", "  ", "MSFT"})
	assert.Len(t, out, 2)
}

func TestPriceFeed_WriteCacheSetError_FallsBackToMemory(t *testing.T) {
	t.Parallel()
	s := newFeed()
	fc := &fakeCache{snap: nil, setErr: errors.New("write fail")}
	s.SetCache(fc)
	_, ok := s.GetSingle(context.Background(), "AMZN")
	require.True(t, ok)
	assert.Positive(t, fc.setN) // Set was attempted
}
