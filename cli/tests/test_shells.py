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
from mobile_foundry.generation import ProductInputs, create_project, plan_sources
from mobile_foundry.profiles import ProfileError, discover, validate_profile


class ShellTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="mobile-shell-")
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name).resolve()
        self.profiles, _ = discover(ROOT)

    def test_all_shell_platform_combinations_copy_only_the_selected_navigation(self):
        for shell in ("workspace", "browse"):
            profile, manifest = self.profiles["native-" + shell]
            before = {item.relative: hashlib.sha256(item.data).hexdigest() for item in plan_sources(ROOT, profile, ProductInputs.admit("before", "both"))}
            for platform in ("swift", "kotlin", "both"):
                with self.subTest(shell=shell, platform=platform):
                    product = create_project(ROOT, profile, manifest, ProductInputs.admit(shell+"-"+platform, platform, display_name="Custom Shell", app_id="com.example.customshell"), self.output)
                    metadata = json.loads((product / "mobile-foundry.json").read_text())
                    self.assertEqual(metadata["shell"], shell)
                    self.assertEqual(metadata["profile"], "native-"+shell)
                    self.assertEqual(metadata["capabilities"], ["ui"])
                    self.assertEqual((product / "frontend/swift").exists(), platform != "kotlin")
                    self.assertEqual((product / "frontend/kotlin").exists(), platform != "swift")
                    guide = (product / "SETUP.md").read_text()
                    self.assertIn("The "+shell.title()+" shell", guide)
                    self.assertNotIn("The Daily shell", guide)
                    self.assertNotIn(str(ROOT), guide)
                    if platform != "kotlin":
                        app = product / "frontend/swift/apps/ProductApp"
                        self.assertEqual([p.name for p in (app / "Sources/Screens").iterdir()], [shell.title()+"Screen.swift"])
                        self.assertTrue((app / ("UITests/"+shell.title()+"UITests.swift")).is_file())
                        self.assertNotIn("Daily", (app / "README.md").read_text())
                    if platform != "swift":
                        app = product / "frontend/kotlin/project/product"
                        self.assertEqual([p.name for p in (app / "src/main/kotlin/dev/mobilefoundry/product/screens").iterdir()], [shell.title()+"Screen.kt"])
                        self.assertTrue((app / ("src/androidTest/kotlin/dev/mobilefoundry/product/"+shell.title()+"ScreenTest.kt")).is_file())
                        self.assertNotIn("Daily", (app / "README.md").read_text())
                    self.assertFalse(any("catalog" in str(p) or "FoundryCatalog" in str(p) for p in product.rglob("*")))
                    self.assertFalse(any(p.name in {"FoundryData", "FoundrySession", "FoundryServices"} for p in product.rglob("*")))
                    make = subprocess.run(["make", "help"], cwd=product, capture_output=True, text=True)
                    self.assertEqual(make.returncode, 0, make.stderr)
            after = {item.relative: hashlib.sha256(item.data).hexdigest() for item in plan_sources(ROOT, profile, ProductInputs.admit("after", "both"))}
            self.assertEqual(before, after)

    def test_required_shell_files_and_effective_graph_detect_inventory_drift(self):
        for shell in ("workspace", "browse"):
            profile, manifest = self.profiles["native-"+shell]
            changed = copy.deepcopy(profile)
            changed["platforms"]["swift"]["overlays"] = [o for o in changed["platforms"]["swift"]["overlays"] if not o["destination"].endswith("ProductState.swift")]
            self.assertTrue(any("required capability file absent" in error for error in validate_profile(changed, ROOT)))
            changed = copy.deepcopy(profile)
            changed["exclude"].append("**/"+shell.title()+"Screen.kt")
            with self.assertRaisesRegex(ProfileError, "Invalid overlay|was excluded"):
                create_project(ROOT, changed, manifest, ProductInputs.admit("excluded", "kotlin"), self.output)
            self.assertFalse((self.output / "excluded").exists())
        changed = copy.deepcopy(profile)
        changed["shell"] = "canvas"
        self.assertTrue(any("Supported generated shells" in error for error in validate_profile(changed, ROOT)))

    def test_terminal_profiles_default_and_incompatible_capability_refusal(self):
        command = [sys.executable, str(ROOT / "bin/mobile-foundry")]
        listed = subprocess.run(command+["list"], cwd=self.output, capture_output=True, text=True)
        self.assertEqual(listed.returncode, 0, listed.stderr)
        for shell in ("workspace", "browse"):
            self.assertIn("native-"+shell, listed.stdout)
            result = subprocess.run(command+["init", shell, "--platform", "kotlin", "--profile", "native-"+shell], cwd=self.output, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run(command+["init", "invalid-"+shell, "--platform", "both", "--profile", "native-"+shell, "--with", "local-data"], cwd=self.output, capture_output=True, text=True)
            self.assertEqual(result.returncode, 1)
            self.assertIn("No registered combination", result.stderr)
            self.assertFalse((self.output / ("invalid-"+shell)).exists())
        result = subprocess.run(command+["init", "default-app", "--platform", "swift"], cwd=self.output, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads((self.output / "default-app/mobile-foundry.json").read_text())["shell"], "daily")


if __name__ == "__main__":
    unittest.main()
