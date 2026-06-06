package http

import (
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"banka1/market-service-go/internal/market"
)

func TestListingSubroutes_Details_OK(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, &fakeListingResolver{lt: market.ListingTypeStock})
	w := httptest.NewRecorder()
	h.ListingSubroutes(w, withRole(httptest.NewRequest(http.MethodGet, "/api/listings/10?period=DAY", nil), "BASIC"))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d body %s", w.Code, w.Body.String())
	}
}

func TestListingSubroutes_MissingPeriod(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	h.ListingSubroutes(w, withRole(httptest.NewRequest(http.MethodGet, "/api/listings/10", nil), "BASIC"))
	if w.Code != http.StatusBadRequest {
		t.Fatalf("status %d, want 400", w.Code)
	}
}

func TestListingSubroutes_BadID(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	h.ListingSubroutes(w, withRole(httptest.NewRequest(http.MethodGet, "/api/listings/notanid", nil), "BASIC"))
	if w.Code != http.StatusNotFound {
		t.Fatalf("status %d, want 404", w.Code)
	}
}

func TestListingSubroutes_RefreshForbidden(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	h.ListingSubroutes(w, withRole(httptest.NewRequest(http.MethodPost, "/api/listings/10/refresh", nil), "CLIENT"))
	if w.Code != http.StatusForbidden {
		t.Fatalf("status %d, want 403", w.Code)
	}
}

func TestListingSubroutes_RefreshOK(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	h.ListingSubroutes(w, withRole(httptest.NewRequest(http.MethodPost, "/api/listings/10/refresh", nil), "ADMIN"))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d", w.Code)
	}
}

func TestInternalListingSubroutes_RefreshOK(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	h.InternalListingSubroutes(w, httptest.NewRequest(http.MethodPost, "/api/internal/listings/10/refresh", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d", w.Code)
	}
}

func TestInternalListingSubroutes_NotFound(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	h.InternalListingSubroutes(w, httptest.NewRequest(http.MethodGet, "/api/internal/listings/10", nil))
	if w.Code != http.StatusNotFound {
		t.Fatalf("status %d, want 404", w.Code)
	}
}

func TestListFuturesListings_OK(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	h.ListFuturesListings(w, httptest.NewRequest(http.MethodGet, "/listings/futures?page=0&size=20", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("status %d", w.Code)
	}
}

func TestListStockListings_BadSize(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	h.ListStockListings(w, httptest.NewRequest(http.MethodGet, "/listings/stocks?size=0", nil))
	if w.Code != http.StatusBadRequest {
		t.Fatalf("status %d, want 400", w.Code)
	}
}

func TestWatchlistItems_GetWithFilter(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	r := withRole(httptest.NewRequest(http.MethodGet, "/watchlists/2/items?type=STOCK", nil), "CLIENT")
	h.WatchlistSubroutes(w, r)
	if w.Code != http.StatusOK {
		t.Fatalf("status %d body %s", w.Code, w.Body.String())
	}
}

// error-path helpers
func TestGetRates_FXError(t *testing.T) {
	h := appWith(&fakeMarket{}, &fakeFX{err: errors.New("fx down")}, nil, nil)
	w := httptest.NewRecorder()
	h.GetRates(w, httptest.NewRequest(http.MethodGet, "/exchange-rates", nil))
	if w.Code < 400 {
		t.Fatalf("expected error status, got %d", w.Code)
	}
}

func TestCreatePriceAlert_ServiceError(t *testing.T) {
	h := appWith(&fakeMarket{err: errors.New("boom")}, &fakeFX{}, nil, nil)
	w := httptest.NewRecorder()
	body := `{"listingId":10,"condition":"ABOVE","threshold":100,"notificationType":"EMAIL"}`
	r := withRole(httptest.NewRequest(http.MethodPost, "/price-alerts", strings.NewReader(body)), "CLIENT")
	h.CreatePriceAlert(w, r)
	if w.Code < 400 {
		t.Fatalf("expected error status, got %d", w.Code)
	}
}
