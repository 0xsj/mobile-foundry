package security

import (
	"context"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	"strings"
)

type InstallationProofs struct{}

func (InstallationProofs) Digest(ctx context.Context, secret string) (string, error) {
	if err := ctx.Err(); err != nil {
		return "", err
	}
	if len(secret) != 46 || !strings.HasPrefix(secret, "i1.") {
		return "", app.ErrInput
	}
	bytes, err := base64.RawURLEncoding.Strict().DecodeString(secret[3:])
	if err != nil || len(bytes) != 32 || base64.RawURLEncoding.EncodeToString(bytes) != secret[3:] {
		return "", app.ErrInput
	}
	hash := sha256.Sum256([]byte("notifications.installation-proof.v1\x00" + secret))
	return hex.EncodeToString(hash[:]), nil
}
