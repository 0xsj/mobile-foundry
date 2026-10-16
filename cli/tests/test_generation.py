import copy
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "cli"))
from mobile_foundry.generation import ProductInputs, create_project, plan_sources
from mobile_foundry.profiles import ProfileError, discover, validate_profile


class GenerationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="mobile-cli-test-")
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name).resolve()
        self.profile, self.manifest = discover(ROOT)[0]["native-daily"]

    def generate(self, name="example", platform="both", root=ROOT, **inputs):
        return create_project(root, self.profile, self.manifest, ProductInputs.admit(name, platform, **inputs), self.output)

    def minimal_source_copy(self):
        source = self.output / "source"
        source.mkdir()
        for item in plan_sources(ROOT, self.profile, ProductInputs.admit("source", "both")):
            path = source / item.relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(item.data)
            path.chmod(0o755 if item.executable else 0o644)
        for relative in ["mobile-foundry.starters.json", self.manifest, "templates/overlays/kotlin/settings.gradle.kts"]:
            path = source / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes((ROOT / relative).read_bytes())
        return source

    def test_all_platform_combinations_copy_only_selected_closure_and_branding(self):
        for platform in ("swift", "kotlin", "both"):
            with self.subTest(platform=platform):
                destination = self.generate(name="sample-" + platform, platform=platform, display_name='Canopy & "Friends"', app_id="com.example.canopy")
                metadata = json.loads((destination / "mobile-foundry.json").read_text())
                self.assertEqual(metadata["backend"], "none")
                self.assertEqual(metadata["appId"], "com.example.canopy")
                self.assertEqual(len(metadata["source"]["contentSha256"]), 64)
                self.assertNotIn(str(ROOT), (destination / "mobile-foundry.json").read_text())
                self.assertFalse((destination / "backend").exists())
                self.assertFalse((destination / "frontend/swift/apps/FoundryCatalog").exists())
                self.assertFalse((destination / "frontend/kotlin/project/app").exists())
                for fixture in ("kernel", "query", "ui"):
                    self.assertTrue((destination / "contracts/fixtures" / fixture).is_dir())
                self.assertEqual((destination / "frontend/swift").exists(), platform != "kotlin")
                self.assertEqual((destination / "frontend/kotlin").exists(), platform != "swift")
                if platform != "kotlin":
                    config = (destination / "frontend/swift/apps/ProductApp/Config/Product.xcconfig").read_text()
                    self.assertIn("PRODUCT_APP_IDENTIFIER = com.example.canopy", config)
                    self.assertIn('PRODUCT_DISPLAY_NAME = Canopy & "Friends"', config)
                    self.assertRegex(config, r"DEVELOPMENT_TEAM =\n")
                    self.assertFalse((destination / "frontend/swift/apps/ProductApp/ProductApp.xcodeproj").exists())
                if platform != "swift":
                    kotlin = destination / "frontend/kotlin/project"
                    self.assertTrue(os.access(kotlin / "gradlew", os.X_OK))
                    self.assertTrue((kotlin / "gradle/wrapper/gradle-wrapper.jar").is_file())
                    settings = (kotlin / "settings.gradle.kts").read_text()
                    self.assertNotIn('\":app\"', settings)
                    self.assertIn('\":product\"', settings)
                    label = ET.parse(kotlin / "product/src/main/res/values/strings.xml").find("./string[@name='app_name']").text
                    self.assertIn('Canopy & \\"Friends\\"', label)
                made = subprocess.run(["make", "help"], cwd=destination, text=True, capture_output=True)
                self.assertEqual(made.returncode, 0, made.stderr)
                self.assertEqual("make ios-build" in made.stdout, platform != "kotlin")
                self.assertEqual("make android-build" in made.stdout, platform != "swift")
                self.assertFalse(any(path.is_symlink() for path in destination.rglob("*")))

    def test_existing_directory_file_and_dangling_symlink_are_preserved(self):
        for name, kind in (("existing", "dir"), ("empty", "empty"), ("file", "file"), ("link", "link")):
            path = self.output / name
            if kind == "dir":
                path.mkdir(); (path / "keep").write_text("keep")
            elif kind == "empty":
                path.mkdir()
            elif kind == "file":
                path.write_text("keep")
            else:
                path.symlink_to(self.output / "missing")
            with self.assertRaisesRegex(ProfileError, "already exists"):
                self.generate(name=name)
            self.assertTrue(os.path.lexists(path))
        self.assertEqual((self.output / "existing/keep").read_text(), "keep")
        self.assertEqual(list((self.output / "empty").iterdir()), [])
        self.assertEqual((self.output / "file").read_text(), "keep")
        self.assertTrue((self.output / "link").is_symlink())

    def test_source_overlap_and_invalid_identity_refuse_without_output(self):
        with self.assertRaisesRegex(ProfileError, "must not contain"):
            create_project(ROOT, self.profile, self.manifest, ProductInputs.admit("overlap", "both"), ROOT)
        for fields in [dict(name="../outside"), dict(name="/absolute"), dict(name="trailing-"), dict(name="Upper"), dict(name="x", app_id="invalid"), dict(name="x", app_id="com.7invalid"), dict(name="x", app_id="com.example.bad_id"), dict(name="x", display_name="bad\nlabel"), dict(name="x", display_name="$(HOME)"), dict(name="x", display_name="bad//comment"), dict(name="x", ios_team="INVALID"), dict(name="x", platform="kotlin", ios_team="ABCD123456")]:
            with self.subTest(fields=fields), self.assertRaises(ProfileError):
                values = dict(platform="both", **{key: value for key, value in fields.items() if key != "platform"})
                if "platform" in fields:
                    values["platform"] = fields["platform"]
                ProductInputs.admit(**values)
        self.assertEqual(list(self.output.iterdir()), [])

    def test_copy_failures_and_interruptions_remove_only_new_owned_output(self):
        original_open = Path.open
        def fail_copy(path, mode="r", *args, **kwargs):
            if mode == "xb":
                raise OSError("copy write failure")
            return original_open(path, mode, *args, **kwargs)
        with patch.object(Path, "open", new=fail_copy):
            with self.assertRaisesRegex(OSError, "copy write failure"):
                self.generate(name="copy-failed")
        self.assertFalse((self.output / "copy-failed").exists())
        for index, error in enumerate((OSError("disk failure"), KeyboardInterrupt())):
            name = "failed-" + str(index)
            with patch("mobile_foundry.generation.configure_product", side_effect=error):
                with self.assertRaises(type(error)):
                    self.generate(name=name)
            self.assertFalse((self.output / name).exists())
        keeper = self.output / "keeper"; keeper.mkdir(); (keeper / "keep").write_text("keep")
        with patch("mobile_foundry.generation.configure_product", side_effect=OSError("unused")):
            with self.assertRaises(ProfileError):
                self.generate(name="keeper")
        self.assertEqual((keeper / "keep").read_text(), "keep")

    def test_copy_prunes_cache_sdk_secrets_and_signing_and_preserves_source(self):
        source = self.minimal_source_copy()
        base = source / "frontend/kotlin/project/product/src"
        for relative in (".gradle/cached.bin", "build/output.apk", ".env.local", "local.properties", "signing/private.keystore", "signing/key.pem", "xcuserdata/user.xcuserstate"):
            path = base / relative; path.parent.mkdir(parents=True, exist_ok=True); path.write_text("private")
        (base / ".cache").symlink_to(self.output / "unavailable-cache", target_is_directory=True)
        def digest():
            return {str(path.relative_to(source)): hashlib.sha256(path.read_bytes()).hexdigest() for path in source.rglob("*") if path.is_file() and not path.is_symlink()}
        before = digest()
        destination = self.generate(root=source)
        self.assertEqual(before, digest())
        for path in destination.rglob("*"):
            self.assertNotIn(path.name, {".gradle", "build", ".env.local", "local.properties", "private.keystore", "key.pem", "xcuserdata", ".cache"})
        copied = destination / "frontend/kotlin/project/product/src/main/res/drawable/ic_product.xml"
        copied.write_text("changed output")
        self.assertEqual(before, digest())

    def test_nested_symlinks_special_files_and_identity_drift_refuse_safely(self):
        source = self.minimal_source_copy()
        link = source / "frontend/kotlin/project/product/src/link"
        link.symlink_to(ROOT / "README.md")
        with self.assertRaisesRegex(ProfileError, "symlink"):
            self.generate(root=source)
        self.assertFalse((self.output / "example").exists())
        link.unlink()
        if hasattr(os, "mkfifo"):
            os.mkfifo(link)
            with self.assertRaisesRegex(ProfileError, "regular file"):
                self.generate(root=source)
            link.unlink()
        config = source / "frontend/swift/apps/ProductApp/Config/Product.xcconfig"
        original = config.read_text(); config.write_text(original.replace("PRODUCT_APP_IDENTIFIER", "RENAMED_ID"))
        with self.assertRaisesRegex(ProfileError, "drifted"):
            self.generate(root=source)
        self.assertFalse((self.output / "example").exists())
        self.assertIn("RENAMED_ID", config.read_text())

    def test_dependency_fixture_and_path_mutations_fail_profile_validation(self):
        scenarios = [("swift", "packages", "FoundryQuery"), ("kotlin", "modules", ":core:query")]
        for platform, key, missing in scenarios:
            profile = copy.deepcopy(self.profile); profile["platforms"][platform][key].remove(missing)
            self.assertTrue(any("depends on unselected" in error for error in validate_profile(profile, ROOT)))
        profile = copy.deepcopy(self.profile); profile["sharedSources"].remove("contracts/fixtures/ui")
        self.assertTrue(any("missing ui test fixtures" in error for error in validate_profile(profile, ROOT)))
        profile = copy.deepcopy(self.profile); profile["sharedSources"].append("../outside")
        self.assertTrue(any("Nonportable" in error for error in validate_profile(profile, ROOT)))


if __name__ == "__main__":
    unittest.main()
