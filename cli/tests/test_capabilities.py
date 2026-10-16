import copy
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "cli"))
from mobile_foundry.capabilities import discover_combinations, select_combination
from mobile_foundry.generation import ProductInputs, create_project, plan_sources
from mobile_foundry.profiles import ProfileError, discover, validate_profile


class CapabilityTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="mobile-capability-")
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name).resolve()
        self.profiles, _ = discover(ROOT)
        self.capabilities, self.combinations = discover_combinations(ROOT, self.profiles)
        self.profile, self.manifest = select_combination("native-daily", ["local-data"], self.profiles, self.capabilities, self.combinations)

    def test_each_platform_selects_local_closure_overlays_guides_and_provenance(self):
        before = {item.relative: hashlib.sha256(item.data).hexdigest() for item in plan_sources(ROOT, self.profile, ProductInputs.admit("before", "both"))}
        for platform in ("swift", "kotlin", "both"):
            with self.subTest(platform=platform):
                app = create_project(ROOT, self.profile, self.manifest, ProductInputs.admit("local-" + platform, platform, app_id="com.example.local"), self.output)
                metadata = json.loads((app / "mobile-foundry.json").read_text())
                self.assertEqual(metadata["capabilities"], ["ui", "local-data"])
                self.assertEqual(metadata["source"]["profileSha256"], hashlib.sha256((ROOT / self.manifest).read_bytes()).hexdigest())
                self.assertIn("Open Library", (app / "SETUP.md").read_text())
                self.assertTrue((app / "contracts/fixtures/local-notes/repository.json").exists())
                self.assertFalse(any("FoundryCatalog" in str(p) or "catalog" in str(p) for p in app.rglob("*")))
                if platform != "kotlin":
                    base = app / "frontend/swift"
                    self.assertFalse((base / "packages/FoundrySession").exists())
                    self.assertFalse((base / "packages/FoundryHTTP").exists())
                    self.assertIn("FoundryData", (base / "apps/ProductApp/project.yml").read_text())
                    self.assertNotIn("HTTP", (base / "packages/FoundryServices/Package.swift").read_text())
                    self.assertTrue((base / "apps/ProductApp/UITests/LocalNotesUITests.swift").is_file())
                    self.assertIn("FoundryData", (app / "Makefile").read_text())
                if platform != "swift":
                    base = app / "frontend/kotlin/project"
                    self.assertFalse((base / "core/session").exists())
                    self.assertFalse((base / "core/http").exists())
                    self.assertIn('\":core:data\"', (base / "settings.gradle.kts").read_text())
                    self.assertIn(":core:data:connectedDebugAndroidTest", (app / "Makefile").read_text())
                    self.assertTrue((base / "product/src/test/kotlin/dev/mobilefoundry/product/notes/LocalNotesViewModelTest.kt").is_file())
                for p in app.rglob("*"):
                    if p.is_file() and p.suffix in {".swift", ".kt", ".kts", ".yml"}:
                        self.assertNotRegex(p.read_text(), r"(?m)^(?:package|import) dev\.mobilefoundry\.catalog")
                        self.assertNotIn("import FoundryCatalog", p.read_text())
        after = {item.relative: hashlib.sha256(item.data).hexdigest() for item in plan_sources(ROOT, self.profile, ProductInputs.admit("after", "both"))}
        self.assertEqual(before, after)

    def test_unknown_repeated_planned_and_unregistered_selections_refuse(self):
        for selected, expected in ((["unknown"], "Unknown capability"), (["local-data", "local-data"], "repeat"), (["later"], "planned")):
            with self.subTest(selected=selected), self.assertRaisesRegex(ProfileError, expected):
                select_combination("native-daily", selected, self.profiles, {**self.capabilities, "later": {"status": "planned"}}, self.combinations)
        with self.assertRaisesRegex(ProfileError, "No registered combination"):
            select_combination("native-daily", ["local-data"], self.profiles, self.capabilities, {})
        self.assertEqual(select_combination("native-daily", [], self.profiles, self.capabilities, self.combinations), self.profiles["native-daily"])
        self.assertEqual(list(self.output.iterdir()), [])

    def test_effective_manifest_and_settings_drifts_are_checked(self):
        profile = copy.deepcopy(self.profile)
        profile["platforms"]["swift"]["overlays"] = [o for o in profile["platforms"]["swift"]["overlays"] if not o["destination"].endswith("FoundryData/Package.swift")]
        errors = validate_profile(profile, ROOT)
        self.assertTrue(any("depends on unselected FoundrySession" in e for e in errors), errors)
        profile = copy.deepcopy(self.profile)
        profile["platforms"]["kotlin"]["overlays"][0]["source"] = "templates/overlays/kotlin/settings.gradle.kts"
        self.assertTrue(any("settings overlay differs" in e for e in validate_profile(profile, ROOT)))
        profile = copy.deepcopy(self.profile)
        profile["platforms"]["swift"]["overlays"].append(profile["platforms"]["swift"]["overlays"][0])
        self.assertTrue(any("duplicate overlay" in e for e in validate_profile(profile, ROOT)))

    def test_missing_capability_sources_fixtures_and_excluded_required_files_refuse(self):
        profile = copy.deepcopy(self.profile)
        profile["sharedSources"].remove("contracts/fixtures/local-notes")
        self.assertTrue(any("missing local-notes" in e for e in validate_profile(profile, ROOT)))
        profile = copy.deepcopy(self.profile)
        path = "frontend/swift/packages/FoundryServices/Sources/FoundryServices/NotesRepository.swift"
        profile["platforms"]["swift"]["sources"].remove(path)
        self.assertTrue(any("required capability file absent" in e for e in validate_profile(profile, ROOT)))
        profile = copy.deepcopy(self.profile); profile["exclude"].append("**/NotesRepository.swift")
        with self.assertRaisesRegex(ProfileError, "was excluded"):
            create_project(ROOT, profile, self.manifest, ProductInputs.admit("excluded", "swift"), self.output)
        profile = copy.deepcopy(self.profile); profile["exclude"].append("**/local-notes/repository.json")
        with self.assertRaisesRegex(ProfileError, "Required shared file was excluded"):
            create_project(ROOT, profile, self.manifest, ProductInputs.admit("excluded", "kotlin"), self.output)
        self.assertEqual(list(self.output.iterdir()), [])

    def test_capability_copy_failures_clean_owned_destination_and_preserve_existing(self):
        keeper = self.output / "keeper"; keeper.mkdir(); (keeper / "draft").write_text("keep")
        with patch("mobile_foundry.generation.write_documents", side_effect=OSError("guide failure")):
            with self.assertRaisesRegex(OSError, "guide failure"):
                create_project(ROOT, self.profile, self.manifest, ProductInputs.admit("failed", "both"), self.output)
        self.assertFalse((self.output / "failed").exists())
        with self.assertRaisesRegex(ProfileError, "already exists"):
            create_project(ROOT, self.profile, self.manifest, ProductInputs.admit("keeper", "both"), self.output)
        self.assertEqual((keeper / "draft").read_text(), "keep")

    def test_terminal_selection_and_registry_mismatch_are_explicit(self):
        result = subprocess.run([sys.executable, str(ROOT / "bin/mobile-foundry"), "init", "notes-app", "--platform", "kotlin", "--with", "local-data"], cwd=self.output, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("ui, local-data", result.stdout)
        for selected in (["unknown"], ["local-data", "local-data"]):
            args = [sys.executable, str(ROOT / "bin/mobile-foundry"), "init", "invalid", "--platform", "both"]
            for item in selected:
                args += ["--with", item]
            result = subprocess.run(args, cwd=self.output, capture_output=True, text=True)
            self.assertEqual(result.returncode, 1, result.stderr)
            self.assertNotIn("Traceback", result.stderr)
            self.assertFalse((self.output / "invalid").exists())
        registry = json.loads((ROOT / "mobile-foundry.starters.json").read_text())
        registry["combinations"][0]["capabilities"] = ["missing"]
        with patch("mobile_foundry.capabilities.read_object", return_value=registry), self.assertRaisesRegex(ProfileError, "unknown/planned"):
            discover_combinations(ROOT, self.profiles)
        registry = json.loads((ROOT / "mobile-foundry.starters.json").read_text())
        malformed = copy.deepcopy(self.profile); malformed["platforms"] = ["swift", "kotlin"]
        with patch("mobile_foundry.capabilities.read_object", side_effect=[registry, malformed]), self.assertRaisesRegex(ProfileError, "platforms"):
            discover_combinations(ROOT, self.profiles)


if __name__ == "__main__":
    unittest.main()
