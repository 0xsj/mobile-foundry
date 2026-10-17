package httpx

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestProxyRequiresExactTrustedPeerAndSingleAddress(t *testing.T) {
	for _, test := range []struct {
		peer, forward, want string
		status              int
	}{{"127.0.0.1:1234", "203.0.113.9", "203.0.113.9:1234", 200}, {"192.0.2.9:1234", "203.0.113.9", "192.0.2.9:1234", 200}, {"127.0.0.1:1234", "spoof, 203.0.113.9", "", 400}} {
		handler := TrustedProxy(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.RemoteAddr != test.want {
				t.Fatalf("proxy admission: %q", r.RemoteAddr)
			}
			w.WriteHeader(200)
		}), "127.0.0.1/32")
		request := httptest.NewRequest("GET", "http://local", nil)
		request.RemoteAddr = test.peer
		request.Header.Set("X-Forwarded-For", test.forward)
		response := httptest.NewRecorder()
		handler.ServeHTTP(response, request)
		if response.Code != test.status {
			t.Fatal(response.Code)
		}
	}
}
