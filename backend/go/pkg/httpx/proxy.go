package httpx

import (
	"net"
	"net/http"
	"strings"
)

// TrustedProxy accepts only a single canonical forwarded address from explicitly
// allowed peers. The edge must overwrite X-Forwarded-For, not append client input.
func TrustedProxy(next http.Handler, cidrs string) http.Handler {
	var networks []*net.IPNet
	for _, cidr := range strings.Split(cidrs, ",") {
		_, network, err := net.ParseCIDR(cidr)
		if err == nil {
			networks = append(networks, network)
		}
	}
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		host, port, err := net.SplitHostPort(r.RemoteAddr)
		if err == nil {
			peer := net.ParseIP(host)
			trusted := false
			for _, network := range networks {
				trusted = trusted || network.Contains(peer)
			}
			if trusted {
				values := r.Header.Values("X-Forwarded-For")
				if len(values) != 1 || net.ParseIP(values[0]) == nil {
					report(w, r, ErrInvalidJSON)
					return
				}
				copy := r.Clone(r.Context())
				copy.RemoteAddr = net.JoinHostPort(net.ParseIP(values[0]).String(), port)
				r = copy
			}
		}
		next.ServeHTTP(w, r)
	})
}
