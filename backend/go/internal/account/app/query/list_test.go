package query

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/0xsj/mobile-foundry/backend/internal/account/domain"
	"github.com/0xsj/mobile-foundry/backend/pkg/events"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
	"github.com/0xsj/mobile-foundry/backend/pkg/principal"
)

type faultyDirectory struct {
	rows []domain.Account
	err  error
}

func (s faultyDirectory) Bootstrap(context.Context, domain.Account, domain.Fact, events.Metadata) (domain.Account, bool, error) {
	return domain.Account{}, false, s.err
}
func (s faultyDirectory) List(context.Context, id.ID, id.ID, int) ([]domain.Account, error) {
	return s.rows, s.err
}

func TestDiscoveryDoesNotHideDependencyFailureOrProjectForeignRows(t *testing.T) {
	owner := id.ID("00000000-0000-4000-8000-000000000001")
	foreign, err := domain.New(id.ID("00000000-0000-4000-8000-000000000003"), id.ID("00000000-0000-4000-8000-000000000002"), "Private", time.Now())
	if err != nil {
		t.Fatal(err)
	}
	input := ListInput{Principal: principal.Principal{ID: owner}, Limit: 20}
	unavailable := errors.New("dependency unavailable")
	if page, err := (List{Directory: faultyDirectory{err: unavailable}}).Handle(context.Background(), input); !errors.Is(err, unavailable) || len(page.Accounts) != 0 {
		t.Fatal("failure became an empty successful page", err)
	}
	if page, err := (List{Directory: faultyDirectory{rows: []domain.Account{foreign}}}).Handle(context.Background(), input); !errors.Is(err, domain.ErrState) || len(page.Accounts) != 0 {
		t.Fatal("faulty adapter leaked another owner", err)
	}
}
