package market

import (
	"context"
	"log/slog"
	"time"
	"testing"

	"banka1/market-service-go/internal/platform"
	"github.com/pashagolub/pgxmock/v4"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func testService(m pgxmock.PgxPoolIface) *Service {
	return &Service{
		cfg:    platform.Config{},
		repo:   &Repository{db: m},
		logger: slog.Default(),
	}
}

func TestService_ListStockExchanges(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	m.ExpectQuery("from stock_exchange").WillReturnRows(pgxmock.NewRows(exchangeCols).AddRow(exchangeRow()...))
	out, err := s.ListStockExchanges(context.Background())
	require.NoError(t, err)
	assert.Len(t, out, 1)
	assert.Equal(t, "NYSE", out[0].ExchangeName)
}

func TestService_ToggleStockExchangeActive(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	m.ExpectQuery("from stock_exchange").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows(exchangeCols).AddRow(exchangeRow()...))
	m.ExpectExec("update stock_exchange").WithArgs(anyN(2)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	resp, err := s.ToggleStockExchangeActive(context.Background(), 1)
	require.NoError(t, err)
	assert.False(t, resp.IsActive) // toggled from true
}

func TestService_ListPriceAlerts(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	m.ExpectQuery("from price_alerts").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows(alertCols).AddRow(alertRow()...))
	out, err := s.ListPriceAlerts(context.Background(), 5)
	require.NoError(t, err)
	assert.Len(t, out, 1)
}

func TestService_CreatePriceAlert_Success(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	m.ExpectQuery("exists").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows([]string{"exists"}).AddRow(true))
	m.ExpectQuery("insert into price_alerts").WithArgs(anyN(10)...).WillReturnRows(pgxmock.NewRows(alertCols).AddRow(alertRow()...))
	a, err := s.CreatePriceAlert(context.Background(), 5, "USER", "e@x.com", "u", 10, PriceAlertAbove, "100", "EMAIL")
	require.NoError(t, err)
	assert.Equal(t, int64(1), a.ID)
}

func TestService_CreatePriceAlert_BadInput(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	_, err := s.CreatePriceAlert(context.Background(), 5, "USER", "", "", 0, PriceAlertAbove, "100", "EMAIL")
	assert.Error(t, err)
}

func TestService_CreatePriceAlert_ListingMissing(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	m.ExpectQuery("exists").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows([]string{"exists"}).AddRow(false))
	_, err := s.CreatePriceAlert(context.Background(), 5, "USER", "", "", 10, PriceAlertAbove, "100", "EMAIL")
	assert.ErrorIs(t, err, ErrNotFound)
}

func TestService_TogglePriceAlert(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	m.ExpectQuery("update price_alerts").WithArgs(anyN(2)...).WillReturnRows(pgxmock.NewRows(alertCols).AddRow(alertRow()...))
	a, err := s.TogglePriceAlert(context.Background(), 5, 1)
	require.NoError(t, err)
	assert.Equal(t, int64(1), a.ID)
}

func TestService_DeletePriceAlert(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	m.ExpectExec("delete from price_alerts").WithArgs(anyN(2)...).WillReturnResult(pgxmock.NewResult("DELETE", 1))
	require.NoError(t, s.DeletePriceAlert(context.Background(), 5, 1))
}

func TestService_DeletePriceAlert_NotFound(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	m.ExpectExec("delete from price_alerts").WithArgs(anyN(2)...).WillReturnResult(pgxmock.NewResult("DELETE", 0))
	assert.ErrorIs(t, s.DeletePriceAlert(context.Background(), 5, 1), ErrNotFound)
}

func TestService_EvaluateActivePriceAlerts_Fires(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	// active alert ABOVE 100, not yet triggered
	row := alertRow()
	row[4] = PriceAlertCondition("ABOVE")
	row[5] = "100"
	m.ExpectQuery("from price_alerts").WillReturnRows(pgxmock.NewRows(alertCols).AddRow(row...))
	// listing with price 150 -> ABOVE satisfied
	lr := listingRow()
	lr[8] = "150" // price col
	m.ExpectQuery("from listing").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows(listingCols).AddRow(lr...))
	m.ExpectExec("update price_alerts").WithArgs(anyN(2)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))

	fired, err := s.EvaluateActivePriceAlerts(context.Background())
	require.NoError(t, err)
	assert.Equal(t, 1, fired)
}

