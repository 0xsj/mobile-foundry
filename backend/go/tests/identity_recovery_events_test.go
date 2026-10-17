package tests

import (
	"context"
	auditdomain "github.com/0xsj/mobile-foundry/backend/internal/audit/domain"
	auditreceiver "github.com/0xsj/mobile-foundry/backend/internal/audit/infra/events"
	identitydomain "github.com/0xsj/mobile-foundry/backend/internal/identity/domain"
	producer "github.com/0xsj/mobile-foundry/backend/internal/identity/infra/events"
	notificationapp "github.com/0xsj/mobile-foundry/backend/internal/notifications/app"
	notificationreceiver "github.com/0xsj/mobile-foundry/backend/internal/notifications/infra/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"testing"
	"time"
)

type auditRecoveryRecorder struct{ count int }

func (r *auditRecoveryRecorder) Handle(_ context.Context, _ string, e auditdomain.Evidence) error {
	if _, err := auditdomain.New(e, time.Now()); err != nil {
		return err
	}
	r.count++
	return nil
}

type recoveryRetirer struct{ count int }

func (r *recoveryRetirer) Handle(_ context.Context, e notificationapp.SessionRevocation) error {
	if err := e.Validate(); err != nil {
		return err
	}
	r.count++
	return nil
}
func TestPasswordRevocationUsesV2WithoutExpandingLegacyReasonAdmission(t *testing.T) {
	actor, session := freshID(t), freshID(t)
	now := identitydomain.Time(time.Now())
	for _, reason := range []string{"password_change", "password_reset"} {
		message, err := producer.Message(identitydomain.Fact{PrincipalID: actor, SessionID: session, Action: "session.revoked", Reason: reason, Version: 2, OccurredAt: now}, events.Metadata{}, id.Random{})
		if err != nil || message.Name != "identity.session.revoked.v2" {
			t.Fatal("wrong revocation schema", err)
		}
		audit := &auditRecoveryRecorder{}
		retire := &recoveryRetirer{}
		if err = auditreceiver.SubscribeIdentity(audit).Handle(context.Background(), message); err != nil {
			t.Fatal(err)
		}
		if err = notificationreceiver.SubscribeIdentity(retire).Handle(context.Background(), message); err != nil {
			t.Fatal(err)
		}
		if audit.count != 1 || retire.count != 1 {
			t.Fatal("required receivers not invoked")
		}
		message.Name = "identity.session.revoked.v1"
		if auditreceiver.SubscribeIdentity(audit).Handle(context.Background(), message) == nil || notificationreceiver.SubscribeIdentity(retire).Handle(context.Background(), message) == nil {
			t.Fatal("v1 reason vocabulary silently expanded")
		}
	}
}
