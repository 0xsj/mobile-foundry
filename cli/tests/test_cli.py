import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "cli"))
from mobile_foundry.cli import main
from mobile_foundry.doctor import inspect, probe
from mobile_foundry.profiles import discover


class CommandTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="mobile-command-test-")
        self.addCleanup(self.temp.cleanup)
        self.cwd = Path(self.temp.name).resolve()

    def run_cli(self, *args):
        return subprocess.run([sys.executable, str(ROOT / "bin/mobile-foundry"), *args], cwd=self.cwd, capture_output=True, text=True, timeout=30)

    def test_command_is_anchored_to_source_and_generates_in_callers_directory(self):
        listed = self.run_cli("list")
        self.assertEqual(listed.returncode, 0, listed.stderr)
        self.assertIn("native-daily", listed.stdout)
        self.assertRegex(listed.stdout, r"go: (candidate|standalone)")
        result = self.run_cli("init", "from-terminal", "--platform", "kotlin")
        self.assertEqual(result.returncode, 0, result.stderr)
        generated = self.cwd / "from-terminal"
        metadata = json.loads((generated / "mobile-foundry.json").read_text())
        self.assertEqual(metadata["platforms"], ["kotlin"])
        self.assertEqual(metadata["displayName"], "From Terminal")
        self.assertEqual(metadata["appId"], "dev.example.fromterminal")
        self.assertNotIn(str(ROOT), (generated / "SETUP.md").read_text())

    def test_noninteractive_selection_and_planned_or_unknown_backends_refuse_before_write(self):
        for args, expected in [
            (("init", "missing"), "Choose --platform"),
            (("init", "go-project", "--platform", "none"), "requires --backend go"),
            (("init", "unknown", "--platform", "swift", "--backend", "unknown"), "not registered"),
            (("init", "unknown", "--platform", "swift", "--profile", "missing"), "Unknown profile"),
            (("init", "invalid", "--platform", "swift", "--app-id", "not valid"), "App ID"),
            (("init", "invalid", "--platform", "both", "--output", "missing-parent"), "No such file"),
        ]:
            with self.subTest(args=args):
                result = self.run_cli(*args)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(expected, result.stderr)
                self.assertNotIn("Traceback", result.stderr)
        self.assertEqual(list(self.cwd.iterdir()), [])

    def test_source_option_and_registry_drift_errors_are_explicit(self):
        result = self.run_cli("list", "--source", str(ROOT))
        self.assertEqual(result.returncode, 0, result.stderr)
        registry = {"schemaVersion": 1, "profiles": [], "backends": []}
        (self.cwd / "mobile-foundry.starters.json").write_text(json.dumps(registry))
        result = self.run_cli("list", "--source", str(self.cwd))
        self.assertEqual(result.returncode, 1)
        self.assertIn("profiles", result.stderr)
        registry["profiles"] = [{"id": "missing", "manifest": "../escaped.json"}]
        (self.cwd / "mobile-foundry.starters.json").write_text(json.dumps(registry))
        result = self.run_cli("list", "--source", str(self.cwd))
        self.assertEqual(result.returncode, 1)
        self.assertIn("Nonportable", result.stderr)

    def test_doctor_reports_missing_tools_and_returns_failure(self):
        with patch("mobile_foundry.doctor.shutil.which", return_value=None):
            results = inspect(("swift",), {})
        self.assertEqual([item.name for item in results], ["Python", "xcodebuild", "swift", "xcodegen"])
        self.assertTrue(all(not item.ok for item in results[1:]))
        with patch("mobile_foundry.cli.inspect", return_value=results), patch("builtins.print"):
            self.assertEqual(main(["doctor", "--platform", "swift"]), 1)
        with patch("mobile_foundry.doctor.shutil.which", return_value="/tool"), patch("mobile_foundry.doctor.subprocess.run", return_value=subprocess.CompletedProcess([], 0, "Xcode 26.2\n", "")):
            self.assertTrue(probe("xcodebuild", ["-version"], (26, 2), r"Xcode (\d+)\.(\d+)").ok)

    def test_local_install_links_source_and_refuses_another_command(self):
        spec = importlib.util.spec_from_file_location("install_cli", ROOT / "scripts/install_cli.py")
        installer = importlib.util.module_from_spec(spec); spec.loader.exec_module(installer)
        link = installer.install(self.cwd)
        self.assertTrue(link.is_symlink())
        self.assertEqual(installer.install(self.cwd), link)
        result = subprocess.run([str(link), "list"], cwd=self.cwd, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        link.unlink(); link.write_text("existing command")
        with self.assertRaises(FileExistsError):
            installer.install(self.cwd)
        self.assertEqual(link.read_text(), "existing command")

    def test_doctor_separates_gradle_launcher_from_java_compilation_toolchain(self):
        for version, expected in ((17, True), (21, True), (26, False)):
            with self.subTest(version=version), patch("mobile_foundry.doctor.subprocess.run", return_value=subprocess.CompletedProcess([], 0, "", f'openjdk version "{version}.0.1"')):
                results = inspect(("kotlin",), {"JAVA_HOME": "/chosen/jdk", "ANDROID_HOME": str(self.cwd)})
            launcher = next(item for item in results if item.name == "Gradle JVM 17–25")
            self.assertEqual(launcher.ok, expected)
            self.assertIn("JDK 17", next(item.detail for item in results if item.name == "Java compilation toolchain"))


if __name__ == "__main__":
    unittest.main()
