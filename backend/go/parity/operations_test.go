package parity

import (
	"encoding/json"
	"github.com/0xsj/mobile-foundry/backend/pkg/httpx"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
)

func TestSharedTrustedProxy(t *testing.T) {
	data, err := os.ReadFile("../../../contracts/fixtures/backend-operations/proxy.json")
	if err != nil {
		t.Fatal(err)
	}
	var cases []struct {
		Name, CIDRs, Peer, IP string
		Headers               []string
		Status                int
	}
	if err = json.Unmarshal(data, &cases); err != nil {
		t.Fatal(err)
	}
	for _, c := range cases {
		t.Run(c.Name, func(t *testing.T) {
			var actual string
			handler := httpx.TrustedProxy(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				actual, _, _ = net.SplitHostPort(r.RemoteAddr)
				w.WriteHeader(200)
			}), c.CIDRs)
			request := httptest.NewRequest("GET", "http://local/", nil)
			request.RemoteAddr = net.JoinHostPort(c.Peer, "1234")
			for _, v := range c.Headers {
				request.Header.Add("X-Forwarded-For", v)
			}
			response := httptest.NewRecorder()
			handler.ServeHTTP(response, request)
			if response.Code != c.Status || (c.Status == 200 && actual != c.IP) {
				t.Fatalf("status/address: %d %q", response.Code, actual)
			}
		})
	}
}
