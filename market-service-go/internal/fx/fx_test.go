package fx

import (
	"context"
	"testing"
	"time"

	"banka1/market-service-go/internal/clients"
	"banka1/market-service-go/internal/platform"
	"github.com/pashagolub/pgxmock/v4"
	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// ---------------------------------------------------------------------------
// Repository (pgxmock)
// ---------------------------------------------------------------------------

func newMock(t *testing.T) pgxmock.PgxPoolIface {
	t.Helper()
	m, err := pgxmock.NewPool()
	require.NoError(t, err)
	t.Cleanup(m.Close)
	return m
}

var rateCols = []string{"currency_code", "buying_rate", "selling_rate", "rate_date", "created_at"}

func rateRow() []any {
	return []any{"EUR", "117.0", "118.0", time.Now(), time.Now()}
}

func TestRepo_GetRatesByDate(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("from exchange_rate").WithArgs(pgxmock.AnyArg()).WillReturnRows(pgxmock.NewRows(rateCols).AddRow(rateRow()...))
	out, err := r.GetRatesByDate(context.Background(), time.Now())
	require.NoError(t, err)
	assert.Len(t, out, 1)
	assert.Equal(t, "EUR", out[0].CurrencyCode)
}

func TestRepo_GetRatesByRange(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectQuery("from exchange_rate").WithArgs(pgxmock.AnyArg(), pgxmock.AnyArg(), pgxmock.AnyArg()).
		WillReturnRows(pgxmock.NewRows(rateCols).AddRow(rateRow()...))
	out, err := r.GetRatesByRange(context.Background(), "eur", time.Now(), time.Now())
	require.NoError(t, err)
	assert.Len(t, out, 1)
}

func TestRepo_LatestDate(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	now := time.Now()
	m.ExpectQuery("max").WillReturnRows(pgxmock.NewRows([]string{"max"}).AddRow(&now))
	_, err := r.LatestDate(context.Background())
	require.NoError(t, err)
}

func TestRepo_ReplaceSnapshot(t *testing.T) {
	m := newMock(t)
	r := &Repository{db: m}
	m.ExpectBegin()
	m.ExpectExec("delete from exchange_rate").WithArgs(pgxmock.AnyArg()).WillReturnResult(pgxmock.NewResult("DELETE", 1))
	m.ExpectExec("insert into exchange_rate").WithArgs(pgxmock.AnyArg(), pgxmock.AnyArg(), pgxmock.AnyArg(), pgxmock.AnyArg()).WillReturnResult(pgxmock.NewResult("INSERT", 1))
	m.ExpectCommit()
	m.ExpectQuery("from exchange_rate").WithArgs(pgxmock.AnyArg()).WillReturnRows(pgxmock.NewRows(rateCols).AddRow(rateRow()...))

	out, err := r.ReplaceSnapshot(context.Background(), time.Now(), []ExchangeRate{
		{CurrencyCode: "EUR", BuyingRate: decimal.RequireFromString("117"), SellingRate: decimal.RequireFromString("118")},
	})
	require.NoError(t, err)
	assert.Len(t, out, 1)
}

func TestRepo_NewRepository(t *testing.T) {
	t.Parallel()
	assert.NotNil(t, NewRepository(nil))
}

// ---------------------------------------------------------------------------
// Service (fake repo)
// ---------------------------------------------------------------------------

type fakeRepo struct {
	ratesByDate  []ExchangeRate
	ratesByRange []ExchangeRate
	latest       *time.Time
	replaceErr   error
	dateErr      error
	replaced     []ExchangeRate
}

func (f *fakeRepo) GetRatesByDate(context.Context, time.Time) ([]ExchangeRate, error) {
	return f.ratesByDate, f.dateErr
}
func (f *fakeRepo) GetRatesByRange(context.Context, string, time.Time, time.Time) ([]ExchangeRate, error) {
	return f.ratesByRange, nil
}
func (f *fakeRepo) LatestDate(context.Context) (*time.Time, error) { return f.latest, nil }
func (f *fakeRepo) ReplaceSnapshot(_ context.Context, _ time.Time, rates []ExchangeRate) ([]ExchangeRate, error) {
	f.replaced = rates
	return rates, f.replaceErr
}

func testService(repo fxRepository) *Service {
	return &Service{
		cfg: platform.Config{FX: platform.FXConfig{
			MarginPercentage:     "1.0",
			CommissionPercentage: "0.70",
			SupportedCurrencies:  []string{"EUR"},
		}},
		repo: repo,
	}
}

func eur() ExchangeRate {
	return ExchangeRate{CurrencyCode: "EUR", BuyingRate: decimal.RequireFromString("117"), SellingRate: decimal.RequireFromString("118")}
}

func TestService_GetRates(t *testing.T) {
	t.Parallel()
	svc := testService(&fakeRepo{ratesByDate: []ExchangeRate{eur()}})
	out, err := svc.GetRates(context.Background(), "2024-01-02")
	require.NoError(t, err)
	assert.Len(t, out, 1)
}

func TestService_GetRate_RSD(t *testing.T) {
	t.Parallel()
	svc := testService(&fakeRepo{})
	r, err := svc.GetRate(context.Background(), "RSD", "2024-01-02")
	require.NoError(t, err)
	assert.Equal(t, "RSD", r.CurrencyCode)
}

func TestService_GetRate_Found(t *testing.T) {
	t.Parallel()
	svc := testService(&fakeRepo{ratesByDate: []ExchangeRate{eur()}})
	r, err := svc.GetRate(context.Background(), "EUR", "2024-01-02")
	require.NoError(t, err)
	assert.Equal(t, "EUR", r.CurrencyCode)
}

func TestService_GetRate_NotFound(t *testing.T) {
	t.Parallel()
	svc := testService(&fakeRepo{ratesByDate: []ExchangeRate{eur()}})
	_, err := svc.GetRate(context.Background(), "GBP", "2024-01-02")
	assert.Error(t, err)
}

func TestService_GetRateHistory(t *testing.T) {
	t.Parallel()
	svc := testService(&fakeRepo{ratesByRange: []ExchangeRate{eur()}})
	out, err := svc.GetRateHistory(context.Background(), "EUR", "2024-01-01", "2024-01-31")
	require.NoError(t, err)
	assert.Len(t, out, 1)
}

func TestService_GetRateHistory_BadDates(t *testing.T) {
	t.Parallel()
	svc := testService(&fakeRepo{})
	_, err := svc.GetRateHistory(context.Background(), "EUR", "bad", "2024-01-31")
	assert.Error(t, err)
	_, err = svc.GetRateHistory(context.Background(), "EUR", "2024-01-01", "bad")
	assert.Error(t, err)
}

func TestService_Calculate_SameCurrency(t *testing.T) {
	t.Parallel()
	svc := testService(&fakeRepo{})
	resp, err := svc.Calculate(context.Background(), "EUR", "EUR", "100", "2024-01-02", false)
	require.NoError(t, err)
	assert.True(t, resp.ToAmount.Equal(decimal.RequireFromString("100")))
}

func TestService_Calculate_ForeignToRSD(t *testing.T) {
	t.Parallel()
	svc := testService(&fakeRepo{ratesByDate: []ExchangeRate{eur()}})
	resp, err := svc.Calculate(context.Background(), "EUR", "RSD", "10", "2024-01-02", true)
	require.NoError(t, err)
	// 10 EUR * 117 buying = 1170 RSD
	assert.True(t, resp.ToAmount.Equal(decimal.RequireFromString("1170")), "got %s", resp.ToAmount)
	assert.True(t, resp.Commission.GreaterThan(decimal.Zero))
}

func TestService_Calculate_RSDToForeign(t *testing.T) {
	t.Parallel()
	svc := testService(&fakeRepo{ratesByDate: []ExchangeRate{eur()}})
	resp, err := svc.Calculate(context.Background(), "RSD", "EUR", "1180", "2024-01-02", false)
	require.NoError(t, err)
	// 1180 RSD / 118 selling = 10 EUR
	assert.True(t, resp.ToAmount.Equal(decimal.RequireFromString("10")), "got %s", resp.ToAmount)
}

func TestService_Calculate_BadAmount(t *testing.T) {
	t.Parallel()
	svc := testService(&fakeRepo{})
	_, err := svc.Calculate(context.Background(), "EUR", "RSD", "notnum", "2024-01-02", false)
	assert.Error(t, err)
}

func TestService_ResolveSnapshotDate_Latest(t *testing.T) {
	t.Parallel()
	now := time.Now()
	svc := testService(&fakeRepo{latest: &now, ratesByDate: []ExchangeRate{eur()}})
	out, err := svc.GetRates(context.Background(), "") // empty date -> latest
	require.NoError(t, err)
	assert.Len(t, out, 1)
}

func TestService_ResolveSnapshotDate_Empty(t *testing.T) {
	t.Parallel()
	svc := testService(&fakeRepo{latest: nil})
	_, err := svc.GetRates(context.Background(), "")
	assert.Error(t, err, "empty DB should error")
}

func TestService_FetchOnStartup_Disabled(t *testing.T) {
	t.Parallel()
	svc := testService(&fakeRepo{})
	svc.cfg.FX.FetchOnStartup = false
	require.NoError(t, svc.FetchOnStartup(context.Background()))
}

func TestService_RateHelpers(t *testing.T) {
	t.Parallel()
	svc := testService(&fakeRepo{})
	market := decimal.RequireFromString("100")
	// margin 1% -> buying 99, selling 101
	assert.True(t, svc.calculateBuyingRate(market).Equal(decimal.RequireFromString("99")))
	assert.True(t, svc.calculateSellingRate(market).Equal(decimal.RequireFromString("101")))
	assert.True(t, svc.resolveMarginFactor().Equal(decimal.RequireFromString("0.01")))
	assert.True(t, svc.resolveCommissionFactor().Equal(decimal.RequireFromString("0.007")))
}

func TestService_FetchAndStore_FallbackOnClientError(t *testing.T) {
	t.Parallel()
	now := time.Now()
	repo := &fakeRepo{latest: &now, ratesByDate: []ExchangeRate{eur()}}
	svc := testService(repo)
	// client points at an unreachable base URL -> FetchExchangeRate errors -> fallback path.
	// fallback uses LatestDate + GetRatesByDate + ReplaceSnapshot (all stubbed).
	svc.SetClient(clients.NewTwelveDataClient("http://127.0.0.1:0", "", nil))
	result, err := svc.FetchAndStoreDailyRates(context.Background())
	require.NoError(t, err)
	assert.Equal(t, true, result["fallbackUsed"])
}

func TestBaseCurrencyHelper(t *testing.T) {
	t.Parallel()
	r := baseCurrency(time.Date(2024, 1, 2, 0, 0, 0, 0, time.UTC))
	assert.Equal(t, "RSD", r.CurrencyCode)
	assert.True(t, r.BuyingRate.Equal(decimal.NewFromInt(1)))
}
