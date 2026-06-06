package market

import (
	"context"
	"testing"
	"time"

	"github.com/pashagolub/pgxmock/v4"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func newMock(t *testing.T) pgxmock.PgxPoolIface {
	t.Helper()
	m, err := pgxmock.NewPool()
	require.NoError(t, err)
	t.Cleanup(m.Close)
	return m
}

func anyN(n int) []any {
	a := make([]any, n)
	for i := range a {
		a[i] = pgxmock.AnyArg()
	}
	return a
}

func sp(s string) *string { return &s }

var exchangeCols = []string{
	"id", "exchange_name", "exchange_acronym", "exchange_mic_code", "polity", "currency", "time_zone",
	"open_time", "close_time", "pre_market_open_time", "pre_market_close_time",
	"post_market_open_time", "post_market_close_time", "is_active",
}

func exchangeRow() []any {
	return []any{
		int64(1), "NYSE", "NY", "XNYS", "US", "USD", "America/New_York",
		"09:30:00", "16:00:00", (*string)(nil), (*string)(nil), (*string)(nil), (*string)(nil), true,
	}
}

var listingCols = []string{
	"id", "security_id", "listing_type", "stock_exchange_id", "ticker", "name", "last_refresh",
	"exchange_mic_code", "price", "ask", "bid", "change", "volume", "currency",
}

func listingRow() []any {
	return []any{
		int64(1), int64(10), ListingType("STOCK"), int64(2), "AAPL", "Apple", time.Now(),
		"XNYS", "150", "151", "149", "1.5", int64(1000), "USD",
	}
}

func listingRowWithSettlement() []any {
	return append(listingRow(), (*time.Time)(nil))
}

var alertCols = []string{
	"id", "user_id", "recipient_type", "listing_id", "condition", "threshold",
	"notification_type", "user_email", "username", "active", "created_at", "last_triggered_at",
}

func alertRow() []any {
	return []any{
		int64(1), int64(5), "USER", int64(10), PriceAlertCondition("ABOVE"), "100",
		"PRICE_ALERT", sp("e@x.com"), sp("u"), true, time.Now(), (*time.Time)(nil),
	}
}

// ---------------------------------------------------------------------------
// Stock exchanges
// ---------------------------------------------------------------------------

func TestRepo_ListStockExchanges(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("from stock_exchange").WillReturnRows(pgxmock.NewRows(exchangeCols).AddRow(exchangeRow()...))
	out, err := r.ListStockExchanges(context.Background())
	require.NoError(t, err)
	assert.Len(t, out, 1)
}

func TestRepo_GetStockExchange(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("from stock_exchange").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows(exchangeCols).AddRow(exchangeRow()...))
	ex, err := r.GetStockExchange(context.Background(), 1)
	require.NoError(t, err)
	assert.Equal(t, "NYSE", ex.ExchangeName)
}

func TestRepo_GetStockExchangeByMIC(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("from stock_exchange").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows(exchangeCols).AddRow(exchangeRow()...))
	ex, err := r.GetStockExchangeByMIC(context.Background(), "XNYS")
	require.NoError(t, err)
	assert.Equal(t, "XNYS", ex.ExchangeMICCode)
}

func TestRepo_InsertStockExchange(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectExec("insert into stock_exchange").WithArgs(anyN(13)...).WillReturnResult(pgxmock.NewResult("INSERT", 1))
	require.NoError(t, r.InsertStockExchange(context.Background(), StockExchange{ExchangeName: "X"}))
}

func TestRepo_UpdateStockExchange(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectExec("update stock_exchange").WithArgs(anyN(13)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	require.NoError(t, r.UpdateStockExchange(context.Background(), StockExchange{ID: 1}))
}

// ---------------------------------------------------------------------------
// Listings
// ---------------------------------------------------------------------------

func TestRepo_GetListing(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("from listing").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows(listingCols).AddRow(listingRow()...))
	l, err := r.GetListing(context.Background(), 1)
	require.NoError(t, err)
	assert.Equal(t, "AAPL", l.Ticker)
}

