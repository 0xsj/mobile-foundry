package records

import (
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/domain"
	"strconv"
	"strings"
	"time"
)

func ProofMatches(want, got string) bool {
	return len(want) == 64 && len(got) == 64 && subtle.ConstantTimeCompare([]byte(want), []byte(got)) == 1
}
func InstallationTarget(s domain.InstallationSpec) string {
	sum := sha256.Sum256([]byte(strings.Join([]string{"notifications.target.v1", s.Route.Provider, s.Route.Application, s.Route.Environment, s.Token}, "\x00")))
	return hex.EncodeToString(sum[:])
}
func RevocationFingerprint(r app.SessionRevocation) string {
	sum := sha256.Sum256([]byte(strings.Join([]string{"notifications.session-revocation.v1", r.EventID.String(), r.PrincipalID.String(), r.SessionID.String(), strconv.FormatUint(r.Version, 10), r.Reason, r.OccurredAt.UTC().Truncate(time.Microsecond).Format(time.RFC3339Nano)}, "\x00")))
	return hex.EncodeToString(sum[:])
}
