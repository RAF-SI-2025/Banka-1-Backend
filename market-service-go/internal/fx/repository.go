package fx

import (
	"context"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/shopspring/decimal"
)

// pgxPool is the subset of *pgxpool.Pool used by Repository, allowing a mock
// pool to be injected in unit tests; *pgxpool.Pool satisfies it.
type pgxPool interface {
	Begin(ctx context.Context) (pgx.Tx, error)
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
}

type Repository struct {
	db pgxPool
}

func NewRepository(db *pgxpool.Pool) *Repository {
	return &Repository{db: db}
}

func (r *Repository) GetRatesByDate(ctx context.Context, date time.Time) ([]ExchangeRate, error) {
	rows, err := r.db.Query(ctx, `select currency_code, buying_rate::text, selling_rate::text, rate_date, created_at
		from exchange_rate where rate_date = $1 order by currency_code asc`, date.Format("2006-01-02"))
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := make([]ExchangeRate, 0)
	for rows.Next() {
		var item ExchangeRate
		var snapshotDate time.Time
		var createdAt time.Time
		var buyingRate string
		var sellingRate string
		if err := rows.Scan(&item.CurrencyCode, &buyingRate, &sellingRate, &snapshotDate, &createdAt); err != nil {
			return nil, err
		}
		item.BuyingRate = decimal.RequireFromString(buyingRate)
		item.SellingRate = decimal.RequireFromString(sellingRate)
		item.Date = snapshotDate.Format("2006-01-02")
		item.CreatedAt = createdAt.UTC().Format(time.RFC3339)
		out = append(out, item)
	}
	return out, rows.Err()
}

func (r *Repository) GetRatesByRange(ctx context.Context, currencyCode string, from, to time.Time) ([]ExchangeRate, error) {
	rows, err := r.db.Query(ctx, `select currency_code, buying_rate::text, selling_rate::text, rate_date, created_at
		from exchange_rate
		where currency_code = $1 and rate_date >= $2 and rate_date <= $3
		order by rate_date asc`,
		strings.ToUpper(currencyCode), from.Format("2006-01-02"), to.Format("2006-01-02"))
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := make([]ExchangeRate, 0)
	for rows.Next() {
		var item ExchangeRate
		var snapshotDate time.Time
		var createdAt time.Time
		var buyingRate string
		var sellingRate string
		if err := rows.Scan(&item.CurrencyCode, &buyingRate, &sellingRate, &snapshotDate, &createdAt); err != nil {
			return nil, err
		}
		item.BuyingRate = decimal.RequireFromString(buyingRate)
		item.SellingRate = decimal.RequireFromString(sellingRate)
		item.Date = snapshotDate.Format("2006-01-02")
		item.CreatedAt = createdAt.UTC().Format(time.RFC3339)
		out = append(out, item)
	}
	return out, rows.Err()
}

func (r *Repository) LatestDate(ctx context.Context) (*time.Time, error) {
	var value *time.Time
	err := r.db.QueryRow(ctx, `select max(rate_date) from exchange_rate`).Scan(&value)
	return value, err
}

func (r *Repository) ReplaceSnapshot(ctx context.Context, snapshotDate time.Time, rates []ExchangeRate) ([]ExchangeRate, error) {
	tx, err := r.db.Begin(ctx)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback(ctx)
	if _, err := tx.Exec(ctx, `delete from exchange_rate where rate_date = $1`, snapshotDate.Format("2006-01-02")); err != nil {
		return nil, err
	}
	for _, rate := range rates {
		if _, err := tx.Exec(ctx, `insert into exchange_rate (currency_code, buying_rate, selling_rate, rate_date) values ($1, $2::numeric, $3::numeric, $4)`,
			rate.CurrencyCode, rate.BuyingRate, rate.SellingRate, snapshotDate.Format("2006-01-02")); err != nil {
			return nil, err
		}
	}
	if err := tx.Commit(ctx); err != nil {
		return nil, err
	}
	return r.GetRatesByDate(ctx, snapshotDate)
}
