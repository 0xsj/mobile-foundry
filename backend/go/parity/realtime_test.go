package parity

import (
	"encoding/json"
	"github.com/0xsj/mobile-foundry/backend/pkg/broadcast"
	apperrors "github.com/0xsj/mobile-foundry/backend/pkg/errors"
	"os"
	"testing"
)

func TestSharedRealtimeHub(t *testing.T) {
	var cases []struct {
		Name     string
		Maximum  int
		PerOwner int `json:"per_owner"`
		Steps    []struct {
			Op, Slot, Key, Owner, Expected string
			Count                          int
		}
	}
	wire, e := os.ReadFile("../../../contracts/fixtures/backend-realtime/hub.json")
	if e != nil {
		t.Fatal(e)
	}
	if json.Unmarshal(wire, &cases) != nil {
		t.Fatal("fixture JSON")
	}
	for _, c := range cases {
		t.Run(c.Name, func(t *testing.T) {
			hub := broadcast.New(c.Maximum, c.PerOwner)
			defer hub.Close()
			channels := map[string]<-chan struct{}{}
			releases := map[string]func(){}
			defer func() {
				for _, release := range releases {
					release()
				}
			}()
			for _, step := range c.Steps {
				switch step.Op {
				case "watch":
					channel, release, err := hub.Watch(step.Key, step.Owner)
					if step.Expected != "" {
						if err == nil || apperrors.Public(err).Code != step.Expected {
							t.Fatal("watch admission")
						}
					} else {
						if err != nil {
							t.Fatal(err)
						}
						channels[step.Slot] = channel
						releases[step.Slot] = release
					}
				case "publish":
					for range step.Count {
						hub.Publish(step.Key)
					}
				case "release":
					releases[step.Slot]()
				case "close":
					_ = hub.Close()
				case "take":
					actual := "empty"
					select {
					case _, open := <-channels[step.Slot]:
						if open {
							actual = "dirty"
						} else {
							actual = "closed"
						}
					default:
					}
					if actual != step.Expected {
						t.Fatal("take", actual, step.Expected)
					}
				}
			}
		})
	}
}
