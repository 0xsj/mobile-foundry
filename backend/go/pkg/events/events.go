// Package events owns integration envelopes and delivery mechanics, not business facts.
package events

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"regexp"
	"slices"
	"sync"
	"time"

	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"github.com/0xsj/mobile-foundry/backend/pkg/id"
)

const MaxBytes = 16384

var eventName = regexp.MustCompile(`^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+\.v[1-9][0-9]{0,5}$`)
var ownerName = regexp.MustCompile(`^[a-z][a-z0-9_]{0,63}$`)
var consumerName = regexp.MustCompile(`^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$`)
var metadataToken = regexp.MustCompile(`^[A-Za-z0-9._:-]{1,128}$`)
var (
	ErrInvalid    = apperrors.New(apperrors.Invalid, "events.invalid", "invalid integration envelope or subscription")
	ErrNoConsumer = apperrors.New(apperrors.Unavailable, "events.no_consumer", "event has no registered consumer")
)

type Metadata struct {
	RequestID     string `json:"request_id,omitempty"`
	CorrelationID string `json:"correlation_id,omitempty"`
}
type Envelope struct {
	ID         id.ID           `json:"id"`
	Name       string          `json:"name"`
	Producer   string          `json:"producer"`
	AccountID  id.ID           `json:"account_id,omitempty"`
	ActorID    id.ID           `json:"actor_id"`
	Version    uint64          `json:"aggregate_version"`
	OccurredAt time.Time       `json:"occurred_at"`
	Metadata   Metadata        `json:"metadata"`
	Data       json.RawMessage `json:"data"`
}

func (m Envelope) Clone() Envelope { m.Data = bytes.Clone(m.Data); return m }
func (m Envelope) Validate() error {
	if !m.ID.Valid() || (m.AccountID != "" && !m.AccountID.Valid()) || !m.ActorID.Valid() || !eventName.MatchString(m.Name) || !ownerName.MatchString(m.Producer) || m.Version == 0 || m.Version > 1<<53-1 || m.OccurredAt.IsZero() || m.OccurredAt.Year() < 1970 || m.OccurredAt.Year() > 9999 || len(m.Data) > MaxBytes || !json.Valid(m.Data) || len(bytes.TrimSpace(m.Data)) == 0 || bytes.TrimSpace(m.Data)[0] != '{' {
		return ErrInvalid
	}
	for _, value := range []string{m.Metadata.RequestID, m.Metadata.CorrelationID} {
		if value != "" && !metadataToken.MatchString(value) {
			return ErrInvalid
		}
	}
	return nil
}

type Publisher interface {
	Publish(context.Context, Envelope) error
}
type Subscription struct {
	Consumer string
	Names    []string
	Handle   func(context.Context, Envelope) error
}

// Bus permits another delivery adapter while retaining explicit subscriptions.
type Bus interface {
	Publisher
	Register(Subscription) error
	Freeze()
}
type Memory struct {
	mu            sync.RWMutex
	subscriptions map[string]Subscription
	frozen        bool
}

func NewMemory() *Memory { return &Memory{subscriptions: make(map[string]Subscription)} }
func (bus *Memory) Register(subscription Subscription) error {
	if !consumerName.MatchString(subscription.Consumer) || subscription.Handle == nil || len(subscription.Names) == 0 {
		return ErrInvalid
	}
	for _, name := range subscription.Names {
		if !eventName.MatchString(name) {
			return ErrInvalid
		}
	}
	bus.mu.Lock()
	defer bus.mu.Unlock()
	if bus.frozen || bus.subscriptions[subscription.Consumer].Handle != nil {
		return ErrInvalid
	}
	subscription.Names = slices.Clone(subscription.Names)
	bus.subscriptions[subscription.Consumer] = subscription
	return nil
}
func (bus *Memory) Freeze() { bus.mu.Lock(); defer bus.mu.Unlock(); bus.frozen = true }
func (bus *Memory) Publish(ctx context.Context, message Envelope) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	if err := message.Validate(); err != nil {
		return err
	}
	bus.mu.RLock()
	var subscriptions []Subscription
	for _, subscription := range bus.subscriptions {
		if slices.Contains(subscription.Names, message.Name) {
			subscriptions = append(subscriptions, subscription)
		}
	}
	bus.mu.RUnlock()
	if len(subscriptions) == 0 {
		return ErrNoConsumer
	}
	slices.SortFunc(subscriptions, func(a, b Subscription) int {
		if a.Consumer < b.Consumer {
			return -1
		}
		if a.Consumer > b.Consumer {
			return 1
		}
		return 0
	})
	var failed error
	for _, subscription := range subscriptions {
		if err := ctx.Err(); err != nil {
			return errors.Join(failed, err)
		}
		failed = errors.Join(failed, subscription.Handle(ctx, message.Clone()))
	}
	return failed
}
