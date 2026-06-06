package clients

import (
	"context"
	"io"
	"net/http"
	"strings"
	"testing"
)

func jsonResp(body string) roundTripFunc {
	return roundTripFunc(func(*http.Request) (*http.Response, error) {
		return &http.Response{
			StatusCode: http.StatusOK,
			Body:       io.NopCloser(strings.NewReader(body)),
			Header:     make(http.Header),
		}, nil
	})
}

func TestFetchCompanyOverview_Parses(t *testing.T) {
	c := NewAlphaVantageClient("https://x", "key", jsonResp(`{"Symbol":"IBM","Name":"IBM Corp","SharesOutstanding":"900000","DividendYield":"0.05"}`))
	ov, err := c.FetchCompanyOverview(context.Background(), "IBM")
	if err != nil {
		t.Fatalf("err: %v", err)
	}
	if ov == nil || ov.Name != "IBM Corp" || ov.SharesOutstanding != 900000 {
		t.Fatalf("unexpected: %+v", ov)
	}
}

func TestFetchCompanyOverview_EmptyPayload(t *testing.T) {
	c := NewAlphaVantageClient("https://x", "key", jsonResp(`{}`))
	ov, err := c.FetchCompanyOverview(context.Background(), "IBM")
	if err != nil || ov != nil {
		t.Fatalf("expected nil overview, got %+v err %v", ov, err)
	}
}

func TestFetchCompanyOverview_NoAPIKey(t *testing.T) {
	c := NewAlphaVantageClient("https://x", "", jsonResp(`{}`))
	ov, err := c.FetchCompanyOverview(context.Background(), "IBM")
	if err != nil || ov != nil {
		t.Fatalf("no api key should return nil,nil; got %+v %v", ov, err)
	}
}

func TestFetchDaily_Parses(t *testing.T) {
	body := `{"Time Series (Daily)":{"2026-05-08":{"4. close":"151.30","5. volume":"1000"},"2026-05-07":{"4. close":"150.10","5. volume":"2000"}}}`
	c := NewAlphaVantageClient("https://x", "key", jsonResp(body))
	out, err := c.FetchDaily(context.Background(), "IBM")
	if err != nil {
		t.Fatalf("err: %v", err)
	}
	if len(out) != 2 {
		t.Fatalf("expected 2 daily values, got %d", len(out))
	}
}

func TestFetchDaily_NoAPIKey(t *testing.T) {
	c := NewAlphaVantageClient("https://x", "", jsonResp(`{}`))
	out, err := c.FetchDaily(context.Background(), "IBM")
	if err != nil || out != nil {
		t.Fatalf("no api key should return nil,nil; got %v %v", out, err)
	}
}
