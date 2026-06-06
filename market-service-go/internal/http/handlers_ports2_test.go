package http

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	gpauth "banka1/go-platform/auth"
	"banka1/market-service-go/internal/fx"
	"banka1/market-service-go/internal/market"
	"banka1/market-service-go/internal/platform"
)

type fakePriceFeed struct {
	current []market.StockPriceSnapshot
	single  market.StockPriceSnapshot
	found   bool
}

func (f *fakePriceFeed) GetCurrent(context.Context, []string) []market.StockPriceSnapshot {
	return f.current
}
func (f *fakePriceFeed) GetSingle(context.Context, string) (market.StockPriceSnapshot, bool) {
	return f.single, f.found
}

func appWith(mkt MarketServicePort, fxs FXServicePort, pf PriceFeedPort, res ListingTypeResolver) *Handlers {
	if res == nil {
		res = &fakeListingResolver{}
	}
	return NewHandlers(platform.Config{}, &App{
		Config:        platform.Config{},
		MarketService: mkt,
		FXService:     fxs,
		PriceFeed:     pf,
		MarketRepo:    res,
	})
}

func withRole(r *http.Request, role string) *http.Request {
	ctx := gpauth.WithPrincipal(r.Context(), gpauth.Principal{ID: 5, Role: role})
	return r.WithContext(ctx)
}

func TestGetCurrentPriceFeed(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, &fakePriceFeed{}, nil)
	w := httptest.NewRecorder()
	h.GetCurrentPriceFeed(w, httptest.NewRequest(http.MethodGet, "/stocks/price-feed?tickers=AAPL,MSFT", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d", w.Code)
	}
}

func TestGetSinglePriceFeed_Found(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, &fakePriceFeed{found: true}, nil)
	w := httptest.NewRecorder()
	h.GetSinglePriceFeed(w, httptest.NewRequest(http.MethodGet, "/stocks/price-feed/single/AAPL", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d", w.Code)
	}
}

func TestGetSinglePriceFeed_NotFound(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, &fakePriceFeed{found: false}, nil)
	w := httptest.NewRecorder()
	h.GetSinglePriceFeed(w, httptest.NewRequest(http.MethodGet, "/stocks/price-feed/single/AAPL", nil))
	if w.Code != http.StatusNotFound {
		t.Fatalf("status %d, want 404", w.Code)
	}
}