func TestService_ListWatchlists(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	m.ExpectQuery("from watchlists").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows([]string{"id", "user_id", "name", "created_at", "count"}).AddRow(int64(1), int64(5), "WL", time.Now(), int64(0)))
	out, err := s.ListWatchlists(context.Background(), 5)
	require.NoError(t, err)
	assert.Len(t, out, 1)
}

func TestService_CreateWatchlist_Success(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	m.ExpectQuery("insert into watchlists").WithArgs(anyN(3)...).
		WillReturnRows(pgxmock.NewRows([]string{"id", "user_id", "name", "created_at"}).AddRow(int64(1), int64(5), "WL", time.Now()))
	wl, err := s.CreateWatchlist(context.Background(), 5, "WL")
	require.NoError(t, err)
	assert.Equal(t, int64(0), wl.ItemCount)
}

func TestService_CreateWatchlist_BadName(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	_, err := s.CreateWatchlist(context.Background(), 5, "   ")
	assert.Error(t, err)
}

func TestService_DeleteWatchlist(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	m.ExpectBegin()
	m.ExpectQuery("exists").WithArgs(anyN(2)...).WillReturnRows(pgxmock.NewRows([]string{"exists"}).AddRow(true))
	m.ExpectExec("delete from watchlist_items").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("DELETE", 1))
	m.ExpectExec("delete from watchlists").WithArgs(anyN(2)...).WillReturnResult(pgxmock.NewResult("DELETE", 1))
	m.ExpectCommit()
	require.NoError(t, s.DeleteWatchlist(context.Background(), 5, 1))
}

func TestService_ListWatchlistItems(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	m.ExpectQuery("exists").WithArgs(anyN(2)...).WillReturnRows(pgxmock.NewRows([]string{"exists"}).AddRow(true))
	cols := append([]string{"wi_id", "wi_watchlist_id", "wi_listing_id", "wi_added_at"}, listingCols...)
	vals := append([]any{int64(1), int64(2), int64(10), time.Now()}, listingRow()...)
	m.ExpectQuery("from watchlist_items").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows(cols).AddRow(vals...))
	out, err := s.ListWatchlistItems(context.Background(), 5, 2, nil)
	require.NoError(t, err)
	assert.Len(t, out, 1)
}

func TestService_AddWatchlistItem(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	m.ExpectQuery("exists").WithArgs(anyN(2)...).WillReturnRows(pgxmock.NewRows([]string{"exists"}).AddRow(true))
	m.ExpectQuery("exists").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows([]string{"exists"}).AddRow(true))
	m.ExpectQuery("insert into watchlist_items").WithArgs(anyN(3)...).
		WillReturnRows(pgxmock.NewRows([]string{"id", "watchlist_id", "listing_id", "added_at"}).AddRow(int64(1), int64(2), int64(10), time.Now()))
	m.ExpectQuery("from listing").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows(listingCols).AddRow(listingRow()...))
	item, err := s.AddWatchlistItem(context.Background(), 5, 2, 10)
	require.NoError(t, err)
	assert.Equal(t, int64(1), item.ID)
}

func TestService_RemoveWatchlistItem(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	m.ExpectQuery("exists").WithArgs(anyN(2)...).WillReturnRows(pgxmock.NewRows([]string{"exists"}).AddRow(true))
	m.ExpectExec("delete from watchlist_items").WithArgs(anyN(2)...).WillReturnResult(pgxmock.NewResult("DELETE", 1))
	require.NoError(t, s.RemoveWatchlistItem(context.Background(), 5, 2, 1))
}

func TestService_ListDividendData(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	m.ExpectQuery("from listing").WillReturnRows(pgxmock.NewRows([]string{"id", "ticker", "price", "currency", "dividend_yield"}).
		AddRow(int64(1), "AAPL", "150", "USD", "0.5"))
	out, err := s.ListDividendData(context.Background())
	require.NoError(t, err)
	assert.Len(t, out, 1)
}