func TestRepo_GetListingType(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("from listing").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows([]string{"listing_type"}).AddRow(ListingType("STOCK")))
	lt, err := r.GetListingType(context.Background(), 1)
	require.NoError(t, err)
	assert.Equal(t, ListingType("STOCK"), lt)
}

func TestRepo_ListListingsByType(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("count").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows([]string{"count"}).AddRow(int64(1)))
	m.ExpectQuery("from listing").WithArgs(anyN(3)...).WillReturnRows(pgxmock.NewRows(append([]string{}, append(listingCols, "settlement_date")...)).AddRow(listingRowWithSettlement()...))
	out, total, err := r.ListListingsByType(context.Background(), ListingTypeStock, ListingFilter{}, 0, 10, "", "")
	require.NoError(t, err)
	assert.Equal(t, int64(1), total)
	assert.Len(t, out, 1)
}

func TestRepo_GetListingHistory(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("listing_daily_price_info").WithArgs(anyN(2)...).
		WillReturnRows(pgxmock.NewRows([]string{"date", "price", "ask", "bid", "change", "volume"}).
			AddRow(time.Now(), "1", "1", "1", "0", int64(5)))
	out, err := r.GetListingHistory(context.Background(), 1, time.Now())
	require.NoError(t, err)
	assert.Len(t, out, 1)
}

func TestRepo_GetListingDetailsRow(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	cols := []string{
		"id", "security_id", "listing_type", "stock_exchange_id", "ticker", "name", "last_refresh",
		"price", "ask", "bid", "change", "volume", "currency",
		"exchange_name", "exchange_acronym", "exchange_mic_code", "polity",
		"outstanding_shares", "dividend_yield",
		"contract_size", "contract_unit", "settlement_date",
		"base_currency", "quote_currency", "exchange_rate", "liquidity",
		"so_id", "so_ticker", "option_type", "strike_price", "implied_volatility", "open_interest",
		"last_price", "ask2", "bid2", "volume2", "settlement_date2",
	}
	vals := []any{
		int64(1), int64(10), ListingType("STOCK"), int64(2), "AAPL", "Apple", time.Now(),
		"150", "151", "149", "1.5", int64(1000), "USD",
		"NYSE", "NY", "XNYS", "US",
		(*int64)(nil), (*string)(nil),
		(*int32)(nil), (*string)(nil), (*time.Time)(nil),
		(*string)(nil), (*string)(nil), (*string)(nil), (*string)(nil),
		(*int64)(nil), (*string)(nil), (*string)(nil), (*string)(nil), (*string)(nil), (*int32)(nil),
		(*string)(nil), (*string)(nil), (*string)(nil), (*int64)(nil), (*time.Time)(nil),
	}
	m.ExpectQuery("from listing").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows(cols).AddRow(vals...))
	row, err := r.GetListingDetailsRow(context.Background(), 1)
	require.NoError(t, err)
	assert.Equal(t, "AAPL", row.Ticker)
}

func TestRepo_ListOptionsForStock(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	cols := []string{"id", "ticker", "option_type", "strike_price", "implied_volatility", "open_interest", "last_price", "ask", "bid", "volume", "settlement_date"}
	m.ExpectQuery("from stock_option").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows(cols).AddRow(int64(1), "AAPL-C", "CALL", "150", "0.2", int32(10), "5", "5.1", "4.9", int64(100), time.Now()))
	out, err := r.ListOptionsForStock(context.Background(), 1)
	require.NoError(t, err)
	assert.Len(t, out, 1)
}

