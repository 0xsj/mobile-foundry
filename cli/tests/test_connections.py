import copy
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "cli"))
from mobile_foundry.backends import select_backend
from mobile_foundry.capabilities import discover_combinations, select_combination
from mobile_foundry.connections import connection_base
from mobile_foundry.generation import ProductInputs, create_project, plan_sources
from mobile_foundry.profiles import ProfileError, discover, validate_profile


class ConnectionTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="mobile-connected-cli-")
        self.addCleanup(temporary.cleanup)
        self.output = Path(temporary.name).resolve()
        self.profiles, backends = discover(ROOT)
        self.capabilities, self.combinations = discover_combinations(ROOT, self.profiles)
        self.profile, self.manifest = select_combination("native-daily", ["go-connected"], self.profiles, self.capabilities, self.combinations)
        self.backend = select_backend(ROOT, backends, "go", "github.com/example/connected/backend", "connected")

    def test_swift_kotlin_and_both_select_connected_closure_and_configure_public_endpoint(self):
        before = {f.relative: hashlib.sha256(f.data).hexdigest() for f in plan_sources(ROOT, self.profile, ProductInputs.admit("before", "both"))}
        for platform in ("swift", "kotlin", "both"):
            with self.subTest(platform=platform):
                app = create_project(ROOT, self.profile, self.manifest, ProductInputs.admit("connected-" + platform, platform), self.output,
                    backend=self.backend, api_base_url="https://API.example.com:443/v1/")
                metadata = json.loads((app / "mobile-foundry.json").read_text())
                self.assertEqual(metadata["nativeBackendConnection"], "go-connected")
                self.assertEqual(metadata["apiBaseURL"], "https://api.example.com/v1")
                for native in metadata["platforms"]:
                    config = app / self.profile["platforms"][native]["connectionFile"]
                    self.assertIn('"https://api.example.com/v1"', config.read_text())
                guide = (app / "SETUP.md").read_text()
                self.assertIn("Connected native workflow", guide)
                self.assertNotIn("remote repository composition have not been selected", guide)
                self.assertNotIn("source checkout link", guide)
                self.assertIn("connected-test", (app / "Makefile").read_text())
                self.assertTrue((app / "contracts/fixtures/pending-notes/records.json").is_file())
                self.assertIn("SQLite journal before HTTP", guide)
                self.assertTrue((app / "scripts/connected_acceptance.py").is_file())
                self.assertTrue((app / "scripts/local_mail_capture.py").is_file())
                self.assertTrue((app / "contracts/fixtures/native-lifecycle/hints.json").is_file())
                self.assertTrue((app / "contracts/fixtures/notes-read-cache/snapshots.json").is_file())
                self.assertIn("NotesReadCache", guide)
                self.assertIn("AccountLiveHints", guide)
                self.assertIn("IdentityRecovery", guide)
                self.assertIn("IdentityRegistration", guide)
                self.assertIn("AccountManagement", guide)
                self.assertTrue((app / "contracts/fixtures/account/names.json").is_file())
                self.assertNotIn("registration UI, MFA", guide)
                self.assertTrue((app / "contracts/fixtures/native-registration/inputs.json").is_file())
                self.assertFalse(any(p.name in {"FoundryCatalog", "catalog"} for p in app.rglob("*")))
                if platform != "kotlin":
                    self.assertTrue((app / "frontend/swift/packages/FoundrySession/Sources/FoundrySession/KeychainCredentialVault.swift").is_file())
                    self.assertTrue((app / "frontend/swift/packages/FoundryData/Sources/FoundryData/SQLitePendingNotesStore.swift").is_file())
                    self.assertTrue((app / "frontend/swift/packages/FoundryData/Sources/FoundryData/SQLiteNotesReadCache.swift").is_file())
                    self.assertFalse((app / "frontend/swift/packages/FoundryData/Sources/FoundryData/SQLiteNotesRepository.swift").exists())
                    self.assertTrue((app / "frontend/swift/apps/ProductApp/Sources/Screens/RegistrationView.swift").is_file())
                    self.assertTrue((app / "frontend/swift/apps/ProductApp/Sources/Screens/WorkspaceView.swift").is_file())
                    self.assertTrue((app / "frontend/swift/apps/ProductApp/Tests/WorkspaceModelTests.swift").is_file())
                    self.assertTrue((app / "frontend/swift/packages/FoundrySession/Sources/FoundrySession/IdentityRegistration.swift").is_file())
                    self.assertIn("FoundryHTTP", (app / "frontend/swift/apps/ProductApp/project.yml").read_text())
                    self.assertTrue((app / "frontend/swift/apps/ProductApp/Sources/Screens/IdentitySettingsView.swift").is_file())
                if platform != "swift":
                    self.assertTrue((app / "frontend/kotlin/project/core/data/src/main/kotlin/dev/mobilefoundry/data/SQLitePendingNotesStore.kt").is_file())
                    self.assertTrue((app / "frontend/kotlin/project/core/data/src/main/kotlin/dev/mobilefoundry/data/SQLiteNotesReadCache.kt").is_file())
                    base = app / "frontend/kotlin/project/product"
                    self.assertTrue((base / "src/main/kotlin/dev/mobilefoundry/product/screens/IdentitySettingsScreen.kt").is_file())
                    self.assertTrue((base / "src/main/kotlin/dev/mobilefoundry/product/screens/RegistrationScreen.kt").is_file())
                    self.assertTrue((app / "frontend/kotlin/project/core/session/src/main/kotlin/dev/mobilefoundry/session/IdentityRegistration.kt").is_file())
                    self.assertIn("INTERNET", (base / "src/main/AndroidManifest.xml").read_text())
                    self.assertIn('usesCleartextTraffic="false"', (base / "src/main/AndroidManifest.xml").read_text())
                    self.assertTrue((base / "src/debug/res/xml/network_security_config.xml").is_file())
                    self.assertFalse((base / "src/main/res/xml/network_security_config.xml").exists())
        after = {f.relative: hashlib.sha256(f.data).hexdigest() for f in plan_sources(ROOT, self.profile, ProductInputs.admit("after", "both"))}
        self.assertEqual(before, after)

    def test_backend_and_endpoint_selection_refuse_before_destination_creation(self):
        for profile, backend, value in ((self.profile, None, None), (self.profiles["native-daily"][0], self.backend, "https://api.example.com/v1")):
            with self.assertRaises(ProfileError):
                create_project(ROOT, profile, self.manifest, ProductInputs.admit("refused", "both"), self.output, backend=backend, api_base_url=value)
        for value in ("http://api.example.com/v1", "https://user:password@api.example.com/v1", "https://api.example.com/v1?token=x",
            "https://api.example.com/v1#part", "https://api.example.com/v1?", "https://api.example.com/a/../v1", "https://api.example.com/a//v1",
            "https://api.example.com/%76%31", " https://api.example.com/v1", "https://api.example.com:0/v1", "https://api.example.com:65536/v1", "https://api.example.com/\n"):
            with self.subTest(value=value), self.assertRaises(ProfileError):
                connection_base(self.profile, self.backend, value)
        self.assertEqual(list(self.output.iterdir()), [])
        for value in ("http://127.0.0.1:8080/v1", "http://localhost/v1", "http://[::1]:8080/v1", "https://api.example.com/v1"):
            self.assertEqual(connection_base(self.profile, self.backend, value), value)

    def test_connected_combinations_and_missing_configuration_or_fixtures_refuse(self):
        for shell, selected in (("native-workspace", ["go-connected"]), ("native-browse", ["go-connected"]), ("native-daily", ["local-data", "go-connected"])):
            with self.assertRaisesRegex(ProfileError, "No registered combination"):
                select_combination(shell, selected, self.profiles, self.capabilities, self.combinations)
        profile = copy.deepcopy(self.profile); profile.pop("requiresBackend")
        self.assertTrue(any("requires the Daily" in e for e in validate_profile(profile, ROOT)))
        profile = copy.deepcopy(self.profile); profile["platforms"]["swift"].pop("connectionFile")
        self.assertTrue(any("connection configuration" in e for e in validate_profile(profile, ROOT)))
        profile = copy.deepcopy(self.profile); profile["sharedSources"].remove("contracts/fixtures/go-session")
        self.assertTrue(any("missing go-session" in e for e in validate_profile(profile, ROOT)))
        profile = copy.deepcopy(self.profile); profile["exclude"].append("**/ConnectionConfiguration.swift")
        with self.assertRaisesRegex(ProfileError, "Invalid overlay"):
            create_project(ROOT, profile, self.manifest, ProductInputs.admit("excluded", "swift"), self.output, backend=self.backend)
        self.assertFalse((self.output / "excluded").exists())

    def test_terminal_distinguishes_connection_from_colocation(self):
        command = [sys.executable, str(ROOT / "bin/mobile-foundry"), "init", "connected", "--platform", "swift", "--with", "go-connected"]
        refused = subprocess.run(command, cwd=self.output, capture_output=True, text=True)
        self.assertEqual(refused.returncode, 1)
        self.assertIn("requires --backend go", refused.stderr)
        self.assertFalse((self.output / "connected").exists())
        accepted = subprocess.run(command + ["--backend", "go"], cwd=self.output, capture_output=True, text=True)
        self.assertEqual(accepted.returncode, 0, accepted.stderr)
        self.assertIn("remote Notes are connected", accepted.stdout)
        self.assertNotIn("wiring is not selected", accepted.stdout)


if __name__ == "__main__":
    unittest.main()
