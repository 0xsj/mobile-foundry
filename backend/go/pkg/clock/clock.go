// Package clock makes business time injectable; elapsed HTTP time uses the runtime clock.
package clock

import "time"

type Clock interface{ Now() time.Time }
type System struct{}

func (System) Now() time.Time { return time.Now().UTC() }
