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


class MediaTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="mobile-media-cli-")
        self.addCleanup(temporary.cleanup)
        self.output = Path(temporary.name).resolve()
        self.profiles, self.backends = discover(ROOT)
        self.capabilities, self.combinations = discover_combinations(ROOT, self.profiles)

    def selection(self, backend):
        return select_combination("native-daily", ["inbox", backend + "-connected", "files"], self.profiles, self.capabilities, self.combinations)

    def test_six_copies_share_native_composition_and_reuse_existing_owners(self):
        snapshots = {}
        for backend_id in ("go", "rust"):
            profile, manifest = self.selection(backend_id)
            backend = select_backend(ROOT, self.backends, backend_id, None, "media")
            for native in ("swift", "kotlin", "both"):
                with self.subTest(backend=backend_id, platform=native):
                    inputs = ProductInputs.admit(f"media-{backend_id}-{native}", native)
                    selected = plan_sources(ROOT, profile, inputs)
                    if native in snapshots:
                        self.assertEqual(selected, snapshots[native], "Backend choice must preserve native behavior")
                    snapshots[native] = selected
                    app = create_project(ROOT, profile, manifest, inputs, self.output, backend=backend)
                    metadata = json.loads((app / "mobile-foundry.json").read_text())
                    self.assertEqual(metadata["capabilities"], ["ui"] + sorted(["files", "inbox", backend_id + "-connected"]))
                    setup = (app / "SETUP.md").read_text()
                    self.assertIn("## Files and Inbox together", setup)
                    self.assertIn("Today selects account Files; Activity selects the account inbox", setup)
                    self.assertNotIn("Today remains a product placeholder", setup)
                    self.assertNotIn("Files screens and live push remain separate", setup)
                    self.assertIn("CONNECTED_SCENARIO=media", setup)
                    self.assertIn("Open file reads current authorized metadata", setup)
                    self.assertIn("It does not mark the notice read", setup)
                    for language, ext in (("swift", "swift"), ("kotlin", "kt")):
                        if native != "both" and native != language: continue
                        for name in ("FilesStore", "InboxStore"):
                            selected_owner = next(item for item in selected if item.relative.endswith("/" + name + "." + ext))
                            expected = ROOT / "templates/overlays" / ("files" if name == "FilesStore" else "inbox") / language / (name + "." + ext)
                            self.assertEqual(selected_owner.data, expected.read_bytes())
                    self.assertFalse(any("FoundryCatalog" in item.relative or "FoundryGraphics" in item.relative for item in selected))

    def test_both_owner_and_flow_test_closures_are_required(self):
        profile, _ = self.selection("rust")
        for language, suffixes in (("swift", ("FilesStore.swift", "InboxStore.swift", "MediaUITests.swift", "FileDestinationStore.swift", "FileDestinationScreen.swift", "FileDestinationStoreTests.swift")),
            ("kotlin", ("FileDestinationStore.kt", "FileDestinationScreen.kt", "FileDestinationStoreTest.kt"))):
            for suffix in suffixes:
                changed = copy.deepcopy(profile)
                changed["platforms"][language]["overlays"] = [item for item in changed["platforms"][language]["overlays"] if not item["destination"].endswith(suffix)]
                self.assertTrue(any(suffix in error for error in validate_profile(changed, ROOT)))
        for fixture in ("files", "notifications"):
            changed = copy.deepcopy(profile); changed["sharedSources"].remove("contracts/fixtures/" + fixture)
            self.assertTrue(any(fixture in error for error in validate_profile(changed, ROOT)))

    def test_terminal_requires_one_matching_connection_and_accepts_flag_order(self):
        base = [sys.executable, str(ROOT / "bin/mobile-foundry"), "init", "media", "--platform", "both", "--with", "inbox", "--with", "files", "--backend", "rust"]
        result = subprocess.run(base, cwd=self.output, capture_output=True, text=True)
        self.assertEqual(result.returncode, 1); self.assertFalse((self.output / "media").exists())
        result = subprocess.run(base + ["--with", "rust-connected"], cwd=self.output, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads((self.output / "media/mobile-foundry.json").read_text())["capabilities"], ["ui", "files", "inbox", "rust-connected"])
        for shell, values in (("native-workspace", ["files", "inbox", "go-connected"]),
            ("native-daily", ["files", "inbox", "local-data"]), ("native-daily", ["files", "inbox", "go-connected", "rust-connected"])):
            with self.assertRaisesRegex(ProfileError, "No registered combination"):
                select_combination(shell, values, self.profiles, self.capabilities, self.combinations)


if __name__ == "__main__":
    unittest.main()