func TestRepo_UpdateListingSnapshot(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectExec("update listing").WithArgs(anyN(8)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	require.NoError(t, r.UpdateListingSnapshot(context.Background(), 1, "AAPL", "1", "1", "1", "0", 10, time.Now()))
}

func TestRepo_UpsertDailySnapshot(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectExec("insert into listing_daily_price_info").WithArgs(anyN(7)...).WillReturnResult(pgxmock.NewResult("INSERT", 1))
	require.NoError(t, r.UpsertDailySnapshot(context.Background(), 1, time.Now(), "1", "1", "1", "0", 10))
}

func TestRepo_ListAllListings(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("from listing").WillReturnRows(pgxmock.NewRows(listingCols).AddRow(listingRow()...))
	out, err := r.ListAllListings(context.Background())
	require.NoError(t, err)
	assert.Len(t, out, 1)
}

func TestRepo_ListStockListings(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("from listing").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows(listingCols).AddRow(listingRow()...))
	out, err := r.ListStockListings(context.Background())
	require.NoError(t, err)
	assert.Len(t, out, 1)
}

func TestRepo_UpdateForexPairRate(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectExec("update forex_pair").WithArgs(anyN(2)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	require.NoError(t, r.UpdateForexPairRate(context.Background(), 1, "1.1"))
}

func TestRepo_GetStockByTicker(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("from stock").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows([]string{"id", "ticker", "name", "outstanding_shares", "dividend_yield", "listing_id"}).
			AddRow(int64(1), "AAPL", "Apple", int64(1000), "0.5", int64(2)))
	row, err := r.GetStockByTicker(context.Background(), "AAPL")
	require.NoError(t, err)
	assert.Equal(t, "AAPL", row.Ticker)
}

func TestRepo_UpdateStockFundamentals(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectExec("update stock").WithArgs(anyN(4)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	require.NoError(t, r.UpdateStockFundamentals(context.Background(), 1, "Apple", 1000, "0.5"))
}

func TestRepo_LatestHistoryDate(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	now := time.Now()
	m.ExpectQuery("max").WillReturnRows(pgxmock.NewRows([]string{"max"}).AddRow(&now))
	_, err := r.LatestHistoryDate(context.Background())
	require.NoError(t, err)
}

func TestRepo_ListingExists(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("exists").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows([]string{"exists"}).AddRow(true))
	ok, err := r.ListingExists(context.Background(), 1)
	require.NoError(t, err)
	assert.True(t, ok)
}

func TestRepo_ListDividendData(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("from listing").WillReturnRows(pgxmock.NewRows([]string{"id", "ticker", "price", "currency", "dividend_yield"}).
		AddRow(int64(1), "AAPL", "150", "USD", "0.5"))
	out, err := r.ListDividendData(context.Background())
	require.NoError(t, err)
	assert.Len(t, out, 1)
}

// ---------------------------------------------------------------------------
// Price alerts
// ---------------------------------------------------------------------------

func TestRepo_ListPriceAlertsByUser(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("from price_alerts").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows(alertCols).AddRow(alertRow()...))
	out, err := r.ListPriceAlertsByUser(context.Background(), 5)
	require.NoError(t, err)
	assert.Len(t, out, 1)
}

func TestRepo_ListActivePriceAlerts(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("from price_alerts").WillReturnRows(pgxmock.NewRows(alertCols).AddRow(alertRow()...))
	out, err := r.ListActivePriceAlerts(context.Background())
	require.NoError(t, err)
	assert.Len(t, out, 1)
}

func TestRepo_CreatePriceAlert(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("insert into price_alerts").WithArgs(anyN(10)...).WillReturnRows(pgxmock.NewRows(alertCols).AddRow(alertRow()...))
	a, err := r.CreatePriceAlert(context.Background(), PriceAlert{UserID: 5})
	require.NoError(t, err)
	assert.Equal(t, int64(1), a.ID)
}

func TestRepo_ToggleOwnedPriceAlert(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("update price_alerts").WithArgs(anyN(2)...).WillReturnRows(pgxmock.NewRows(alertCols).AddRow(alertRow()...))
	a, err := r.ToggleOwnedPriceAlert(context.Background(), 5, 1)
	require.NoError(t, err)
	assert.Equal(t, int64(1), a.ID)
}

func TestRepo_DeleteOwnedPriceAlert(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectExec("delete from price_alerts").WithArgs(anyN(2)...).WillReturnResult(pgxmock.NewResult("DELETE", 1))
	ok, err := r.DeleteOwnedPriceAlert(context.Background(), 5, 1)
	require.NoError(t, err)
	assert.True(t, ok)
}

