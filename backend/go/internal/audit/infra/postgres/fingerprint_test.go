package postgres

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"testing"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/audit/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

func TestAccountEvidenceFingerprintSurvivesPrincipalExtension(t *testing.T) {
	legacy := struct {
		EventID, AccountID, ActorID id.ID
		Action                      string
		Version                     uint64
		OccurredAt                  time.Time
		RequestID, CorrelationID    string
	}{id.ID("00000000-0000-4000-8000-000000000001"), id.ID("00000000-0000-4000-8000-000000000002"), id.ID("00000000-0000-4000-8000-000000000003"), "account.created", 1, time.Date(2026, 10, 13, 0, 0, 0, 0, time.UTC), "request.1", "correlation.1"}
	wire, _ := json.Marshal(legacy)
	digest := sha256.Sum256(wire)
	current := domain.Evidence{EventID: legacy.EventID, AccountID: legacy.AccountID, ActorID: legacy.ActorID, Action: legacy.Action, Version: legacy.Version, OccurredAt: legacy.OccurredAt, RequestID: legacy.RequestID, CorrelationID: legacy.CorrelationID}
	got, err := fingerprint(current)
	if err != nil || got != hex.EncodeToString(digest[:]) {
		t.Fatal("existing inbox identity would conflict after upgrade", err)
	}
}
