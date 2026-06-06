package http

import (
	"encoding/base64"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// ---------------------------------------------------------------------------
// claimAsInt
// ---------------------------------------------------------------------------

func TestClaimAsInt_Float64_ReturnsValue(t *testing.T) {
	t.Parallel()
	got, ok := claimAsInt(float64(42))
	require.True(t, ok)
	assert.Equal(t, int64(42), got)
}

func TestClaimAsInt_Int64_ReturnsValue(t *testing.T) {
	t.Parallel()
	got, ok := claimAsInt(int64(7))
	require.True(t, ok)
	assert.Equal(t, int64(7), got)
}

func TestClaimAsInt_JSONNumber_ReturnsValue(t *testing.T) {
	t.Parallel()
	got, ok := claimAsInt(json.Number("123"))
	require.True(t, ok)
	assert.Equal(t, int64(123), got)
}

func TestClaimAsInt_JSONNumberInvalid_ReturnsFalse(t *testing.T) {
	t.Parallel()
	_, ok := claimAsInt(json.Number("not-a-number"))
	assert.False(t, ok)
}

func TestClaimAsInt_NumericString_ReturnsValue(t *testing.T) {
	t.Parallel()
	got, ok := claimAsInt("99")
	require.True(t, ok)
	assert.Equal(t, int64(99), got)
}

func TestClaimAsInt_NonNumericString_ReturnsFalse(t *testing.T) {
	t.Parallel()
	_, ok := claimAsInt("abc")
	assert.False(t, ok)
}

func TestClaimAsInt_UnsupportedType_ReturnsFalse(t *testing.T) {
	t.Parallel()
	_, ok := claimAsInt(map[string]any{})
	assert.False(t, ok)
}

func TestClaimAsInt_Nil_ReturnsFalse(t *testing.T) {
	t.Parallel()
	_, ok := claimAsInt(nil)
	assert.False(t, ok)
}

// ---------------------------------------------------------------------------
// claimAsStrings
// ---------------------------------------------------------------------------

func TestClaimAsStrings_SliceOfAny_FiltersStrings(t *testing.T) {
	t.Parallel()
	got := claimAsStrings([]any{"A", 1, "B", true})
	assert.Equal(t, []string{"A", "B"}, got)
}

func TestClaimAsStrings_SliceOfString_ReturnsSame(t *testing.T) {
	t.Parallel()
	got := claimAsStrings([]string{"X", "Y"})
	assert.Equal(t, []string{"X", "Y"}, got)
}

func TestClaimAsStrings_EmptyString_ReturnsNil(t *testing.T) {
	t.Parallel()
	assert.Nil(t, claimAsStrings(""))
}

func TestClaimAsStrings_SingleRole_ReturnsSlice(t *testing.T) {
	t.Parallel()
	assert.Equal(t, []string{"SERVICE"}, claimAsStrings("SERVICE"))
}

func TestClaimAsStrings_CommaSeparated_Splits(t *testing.T) {
	t.Parallel()
	assert.Equal(t, []string{"A", "B", "C"}, claimAsStrings("A,B,C"))
}

func TestClaimAsStrings_SpaceSeparated_Splits(t *testing.T) {
	t.Parallel()
	assert.Equal(t, []string{"A", "B"}, claimAsStrings("A B"))
}

func TestClaimAsStrings_MixedSeparatorsWithBlanks_Splits(t *testing.T) {
	t.Parallel()
	assert.Equal(t, []string{"A", "B"}, claimAsStrings("A, ,B"))
}

func TestClaimAsStrings_UnsupportedType_ReturnsNil(t *testing.T) {
	t.Parallel()
	assert.Nil(t, claimAsStrings(42))
}

// ---------------------------------------------------------------------------
// rolesFromHeader
// ---------------------------------------------------------------------------

func TestRolesFromHeader_UserRolesHeader_Parsed(t *testing.T) {
	t.Parallel()
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.Header.Set("X-User-Roles", "ADMIN, SERVICE ,")
	assert.Equal(t, []string{"ADMIN", "SERVICE"}, rolesFromHeader(req))
}

func TestRolesFromHeader_FallbackRolesHeader_Parsed(t *testing.T) {
	t.Parallel()
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.Header.Set("X-Roles", "CLIENT")
	assert.Equal(t, []string{"CLIENT"}, rolesFromHeader(req))
}

func TestRolesFromHeader_NoHeaders_ReturnsNil(t *testing.T) {
	t.Parallel()
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	assert.Nil(t, rolesFromHeader(req))
}

// ---------------------------------------------------------------------------
// bearerToken
// ---------------------------------------------------------------------------

func TestBearerToken_ValidHeader_ReturnsToken(t *testing.T) {
	t.Parallel()
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.Header.Set("Authorization", "Bearer abc.def.ghi")
	assert.Equal(t, "abc.def.ghi", bearerToken(req))
}

func TestBearerToken_CaseInsensitivePrefix_ReturnsToken(t *testing.T) {
	t.Parallel()
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.Header.Set("Authorization", "bearer tok")
	assert.Equal(t, "tok", bearerToken(req))
}

func TestBearerToken_NoBearerPrefix_ReturnsEmpty(t *testing.T) {
	t.Parallel()
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.Header.Set("Authorization", "Basic xyz")
	assert.Equal(t, "", bearerToken(req))
}

func TestBearerToken_Missing_ReturnsEmpty(t *testing.T) {
	t.Parallel()
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	assert.Equal(t, "", bearerToken(req))
}

// ---------------------------------------------------------------------------
// expired
// ---------------------------------------------------------------------------

func TestExpired_NilValue_ReturnsFalse(t *testing.T) {
	t.Parallel()
	assert.False(t, expired(nil, time.Now()))
}

func TestExpired_FutureExp_ReturnsFalse(t *testing.T) {
	t.Parallel()
	future := float64(time.Now().Add(time.Hour).Unix())
	assert.False(t, expired(future, time.Now()))
}

func TestExpired_PastExp_ReturnsTrue(t *testing.T) {
	t.Parallel()
	past := float64(time.Now().Add(-time.Hour).Unix())
	assert.True(t, expired(past, time.Now()))
}

func TestExpired_UnparseableValue_ReturnsTrue(t *testing.T) {
	t.Parallel()
	assert.True(t, expired("not-a-number", time.Now()))
}

// ---------------------------------------------------------------------------
// decodeJWTPart
// ---------------------------------------------------------------------------

func TestDecodeJWTPart_RawURLEncoding_Decodes(t *testing.T) {
	t.Parallel()
	encoded := base64.RawURLEncoding.EncodeToString([]byte("hello"))
	got, ok := decodeJWTPart(encoded)
	require.True(t, ok)
	assert.Equal(t, "hello", string(got))
}

func TestDecodeJWTPart_StdURLEncodingFallback_Decodes(t *testing.T) {
	t.Parallel()
	encoded := base64.URLEncoding.EncodeToString([]byte("padded!!"))
	got, ok := decodeJWTPart(encoded)
	require.True(t, ok)
	assert.Equal(t, "padded!!", string(got))
}

func TestDecodeJWTPart_Invalid_ReturnsFalse(t *testing.T) {
	t.Parallel()
	_, ok := decodeJWTPart("!!!not base64!!!")
	assert.False(t, ok)
}

// ---------------------------------------------------------------------------
// hasServiceRole
// ---------------------------------------------------------------------------

func TestHasServiceRole_PlainService_True(t *testing.T) {
	t.Parallel()
	assert.True(t, hasServiceRole([]string{"USER", "service"}))
}

func TestHasServiceRole_RolePrefix_True(t *testing.T) {
	t.Parallel()
	assert.True(t, hasServiceRole([]string{"ROLE_SERVICE"}))
}

func TestHasServiceRole_NoService_False(t *testing.T) {
	t.Parallel()
	assert.False(t, hasServiceRole([]string{"USER", "ADMIN"}))
}

func TestHasServiceRole_Empty_False(t *testing.T) {
	t.Parallel()
	assert.False(t, hasServiceRole(nil))
}

// ---------------------------------------------------------------------------
// principalFromRequest — header-based identity (no token)
// ---------------------------------------------------------------------------

func TestPrincipalFromRequest_XUserIdHeader_ReturnsPrincipal(t *testing.T) {
	t.Parallel()
	h := &Handler{cfg: testAuthConfig()}
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.Header.Set("X-User-Id", "55")
	req.Header.Set("X-User-Roles", "CLIENT")
	rec := httptest.NewRecorder()

	principal, ok := h.principalFromRequest(rec, req, true)
	require.True(t, ok)
	assert.Equal(t, int64(55), principal.ID)
	assert.Equal(t, []string{"CLIENT"}, principal.Roles)
}

func TestPrincipalFromRequest_XClientIdHeader_ReturnsPrincipal(t *testing.T) {
	t.Parallel()
	h := &Handler{cfg: testAuthConfig()}
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.Header.Set("X-Client-Id", "77")
	rec := httptest.NewRecorder()

	principal, ok := h.principalFromRequest(rec, req, true)
	require.True(t, ok)
	assert.Equal(t, int64(77), principal.ID)
}

func TestPrincipalFromRequest_RequiredButMissing_Writes401(t *testing.T) {
	t.Parallel()
	h := &Handler{cfg: testAuthConfig()}
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	rec := httptest.NewRecorder()

	_, ok := h.principalFromRequest(rec, req, true)
	assert.False(t, ok)
	assert.Equal(t, http.StatusUnauthorized, rec.Code)
}

func TestPrincipalFromRequest_NotRequiredAndMissing_ReturnsEmptyOK(t *testing.T) {
	t.Parallel()
	h := &Handler{cfg: testAuthConfig()}
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	rec := httptest.NewRecorder()

	principal, ok := h.principalFromRequest(rec, req, false)
	assert.True(t, ok)
	assert.Equal(t, int64(0), principal.ID)
}

func TestPrincipalFromRequest_InvalidHeaderIDRequired_Writes401(t *testing.T) {
	t.Parallel()
	h := &Handler{cfg: testAuthConfig()}
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.Header.Set("X-User-Id", "not-a-number")
	rec := httptest.NewRecorder()

	_, ok := h.principalFromRequest(rec, req, true)
	assert.False(t, ok)
	assert.Equal(t, http.StatusUnauthorized, rec.Code)
}

func TestPrincipalFromRequest_InvalidTokenRequired_Writes401(t *testing.T) {
	t.Parallel()
	h := &Handler{cfg: testAuthConfig()}
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.Header.Set("Authorization", "Bearer not.a.validtoken")
	rec := httptest.NewRecorder()

	_, ok := h.principalFromRequest(rec, req, true)
	assert.False(t, ok)
	assert.Equal(t, http.StatusUnauthorized, rec.Code)
}

func TestPrincipalFromRequest_ValidTokenNoRolesUsesHeaderRoles(t *testing.T) {
	t.Parallel()
	h := &Handler{cfg: testAuthConfig()}
	token := signedTestJWT(t, h.cfg.JWTSecret, map[string]any{
		"id":  9,
		"exp": time.Now().Add(time.Hour).Unix(),
	})
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("X-User-Roles", "FALLBACK")
	rec := httptest.NewRecorder()

	principal, ok := h.principalFromRequest(rec, req, true)
	require.True(t, ok)
	assert.Equal(t, int64(9), principal.ID)
	assert.Equal(t, []string{"FALLBACK"}, principal.Roles)
}

// ---------------------------------------------------------------------------
// verifiedJWTClaims — failure branches
// ---------------------------------------------------------------------------

func TestVerifiedJWTClaims_WrongPartCount_ReturnsFalse(t *testing.T) {
	t.Parallel()
	h := &Handler{cfg: testAuthConfig()}
	_, ok := h.verifiedJWTClaims("only.two")
	assert.False(t, ok)
}

func TestVerifiedJWTClaims_EmptySecret_ReturnsFalse(t *testing.T) {
	t.Parallel()
	cfg := testAuthConfig()
	cfg.JWTSecret = ""
	h := &Handler{cfg: cfg}
	_, ok := h.verifiedJWTClaims("a.b.c")
	assert.False(t, ok)
}

func TestVerifiedJWTClaims_NonHS256Alg_ReturnsFalse(t *testing.T) {
	t.Parallel()
	h := &Handler{cfg: testAuthConfig()}
	header := base64.RawURLEncoding.EncodeToString([]byte(`{"alg":"none"}`))
	payload := base64.RawURLEncoding.EncodeToString([]byte(`{"id":1}`))
	sig := base64.RawURLEncoding.EncodeToString([]byte("x"))
	_, ok := h.verifiedJWTClaims(header + "." + payload + "." + sig)
	assert.False(t, ok)
}

func TestVerifiedJWTClaims_ExpiredToken_ReturnsFalse(t *testing.T) {
	t.Parallel()
	h := &Handler{cfg: testAuthConfig()}
	token := signedTestJWT(t, h.cfg.JWTSecret, map[string]any{
		"id":  1,
		"exp": time.Now().Add(-time.Hour).Unix(),
	})
	_, ok := h.verifiedJWTClaims(token)
	assert.False(t, ok)
}

func TestVerifiedJWTClaims_ValidToken_ReturnsClaims(t *testing.T) {
	t.Parallel()
	h := &Handler{cfg: testAuthConfig()}
	token := signedTestJWT(t, h.cfg.JWTSecret, map[string]any{
		"id":  1,
		"exp": time.Now().Add(time.Hour).Unix(),
	})
	claims, ok := h.verifiedJWTClaims(token)
	require.True(t, ok)
	assert.NotNil(t, claims["id"])
}

// ---------------------------------------------------------------------------
// rolesFromClaims — fallback to "roles" when configured claim is empty
// ---------------------------------------------------------------------------

func TestRolesFromClaims_FallsBackToRolesKey(t *testing.T) {
	t.Parallel()
	cfg := testAuthConfig()
	cfg.JWTRoleClaim = "authorities"
	h := &Handler{cfg: cfg}
	roles := h.rolesFromClaims(map[string]any{"roles": "SERVICE"})
	assert.Equal(t, []string{"SERVICE"}, roles)
}

func TestRolesFromClaims_UsesConfiguredClaim(t *testing.T) {
	t.Parallel()
	cfg := testAuthConfig()
	cfg.JWTRoleClaim = "authorities"
	h := &Handler{cfg: cfg}
	roles := h.rolesFromClaims(map[string]any{"authorities": []any{"ADMIN"}})
	assert.Equal(t, []string{"ADMIN"}, roles)
}
