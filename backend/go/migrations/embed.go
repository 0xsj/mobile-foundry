// Package migrations contains immutable, module-owned deployment artifacts.
// Only process composition selects these files; reusable packages own no catalog.
package migrations

import "embed"

//go:embed account/*.sql audit/*.sql identity/*.sql notes/*.sql
var Files embed.FS
