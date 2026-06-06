package user

import (
	"errors"
	"strconv"
	"testing"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/stretchr/testify/assert"
)

func TestItoa_MatchesStrconv(t *testing.T) {
	t.Parallel()
	for _, n := range []int{0, 1, 9, 10, 42, 100, 999999} {
		assert.Equal(t, strconv.Itoa(n), itoa(n))
	}
}

func TestNilIfBlank(t *testing.T) {
	t.Parallel()
	assert.Nil(t, nilIfBlank("   "))
	assert.Nil(t, nilIfBlank(""))
	assert.Equal(t, "x", nilIfBlank("  x  "))
}

func TestNormalizePhone(t *testing.T) {
	t.Parallel()
	assert.Nil(t, normalizePhone(""))
	assert.Equal(t, "+38160123", normalizePhone("+38160123")) // already +
	assert.Equal(t, "+38160123", normalizePhone("0038160123")) // 00 -> +
	assert.Equal(t, "+38160123456", normalizePhone("060123456")) // 0 -> +381
	assert.Equal(t, "+38160", normalizePhone("38160"))           // bare -> +
}

func TestDefaultString(t *testing.T) {
	t.Parallel()
	assert.Equal(t, "fb", defaultString("  ", "fb"))
	assert.Equal(t, "val", defaultString("val", "fb"))
}

func TestValueOr(t *testing.T) {
	t.Parallel()
	assert.Equal(t, "fb", valueOr(nil, "fb"))
	s := "set"
	assert.Equal(t, "set", valueOr(&s, "fb"))
}

func TestPtrValueOr(t *testing.T) {
	t.Parallel()
	fb := "fallback"
	assert.Equal(t, &fb, ptrValueOr(nil, &fb))
	v := "  value  "
	assert.Equal(t, "value", ptrValueOr(&v, &fb))
	blank := "  "
	assert.Nil(t, ptrValueOr(&blank, &fb))
}

func TestBoolValueOr(t *testing.T) {
	t.Parallel()
	assert.True(t, boolValueOr(nil, true))
	b := false
	assert.False(t, boolValueOr(&b, true))
}

func TestMapNotFound(t *testing.T) {
	t.Parallel()
	assert.Nil(t, mapNotFound(nil))
	assert.ErrorIs(t, mapNotFound(pgx.ErrNoRows), ErrNotFound)
	other := errors.New("boom")
	assert.Equal(t, other, mapNotFound(other))
}

func TestMapPgError_UniqueViolation(t *testing.T) {
	t.Parallel()
	dup := &pgconn.PgError{Code: "23505"}
	assert.ErrorIs(t, mapPgError(dup), ErrDuplicate)
	other := errors.New("x")
	assert.Equal(t, other, mapPgError(other))
}

func TestIsUndefinedColumn(t *testing.T) {
	t.Parallel()
	assert.True(t, isUndefinedColumn(&pgconn.PgError{Code: "42703"}))
	assert.False(t, isUndefinedColumn(&pgconn.PgError{Code: "23505"}))
	assert.False(t, isUndefinedColumn(errors.New("x")))
}

func TestEmployeeWhere(t *testing.T) {
	t.Parallel()
	clause, args := employeeWhere(SearchQuery{})
	assert.Equal(t, "", clause)
	assert.Empty(t, args)

	clause, args = employeeWhere(SearchQuery{Ime: "an", Email: "a@b"})
	assert.Contains(t, clause, "lower(ime) LIKE $1")
	assert.Contains(t, clause, "lower(email) LIKE $2")
	assert.Len(t, args, 2)

	clause, args = employeeWhere(SearchQuery{Query: "smith"})
	assert.Contains(t, clause, "lower(ime) LIKE $1 OR lower(prezime) LIKE $1")
	assert.Len(t, args, 1)
}

func TestClientWhere(t *testing.T) {
	t.Parallel()
	clause, args := clientWhere(SearchQuery{})
	assert.Equal(t, "", clause)
	assert.Empty(t, args)

	clause, args = clientWhere(SearchQuery{Prezime: "doe", Query: "x"})
	assert.Contains(t, clause, "lower(prezime) LIKE $1")
	assert.Contains(t, clause, "OR lower(email) LIKE $2")
	assert.Len(t, args, 2)
}
