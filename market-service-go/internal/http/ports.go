package http

import (
	"context"

	"banka1/market-service-go/internal/api"
	"banka1/market-service-go/internal/fx"
	"banka1/market-service-go/internal/market"
)

// These interfaces are the ports the HTTP (and gRPC) surface depends on. They
// are satisfied by the concrete *market.Service / *fx.Service / *market.Repository
// / *market.PriceFeedService, and exist so the handlers can be unit-tested with
// in-memory fakes instead of a live database.

type MarketServicePort interface {
	GetExchangeStatus(ctx context.Context, id int64) (*api.StockExchangeStatusResponse, error)
	ListStockExchanges(ctx context.Context) ([]api.StockExchangeResponse, error)
	ToggleStockExchangeActive(ctx context.Context, id int64) (*api.StockExchangeToggleResponse, error)
	GetListingDetails(ctx context.Context, id int64, period string) (*api.ListingDetailsResponse, error)
	ListListings(ctx context.Context, listingType market.ListingType, filter market.ListingFilter, page, size int, sortBy, sortDirection string) (*api.PageListingSummaryResponse, error)
	RefreshListing(ctx context.Context, listingID int64) (*api.ListingRefreshResponse, error)
	RefreshStockByTicker(ctx context.Context, ticker string) (*api.StockMarketDataRefreshResponse, error)
	RefreshAllStocks(ctx context.Context) ([]api.StockMarketDataRefreshResponse, error)
	ImportStockExchanges(ctx context.Context, csvPath string) (*api.StockExchangeImportResponse, error)
	ListPriceAlerts(ctx context.Context, userID int64) ([]market.PriceAlert, error)
	CreatePriceAlert(ctx context.Context, userID int64, recipientType, userEmail, username string, listingID int64, condition market.PriceAlertCondition, threshold, notificationType string) (*market.PriceAlert, error)
	TogglePriceAlert(ctx context.Context, userID, alertID int64) (*market.PriceAlert, error)
	DeletePriceAlert(ctx context.Context, userID, alertID int64) error
	ListWatchlists(ctx context.Context, userID int64) ([]market.Watchlist, error)
	CreateWatchlist(ctx context.Context, userID int64, name string) (*market.Watchlist, error)
	DeleteWatchlist(ctx context.Context, userID, watchlistID int64) error
	ListWatchlistItems(ctx context.Context, userID, watchlistID int64, filter *market.ListingType) ([]market.WatchlistItem, error)
	AddWatchlistItem(ctx context.Context, userID, watchlistID, listingID int64) (*market.WatchlistItem, error)
	RemoveWatchlistItem(ctx context.Context, userID, watchlistID, itemID int64) error
	ListDividendData(ctx context.Context) ([]market.DividendData, error)
}

type FXServicePort interface {
	GetRates(ctx context.Context, dateText string) ([]fx.ExchangeRate, error)
	GetRate(ctx context.Context, currencyCode, dateText string) (*fx.ExchangeRate, error)
	GetRateHistory(ctx context.Context, currencyCode, fromText, toText string) ([]fx.ExchangeRate, error)
	Calculate(ctx context.Context, fromCurrency, toCurrency, amountText, dateText string, includeCommission bool) (*fx.ConversionResponse, error)
	FetchAndStoreDailyRates(ctx context.Context) (map[string]any, error)
}

type PriceFeedPort interface {
	GetCurrent(ctx context.Context, tickers []string) []market.StockPriceSnapshot
	GetSingle(ctx context.Context, ticker string) (market.StockPriceSnapshot, bool)
}

type ListingTypeResolver interface {
	GetListingType(ctx context.Context, id int64) (market.ListingType, error)
}
