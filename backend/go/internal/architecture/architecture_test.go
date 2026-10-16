package architecture

import (
	"go/parser"
	"go/token"
	"io/fs"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
)

const modulePath = "github.com/0xsj/mobile-foundry/backend/"

func TestImportBoundaries(t *testing.T) {
	root := filepath.Join("..", "..")
	err := filepath.WalkDir(root, func(path string, entry fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if entry.IsDir() {
			if entry.Name() == ".cache" || entry.Name() == "vendor" {
				return filepath.SkipDir
			}
			return nil
		}
		if !strings.HasSuffix(path, ".go") {
			return nil
		}
		relative, err := filepath.Rel(root, path)
		if err != nil {
			return err
		}
		source, err := parser.ParseFile(token.NewFileSet(), path, nil, parser.ImportsOnly)
		if err != nil {
			return err
		}
		for _, spec := range source.Imports {
			imported, _ := strconv.Unquote(spec.Path.Value)
			if !allowed(filepath.ToSlash(relative), imported) {
				t.Errorf("%s imports forbidden %s", relative, imported)
			}
		}
		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
}

// The policy covers production and test source. Consumer ports point inward;
// a test cannot quietly normalize a forbidden production dependency.
func allowed(source, imported string) bool {
	parts := strings.Split(source, "/")
	local := strings.HasPrefix(imported, modulePath)
	target := strings.TrimPrefix(imported, modulePath)
	if parts[0] == "migrations" {
		return imported == "embed"
	}
	if parts[0] == "pkg" {
		return !local || strings.HasPrefix(target, "pkg/")
	}
	if parts[0] == "cmd" || (parts[0] == "internal" && (parts[1] == "server" || parts[1] == "architecture")) {
		if parts[0] == "cmd" && imported == modulePath+"migrations" {
			return true
		}
		if !local || strings.HasPrefix(target, "pkg/") {
			return true
		}
		return strings.HasPrefix(target, "internal/") && len(strings.Split(target, "/")) == 2
	}
	if parts[0] != "internal" || len(parts) < 3 {
		return true
	}
	owner := "internal/" + parts[1]
	layer := parts[2]
	if layer != "domain" && layer != "app" && layer != "infra" && layer != "transport" {
		return !local || strings.HasPrefix(target, "pkg/") || strings.HasPrefix(target, owner+"/")
	}
	if !local {
		if layer == "domain" || layer == "app" {
			for _, prefix := range []string{"net", "database", "os", "io", "log", "syscall", "unsafe", "crypto", "plugin"} {
				if imported == prefix || strings.HasPrefix(imported, prefix+"/") {
					return false
				}
			}
			// Third-party adapter/framework dependencies stay out of the core.
			if strings.Contains(strings.Split(imported, "/")[0], ".") {
				return false
			}
		}
		return true
	}
	if strings.HasPrefix(target, "pkg/") {
		if layer == "domain" || layer == "app" {
			shared := strings.Split(target, "/")[1]
			return shared == "errors" || shared == "id" || shared == "clock" || (layer == "app" && (shared == "principal" || shared == "events"))
		}
		return true
	}
	if !strings.HasPrefix(target, owner+"/") {
		return false
	}
	targetLayer := strings.Split(strings.TrimPrefix(target, owner+"/"), "/")[0]
	switch layer {
	case "domain":
		return targetLayer == "domain"
	case "app":
		return targetLayer == "app" || targetLayer == "domain"
	case "infra":
		return targetLayer == "infra" || targetLayer == "app" || targetLayer == "domain"
	case "transport":
		return targetLayer == "transport" || targetLayer == "app" || targetLayer == "domain"
	}
	return false
}

func TestPolicyRejectsRepresentativeViolations(t *testing.T) {
	for _, test := range []struct {
		source, target string
		valid          bool
	}{
		{"internal/account/domain/account.go", modulePath + "pkg/errors", true},
		{"cmd/migrate/main.go", modulePath + "migrations", true},
		{"internal/account/app/command/create.go", modulePath + "migrations", false},
		{"migrations/embed.go", modulePath + "internal/account/domain", false},
		{"internal/account/domain/account.go", "net/http", false},
		{"internal/account/domain/account.go", modulePath + "internal/account/app", false},
		{"internal/account/app/command/create.go", modulePath + "internal/account/infra/postgres", false},
		{"internal/account/app/query/get.go", modulePath + "internal/identity/domain", false},
		{"internal/account/app/query/get.go", modulePath + "pkg/httpx", false},
		{"internal/account/infra/postgres/store.go", modulePath + "internal/account/app", true},
		{"internal/account/transport/http/v1/handler.go", modulePath + "internal/account/infra/postgres", false},
		{"internal/account/module.go", modulePath + "internal/account/infra/postgres", true},
		{"pkg/httpx/httpx.go", modulePath + "internal/account/domain", false},
		{"internal/server/server.go", modulePath + "internal/health", true},
		{"cmd/server/main.go", modulePath + "internal/account/app/command", false},
		{"internal/audit/infra/events/consumer.go", modulePath + "internal/account/domain", false},
		{"internal/audit/app/query/list.go", modulePath + "pkg/capability", false},
		{"internal/account/domain/account.go", modulePath + "pkg/events", false},
		{"internal/audit/infra/capabilities/account.go", modulePath + "pkg/capability", true},
	} {
		if result := allowed(test.source, test.target); result != test.valid {
			t.Errorf("policy %s -> %s: %v", test.source, test.target, result)
		}
	}
}
