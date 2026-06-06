package fx

import (
	"context"
	"fmt"
	"strings"
	"time"

	"banka1/market-service-go/internal/clients"
	"banka1/market-service-go/internal/platform"
	"github.com/shopspring/decimal"
)

const (
	calculationScale = 8
	commissionScale  = 2
	rateScale        = 8
)

// fxRepository is the persistence port used by Service; *Repository implements it.
// Declared as an interface so unit tests can inject an in-memory fake.
type fxRepository interface {
	GetRatesByDate(ctx context.Context, date time.Time) ([]ExchangeRate, error)
	GetRatesByRange(ctx context.Context, currencyCode string, from, to time.Time) ([]ExchangeRate, error)
	LatestDate(ctx context.Context) (*time.Time, error)
	ReplaceSnapshot(ctx context.Context, snapshotDate time.Time, rates []ExchangeRate) ([]ExchangeRate, error)
}

type Service struct {
	cfg    platform.Config
	repo   fxRepository
	client *clients.TwelveDataClient
}

func NewService(cfg platform.Config, repo *Repository) *Service {
	return &Service{
		cfg:    cfg,
		repo:   repo,
		client: clients.NewTwelveDataClient(cfg.FX.TwelveDataBaseURL, cfg.FX.TwelveDataAPIKey, nil),
	}
}

func (s *Service) SetClient(client *clients.TwelveDataClient) {
	s.client = client
}

func (s *Service) GetRates(ctx context.Context, dateText string) ([]ExchangeRate, error) {
	snapshotDate, err := s.resolveSnapshotDate(ctx, dateText)
	if err != nil {
		return nil, err
	}
	rates, err := s.repo.GetRatesByDate(ctx, snapshotDate)
	if err != nil {
		return nil, err
	}
	return rates, nil
}

func (s *Service) GetRate(ctx context.Context, currencyCode, dateText string) (*ExchangeRate, error) {
	snapshotDate, err := s.resolveSnapshotDate(ctx, dateText)
	if err != nil {
		return nil, err
	}
	if strings.EqualFold(currencyCode, "RSD") {
		rate := baseCurrency(snapshotDate)
		return &rate, nil
	}
	rates, err := s.repo.GetRatesByDate(ctx, snapshotDate)
	if err != nil {
		return nil, err
	}
	for _, item := range rates {
		if strings.EqualFold(item.CurrencyCode, currencyCode) {
			return &item, nil
		}
	}
	return nil, fmt.Errorf("rate not found for %s on %s", currencyCode, snapshotDate.Format("2006-01-02"))
}

func (s *Service) GetRateHistory(ctx context.Context, currencyCode, fromText, toText string) ([]ExchangeRate, error) {
	from, err := time.Parse("2006-01-02", fromText)
	if err != nil {
		return nil, fmt.Errorf("invalid from date: %w", err)
	}
	to, err := time.Parse("2006-01-02", toText)
	if err != nil {
		return nil, fmt.Errorf("invalid to date: %w", err)
	}
	return s.repo.GetRatesByRange(ctx, currencyCode, from, to)
}

func (s *Service) Calculate(ctx context.Context, fromCurrency, toCurrency, amountText, dateText string, includeCommission bool) (*ConversionResponse, error) {
	amount, err := decimal.NewFromString(amountText)
	if err != nil {
		return nil, err
	}
	fromCurrency = strings.ToUpper(strings.TrimSpace(fromCurrency))
	toCurrency = strings.ToUpper(strings.TrimSpace(toCurrency))
	if fromCurrency == toCurrency {
		snapshotDate, err := s.resolveSnapshotDate(ctx, dateText)
		if err != nil {
			return nil, err
		}
		return &ConversionResponse{
			FromCurrency: fromCurrency,
			ToCurrency:   toCurrency,
			FromAmount:   amount,
			ToAmount:     amount,
			Rate:         decimal.NewFromInt(1).Round(calculationScale),
			Commission:   decimal.Zero.Round(commissionScale),
			Date:         snapshotDate.Format("2006-01-02"),
		}, nil
	}
	snapshotDate, err := s.resolveSnapshotDate(ctx, dateText)
	if err != nil {
		return nil, err
	}
	sourceBuying := decimal.NewFromInt(1)
	targetSelling := decimal.NewFromInt(1)
	if fromCurrency != "RSD" {
		rate, err := s.GetRate(ctx, fromCurrency, snapshotDate.Format("2006-01-02"))
		if err != nil {
			return nil, err
		}
		sourceBuying = rate.BuyingRate
	}
	if toCurrency != "RSD" {
		rate, err := s.GetRate(ctx, toCurrency, snapshotDate.Format("2006-01-02"))
		if err != nil {
			return nil, err
		}
		targetSelling = rate.SellingRate
	}
	amountInRSD := amount
	if fromCurrency != "RSD" {
		amountInRSD = amount.Mul(sourceBuying)
	}
	converted := amountInRSD
	if toCurrency != "RSD" {
		converted = amountInRSD.DivRound(targetSelling, calculationScale)
	} else {
		converted = amountInRSD.Round(calculationScale)
	}
	effectiveRate := converted.DivRound(amount, calculationScale)
	commission := decimal.Zero.Round(commissionScale)
	if includeCommission {
		commission = amount.Mul(s.resolveCommissionFactor()).Round(commissionScale)
	}
	return &ConversionResponse{
		FromCurrency: fromCurrency,
		ToCurrency:   toCurrency,
		FromAmount:   amount,
		ToAmount:     converted,
		Rate:         effectiveRate,
		Commission:   commission,
		Date:         snapshotDate.Format("2006-01-02"),
	}, nil
}

