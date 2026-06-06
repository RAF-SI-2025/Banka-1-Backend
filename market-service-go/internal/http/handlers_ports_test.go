package http

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	gpauth "banka1/go-platform/auth"
	"banka1/market-service-go/internal/api"
	"banka1/market-service-go/internal/fx"
	"banka1/market-service-go/internal/market"
	"banka1/market-service-go/internal/platform"
)

// ---------------------------------------------------------------------------
// Port fakes
// ---------------------------------------------------------------------------

type fakeMarket struct {
	err        error
	exchanges  []api.StockExchangeResponse
	dividends  []market.DividendData
	alerts     []market.PriceAlert
	alert      *market.PriceAlert
	watchlists []market.Watchlist
	watchlist  *market.Watchlist
	toggleResp *api.StockExchangeToggleResponse
}

func (f *fakeMarket) GetExchangeStatus(context.Context, int64) (*api.StockExchangeStatusResponse, error) {
	return &api.StockExchangeStatusResponse{}, f.err
}
func (f *fakeMarket) ListStockExchanges(context.Context) ([]api.StockExchangeResponse, error) {
	return f.exchanges, f.err
}
func (f *fakeMarket) ToggleStockExchangeActive(context.Context, int64) (*api.StockExchangeToggleResponse, error) {
	return f.toggleResp, f.err
}
func (f *fakeMarket) GetListingDetails(context.Context, int64, string) (*api.ListingDetailsResponse, error) {
	return &api.ListingDetailsResponse{}, f.err
}
func (f *fakeMarket) ListListings(context.Context, market.ListingType, market.ListingFilter, int, int, string, string) (*api.PageListingSummaryResponse, error) {
	return &api.PageListingSummaryResponse{}, f.err
}
func (f *fakeMarket) RefreshListing(context.Context, int64) (*api.ListingRefreshResponse, error) {
	return &api.ListingRefreshResponse{}, f.err
}
func (f *fakeMarket) RefreshStockByTicker(context.Context, string) (*api.StockMarketDataRefreshResponse, error) {
	return &api.StockMarketDataRefreshResponse{}, f.err
}
func (f *fakeMarket) RefreshAllStocks(context.Context) ([]api.StockMarketDataRefreshResponse, error) {
	return nil, f.err
}
func (f *fakeMarket) ImportStockExchanges(context.Context, string) (*api.StockExchangeImportResponse, error) {
	return &api.StockExchangeImportResponse{}, f.err
}
func (f *fakeMarket) ListPriceAlerts(context.Context, int64) ([]market.PriceAlert, error) {
	return f.alerts, f.err
}
func (f *fakeMarket) CreatePriceAlert(context.Context, int64, string, string, string, int64, market.PriceAlertCondition, string, string) (*market.PriceAlert, error) {
	return f.alert, f.err
}
func (f *fakeMarket) TogglePriceAlert(context.Context, int64, int64) (*market.PriceAlert, error) {
	return f.alert, f.err
}
func (f *fakeMarket) DeletePriceAlert(context.Context, int64, int64) error { return f.err }
func (f *fakeMarket) ListWatchlists(context.Context, int64) ([]market.Watchlist, error) {
	return f.watchlists, f.err
}
func (f *fakeMarket) CreateWatchlist(context.Context, int64, string) (*market.Watchlist, error) {
	return f.watchlist, f.err
}
func (f *fakeMarket) DeleteWatchlist(context.Context, int64, int64) error { return f.err }
func (f *fakeMarket) ListWatchlistItems(context.Context, int64, int64, *market.ListingType) ([]market.WatchlistItem, error) {
	return nil, f.err
}
func (f *fakeMarket) AddWatchlistItem(context.Context, int64, int64, int64) (*market.WatchlistItem, error) {
	return &market.WatchlistItem{
		ID: 1, WatchlistID: 2, ListingID: 10,
		Listing: market.Listing{ID: 10, Ticker: "AAPL", Price: "150", Ask: "151", Bid: "149", Change: "1.5"},
	}, f.err
}
func (f *fakeMarket) RemoveWatchlistItem(context.Context, int64, int64, int64) error { return f.err }
func (f *fakeMarket) ListDividendData(context.Context) ([]market.DividendData, error) {
	return f.dividends, f.err
}

