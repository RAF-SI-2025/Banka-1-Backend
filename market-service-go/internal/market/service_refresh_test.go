package market

import (
	"context"
	"io"
	"net/http"
	"strings"
	"testing"
	"time"

	"banka1/market-service-go/internal/clients"
	"github.com/pashagolub/pgxmock/v4"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// doerFunc adapts a function to clients.HTTPDoer, branching on the AlphaVantage
// "function" query param so a single client can serve quote/daily/overview/fx.
type doerFunc func(*http.Request) (*http.Response, error)

func (f doerFunc) Do(r *http.Request) (*http.Response, error) { return f(r) }

func jsonResp(body string) *http.Response {
	return &http.Response{StatusCode: 200, Body: io.NopCloser(strings.NewReader(body)), Header: make(http.Header)}
}

const (
	bodyGlobalQuote = `{"Global Quote":{"01. symbol":"AAPL","02. open":"148","05. price":"150","06. volume":"1000","07. latest trading day":"2026-05-08","08. previous close":"149","09. change":"1","10. change percent":"0.7%"}}`
	bodyFXRate      = `{"Realtime Currency Exchange Rate":{"1. From_Currency Code":"EUR","3. To_Currency Code":"USD","5. Exchange Rate":"1.10","6. Last Refreshed":"2026-05-08 12:00:00"}}`
	bodyDaily       = `{"Time Series (Daily)":{"2026-05-08":{"4. close":"150","5. volume":"1000"}}}`
	bodyOverview    = `{"Symbol":"AAPL","Name":"Apple","SharesOutstanding":"1000","DividendYield":"0.5"}`
)

func alphaClient() *clients.AlphaVantageClient {
	return clients.NewAlphaVantageClient("https://x", "key", doerFunc(func(r *http.Request) (*http.Response, error) {
		switch r.URL.Query().Get("function") {
		case "GLOBAL_QUOTE":
			return jsonResp(bodyGlobalQuote), nil
		case "CURRENCY_EXCHANGE_RATE":
			return jsonResp(bodyFXRate), nil
		case "TIME_SERIES_DAILY":
			return jsonResp(bodyDaily), nil
		case "OVERVIEW":
			return jsonResp(bodyOverview), nil
		default:
			return jsonResp(`{}`), nil
		}
	}))
}

func listingRowType(lt ListingType, ticker string) []any {
	row := listingRow()
	row[2] = lt
	row[4] = ticker
	return row
}

// ---------------------------------------------------------------------------
// RefreshListing
// ---------------------------------------------------------------------------

func TestService_RefreshListing_Stock(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	s.SetAlphaClient(alphaClient())
	m.ExpectQuery("from listing").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows(listingCols).AddRow(listingRowType("STOCK", "AAPL")...))
	m.ExpectExec("update listing").WithArgs(anyN(8)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	m.ExpectExec("insert into listing_daily_price_info").WithArgs(anyN(7)...).WillReturnResult(pgxmock.NewResult("INSERT", 1))

	resp, err := s.RefreshListing(context.Background(), 1)
	require.NoError(t, err)
	assert.Equal(t, "AAPL", resp.Ticker)
}

func TestService_RefreshListing_Forex(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	s.SetAlphaClient(alphaClient())
	m.ExpectQuery("from listing").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows(listingCols).AddRow(listingRowType("FOREX", "EUR/USD")...))
	m.ExpectExec("update forex_pair").WithArgs(anyN(2)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	m.ExpectExec("update listing").WithArgs(anyN(8)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	m.ExpectExec("insert into listing_daily_price_info").WithArgs(anyN(7)...).WillReturnResult(pgxmock.NewResult("INSERT", 1))

	resp, err := s.RefreshListing(context.Background(), 1)
	require.NoError(t, err)
	assert.Equal(t, "EUR/USD", resp.Ticker)
}

func TestService_RefreshListing_FuturesNotSupported(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	s.SetAlphaClient(alphaClient())
	m.ExpectQuery("from listing").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows(listingCols).AddRow(listingRowType("FUTURES", "ESZ5")...))
	_, err := s.RefreshListing(context.Background(), 1)
	assert.Error(t, err)
}

// ---------------------------------------------------------------------------
// RefreshStockByTicker
// ---------------------------------------------------------------------------

func TestService_RefreshStockByTicker(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	s.SetAlphaClient(alphaClient())
	m.ExpectQuery("from stock").WithArgs(anyN(1)...).
		WillReturnRows(pgxmock.NewRows([]string{"id", "ticker", "name", "outstanding_shares", "dividend_yield", "listing_id"}).
			AddRow(int64(1), "AAPL", "Apple", int64(1000), "0.5", int64(2)))
	m.ExpectExec("update stock").WithArgs(anyN(4)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	m.ExpectExec("update listing").WithArgs(anyN(8)...).WillReturnResult(pgxmock.NewResult("UPDATE", 1))
	m.ExpectExec("insert into listing_daily_price_info").WithArgs(anyN(7)...).WillReturnResult(pgxmock.NewResult("INSERT", 1))

	resp, err := s.RefreshStockByTicker(context.Background(), "aapl")
	require.NoError(t, err)
	assert.Equal(t, "AAPL", resp.Ticker)
	assert.Equal(t, 1, resp.RefreshedDailyEntries)
}

// ---------------------------------------------------------------------------
// GetListingDetails — futures / forex / option branches
// ---------------------------------------------------------------------------

var detailCols = []string{
	"id", "security_id", "listing_type", "stock_exchange_id", "ticker", "name", "last_refresh",
	"price", "ask", "bid", "change", "volume", "currency",
	"exchange_name", "exchange_acronym", "exchange_mic_code", "polity",
	"outstanding_shares", "dividend_yield",
	"contract_size", "contract_unit", "settlement_date",
	"base_currency", "quote_currency", "exchange_rate", "liquidity",
	"so_id", "so_ticker", "option_type", "strike_price", "implied_volatility", "open_interest",
	"last_price", "ask2", "bid2", "volume2", "settlement_date2",
}

func detailVals(lt ListingType) []any {
	cs := int32(10)
	cu := "BBL"
	settle := time.Now()
	base, quote, rate, liq := "EUR", "USD", "1.1", "HIGH"
	ot, sp, iv := "CALL", "150", "0.2"
	oi := int32(5)
	lp, oask, obid := "5", "5.1", "4.9"
	ovol := int64(50)
	return []any{
		int64(1), int64(10), lt, int64(2), "TICK", "Name", time.Now(),
		"150", "151", "149", "1.5", int64(1000), "USD",
		"NYSE", "NY", "XNYS", "US",
		(*int64)(nil), (*string)(nil),
		&cs, &cu, &settle,
		&base, &quote, &rate, &liq,
		(*int64)(nil), (*string)(nil), &ot, &sp, &iv, &oi,
		&lp, &oask, &obid, &ovol, &settle,
	}
}

func TestService_GetListingDetails_Futures(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	m.ExpectQuery("from listing").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows(detailCols).AddRow(detailVals("FUTURES")...))
	m.ExpectQuery("listing_daily_price_info").WithArgs(anyN(2)...).WillReturnRows(pgxmock.NewRows([]string{"date", "price", "ask", "bid", "change", "volume"}))
	resp, err := s.GetListingDetails(context.Background(), 1, "DAY")
	require.NoError(t, err)
	require.NotNil(t, resp.FuturesDetails)
}

func TestService_GetListingDetails_Forex(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	m.ExpectQuery("from listing").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows(detailCols).AddRow(detailVals("FOREX")...))
	m.ExpectQuery("listing_daily_price_info").WithArgs(anyN(2)...).WillReturnRows(pgxmock.NewRows([]string{"date", "price", "ask", "bid", "change", "volume"}))
	resp, err := s.GetListingDetails(context.Background(), 1, "DAY")
	require.NoError(t, err)
	require.NotNil(t, resp.ForexDetails)
}

func TestService_GetListingDetails_Option(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	m.ExpectQuery("from listing").WithArgs(anyN(1)...).WillReturnRows(pgxmock.NewRows(detailCols).AddRow(detailVals("OPTION")...))
	m.ExpectQuery("listing_daily_price_info").WithArgs(anyN(2)...).WillReturnRows(pgxmock.NewRows([]string{"date", "price", "ask", "bid", "change", "volume"}))
	resp, err := s.GetListingDetails(context.Background(), 1, "DAY")
	require.NoError(t, err)
	assert.NotNil(t, resp.OptionType)
}