func (s *Service) FetchAndStoreDailyRates(ctx context.Context) (map[string]any, error) {
	var fetched []ExchangeRate
	for _, currency := range s.cfg.FX.SupportedCurrencies {
		if currency == "RSD" {
			continue
		}
		rate, err := s.client.FetchExchangeRate(ctx, currency, "RSD")
		if err != nil {
			return s.fallbackToLatestSnapshot(ctx, err)
		}
		if rate == nil {
			return s.fallbackToLatestSnapshot(ctx, fmt.Errorf("no provider response for %s", currency))
		}
		fetched = append(fetched, ExchangeRate{
			CurrencyCode: currency,
			BuyingRate:   s.calculateBuyingRate(rate.Rate),
			SellingRate:  s.calculateSellingRate(rate.Rate),
			Date:         rate.Date.Format("2006-01-02"),
		})
	}
	if len(fetched) == 0 {
		return nil, fmt.Errorf("exchange-rate fetch returned no supported currencies")
	}
	snapshotDate, _ := time.Parse("2006-01-02", fetched[0].Date)
	for _, rate := range fetched[1:] {
		if rate.Date != fetched[0].Date {
			return nil, fmt.Errorf("exchange-rate fetch returned inconsistent snapshot dates")
		}
	}
	stored, err := s.repo.ReplaceSnapshot(ctx, snapshotDate, fetched)
	if err != nil {
		return nil, err
	}
	return map[string]any{
		"fetchedCount":       len(stored),
		"rates":              stored,
		"fallbackUsed":       false,
		"sourceSnapshotDate": snapshotDate.Format("2006-01-02"),
	}, nil
}

func (s *Service) FetchOnStartup(ctx context.Context) error {
	if !s.cfg.FX.FetchOnStartup {
		return nil
	}
	_, err := s.FetchAndStoreDailyRates(ctx)
	return err
}

func (s *Service) resolveSnapshotDate(ctx context.Context, dateText string) (time.Time, error) {
	if strings.TrimSpace(dateText) != "" {
		return time.Parse("2006-01-02", dateText)
	}
	latestDate, err := s.repo.LatestDate(ctx)
	if err != nil {
		return time.Time{}, err
	}
	if latestDate == nil {
		return time.Time{}, fmt.Errorf("local exchange-rate database is empty")
	}
	return time.Date(latestDate.Year(), latestDate.Month(), latestDate.Day(), 0, 0, 0, 0, time.UTC), nil
}

func (s *Service) fallbackToLatestSnapshot(ctx context.Context, rootCause error) (map[string]any, error) {
	targetDate := time.Now().UTC()
	latestSnapshotDate, err := s.repo.LatestDate(ctx)
	if err != nil {
		return nil, err
	}
	if latestSnapshotDate == nil {
		return nil, fmt.Errorf("exchange-rate fetch failed and no local snapshot exists for fallback: %w", rootCause)
	}
	previousRates, err := s.repo.GetRatesByDate(ctx, *latestSnapshotDate)
	if err != nil {
		return nil, err
	}
	if len(previousRates) == 0 {
		return nil, fmt.Errorf("exchange-rate fetch failed and latest snapshot has no rates")
	}
	fallbackRates := make([]ExchangeRate, 0, len(previousRates))
	for _, rate := range previousRates {
		fallbackRates = append(fallbackRates, ExchangeRate{
			CurrencyCode: rate.CurrencyCode,
			BuyingRate:   rate.BuyingRate,
			SellingRate:  rate.SellingRate,
			Date:         time.Date(targetDate.Year(), targetDate.Month(), targetDate.Day(), 0, 0, 0, 0, time.UTC).Format("2006-01-02"),
		})
	}
	stored, err := s.repo.ReplaceSnapshot(ctx, time.Date(targetDate.Year(), targetDate.Month(), targetDate.Day(), 0, 0, 0, 0, time.UTC), fallbackRates)
	if err != nil {
		return nil, err
	}
	return map[string]any{
		"fetchedCount":       len(stored),
		"rates":              stored,
		"fallbackUsed":       true,
		"sourceSnapshotDate": latestSnapshotDate.Format("2006-01-02"),
	}, nil
}

func (s *Service) calculateBuyingRate(marketRate decimal.Decimal) decimal.Decimal {
	return marketRate.Mul(decimal.NewFromInt(1).Sub(s.resolveMarginFactor())).Round(rateScale)
}

func (s *Service) calculateSellingRate(marketRate decimal.Decimal) decimal.Decimal {
	return marketRate.Mul(decimal.NewFromInt(1).Add(s.resolveMarginFactor())).Round(rateScale)
}

func (s *Service) resolveMarginFactor() decimal.Decimal {
	return decimal.RequireFromString(s.cfg.FX.MarginPercentage).DivRound(decimal.NewFromInt(100), calculationScale)
}

func (s *Service) resolveCommissionFactor() decimal.Decimal {
	return decimal.RequireFromString(s.cfg.FX.CommissionPercentage).DivRound(decimal.NewFromInt(100), calculationScale)
}

func baseCurrency(date time.Time) ExchangeRate {
	return ExchangeRate{
		CurrencyCode: "RSD",
		BuyingRate:   decimal.NewFromInt(1),
		SellingRate:  decimal.NewFromInt(1),
		Date:         time.Date(date.Year(), date.Month(), date.Day(), 0, 0, 0, 0, time.UTC).Format("2006-01-02"),
		CreatedAt:    time.Date(date.Year(), date.Month(), date.Day(), 0, 0, 0, 0, time.UTC).Format(time.RFC3339),
	}
}
