package principal

import (
	"context"
	"crypto/subtle"

	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

// Development is an explicit single-credential fixture, not an Identity authority.
// Composition must restrict it to development/test and never log its credential.
type Development struct {
	token    string
	identity id.ID
}

func NewDevelopment(token string, identity id.ID) (*Development, error) {
	if len(token) < 8 || len(token) > 128 || !identity.Valid() {
		return nil, ErrRequired
	}
	return &Development{token: token, identity: identity}, nil
}
func (d *Development) Resolve(ctx context.Context, token string) (Principal, error) {
	if err := ctx.Err(); err != nil {
		return Principal{}, err
	}
	if subtle.ConstantTimeCompare([]byte(token), []byte(d.token)) != 1 {
		return Principal{}, ErrRequired
	}
	return Principal{ID: d.identity}, nil
}
