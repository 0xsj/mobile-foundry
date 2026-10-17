// Package telemetry exposes bounded HTTP counters without paths, identities or credentials as labels.
package telemetry

import (
	"bufio"
	"fmt"
	"net"
	"net/http"
	"sync"
	"time"
)

type Recorder struct {
	mu       sync.Mutex
	counts   map[string]uint64
	seconds  float64
	requests uint64
	active   int64
}

func New() *Recorder { return &Recorder{counts: make(map[string]uint64)} }

type response struct {
	http.ResponseWriter
	status int
}

func (w *response) Unwrap() http.ResponseWriter { return w.ResponseWriter }
func (w *response) WriteHeader(status int) {
	if w.status == 0 {
		w.status = status
		w.ResponseWriter.WriteHeader(status)
	}
}
func (w *response) Write(b []byte) (int, error) {
	if w.status == 0 {
		w.WriteHeader(200)
	}
	return w.ResponseWriter.Write(b)
}
func (w *response) Hijack() (net.Conn, *bufio.ReadWriter, error) {
	c, b, err := http.NewResponseController(w.ResponseWriter).Hijack()
	if err == nil {
		w.status = 101
	}
	return c, b, err
}
func (r *Recorder) Wrap(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, q *http.Request) {
		started := time.Now()
		r.mu.Lock()
		r.active++
		r.mu.Unlock()
		view := &response{ResponseWriter: w}
		defer func() {
			status := view.status
			if status == 0 {
				status = 200
			}
			method := q.Method
			switch method {
			case "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS":
			default:
				method = "OTHER"
			}
			key := fmt.Sprintf("method=%q,status=%q", method, fmt.Sprintf("%dxx", status/100))
			r.mu.Lock()
			r.active--
			r.counts[key]++
			r.requests++
			r.seconds += time.Since(started).Seconds()
			r.mu.Unlock()
		}()
		next.ServeHTTP(view, q)
	})
}
func (r *Recorder) ServeHTTP(w http.ResponseWriter, q *http.Request) {
	if q.Method != "GET" || q.URL.Path != "/metrics" {
		http.NotFound(w, q)
		return
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	w.Header().Set("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	fmt.Fprintln(w, "# TYPE api_http_requests_total counter")
	for labels, count := range r.counts {
		fmt.Fprintf(w, "api_http_requests_total{%s} %d\n", labels, count)
	}
	fmt.Fprintf(w, "# TYPE api_http_active gauge\napi_http_active %d\n# TYPE api_http_duration_seconds summary\napi_http_duration_seconds_sum %g\napi_http_duration_seconds_count %d\n", r.active, r.seconds, r.requests)
}
