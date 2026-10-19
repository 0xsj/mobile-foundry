import copy
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
from mobile_foundry.backends import select_backend
from mobile_foundry.capabilities import discover_combinations, select_combination
from mobile_foundry.connections import connection_base
from mobile_foundry.generation import ProductInputs, create_project, plan_sources
from mobile_foundry.profiles import ProfileError, discover, validate_profile


class RustConnectionTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="mobile-rust-connected-")
        self.addCleanup(temporary.cleanup)
        self.output = Path(temporary.name).resolve()
        self.profiles, self.backends = discover(ROOT)
        self.capabilities, self.combinations = discover_combinations(ROOT, self.profiles)
        self.profile, self.manifest = select_combination("native-daily", ["rust-connected"], self.profiles, self.capabilities, self.combinations)
        self.backend = select_backend(ROOT, self.backends, "rust", None, "canopy")

    def test_all_native_selections_share_ports_with_go_and_own_rust_runtime(self):
        go_profile = self.combinations[("native-daily", ("go-connected",))][0]
        for platform in ("swift", "kotlin", "both"):
            with self.subTest(platform=platform):
                inputs = ProductInputs.admit("rust-" + platform, platform)
                go_sources = plan_sources(ROOT, go_profile, inputs)
                rust_sources = plan_sources(ROOT, self.profile, inputs)
                self.assertEqual(go_sources, rust_sources, "Backend choice must not fork native adapters or recovery policy")
                app = create_project(ROOT, self.profile, self.manifest, inputs, self.output, backend=self.backend,
                    api_base_url="https://API.example.com:443/v1/")
                metadata = json.loads((app / "mobile-foundry.json").read_text())
                self.assertEqual(metadata["nativeBackendConnection"], "rust-connected")
                self.assertEqual(metadata["backend"], "rust")
                self.assertEqual(metadata["rustPackage"], "canopy-api")
                self.assertEqual(metadata["apiBaseURL"], "https://api.example.com/v1")
                self.assertNotIn("goModule", metadata)
                self.assertFalse((app / "backend/go").exists())
                self.assertTrue((app / "backend/rust/scripts/postgres_integration.py").is_file())
                self.assertTrue((app / "scripts/connected_acceptance.py").is_file())
                for native in inputs.platforms:
                    self.assertIn('"https://api.example.com/v1"', (app / self.profile["platforms"][native]["connectionFile"]).read_text())
                guide = (app / "SETUP.md").read_text()
                self.assertIn("Connected native workflow", guide)
                self.assertIn("Backend: rust (connected).", guide)
                self.assertIn("Backend: rust (connected).", (app / "README.md").read_text())
                self.assertIn("make rust-dev", guide)
                self.assertNotIn("make go-dev", guide)
                self.assertNotIn("native auth/data connection is not selected", guide)
                self.assertIn("SQLite journal before HTTP", guide)
                self.assertIn("connected-test", (app / "Makefile").read_text())

    def test_mismatched_and_unregistered_combinations_refuse_before_output(self):
        go = select_backend(ROOT, self.backends, "go", None, "canopy")
        for backend in (None, go):
            with self.assertRaisesRegex(ProfileError, "rust-connected requires --backend rust"):
                create_project(ROOT, self.profile, self.manifest, ProductInputs.admit("refused", "both"), self.output, backend=backend)
        for shell, selected in (("native-workspace", ["rust-connected"]), ("native-browse", ["rust-connected"]),
            ("native-daily", ["local-data", "rust-connected"]), ("native-daily", ["go-connected", "rust-connected"])):
            with self.assertRaisesRegex(ProfileError, "No registered combination"):
                select_combination(shell, selected, self.profiles, self.capabilities, self.combinations)
        for value in ("http://api.example.com/v1", "https://user:secret@example.com/v1", "https://api.example.com/v1?key=x"):
            with self.assertRaises(ProfileError):
                connection_base(self.profile, self.backend, value)
        self.assertEqual(list(self.output.iterdir()), [])
        for mutate in (lambda p: p.pop("requiresBackend"), lambda p: p.update(requiresBackend="go"),
            lambda p: p["platforms"]["swift"].pop("connectionFile"),
            lambda p: p["sharedSources"].remove("contracts/fixtures/pending-notes")):
            changed = copy.deepcopy(self.profile)
            mutate(changed)
            self.assertTrue(validate_profile(changed, ROOT))

    def test_terminal_requires_explicit_matching_connection(self):
        command = [sys.executable, str(ROOT / "bin/mobile-foundry"), "init", "canopy", "--platform", "both", "--with", "rust-connected"]
        for extra in ([], ["--backend", "go"]):
            result = subprocess.run(command + extra, cwd=self.output, capture_output=True, text=True)
            self.assertEqual(result.returncode, 1)
            self.assertIn("requires --backend rust", result.stderr)
            self.assertFalse((self.output / "canopy").exists())
        result = subprocess.run(command + ["--backend", "rust"], cwd=self.output, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("connected to Rust", result.stdout)
        self.assertNotIn("wiring is not selected", result.stdout)

    def test_copied_acceptance_builds_selected_locked_package_without_go(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        self.addCleanup(sys.path.remove, str(ROOT / "scripts"))
        spec = importlib.util.spec_from_file_location("connected_acceptance_probe", ROOT / "scripts/connected_acceptance.py")
        driver = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(driver)
        api = self.output / "backend/rust/cmd/server"
        api.mkdir(parents=True)
        (api / "Cargo.toml").write_text('[package]\nname = "canopy-api"\n')
        with patch.object(driver, "ROOT", self.output), patch.object(driver, "run") as run:
            directory, binary = driver.build_api(self.output, {"CARGO_TARGET_DIR": "/unrelated"}, "rust", "canopy-api")
            self.assertEqual(directory, self.output / "backend/rust")
            self.assertEqual(binary, directory / "target/debug/api-server")
            self.assertEqual(run.call_args.args, ("cargo", "build", "--locked", "-p", "canopy-api", "--bin", "api-server"))
            self.assertEqual(run.call_args.kwargs["env"]["CARGO_TARGET_DIR"], str(directory / "target"))
            self.assertEqual(run.call_args.kwargs["env"]["RUSTUP_AUTO_INSTALL"], "0")
            run.reset_mock()
            with self.assertRaisesRegex(RuntimeError, "identity mismatch"):
                driver.build_api(self.output, {}, "rust", "different")
            run.assert_not_called()
            directory, binary = driver.build_api(self.output, {}, "go")
            self.assertEqual(directory, self.output / "backend/go")
            self.assertEqual(binary, self.output / "server")
            self.assertEqual(run.call_args.args, ("go", "build", "-o", str(binary), "./cmd/server"))
            self.assertEqual(run.call_args.kwargs["cwd"], directory)


if __name__ == "__main__":
    unittest.main()
