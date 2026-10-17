package command

import (
	"context"

	"github.com/0xsj/mobile-foundry/backend/internal/identity/app"
	"github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/clock"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

type Register struct {
	Store     app.Store
	Passwords app.Passwords
	IDs       id.Generator
	Clock     clock.Clock
}

func (h Register) Handle(ctx context.Context, login, name, password string, metadata events.Metadata) (domain.Principal, error) {
	if err := ctx.Err(); err != nil {
		return domain.Principal{}, err
	}
	login, err := domain.Login(login)
	if err != nil {
		return domain.Principal{}, err
	}
	name, err = domain.DisplayName(name)
	if err != nil {
		return domain.Principal{}, err
	}
	if err := domain.Password(password); err != nil {
		return domain.Principal{}, err
	}
	hash, err := h.Passwords.Hash(ctx, password)
	if err != nil {
		return domain.Principal{}, err
	}
	identity, err := h.IDs.NewID()
	if err != nil {
		return domain.Principal{}, err
	}
	p, err := domain.NewPrincipal(identity, login, name, hash, h.Clock.Now())
	if err != nil {
		return domain.Principal{}, err
	}
	if err := h.Store.Register(ctx, p, metadata); err != nil {
		return domain.Principal{}, err
	}
	return p, nil
}

type SignIn struct {
	Store     app.Store
	Passwords app.Passwords
	Tokens    app.Tokens
	IDs       id.Generator
	Clock     clock.Clock
}

func (h SignIn) Handle(ctx context.Context, login, password string, metadata events.Metadata) (app.Grant, error) {
	login, err := domain.Login(login)
	if err != nil {
		return app.Grant{}, domain.ErrCredential
	}
	if err := domain.Password(password); err != nil {
		return app.Grant{}, domain.ErrCredential
	}
	p, found, err := h.Store.ByLogin(ctx, login)
	if err != nil {
		return app.Grant{}, err
	}
	if !found {
		if err := h.Passwords.Dummy(ctx, password); err != nil {
			return app.Grant{}, err
		}
		return app.Grant{}, domain.ErrCredential
	}
	match, err := h.Passwords.Verify(ctx, p.Snapshot().PasswordHash, password)
	if err != nil {
		return app.Grant{}, err
	}
	if !match {
		return app.Grant{}, domain.ErrCredential
	}
	access, ad, err := h.Tokens.Issue(ctx, "access")
	if err != nil {
		return app.Grant{}, err
	}
	refresh, rd, err := h.Tokens.Issue(ctx, "refresh")
	if err != nil {
		return app.Grant{}, err
	}
	identity, err := h.IDs.NewID()
	if err != nil {
		return app.Grant{}, err
	}
	s, err := domain.NewSession(identity, p.Snapshot().ID, ad, rd, h.Clock.Now())
	if err != nil {
		return app.Grant{}, err
	}
	if err := h.Store.IssueChecked(ctx, s, p.Snapshot().PasswordHash, metadata); err != nil {
		return app.Grant{}, err
	}
	return app.NewGrant(p, s, access, refresh), nil
}

type Renew struct {
	Store  app.Store
	Tokens app.Tokens
	Clock  clock.Clock
}

func (h Renew) Handle(ctx context.Context, token string, metadata events.Metadata) (app.Grant, error) {
	digest, err := h.Tokens.Digest(ctx, token, "refresh")
	if err != nil {
		return app.Grant{}, err
	}
	access, ad, err := h.Tokens.Issue(ctx, "access")
	if err != nil {
		return app.Grant{}, err
	}
	refresh, rd, err := h.Tokens.Issue(ctx, "refresh")
	if err != nil {
		return app.Grant{}, err
	}
	s, reused, err := h.Store.Rotate(ctx, digest, ad, rd, h.Clock.Now(), metadata)
	if err != nil {
		return app.Grant{}, err
	}
	if reused {
		return app.Grant{}, domain.ErrCredential
	}
	p, found, err := h.Store.ByID(ctx, s.Snapshot().PrincipalID)
	if err != nil {
		return app.Grant{}, err
	}
	if !found {
		return app.Grant{}, domain.ErrState
	}
	return app.NewGrant(p, s, access, refresh), nil
}

type SignOut struct {
	Store  app.Store
	Tokens app.Tokens
	Clock  clock.Clock
}

func (h SignOut) Handle(ctx context.Context, token string, metadata events.Metadata) error {
	digest, err := h.Tokens.Digest(ctx, token, "refresh")
	if err != nil {
		return err
	}
	return h.Store.Revoke(ctx, digest, h.Clock.Now(), metadata)
}
