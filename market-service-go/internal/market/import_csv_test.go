package market

import (
	"context"
	"os"
	"path/filepath"
	"testing"

	"github.com/jackc/pgx/v5"
	"github.com/pashagolub/pgxmock/v4"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func writeExchangeCSV(t *testing.T) string {
	t.Helper()
	dir := t.TempDir()
	path := filepath.Join(dir, "exchanges.csv")
	content := "Exchange Name,Exchange Acronym,Exchange Mic Code,Country,Currency,Time Zone,Open Time,Close Time\n" +
		"New York Stock Exchange,NYSE,XNYS,United States,USD,America/New_York,09:30,16:00\n"
	require.NoError(t, os.WriteFile(path, []byte(content), 0o644))
	return path
}

func TestService_ImportStockExchanges_CreatesNew(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	path := writeExchangeCSV(t)

	m.ExpectQuery("from stock_exchange").WithArgs(anyN(1)...).WillReturnError(pgx.ErrNoRows)
	m.ExpectExec("insert into stock_exchange").WithArgs(anyN(13)...).WillReturnResult(pgxmock.NewResult("INSERT", 1))

	resp, err := s.ImportStockExchanges(context.Background(), path)
	require.NoError(t, err)
	assert.Equal(t, 1, resp.ProcessedRows)
	assert.Equal(t, 1, resp.CreatedCount)
}

func TestService_ImportStockExchanges_EmptyPath(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	resp, err := s.ImportStockExchanges(context.Background(), "")
	require.NoError(t, err)
	assert.Equal(t, 0, resp.ProcessedRows)
}

func TestService_ImportStockExchanges_MissingFile(t *testing.T) {
	m := newMock(t)
	s := testService(m)
	_, err := s.ImportStockExchanges(context.Background(), "/nonexistent/exchanges.csv")
	assert.Error(t, err)
}

func TestStockExchangeEquals(t *testing.T) {
	t.Parallel()
	a := StockExchange{ID: 1, ExchangeName: "NYSE", Currency: "USD"}
	b := a
	assert.True(t, stockExchangeEquals(a, b))
	b.Currency = "EUR"
	assert.False(t, stockExchangeEquals(a, b))
}

func TestStringPtrEqual(t *testing.T) {
	t.Parallel()
	x, y := "a", "a"
	assert.True(t, stringPtrEqual(&x, &y))
	assert.True(t, stringPtrEqual(nil, nil))
	z := "b"
	assert.False(t, stringPtrEqual(&x, &z))
	assert.False(t, stringPtrEqual(&x, nil))
}
