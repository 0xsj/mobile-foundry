package records

import (
	"crypto/sha256"
	"encoding/hex"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"strings"
	"time"
)

// v1's identifiers cannot contain NUL. Recording time and preferences are not
// source input; duplicate delivery compares the original admitted fact only.
func Fingerprint(d app.Delivery) string {
	wire := strings.Join([]string{"notifications.files.v1", d.EventID.String(), d.AccountID.String(), d.PrincipalID.String(), d.FileID.String(), d.OccurredAt.UTC().Truncate(time.Microsecond).Format(time.RFC3339Nano)}, "\x00")
	sum := sha256.Sum256([]byte(wire))
	return hex.EncodeToString(sum[:])
}
