package query

import (
	"context"
	"errors"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/app"
	"github.com/0xsj/mobile-foundry/backend/internal/audit/domain"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
	"testing"
)

type evidenceStore struct{ reads int }

func (*evidenceStore) Record(context.Context, string, domain.Entry) error { return nil }
func (s *evidenceStore) List(context.Context, id.ID, uint64, int) (app.Page, error) {
	s.reads++
	return app.Page{}, nil
}

type decision struct {
	allowed bool
	failure error
}

func (d decision) Allowed(context.Context, id.ID, id.ID) (bool, error) { return d.allowed, d.failure }
func TestAccessFailureIsNotDenialAndPreventsEvidenceRead(t *testing.T) {
	identity := id.ID("00000000-0000-4000-8000-000000000001")
	unavailable := apperrors.New(apperrors.Unavailable, "access.unavailable", "private")
	for _, sample := range []struct {
		decision decision
		expected error
	}{{decision{failure: unavailable}, unavailable}, {decision{}, app.ErrDenied}} {
		store := &evidenceStore{}
		_, err := (List{Store: store, Access: sample.decision}).Handle(context.Background(), Input{Principal: principal.Principal{ID: identity}, AccountID: identity, Limit: 50})
		if !errors.Is(err, sample.expected) || store.reads != 0 {
			t.Fatal("dependency failure/denial admitted evidence read", err)
		}
	}
}