type fakeFX struct {
	err   error
	rates []fx.ExchangeRate
	rate  *fx.ExchangeRate
	conv  *fx.ConversionResponse
}

func (f *fakeFX) GetRates(context.Context, string) ([]fx.ExchangeRate, error) { return f.rates, f.err }
func (f *fakeFX) GetRate(context.Context, string, string) (*fx.ExchangeRate, error) {
	return f.rate, f.err
}
func (f *fakeFX) GetRateHistory(context.Context, string, string, string) ([]fx.ExchangeRate, error) {
	return f.rates, f.err
}
func (f *fakeFX) Calculate(context.Context, string, string, string, string, bool) (*fx.ConversionResponse, error) {
	return f.conv, f.err
}
func (f *fakeFX) FetchAndStoreDailyRates(context.Context) (map[string]any, error) {
	return map[string]any{}, f.err
}

type fakeListingResolver struct {
	lt  market.ListingType
	err error
}

func (f *fakeListingResolver) GetListingType(context.Context, int64) (market.ListingType, error) {
	return f.lt, f.err
}

func portHandlers(mkt MarketServicePort, fxs FXServicePort) *Handlers {
	return NewHandlers(platform.Config{}, &App{
		Config:        platform.Config{},
		MarketService: mkt,
		FXService:     fxs,
		MarketRepo:    &fakeListingResolver{},
	})
}

func withPrincipal(r *http.Request) *http.Request {
	ctx := gpauth.WithPrincipal(r.Context(), gpauth.Principal{ID: 5, Role: "CLIENT", Email: "e@x.com", Subject: "u"})
	return r.WithContext(ctx)
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

func TestListStockExchanges_OK(t *testing.T) {
	h := portHandlers(&fakeMarket{exchanges: []api.StockExchangeResponse{{ID: 1, ExchangeName: "NYSE"}}}, &fakeFX{})
	w := httptest.NewRecorder()
	h.ListStockExchanges(w, httptest.NewRequest(http.MethodGet, "/stock-exchanges", nil))
	if w.Code != http.StatusOK || !strings.Contains(w.Body.String(), "NYSE") {
		t.Fatalf("status %d body %s", w.Code, w.Body.String())
	}
}

func TestListStockExchanges_Error(t *testing.T) {
	h := portHandlers(&fakeMarket{err: errors.New("db down")}, &fakeFX{})
	w := httptest.NewRecorder()
	h.ListStockExchanges(w, httptest.NewRequest(http.MethodGet, "/stock-exchanges", nil))
	if w.Code != http.StatusInternalServerError {
		t.Fatalf("status %d, want 500", w.Code)
	}
}

func TestDividendData_OK(t *testing.T) {
	h := portHandlers(&fakeMarket{dividends: []market.DividendData{{Ticker: "AAPL", Price: "150", Currency: "USD", DividendYield: "0.5"}}}, &fakeFX{})
	w := httptest.NewRecorder()
	h.DividendData(w, httptest.NewRequest(http.MethodGet, "/dividends", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d", w.Code)
	}
}

func TestListPriceAlerts_Unauthorized(t *testing.T) {
	h := portHandlers(&fakeMarket{}, &fakeFX{})
	w := httptest.NewRecorder()
	h.ListPriceAlerts(w, httptest.NewRequest(http.MethodGet, "/price-alerts", nil))
	if w.Code != http.StatusUnauthorized {
		t.Fatalf("status %d, want 401", w.Code)
	}
}

func TestListPriceAlerts_OK(t *testing.T) {
	h := portHandlers(&fakeMarket{alerts: []market.PriceAlert{{ID: 1, Threshold: "100"}}}, &fakeFX{})
	w := httptest.NewRecorder()
	h.ListPriceAlerts(w, withPrincipal(httptest.NewRequest(http.MethodGet, "/price-alerts", nil)))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d body %s", w.Code, w.Body.String())
	}
}

func TestCreatePriceAlert_Unauthorized(t *testing.T) {
	h := portHandlers(&fakeMarket{}, &fakeFX{})
	w := httptest.NewRecorder()
	h.CreatePriceAlert(w, httptest.NewRequest(http.MethodPost, "/price-alerts", strings.NewReader("{}")))
	if w.Code != http.StatusUnauthorized {
		t.Fatalf("status %d, want 401", w.Code)
	}
}

func TestCreatePriceAlert_BadJSON(t *testing.T) {
	h := portHandlers(&fakeMarket{}, &fakeFX{})
	w := httptest.NewRecorder()
	h.CreatePriceAlert(w, withPrincipal(httptest.NewRequest(http.MethodPost, "/price-alerts", strings.NewReader("{bad"))))
	if w.Code != http.StatusBadRequest {
		t.Fatalf("status %d, want 400", w.Code)
	}
}

func TestCreatePriceAlert_OK(t *testing.T) {
	h := portHandlers(&fakeMarket{alert: &market.PriceAlert{ID: 1, Threshold: "100"}}, &fakeFX{})
	w := httptest.NewRecorder()
	body := `{"listingId":10,"condition":"ABOVE","threshold":100,"notificationType":"EMAIL"}`
	h.CreatePriceAlert(w, withPrincipal(httptest.NewRequest(http.MethodPost, "/price-alerts", strings.NewReader(body))))
	if w.Code != http.StatusCreated {
		t.Fatalf("status %d body %s", w.Code, w.Body.String())
	}
}

func TestPriceAlertSubroutes_Toggle(t *testing.T) {
	h := portHandlers(&fakeMarket{alert: &market.PriceAlert{ID: 7, Threshold: "100"}}, &fakeFX{})
	w := httptest.NewRecorder()
	h.PriceAlertSubroutes(w, withPrincipal(httptest.NewRequest(http.MethodPatch, "/price-alerts/7", nil)))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d body %s", w.Code, w.Body.String())
	}
}