func TestExchangeInfo(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	h.ExchangeInfo(w, httptest.NewRequest(http.MethodGet, "/api/exchange/info", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d", w.Code)
	}
}

func TestStockExchangeSubroutes_IsOpen(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	h.StockExchangeSubroutes(w, httptest.NewRequest(http.MethodGet, "/api/stock-exchanges/1/is-open", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d", w.Code)
	}
}

func TestStockExchangeSubroutes_Status(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	h.StockExchangeSubroutes(w, httptest.NewRequest(http.MethodGet, "/api/stock-exchanges/1/status", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d", w.Code)
	}
}

func TestStockExchangeSubroutes_ToggleForbidden(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	r := withRole(httptest.NewRequest(http.MethodPut, "/api/stock-exchanges/1/toggle-active", nil), "CLIENT")
	h.StockExchangeSubroutes(w, r)
	if w.Code != http.StatusForbidden {
		t.Fatalf("status %d, want 403", w.Code)
	}
}

func TestStockExchangeSubroutes_ToggleOK(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	r := withRole(httptest.NewRequest(http.MethodPut, "/api/stock-exchanges/1/toggle-active", nil), "ADMIN")
	h.StockExchangeSubroutes(w, r)
	if w.Code != http.StatusOK {
		t.Fatalf("status %d", w.Code)
	}
}

func TestListStockListings_OK(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	h.ListStockListings(w, httptest.NewRequest(http.MethodGet, "/listings/stocks?page=0&size=20", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d body %s", w.Code, w.Body.String())
	}
}

func TestListStockListings_BadPage(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	h.ListStockListings(w, httptest.NewRequest(http.MethodGet, "/listings/stocks?page=-1", nil))
	if w.Code != http.StatusBadRequest {
		t.Fatalf("status %d, want 400", w.Code)
	}
}

func TestListForexListings_OK(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	h.ListForexListings(w, httptest.NewRequest(http.MethodGet, "/listings/forex?page=0&size=10", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d", w.Code)
	}
}

func TestGetRateByCurrency_Rate(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{rate: &fx.ExchangeRate{CurrencyCode: "EUR"}}, nil, nil)
	w := httptest.NewRecorder()
	h.GetRateByCurrency(w, httptest.NewRequest(http.MethodGet, "/rates/EUR", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d", w.Code)
	}
}

func TestGetRateByCurrency_History(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{rates: []fx.ExchangeRate{{CurrencyCode: "EUR"}}}, nil, nil)
	w := httptest.NewRecorder()
	h.GetRateByCurrency(w, httptest.NewRequest(http.MethodGet, "/rates/EUR/history?from=2024-01-01&to=2024-01-31", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d", w.Code)
	}
}

func TestCalculate_OK(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{conv: &fx.ConversionResponse{}}, nil, nil)
	w := httptest.NewRecorder()
	h.Calculate(w, httptest.NewRequest(http.MethodGet, "/calculate?fromCurrency=EUR&toCurrency=RSD&amount=100", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d body %s", w.Code, w.Body.String())
	}
}

func TestCalculateNoCommission_OK(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{conv: &fx.ConversionResponse{}}, nil, nil)
	w := httptest.NewRecorder()
	h.CalculateNoCommission(w, httptest.NewRequest(http.MethodGet, "/calculate/no-commission?fromCurrency=EUR&toCurrency=RSD&amount=100", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d body %s", w.Code, w.Body.String())
	}
}

func TestFetchRates_OK(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	h.FetchRates(w, httptest.NewRequest(http.MethodPost, "/rates/fetch", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d", w.Code)
	}
}

func TestImportStockExchanges_OK(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	h.ImportStockExchanges(w, httptest.NewRequest(http.MethodPost, "/admin/import", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d", w.Code)
	}
}

func TestRefreshAllStocks_Accepted(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	h.RefreshAllStocks(w, httptest.NewRequest(http.MethodPost, "/admin/stocks/refresh", nil))
	if w.Code != http.StatusAccepted {
		t.Fatalf("status %d, want 202", w.Code)
	}
}

func TestStockAdminSubroutes_Refresh(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	h.StockAdminSubroutes(w, httptest.NewRequest(http.MethodPost, "/admin/stocks/AAPL/refresh-market-data", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d body %s", w.Code, w.Body.String())
	}
}

func TestWatchlistSubroutes_DeleteWatchlist(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	r := withRole(httptest.NewRequest(http.MethodDelete, "/watchlists/2", nil), "CLIENT")
	h.WatchlistSubroutes(w, r)
	if w.Code != http.StatusNoContent {
		t.Fatalf("status %d", w.Code)
	}
}

func TestWatchlistSubroutes_AddItem(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	r := withRole(httptest.NewRequest(http.MethodPost, "/watchlists/2/items", strings.NewReader(`{"listingId":10}`)), "CLIENT")
	h.WatchlistSubroutes(w, r)
	if w.Code != http.StatusCreated {
		t.Fatalf("status %d body %s", w.Code, w.Body.String())
	}
}

func TestWatchlistSubroutes_RemoveItem(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	r := withRole(httptest.NewRequest(http.MethodDelete, "/watchlists/2/items/3", nil), "CLIENT")
	h.WatchlistSubroutes(w, r)
	if w.Code != http.StatusNoContent {
		t.Fatalf("status %d", w.Code)
	}
}

func TestWatchlistSubroutes_Unauthorized(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	h.WatchlistSubroutes(w, httptest.NewRequest(http.MethodDelete, "/watchlists/2", nil))
	if w.Code != http.StatusUnauthorized {
		t.Fatalf("status %d, want 401", w.Code)
	}
}
