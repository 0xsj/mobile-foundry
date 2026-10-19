import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "cli"))
from mobile_foundry.backends import select_backend
from mobile_foundry.capabilities import discover_combinations, select_combination
from mobile_foundry.generation import ProductInputs, create_project, plan_sources
from mobile_foundry.profiles import ProfileError, discover, validate_profile


class FilesTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="mobile-files-cli-")
        self.addCleanup(temporary.cleanup)
        self.output = Path(temporary.name).resolve()
        self.profiles, self.backends = discover(ROOT)
        self.capabilities, self.combinations = discover_combinations(ROOT, self.profiles)

    def selection(self, backend):
        return select_combination("native-daily", ["files", backend + "-connected"], self.profiles, self.capabilities, self.combinations)

    def test_six_independent_copies_share_native_files_without_catalog_graphics_or_inbox_screen(self):
        snapshots = {}
        for backend_id in ("go", "rust"):
            profile, manifest = self.selection(backend_id)
            backend = select_backend(ROOT, self.backends, backend_id, None, "media")
            for native in ("swift", "kotlin", "both"):
                with self.subTest(backend=backend_id, platform=native):
                    inputs = ProductInputs.admit(f"files-{backend_id}-{native}", native)
                    selected = plan_sources(ROOT, profile, inputs)
                    if native in snapshots:
                        self.assertEqual(selected, snapshots[native])
                    snapshots[native] = selected
                    app = create_project(ROOT, profile, manifest, inputs, self.output, backend=backend)
                    metadata = json.loads((app / "mobile-foundry.json").read_text())
                    self.assertEqual(metadata["capabilities"], ["ui"] + sorted(["files", backend_id + "-connected"]))
                    self.assertIn("## Account files", (app / "SETUP.md").read_text())
                    self.assertIn("survives process exit", (app / "SETUP.md").read_text())
                    self.assertIn("PendingFilesStore", (app / "SETUP.md").read_text())
                    self.assertTrue((app / "contracts/fixtures/pending-files/records.json").is_file())
                    self.assertIn("Forget local recovery", (app / "SETUP.md").read_text())
                    self.assertIn("never rolls back", (app / "SETUP.md").read_text())
                    self.assertTrue((app / "contracts/fixtures/files/photo.png").is_file())
                    paths = [file.relative for file in selected]
                    self.assertFalse(any("FoundryGraphics" in file or "InboxStore" in file or "FoundryCatalog" in file for file in paths))
                    if native != "kotlin":
                        source = app / "frontend/swift/apps/ProductApp/Sources"
                        self.assertTrue((source / "Files/FilesStore.swift").is_file())
                        self.assertEqual((source / "Files/FilesStore.swift").read_bytes(), (ROOT / "templates/overlays/files/swift/FilesStore.swift").read_bytes())
                        self.assertIn("files.review", (source / "Screens/TodayScreen.swift").read_text())
                        self.assertIn("files.bind(HTTPRemoteFilesRepository", (source / "Connection/ConnectedSessionModel.swift").read_text())
                        self.assertIn("files.bind(nil", (source / "Connection/ConnectedSessionModel.swift").read_text())
                        self.assertIn("ActivityScreen()", (source / "ProductRootView.swift").read_text())
                    if native != "swift":
                        source = app / "frontend/kotlin/project/product/src/main/kotlin/dev/mobilefoundry/product"
                        self.assertTrue((source / "connection/FilesStore.kt").is_file())
                        self.assertEqual((source / "connection/FilesStore.kt").read_bytes(), (ROOT / "templates/overlays/files/kotlin/FilesStore.kt").read_bytes())
                        self.assertIn("files.review", (source / "screens/TodayScreen.kt").read_text())
                        self.assertIn("files.bind(HTTPRemoteFilesRepository", (source / "connection/ConnectedSessionModel.kt").read_text())
                        self.assertIn("files.bind(null", (source / "connection/ConnectedSessionModel.kt").read_text())

    def test_base_selections_exclude_files_and_unsupported_combinations_are_refused(self):
        for backend in ("go", "rust"):
            base = self.combinations[("native-daily", (backend + "-connected",))][0]
            sources = plan_sources(ROOT, base, ProductInputs.admit("base", "both"))
            self.assertFalse(any("FilesStore" in file.relative or "RemoteFiles" in file.relative for file in sources))
        for profile, capabilities in (("native-daily", ["files"]), ("native-daily", ["files", "inbox"]),
            ("native-daily", ["files", "go-connected", "rust-connected"]), ("native-workspace", ["files", "go-connected"])):
            with self.assertRaisesRegex(ProfileError, "No registered combination"):
                select_combination(profile, capabilities, self.profiles, self.capabilities, self.combinations)

    def test_required_file_fixture_and_owner_closure_cannot_drift(self):
        profile, _ = self.selection("rust")
        changed = copy.deepcopy(profile); changed["sharedSources"].remove("contracts/fixtures/pending-files")
        self.assertTrue(any("pending-files" in error for error in validate_profile(changed, ROOT)))
        changed = copy.deepcopy(profile); changed["platforms"]["swift"]["sources"].remove("frontend/swift/packages/FoundryData/Sources/FoundryData/SQLitePendingFilesStore.swift")
        self.assertTrue(any("SQLitePendingFilesStore" in error for error in validate_profile(changed, ROOT)))
        changed = copy.deepcopy(profile); changed["sharedSources"].remove("contracts/fixtures/files")
        self.assertTrue(any("files" in error for error in validate_profile(changed, ROOT)))
        changed = copy.deepcopy(profile)
        changed["platforms"]["swift"]["overlays"] = [item for item in changed["platforms"]["swift"]["overlays"] if not item["destination"].endswith("FilesStore.swift")]
        self.assertTrue(any("FilesStore" in error for error in validate_profile(changed, ROOT)))


if __name__ == "__main__":
    unittest.main()
