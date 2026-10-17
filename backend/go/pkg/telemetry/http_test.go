package telemetry

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestMetricsKeepBoundedLabelsAndExcludePrivatePaths(t *testing.T) {
	r := New()
	handler := r.Wrap(http.HandlerFunc(func(w http.ResponseWriter, q *http.Request) { w.WriteHeader(201) }))
	handler.ServeHTTP(httptest.NewRecorder(), httptest.NewRequest("POST", "http://example.test/private-user?token=private-secret", nil))
	out := httptest.NewRecorder()
	r.ServeHTTP(out, httptest.NewRequest("GET", "http://local/metrics", nil))
	text := out.Body.String()
	if !strings.Contains(text, `api_http_requests_total{method="POST",status="2xx"} 1`) || strings.Contains(text, "private-") || !strings.Contains(text, "api_http_active 0") {
		t.Fatal(text)
	}
}
