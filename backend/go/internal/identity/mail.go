package identity

import (
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/infra/email"
)

func LoadMailer(path string, development bool) (app.Mailer, error) {
	return email.Load(path, development)
}
