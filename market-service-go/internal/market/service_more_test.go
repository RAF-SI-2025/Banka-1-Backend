package market

import (
	"context"
	"testing"
	"time"

	"github.com/pashagolub/pgxmock/v4"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// ---------------------------------------------------------------------------
// Pure helpers
// ---------------------------------------------------------------------------

func TestPureHelpers(t *testing.T) {
	t.Parallel()
	v := int64(7)
	assert.Equal(t, int64(7), valueInt64(&v))
	assert.Equal(t, int64(0), valueInt64(nil))

	v32 := int32(3)
	assert.Equal(t, int32(3), valueInt32(&v32))
	assert.Equal(t, int32(0), valueInt32(nil))

	s := "x"
	assert.Equal(t, "x", valueString(&s))
	assert.Equal(t, "", valueString(nil))

	now := time.Date(2024, 1, 2, 0, 0, 0, 0, time.UTC)
	assert.Equal(t, "2024-01-02", formatDatePtr(&now))
	assert.Equal(t, "", formatDatePtr(nil))

	assert.Equal(t, int64(5), maxInt64(0, 5))
	assert.Equal(t, int64(3), maxInt64(3, 5))

	assert.Equal(t, "fb", nonEmptyDecimal("0", "fb"))
	assert.Equal(t, "fb", nonEmptyDecimal("", "fb"))
	assert.Equal(t, "12", nonEmptyDecimal("12", "fb"))

	assert.True(t, sameDay(now, now.Add(time.Hour)))
	assert.False(t, sameDay(now, now.AddDate(0, 0, 1)))

	assert.Equal(t, 0, pageCount(10, 0))
	assert.Equal(t, 2, pageCount(15, 10))
	assert.Equal(t, 1, pageCount(10, 10))
}

// ---------------------------------------------------------------------------
// Service methods
// ---------------------------------------------------------------------------

func TestService_GetExchangeStatus(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	m.ExpectQuery("from stock_exchange").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows(exchangeCols).AddRow(exchangeRow()...))
	status, err := s.GetExchangeStatus(context.Background(), 1)
	require.NoError(t, err)
	assert.Equal(t, "NYSE", status.ExchangeName)
}

func TestService_GetListingDetails_Stock(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	detailCols := []string{
		"id", "security_id", "listing_type", "stock_exchange_id", "ticker", "name", "last_refresh",
		"price", "ask", "bid", "change", "volume", "currency",
		"exchange_name", "exchange_acronym", "exchange_mic_code", "polity",
		"outstanding_shares", "dividend_yield",
		"contract_size", "contract_unit", "settlement_date",
		"base_currency", "quote_currency", "exchange_rate", "liquidity",
		"so_id", "so_ticker", "option_type", "strike_price", "implied_volatility", "open_interest",
		"last_price", "ask2", "bid2", "volume2", "settlement_date2",
	}
	shares := int64(1000)
	dy := "0.5"
	detailVals := []any{
		int64(1), int64(10), ListingType("STOCK"), int64(2), "AAPL", "Apple", time.Now(),
		"150", "151", "149", "1.5", int64(1000), "USD",
		"NYSE", "NY", "XNYS", "US",
		&shares, &dy,
		(*int32)(nil), (*string)(nil), (*time.Time)(nil),
		(*string)(nil), (*string)(nil), (*string)(nil), (*string)(nil),
		(*int64)(nil), (*string)(nil), (*string)(nil), (*string)(nil), (*string)(nil), (*int32)(nil),
		(*string)(nil), (*string)(nil), (*string)(nil), (*int64)(nil), (*time.Time)(nil),
	}
	m.ExpectQuery("from listing").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows(detailCols).AddRow(detailVals...))
	m.ExpectQuery("listing_daily_price_info").WithArgs(anyN(2)...).
		WillReturnRows(pgxmock.NewRows([]string{"date", "price", "ask", "bid", "change", "volume"}).AddRow(time.Now(), "150", "151", "149", "1.5", int64(100)))
	m.ExpectQuery("from stock_option").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows([]string{"id", "ticker", "option_type", "strike_price", "implied_volatility", "open_interest", "last_price", "ask", "bid", "volume", "settlement_date"}))

	resp, err := s.GetListingDetails(context.Background(), 1, "1m")
	require.NoError(t, err)
	assert.Equal(t, "AAPL", resp.Ticker)
	require.NotNil(t, resp.StockDetails)
	assert.Equal(t, int64(1000), resp.StockDetails.OutstandingShares)
	assert.Len(t, resp.PriceHistory, 1)
}

func TestService_ListListings(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	cols := append([]string{}, append(listingCols, "settlement_date")...)
	m.ExpectQuery("count").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows([]string{"count"}).AddRow(int64(1)))
	m.ExpectQuery("from listing").WithArgs(anyN(3)...).WillReturnRows(pgxmock.NewRows(cols).AddRow(listingRowWithSettlement()...))
	resp, err := s.ListListings(context.Background(), ListingTypeStock, ListingFilter{}, 0, 10, "", "")
	require.NoError(t, err)
	assert.Equal(t, int64(1), resp.TotalElements)
	assert.Len(t, resp.Content, 1)
	assert.Equal(t, "AAPL", resp.Content[0].Ticker)
}
