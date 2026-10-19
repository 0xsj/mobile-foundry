import copy
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
from mobile_foundry.generation import ProductInputs, create_project, plan_sources
from mobile_foundry.profiles import ProfileError, discover, validate_profile


class InboxTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="mobile-inbox-cli-")
        self.addCleanup(temporary.cleanup)
        self.output = Path(temporary.name).resolve()
        self.profiles, self.backends = discover(ROOT)
        self.capabilities, self.combinations = discover_combinations(ROOT, self.profiles)

    def selection(self, backend):
        return select_combination("native-daily", [backend + "-connected", "inbox"], self.profiles, self.capabilities, self.combinations)

    def test_six_native_backend_copies_select_shared_inbox_without_files_or_catalog(self):
        source_snapshots = {}
        for backend_id in ("go", "rust"):
            profile, manifest = self.selection(backend_id)
            backend = select_backend(ROOT, self.backends, backend_id, None, "inbox")
            for native in ("swift", "kotlin", "both"):
                with self.subTest(backend=backend_id, platform=native):
                    inputs = ProductInputs.admit(f"inbox-{backend_id}-{native}", native)
                    selected = plan_sources(ROOT, profile, inputs)
                    if native in source_snapshots:
                        self.assertEqual(selected, source_snapshots[native], "Backend choice must preserve inbox source and policies")
                    source_snapshots[native] = selected
                    app = create_project(ROOT, profile, manifest, inputs, self.output, backend=backend)
                    metadata = json.loads((app / "mobile-foundry.json").read_text())
                    self.assertIn("inbox", metadata["capabilities"])
                    self.assertEqual(metadata["nativeBackendConnection"], backend_id + "-connected")
                    self.assertFalse((app / "backend" / ("go" if backend_id == "rust" else "rust")).exists())
                    self.assertTrue((app / "contracts/fixtures/notifications/pages.json").is_file())
                    self.assertIn("## Account inbox", (app / "SETUP.md").read_text())
                    self.assertNotIn("Files/inbox screens", (app / "SETUP.md").read_text())
                    self.assertIn("CONNECTED_SCENARIO ?= full", (app / "Makefile").read_text())
                    if native != "kotlin":
                        source = app / "frontend/swift/apps/ProductApp/Sources"
                        self.assertTrue((source / "Inbox/InboxStore.swift").is_file())
                        self.assertIn("inbox.bind(HTTPNotificationInboxRepository", (source / "Connection/ConnectedSessionModel.swift").read_text())
                        self.assertIn("inbox.bind(nil", (source / "Connection/ConnectedSessionModel.swift").read_text())
                        self.assertFalse((app / "frontend/swift/packages/FoundryServices/Sources/FoundryServices/RemoteFiles.swift").exists())
                        self.assertTrue((app / "frontend/swift/packages/FoundryServices/Sources/FoundryServices/RemoteServiceAdmission.swift").is_file())
                    if native != "swift":
                        source = app / "frontend/kotlin/project/product/src/main/kotlin/dev/mobilefoundry/product"
                        self.assertTrue((source / "connection/InboxStore.kt").is_file())
                        self.assertIn("inbox.bind(HTTPNotificationInboxRepository", (source / "connection/ConnectedSessionModel.kt").read_text())
                        self.assertIn("inbox.bind(null", (source / "connection/ConnectedSessionModel.kt").read_text())
                        self.assertFalse((app / "frontend/kotlin/project/core/services/src/main/kotlin/dev/mobilefoundry/services/RemoteFiles.kt").exists())
                        self.assertTrue((app / "frontend/kotlin/project/core/services/src/main/kotlin/dev/mobilefoundry/services/RemoteServiceAdmission.kt").is_file())
                    self.assertFalse(any(path.name in {"FoundryCatalog", "catalog"} for path in app.rglob("*")))

    def test_base_connected_selections_keep_activity_and_exclude_inbox(self):
        for backend_id in ("go", "rust"):
            profile, _ = self.combinations[("native-daily", (backend_id + "-connected",))]
            files = plan_sources(ROOT, profile, ProductInputs.admit("base", "both"))
            self.assertFalse(any("/InboxStore" in file.relative or "/NotificationInbox" in file.relative for file in files))
            swift = next(file.data.decode() for file in files if file.relative.endswith("ProductRootView.swift"))
            kotlin = next(file.data.decode() for file in files if file.relative.endswith("ProductRoot.kt"))
            self.assertIn("ActivityScreen()", swift)
            self.assertIn("ActivityScreen()", kotlin)

    def test_unregistered_combinations_and_required_fixture_or_composition_drift_refuse(self):
        for shell, selected in (("native-daily", ["inbox"]), ("native-daily", ["inbox", "local-data"]),
            ("native-workspace", ["go-connected", "inbox"]), ("native-browse", ["rust-connected", "inbox"]),
            ("native-daily", ["go-connected", "inbox", "rust-connected"])):
            with self.assertRaisesRegex(ProfileError, "No registered combination"):
                select_combination(shell, selected, self.profiles, self.capabilities, self.combinations)
        profile, _ = self.selection("rust")
        changed = copy.deepcopy(profile)
        changed["sharedSources"].remove("contracts/fixtures/notifications")
        self.assertTrue(any("notifications" in error for error in validate_profile(changed, ROOT)))
        changed = copy.deepcopy(profile)
        changed["platforms"]["swift"]["overlays"] = [item for item in changed["platforms"]["swift"]["overlays"] if not item["destination"].endswith("InboxStore.swift")]
        self.assertTrue(any("InboxStore" in error for error in validate_profile(changed, ROOT)))

    def test_terminal_admits_order_independent_explicit_inbox_and_refuses_missing_connection(self):
        command = [sys.executable, str(ROOT / "bin/mobile-foundry"), "init", "inbox", "--platform", "both", "--with", "inbox", "--backend", "rust"]
        result = subprocess.run(command, cwd=self.output, capture_output=True, text=True)
        self.assertEqual(result.returncode, 1)
        self.assertFalse((self.output / "inbox").exists())
        result = subprocess.run(command + ["--with", "rust-connected"], cwd=self.output, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("inbox", result.stdout)
        metadata = json.loads((self.output / "inbox/mobile-foundry.json").read_text())
        self.assertEqual(metadata["capabilities"], ["ui", "inbox", "rust-connected"])


if __name__ == "__main__":
    unittest.main()
