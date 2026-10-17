import copy
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "cli"))
from mobile_foundry.backends import admit_go_module, select_backend, validate_backend
from mobile_foundry.generation import ProductInputs, create_project, plan_backend_sources
from mobile_foundry.profiles import ProfileError, discover


class BackendTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="mobile-go-cli-")
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name).resolve()
        self.profiles, self.registrations = discover(ROOT)
        self.backend = select_backend(ROOT, self.registrations, "go", "github.com/example/canopy/backend", "canopy")

    def cli(self, *args):
        return subprocess.run([sys.executable, str(ROOT / "bin/mobile-foundry"), *args], cwd=self.output, capture_output=True, text=True, timeout=30)

    def generate(self, name, platform="none", root=ROOT, backend=None):
        profile, manifest = self.profiles["native-daily"] if platform != "none" else (None, None)
        return create_project(root, profile, manifest, ProductInputs.admit(name, platform), self.output, backend=backend or self.backend)

    def test_backend_only_and_each_native_copy_have_local_go_closure_and_module_identity(self):
        before = {item.relative: hashlib.sha256(item.data).hexdigest() for item in plan_backend_sources(ROOT, self.backend, ProductInputs.admit("before", "none"))}
        for platform in ("none", "swift", "kotlin", "both"):
            with self.subTest(platform=platform):
                project = self.generate("go-" + platform, platform)
                metadata = json.loads((project / "mobile-foundry.json").read_text())
                self.assertEqual(metadata["backend"], "go")
                self.assertEqual(metadata["goModule"], self.backend.module)
                self.assertEqual(metadata["backendModules"], self.backend.profile["modules"])
                self.assertEqual(metadata["nativeBackendConnection"], None if platform == "none" else "unconnected")
                self.assertEqual((project / "frontend").exists(), platform != "none")
                self.assertIn("module " + self.backend.module, (project / "backend/go/go.mod").read_text())
                for path in (project / "backend/go").rglob("*.go"):
                    self.assertNotIn(self.backend.profile["sourceModule"], path.read_text(), str(path))
                policy = (project / "backend/go/internal/architecture/architecture_test.go").read_text()
                self.assertIn(self.backend.module + "/", policy)
                self.assertTrue((project / "contracts/fixtures/account/names.json").is_file())
                self.assertFalse((project / "backend/go/notes").exists())
                self.assertTrue((project / "backend/go/migrations/notifications/0003_push_jobs.sql").is_file())
                self.assertTrue((project / "backend/go/migrations/notifications/0004_push_job_recovery.sql").is_file())
                self.assertIn("accept_possible_duplicate", (project / "SETUP.md").read_text())
                self.assertTrue((project / "backend/go/pkg/jobs/worker.go").is_file())
                self.assertIn("PUSH_JOBS_ENABLED", (project / "SETUP.md").read_text())
                self.assertIn("PUSH_JOBS_ENABLED=true", (project / "backend/go/.env.example").read_text())
                self.assertFalse((project / "backend/go/internal/realtime").exists())
                self.assertFalse(any(path.name == ".gitkeep" for path in (project / "backend/go").rglob("*")))
                compose = (project / "backend/go/infra/local/compose.yaml").read_text()
                self.assertIn("name: go-" + platform + "-api", compose)
                self.assertIn("POSTGRES_DB: app", compose)
                self.assertIn("$(POSTGRES_PORT)/app", (project / "backend/go/Makefile").read_text())
                self.assertIn("backend/go/.data/", (project / ".gitignore").read_text())
                self.assertTrue((project / "backend/go/internal/files/module.go").is_file())
                self.assertTrue((project / "backend/go/internal/notifications/module.go").is_file())
                self.assertIn("go-dev:", (project / "Makefile").read_text())
                self.assertIn("make go-integration-test", (project / "SETUP.md").read_text())
                for relative in ("README.md", "SETUP.md", "mobile-foundry.json", "backend/go/README.md"):
                    self.assertNotIn(str(ROOT), (project / relative).read_text())
        after = {item.relative: hashlib.sha256(item.data).hexdigest() for item in plan_backend_sources(ROOT, self.backend, ProductInputs.admit("after", "none"))}
        self.assertEqual(before, after)

    def test_backend_cli_inputs_and_default_module_are_explicit(self):
        result = self.cli("init", "api", "--platform", "none", "--backend", "go")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Platforms: none", result.stdout)
        self.assertIn("Go module: example.com/api/backend", result.stdout)
        metadata = json.loads((self.output / "api/mobile-foundry.json").read_text())
        self.assertIsNone(metadata["profile"]); self.assertIsNone(metadata["appId"])
        self.assertEqual(metadata["platforms"], [])
        for extra, expected in [
            (("--profile", "native-daily"), "does not select"),
            (("--with", "local-data"), "does not select"),
            (("--app-id", "com.example.api"), "requires a native"),
            (("--ios-team", "ABCD123456"), "requires Swift"),
            (("--go-module", "../escape"), "Go module"),
        ]:
            result = self.cli("init", "invalid", "--platform", "none", "--backend", "go", *extra)
            self.assertEqual(result.returncode, 1); self.assertIn(expected, result.stderr)
            self.assertFalse((self.output / "invalid").exists())
        result = self.cli("init", "invalid", "--platform", "swift", "--go-module", "example.com/x/backend")
        self.assertEqual(result.returncode, 1); self.assertIn("requires --backend go", result.stderr)

    def test_bad_module_identities_and_empty_native_app_id_refuse(self):
        for module in ("go-api", "example.com", "example.com/../api", "example.com/a//b", "EXAMPLE.com/api", "example.com/api\nreplace x => y", "example.com/a;touch-file", "example.com/api/v1", "example.com/api/v02", "example.com/con", "example.com/api."):
            with self.subTest(module=module), self.assertRaises(ProfileError):
                admit_go_module(module)
        self.assertEqual(admit_go_module("github.com/example/api/v2"), "github.com/example/api/v2")
        with self.assertRaises(ProfileError):
            ProductInputs.admit("app", "swift", app_id="")

    def test_missing_import_embed_fixture_or_overlay_fails_preflight(self):
        for omitted, expected in (("backend/go/internal/identity", "unselected Go import"), ("backend/go/migrations", "embedded migration"), ("contracts/fixtures/account/names.json", "fixture")):
            profile = copy.deepcopy(self.backend.profile)
            field = "sharedSources" if omitted.startswith("contracts/") else "sources"
            profile[field].remove(omitted)
            if omitted == "backend/go/migrations":
                profile[field] += ["backend/go/migrations/embed.go", "backend/go/migrations/account", "backend/go/migrations/audit", "backend/go/migrations/notes"]
            self.assertTrue(any(expected in message for message in validate_backend(profile, ROOT)))
        profile = copy.deepcopy(self.backend.profile); profile["overlays"].append(copy.deepcopy(profile["overlays"][0]))
        self.assertTrue(any("unique" in message for message in validate_backend(profile, ROOT)))
        selection = copy.copy(self.backend)
        profile = copy.deepcopy(self.backend.profile); profile["exclude"].append("**/0002_personal_accounts.sql")
        object.__setattr__(selection, "profile", profile)
        with self.assertRaisesRegex(ProfileError, "Required backend file was excluded"):
            self.generate("missing", backend=selection)
        self.assertFalse((self.output / "missing").exists())

    def test_refusals_and_failure_cleanup_preserve_owned_sources_and_existing_output(self):
        keeper = self.output / "keeper"; keeper.mkdir(); (keeper / "draft").write_text("keep")
        with self.assertRaisesRegex(ProfileError, "already exists"):
            self.generate("keeper")
        for name, injected in (("failed", OSError("backend rewrite failure")), ("interrupted", KeyboardInterrupt())):
            with patch("mobile_foundry.generation.configure_backend", side_effect=injected), self.assertRaises(type(injected)):
                self.generate(name)
            self.assertFalse((self.output / name).exists())
        self.assertEqual((keeper / "draft").read_text(), "keep")

    def test_alternate_source_go_secrets_caches_and_symlinks_are_handled_before_write(self):
        source = self.output / "source"; source.mkdir()
        for item in plan_backend_sources(ROOT, self.backend, ProductInputs.admit("source", "none")):
            path = source / item.relative; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(item.data)
        # Install the declared overlays into this independent source snapshot.
        for overlay in self.backend.profile["overlays"]:
            path = source / overlay["source"]; path.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(ROOT / overlay["source"], path)
        (source / self.backend.manifest).parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / self.backend.manifest, source / self.backend.manifest)
        base = source / "backend/go/pkg"
        for relative in (".env.local", "build/server", ".cache/record", "private.pem", "signing.p8", ".push-private/credentials.json", "push.local.json", "service-account.json", "test.service-account.json", "serviceAccountKey.json"):
            path = base / relative; path.parent.mkdir(parents=True, exist_ok=True); path.write_text("private")
        project = self.generate("alternate", root=source)
        for pattern in (".push-private/", "*.p8", "push.local.json", "*service-account*.json"):
            self.assertIn(pattern, (project / ".gitignore").read_text())
        self.assertIn("PUSH_CONFIG_FILE", (project / "SETUP.md").read_text())
        for name in (".env.local", "server", "record", "private.pem", "signing.p8", "credentials.json", "push.local.json", "service-account.json", "test.service-account.json", "serviceAccountKey.json"):
            self.assertFalse(any(p.name == name for p in (project / "backend/go/pkg").rglob("*")))
        link = base / "linked.go"; link.symlink_to(ROOT / "README.md")
        with self.assertRaisesRegex(ProfileError, "symlink"):
            self.generate("linked", root=source)
        self.assertFalse((self.output / "linked").exists())


if __name__ == "__main__":
    unittest.main()
