package store

import (
	"context"
	"errors"
	"fmt"
	"reflect"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

type stubDB struct {
	execFn     func(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
	queryFn    func(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
	queryRowFn func(ctx context.Context, sql string, args ...any) pgx.Row
	beginFn    func(ctx context.Context) (pgx.Tx, error)
}

func (s *stubDB) Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error) {
	if s.execFn == nil {
		return pgconn.CommandTag{}, errors.New("unexpected exec")
	}
	return s.execFn(ctx, sql, args...)
}

func (s *stubDB) Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error) {
	if s.queryFn == nil {
		return nil, errors.New("unexpected query")
	}
	return s.queryFn(ctx, sql, args...)
}

func (s *stubDB) QueryRow(ctx context.Context, sql string, args ...any) pgx.Row {
	if s.queryRowFn == nil {
		return stubRow{err: errors.New("unexpected query row")}
	}
	return s.queryRowFn(ctx, sql, args...)
}

func (s *stubDB) Begin(ctx context.Context) (pgx.Tx, error) {
	if s.beginFn == nil {
		return nil, errors.New("unexpected begin")
	}
	return s.beginFn(ctx)
}

type stubTx struct {
	execFn     func(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
	queryFn    func(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
	queryRowFn func(ctx context.Context, sql string, args ...any) pgx.Row
	commitFn   func(ctx context.Context) error
	rollbackFn func(ctx context.Context) error
}

func (s *stubTx) Begin(ctx context.Context) (pgx.Tx, error) {
	return nil, errors.New("nested transaction not supported")
}

func (s *stubTx) Commit(ctx context.Context) error {
	if s.commitFn != nil {
		return s.commitFn(ctx)
	}
	return nil
}

func (s *stubTx) Rollback(ctx context.Context) error {
	if s.rollbackFn != nil {
		return s.rollbackFn(ctx)
	}
	return nil
}

func (s *stubTx) CopyFrom(ctx context.Context, tableName pgx.Identifier, columnNames []string, rowSrc pgx.CopyFromSource) (int64, error) {
	return 0, errors.New("copy from not supported")
}

func (s *stubTx) SendBatch(ctx context.Context, b *pgx.Batch) pgx.BatchResults {
	return nil
}

func (s *stubTx) LargeObjects() pgx.LargeObjects {
	return pgx.LargeObjects{}
}

func (s *stubTx) Prepare(ctx context.Context, name, sql string) (*pgconn.StatementDescription, error) {
	return nil, errors.New("prepare not supported")
}

func (s *stubTx) Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error) {
	if s.execFn == nil {
		return pgconn.CommandTag{}, errors.New("unexpected exec")
	}
	return s.execFn(ctx, sql, args...)
}

func (s *stubTx) Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error) {
	if s.queryFn == nil {
		return nil, errors.New("unexpected query")
	}
	return s.queryFn(ctx, sql, args...)
}

func (s *stubTx) QueryRow(ctx context.Context, sql string, args ...any) pgx.Row {
	if s.queryRowFn == nil {
		return stubRow{err: errors.New("unexpected query row")}
	}
	return s.queryRowFn(ctx, sql, args...)
}

func (s *stubTx) Conn() *pgx.Conn {
	return nil
}

type stubRow struct {
	values []any
	err    error
}

func (s stubRow) Scan(dest ...any) error {
	if s.err != nil {
		return s.err
	}
	return scanValues(dest, s.values)
}

type stubRows struct {
	rows   [][]any
	index  int
	closed bool
	err    error
}

func (s *stubRows) Close() {
	s.closed = true
}

func (s *stubRows) Err() error {
	return s.err
}

func (s *stubRows) CommandTag() pgconn.CommandTag {
	return pgconn.CommandTag{}
}

func (s *stubRows) FieldDescriptions() []pgconn.FieldDescription {
	return nil
}

func (s *stubRows) Next() bool {
	if s.index >= len(s.rows) {
		s.closed = true
		return false
	}
	s.index++
	return true
}

func (s *stubRows) Scan(dest ...any) error {
	if s.index == 0 || s.index > len(s.rows) {
		return errors.New("scan called without next")
	}
	return scanValues(dest, s.rows[s.index-1])
}

func (s *stubRows) Values() ([]any, error) {
	if s.index == 0 || s.index > len(s.rows) {
		return nil, errors.New("values called without next")
	}
	return s.rows[s.index-1], nil
}

func (s *stubRows) RawValues() [][]byte {
	return nil
}

func (s *stubRows) Conn() *pgx.Conn {
	return nil
}

func scanValues(dest []any, values []any) error {
	if len(dest) != len(values) {
		return fmt.Errorf("scan: expected %d values, got %d", len(values), len(dest))
	}
	for i, d := range dest {
		if d == nil {
			continue
		}
		dv := reflect.ValueOf(d)
		if dv.Kind() != reflect.Pointer {
			return fmt.Errorf("scan: destination %d not a pointer", i)
		}
		if values[i] == nil {
			dv.Elem().Set(reflect.Zero(dv.Elem().Type()))
			continue
		}
		value := reflect.ValueOf(values[i])
		if value.Type().AssignableTo(dv.Elem().Type()) {
			dv.Elem().Set(value)
			continue
		}
		if value.Type().ConvertibleTo(dv.Elem().Type()) {
			dv.Elem().Set(value.Convert(dv.Elem().Type()))
			continue
		}
		return fmt.Errorf("scan: cannot assign %s to %s", value.Type(), dv.Elem().Type())
	}
	return nil
}