func TestPriceAlertSubroutes_Delete(t *testing.T) {
	h := portHandlers(&fakeMarket{}, &fakeFX{})
	w := httptest.NewRecorder()
	h.PriceAlertSubroutes(w, withPrincipal(httptest.NewRequest(http.MethodDelete, "/price-alerts/7", nil)))
	if w.Code != http.StatusNoContent {
		t.Fatalf("status %d", w.Code)
	}
}

func TestPriceAlertSubroutes_BadID(t *testing.T) {
	h := portHandlers(&fakeMarket{}, &fakeFX{})
	w := httptest.NewRecorder()
	h.PriceAlertSubroutes(w, withPrincipal(httptest.NewRequest(http.MethodPatch, "/price-alerts/abc", nil)))
	if w.Code != http.StatusNotFound {
		t.Fatalf("status %d, want 404", w.Code)
	}
}

func TestListWatchlists_OK(t *testing.T) {
	h := portHandlers(&fakeMarket{watchlists: []market.Watchlist{{ID: 1, Name: "WL"}}}, &fakeFX{})
	w := httptest.NewRecorder()
	h.ListWatchlists(w, withPrincipal(httptest.NewRequest(http.MethodGet, "/watchlists", nil)))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d body %s", w.Code, w.Body.String())
	}
}

func TestCreateWatchlist_OK(t *testing.T) {
	h := portHandlers(&fakeMarket{watchlist: &market.Watchlist{ID: 1, Name: "WL"}}, &fakeFX{})
	w := httptest.NewRecorder()
	h.CreateWatchlist(w, withPrincipal(httptest.NewRequest(http.MethodPost, "/watchlists", strings.NewReader(`{"name":"WL"}`))))
	if w.Code != http.StatusOK && w.Code != http.StatusCreated {
		t.Fatalf("status %d body %s", w.Code, w.Body.String())
	}
}

func TestGetRates_OK(t *testing.T) {
	h := portHandlers(&fakeMarket{}, &fakeFX{rates: []fx.ExchangeRate{{CurrencyCode: "EUR"}}})
	w := httptest.NewRecorder()
	h.GetRates(w, httptest.NewRequest(http.MethodGet, "/exchange-rates", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d body %s", w.Code, w.Body.String())
	}
}
