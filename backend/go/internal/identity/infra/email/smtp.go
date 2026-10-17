package email

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	"io"
	"net"
	"net/smtp"
	"os"
	"strings"
	"time"
)

type Config struct{ Address, From, Username, Password, Mode string }

func (Config) String() string   { return "identity.EmailConfig{redacted}" }
func (Config) GoString() string { return "identity.EmailConfig{redacted}" }

type Disabled struct{}

func (Disabled) Send(context.Context, app.Mail) error { return app.ErrMail }

// Sender retains private configuration; it never formats credentials or message bodies.
type Sender struct {
	work   chan struct{}
	config Config
	host   string
}

func New(c Config, development bool) (*Sender, error) {
	host, _, err := net.SplitHostPort(c.Address)
	address, e := domain.Email(c.From)
	if err != nil || host == "" || e != nil || address != c.From || strings.ContainsAny(c.Username+c.Password, "\r\n") || len(c.Username) > 256 || len(c.Password) > 1024 {
		return nil, app.ErrMail
	}
	if c.Mode != "starttls" && c.Mode != "tls" && !(development && c.Mode == "development") {
		return nil, app.ErrMail
	}
	if (c.Username == "") != (c.Password == "") {
		return nil, app.ErrMail
	}
	return &Sender{config: c, host: host, work: make(chan struct{}, 4)}, nil
}
func Load(path string, development bool) (app.Mailer, error) {
	if path == "" {
		return Disabled{}, nil
	}
	f, err := os.Open(path)
	if err != nil {
		return nil, app.ErrMail.WithCause(err)
	}
	defer f.Close()
	data, err := io.ReadAll(io.LimitReader(f, 16_385))
	if err != nil || len(data) > 16_384 {
		return nil, app.ErrMail
	}
	decoder := json.NewDecoder(strings.NewReader(string(data)))
	decoder.DisallowUnknownFields()
	var c Config
	if decoder.Decode(&c) != nil {
		return nil, app.ErrMail
	}
	var extra any
	if decoder.Decode(&extra) != io.EOF {
		return nil, app.ErrMail
	}
	return New(c, development)
}
func (s *Sender) Send(ctx context.Context, m app.Mail) error {
	select {
	case s.work <- struct{}{}:
		defer func() { <-s.work }()
	default:
		return app.ErrMail
	}
	address, err := domain.Email(m.To)
	if err != nil || address != m.To || len(m.Token) != 46 || strings.ContainsAny(m.Token, "\r\n") || (m.Purpose != domain.VerifyEmail && m.Purpose != domain.ResetPassword) {
		return app.ErrMail
	}
	budget, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	conn, err := (&net.Dialer{}).DialContext(budget, "tcp", s.config.Address)
	if err != nil {
		return app.ErrMail.WithCause(err)
	}
	defer conn.Close()
	deadline, _ := budget.Deadline()
	_ = conn.SetDeadline(deadline)
	raw := conn
	stop := context.AfterFunc(budget, func() { _ = raw.Close() })
	defer stop()
	if s.config.Mode == "tls" {
		secure := tls.Client(conn, &tls.Config{ServerName: s.host, MinVersion: tls.VersionTLS12})
		if err = secure.HandshakeContext(budget); err != nil {
			return app.ErrMail.WithCause(err)
		}
		conn = secure
	}
	client, err := smtp.NewClient(conn, s.host)
	if err != nil {
		return app.ErrMail.WithCause(err)
	}
	defer client.Close()
	if s.config.Mode == "starttls" {
		if err = client.StartTLS(&tls.Config{ServerName: s.host, MinVersion: tls.VersionTLS12}); err != nil {
			return app.ErrMail.WithCause(err)
		}
	}
	if s.config.Username != "" {
		if err = client.Auth(smtp.PlainAuth("", s.config.Username, s.config.Password, s.host)); err != nil {
			return app.ErrMail.WithCause(err)
		}
	}
	if err = client.Mail(s.config.From); err != nil {
		return app.ErrMail.WithCause(err)
	}
	if err = client.Rcpt(m.To); err != nil {
		return app.ErrMail.WithCause(err)
	}
	writer, err := client.Data()
	if err != nil {
		return app.ErrMail.WithCause(err)
	}
	text := "From: " + s.config.From + "\r\nTo: " + m.To + "\r\nSubject: Account security code\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=utf-8\r\n\r\nPurpose: " + m.Purpose + "\r\nCode: " + m.Token + "\r\nExpires in 20 minutes. If you did not request this, ignore this message.\r\n"
	if _, err = io.WriteString(writer, text); err != nil {
		return app.ErrMail.WithCause(err)
	}
	if err = writer.Close(); err != nil {
		return app.ErrMail.WithCause(err)
	}
	// DATA acceptance is the boundary; a lost QUIT response must not cause resend.
	_ = client.Quit()
	return nil
}
func (Sender) String() string   { return "identity.SMTP{redacted}" }
func (Sender) GoString() string { return "identity.SMTP{redacted}" }
