package account

import (
	"context"
	"database/sql"
	"database/sql/driver"
	"io"
	"math/rand"
	"sync"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// ---------------------------------------------------------------------------
// Minimal stdlib-only fake database/sql driver.
//
// It lets us exercise GenerateMONAS' DB-dependent paths without any external
// mock library. Each SELECT EXISTS(...) query returns the next bool from
// fakeExistsResults; once exhausted it returns the last value forever. When
// fakeQueryErr is set, Query returns that error instead.
// ---------------------------------------------------------------------------

type fakeDriver struct{}

type fakeConn struct{}

type fakeStmt struct{}

type fakeRows struct {
	value any
	done  bool
}

var (
	fakeMu            sync.Mutex
	fakeExistsResults []bool
	fakeExistsIdx     int
	fakeQueryErr      error
	fakeRegisterOnce  sync.Once
)

func registerFakeDriver(t *testing.T) *sql.DB {
	t.Helper()
	fakeRegisterOnce.Do(func() {
		sql.Register("fakemonas", fakeDriver{})
	})
	db, err := sql.Open("fakemonas", "")
	require.NoError(t, err)
	t.Cleanup(func() { _ = db.Close() })
	return db
}

func (fakeDriver) Open(string) (driver.Conn, error) { return fakeConn{}, nil }

func (fakeConn) Prepare(string) (driver.Stmt, error) { return fakeStmt{}, nil }
func (fakeConn) Close() error                        { return nil }
func (fakeConn) Begin() (driver.Tx, error)           { return nil, io.EOF }

func (fakeStmt) Close() error  { return nil }
func (fakeStmt) NumInput() int { return -1 }
func (fakeStmt) Exec([]driver.Value) (driver.Result, error) {
	return nil, io.EOF
}

func (fakeStmt) Query([]driver.Value) (driver.Rows, error) {
	fakeMu.Lock()
	defer fakeMu.Unlock()
	if fakeQueryErr != nil {
		return nil, fakeQueryErr
	}
	v := false
	if len(fakeExistsResults) > 0 {
		if fakeExistsIdx >= len(fakeExistsResults) {
			v = fakeExistsResults[len(fakeExistsResults)-1]
		} else {
			v = fakeExistsResults[fakeExistsIdx]
			fakeExistsIdx++
		}
	}
	return &fakeRows{value: v}, nil
}

func (r *fakeRows) Columns() []string { return []string{"exists"} }
func (r *fakeRows) Close() error      { return nil }
func (r *fakeRows) Next(dest []driver.Value) error {
	if r.done {
		return io.EOF
	}
	r.done = true
	dest[0] = r.value
	return nil
}

func setFakeExists(results []bool, queryErr error) {
	fakeMu.Lock()
	defer fakeMu.Unlock()
	fakeExistsResults = results
	fakeExistsIdx = 0
	fakeQueryErr = queryErr
}

// ---------------------------------------------------------------------------
// GenerateMONAS — DB-backed paths
// ---------------------------------------------------------------------------

func TestGenerateMONAS_FirstCandidateFree_ReturnsValidNumber(t *testing.T) {
	db := registerFakeDriver(t)
	setFakeExists([]bool{false}, nil)

	number, err := GenerateMONAS(context.Background(), db, "11", rand.New(rand.NewSource(1)))
	require.NoError(t, err)
	assert.Len(t, number, 19)
	assert.True(t, ValidateMONAS(number), "generated number should be a valid MONAS: %s", number)
}

func TestGenerateMONAS_NilRandom_UsesDefault(t *testing.T) {
	db := registerFakeDriver(t)
	setFakeExists([]bool{false}, nil)

	number, err := GenerateMONAS(context.Background(), db, "21", nil)
	require.NoError(t, err)
	assert.True(t, ValidateMONAS(number))
}

func TestGenerateMONAS_FirstTakenThenFree_RetriesAndSucceeds(t *testing.T) {
	db := registerFakeDriver(t)
	setFakeExists([]bool{true, false}, nil)

	number, err := GenerateMONAS(context.Background(), db, "11", rand.New(rand.NewSource(2)))
	require.NoError(t, err)
	assert.True(t, ValidateMONAS(number))
}

func TestGenerateMONAS_QueryError_ReturnsError(t *testing.T) {
	db := registerFakeDriver(t)
	setFakeExists(nil, io.ErrUnexpectedEOF)

	_, err := GenerateMONAS(context.Background(), db, "11", rand.New(rand.NewSource(3)))
	assert.Error(t, err)
}

func TestGenerateMONAS_AllCandidatesTaken_ReturnsGenerationFailed(t *testing.T) {
	db := registerFakeDriver(t)
	setFakeExists([]bool{true}, nil) // always taken

	_, err := GenerateMONAS(context.Background(), db, "11", rand.New(rand.NewSource(4)))
	assert.ErrorIs(t, err, ErrMONASGenerationFailed)
}

// ---------------------------------------------------------------------------
// DigitSum
// ---------------------------------------------------------------------------

func TestDigitSum_MixedCharacters_SumsDigitsOnly(t *testing.T) {
	t.Parallel()
	assert.Equal(t, 6, DigitSum("1a2b3"))
}

func TestDigitSum_Empty_ReturnsZero(t *testing.T) {
	t.Parallel()
	assert.Equal(t, 0, DigitSum(""))
}

// ---------------------------------------------------------------------------
// NumberGenerator.Generate — valid mod-11 account number
// ---------------------------------------------------------------------------

func TestNumberGenerator_Generate_Produces16DigitNumber(t *testing.T) {
	t.Parallel()
	number, err := NumberGenerator{}.Generate()
	require.NoError(t, err)
	assert.Len(t, number, 16)
	for _, ch := range number {
		assert.True(t, ch >= '0' && ch <= '9', "expected digit, got %q", ch)
	}
}
