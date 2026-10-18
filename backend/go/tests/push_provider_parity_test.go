package tests

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"os"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

func TestSharedPushPreviewHTTP(t *testing.T) {
	var calls atomic.Int64
	h := pushHTTP(t, gatewayFunc(func(context.Context, app.PushAddress, id.ID) (app.PushDisposition, error) {
		calls.Add(1)
		return app.PushAccepted, nil
	}))
	suffix := randomID(t).String()
	for _, name := range []string{"push-owner-" + suffix, "push-foreign-" + suffix} {
		status, _, _ := h.request("POST", "/v1/identity/register", "", `{"login":"`+name+`","display_name":"Person","password":"private test passphrase"}`, nil)
		if status != 201 {
			t.Fatal("register", status)
		}
	}
	owner, foreign := login(t, h, "push-owner-"+suffix), login(t, h, "push-foreign-"+suffix)
	proof, _ := installationProof(t)
	wrong, _ := installationProof(t)
	installation, preview := randomID(t).String(), randomID(t).String()
	create := `{"installation_id":"` + installation + `","registration_id":"` + randomID(t).String() + `","provider":"fcm","application":"dev.example.app","environment":"production","token":"PRIVATE-installation-address"}`
	if status, _, _ := h.request("POST", "/v1/installations", owner.Access, create, map[string]string{"X-Installation-Key": proof}); status != 201 {
		t.Fatal("installation", status)
	}
	wire, err := os.ReadFile("../../../contracts/fixtures/backend-push-providers/http.json")
	if err != nil {
		t.Fatal(err)
	}
	var cases []map[string]json.RawMessage
	if err = json.Unmarshal(wire, &cases); err != nil {
		t.Fatal(err)
	}
	stringField := func(row map[string]json.RawMessage, key, fallback string) string {
		var result string
		if raw, ok := row[key]; ok {
			if json.Unmarshal(raw, &result) != nil {
				t.Fatal("fixture", key)
			}
			return result
		}
		return fallback
	}
	for _, row := range cases {
		t.Run(stringField(row, "name", ""), func(t *testing.T) {
			method := stringField(row, "method", "POST")
			token := owner.Access
			switch stringField(row, "auth", "owner") {
			case "anonymous":
				token = ""
			case "foreign":
				token = foreign.Access
			}
			body := strings.ReplaceAll(stringField(row, "body", `{"preview_id":"$PREVIEW$"}`), "$PREVIEW$", preview)
			path := "/v1/installations/" + installation + "/push-preview" + stringField(row, "query", "")
			request, e := http.NewRequest(method, h.address+path, strings.NewReader(body))
			if e != nil {
				t.Fatal(e)
			}
			request.Header.Set("Content-Type", "application/json")
			if token != "" {
				request.Header.Set("Authorization", "Bearer "+token)
			}
			switch stringField(row, "proof", "") {
			case "missing":
			case "wrong":
				request.Header.Set("X-Installation-Key", wrong)
			default:
				request.Header.Set("X-Installation-Key", proof)
			}
			if value, ok := row["if_match"]; !ok {
				request.Header.Set("If-Match", `"1"`)
			} else if string(value) != "null" {
				var version string
				_ = json.Unmarshal(value, &version)
				request.Header.Set("If-Match", version)
			}
			if extra, ok := row["headers"]; ok {
				var headers [][2]string
				if json.Unmarshal(extra, &headers) != nil {
					t.Fatal("fixture headers")
				}
				for _, pair := range headers {
					request.Header.Add(pair[0], strings.ReplaceAll(pair[1], "$PROOF$", proof))
				}
			}
			before := calls.Load()
			client := http.Client{Timeout: 17 * time.Second}
			response, e := client.Do(request)
			if e != nil {
				t.Fatal(e)
			}
			defer response.Body.Close()
			wire, e := io.ReadAll(response.Body)
			if e != nil {
				t.Fatal(e)
			}
			var expected int
			_ = json.Unmarshal(row["status"], &expected)
			if response.StatusCode != expected {
				t.Fatalf("status %d expected %d", response.StatusCode, expected)
			}
			sends := int64(0)
			if expected == 200 {
				sends = 1
				var value map[string]any
				if json.Unmarshal(wire, &value) != nil || len(value) != 3 || value["preview_id"] != preview || value["disposition"] != "accepted" || value["retired"] != false || response.Header.Get("ETag") != "" || response.Header.Get("Cache-Control") != "no-store" {
					t.Fatal("preview projection")
				}
			}
			if expected == 405 && response.Header.Get("Allow") != "POST" {
				t.Fatal("method admission")
			}
			if calls.Load()-before != sends {
				t.Fatal("guard or retry failed")
			}
			if strings.Contains(string(wire), proof) || strings.Contains(string(wire), "PRIVATE-installation-address") {
				t.Fatal("private response")
			}
		})
	}
}