func TestRepo_SetPriceAlertLastTriggered(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectExec("update price_alerts").WithArgs(anyN(2)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	require.NoError(t, r.SetPriceAlertLastTriggered(context.Background(), 1, nil))
}

// ---------------------------------------------------------------------------
// Watchlists
// ---------------------------------------------------------------------------

func TestRepo_ListWatchlistsByUser(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("from watchlists").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows([]string{"id", "user_id", "name", "created_at", "count"}).AddRow(int64(1), int64(5), "WL", time.Now(), int64(2)))
	out, err := r.ListWatchlistsByUser(context.Background(), 5)
	require.NoError(t, err)
	assert.Len(t, out, 1)
}

func TestRepo_CreateWatchlist(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("insert into watchlists").WithArgs(anyN(3)...).
		WillReturnRows(pgxmock.NewRows([]string{"id", "user_id", "name", "created_at"}).AddRow(int64(1), int64(5), "WL", time.Now()))
	wl, err := r.CreateWatchlist(context.Background(), 5, "WL", time.Now())
	require.NoError(t, err)
	assert.Equal(t, int64(1), wl.ID)
}

func TestRepo_OwnedWatchlistExists(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("exists").WithArgs(anyN(2)...).WillReturnRows(pgxmock.NewRows([]string{"exists"}).AddRow(true))
	ok, err := r.OwnedWatchlistExists(context.Background(), 5, 1)
	require.NoError(t, err)
	assert.True(t, ok)
}

func TestRepo_DeleteOwnedWatchlist(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectBegin()
	m.ExpectQuery("exists").WithArgs(anyN(2)...).WillReturnRows(pgxmock.NewRows([]string{"exists"}).AddRow(true))
	m.ExpectExec("delete from watchlist_items").WithArgs(anyN(1)...).WillReturnResult(pgxmock.NewResult("DELETE", 1))
	m.ExpectExec("delete from watchlists").WithArgs(anyN(2)...).WillReturnResult(pgxmock.NewResult("DELETE", 1))
	m.ExpectCommit()
	ok, err := r.DeleteOwnedWatchlist(context.Background(), 5, 1)
	require.NoError(t, err)
	assert.True(t, ok)
}

func TestRepo_ListWatchlistItems(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	cols := append([]string{"wi_id", "wi_watchlist_id", "wi_listing_id", "wi_added_at"}, listingCols...)
	vals := append([]any{int64(1), int64(2), int64(10), time.Now()}, listingRow()...)
	m.ExpectQuery("from watchlist_items").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows(cols).AddRow(vals...))
	out, err := r.ListWatchlistItems(context.Background(), 2)
	require.NoError(t, err)
	assert.Len(t, out, 1)
}

func TestRepo_CreateWatchlistItem(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("insert into watchlist_items").WithArgs(anyN(3)...).
		WillReturnRows(pgxmock.NewRows([]string{"id", "watchlist_id", "listing_id", "added_at"}).AddRow(int64(1), int64(2), int64(10), time.Now()))
	m.ExpectQuery("from listing").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows(listingCols).AddRow(listingRow()...))
	item, err := r.CreateWatchlistItem(context.Background(), 2, 10, time.Now())
	require.NoError(t, err)
	assert.Equal(t, int64(1), item.ID)
}

func TestRepo_DeleteOwnedWatchlistItem(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectExec("delete from watchlist_items").WithArgs(anyN(2)...).WillReturnResult(pgxmock.NewResult("DELETE", 1))
	ok, err := r.DeleteOwnedWatchlistItem(context.Background(), 2, 1)
	require.NoError(t, err)
	assert.True(t, ok)
}

func TestRepo_NewRepository(t *testing.T) {
	t.Parallel()
	assert.NotNil(t, NewRepository(nil))
}

func TestRepo_IsUniqueViolation(t *testing.T) {
	t.Parallel()
	assert.False(t, IsUniqueViolation(context.DeadlineExceeded))
}
