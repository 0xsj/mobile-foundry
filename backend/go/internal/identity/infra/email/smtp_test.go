package email

import (
	"context"
	"fmt"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"testing"
)

func TestSMTPRequiresEncryptedProductionAndRedactsConfig(t *testing.T) {
	config := Config{Address: "localhost:1025", From: "security@example.test", Mode: "development"}
	if _, err := New(config, false); err == nil {
		t.Fatal("plaintext selected for production")
	}
	if _, err := New(config, true); err != nil {
		t.Fatal(err)
	}
	config.Mode = "tls"
	config.Password = "private-config-password"
	config.Username = "private-user"
	sender, err := New(config, false)
	if err != nil {
		t.Fatal(err)
	}
	if text := fmt.Sprintf("%v %#v %v %#v", config, config, sender, sender); text != "identity.EmailConfig{redacted} identity.EmailConfig{redacted} identity.SMTP{redacted} identity.SMTP{redacted}" {
		t.Fatal("configuration formatting not redacted")
	}
	if err = sender.Send(context.Background(), app.Mail{To: "victim@example.test\r\nBcc: other@example.test", Purpose: "verify_email", Token: "bad"}); err == nil {
		t.Fatal("mail header injection admitted")
	}
}
