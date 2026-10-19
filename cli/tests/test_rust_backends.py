import copy
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import tomllib
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/"cli"))
from mobile_foundry.backends import select_backend,validate_backend
from mobile_foundry.generation import ProductInputs,create_project,plan_backend_sources
from mobile_foundry.profiles import discover,ProfileError

class RustBackendTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix="mobile-rust-cli-");self.addCleanup(self.temp.cleanup)
        self.output=Path(self.temp.name).resolve();self.profiles,self.registrations=discover(ROOT)
        self.backend=select_backend(ROOT,self.registrations,"rust",None,"canopy",rust_package="canopy-api")
    def cli(self,*args):
        return subprocess.run([sys.executable,str(ROOT/"bin/mobile-foundry"),*args],cwd=self.output,capture_output=True,text=True,timeout=45)
    def generate(self,name,platform="none",root=ROOT,backend=None):
        profile,manifest=self.profiles["native-daily"] if platform!="none" else (None,None)
        return create_project(root,profile,manifest,ProductInputs.admit(name,platform),self.output,backend=backend or self.backend)
    def test_independent_cargo_closure_for_backend_and_all_native_colocations(self):
        before={f.relative:hashlib.sha256(f.data).hexdigest() for f in plan_backend_sources(ROOT,self.backend,ProductInputs.admit("before","none"))}
        for platform in ("none","swift","kotlin","both"):
            with self.subTest(platform=platform):
                project=self.generate("rust-"+platform,platform);api=project/"backend/rust"
                metadata=json.loads((project/"mobile-foundry.json").read_text())
                self.assertEqual(metadata["backend"],"rust");self.assertEqual(metadata["rustPackage"],"canopy-api");self.assertNotIn("goModule",metadata)
                self.assertEqual(metadata["nativeBackendConnection"],None if platform=="none" else "unconnected")
                self.assertEqual((project/"frontend").exists(),platform!="none")
                server=tomllib.loads((api/"cmd/server/Cargo.toml").read_text())
                self.assertEqual(server["package"]["name"],"canopy-api");self.assertEqual(server["bin"][0]["name"],"api-server")
                locked=tomllib.loads((api/"Cargo.lock").read_text())["package"]
                self.assertTrue(any(p["name"]=="canopy-api" for p in locked));self.assertFalse(any(p["name"]=="api-server" for p in locked))
                self.assertTrue((api/"pkg/kernel/src/lib.rs").is_file());self.assertEqual(len(tomllib.loads((api/"Cargo.toml").read_text())["workspace"]["members"]),24)
                self.assertTrue((project/"contracts/fixtures/backend-realtime/hub.json").is_file())
                self.assertTrue((api/"scripts/postgres_owner.py").is_file());self.assertTrue((api/"scripts/postgres_integration.py").is_file())
                self.assertIn("-p canopy-api",(api/"Makefile").read_text());self.assertNotIn("../../scripts",(api/"Makefile").read_text())
                self.assertIn("name: rust-"+platform+"-api",(api/"infra/local/compose.yaml").read_text())
                self.assertIn("PROD_PROJECT ?= rust-"+platform+"-prod",(api/"Makefile").read_text())
                self.assertIn("rust-"+platform+"-api:local",(api/"infra/production/.env.example").read_text())
                self.assertFalse((project/"backend/go").exists());self.assertFalse((api/"notes").exists());self.assertFalse((api/"AGENTS.md").exists())
                self.assertIn("rust-integration-test",(project/"SETUP.md").read_text());self.assertIn("rust-dev:",(project/"Makefile").read_text())
                for path in [project/"README.md",project/"SETUP.md",project/"mobile-foundry.json",api/"README.md"]:self.assertNotIn(str(ROOT),path.read_text())
        after={f.relative:hashlib.sha256(f.data).hexdigest() for f in plan_backend_sources(ROOT,self.backend,ProductInputs.admit("after","none"))}
        self.assertEqual(before,after)
    def test_terminal_default_and_explicit_identity_refuse_incompatible_flags(self):
        result=self.cli("init","rust-api","--platform","none","--backend","rust")
        self.assertEqual(result.returncode,0,result.stderr);self.assertIn("Rust API package: rust-api-api",result.stdout)
        for extra,expected in [(('--go-module','example.com/x/backend'),'requires --backend go'),(('--rust-package','kernel'),'collides'),(('--rust-package','../escape'),'Rust package'),(('--with','go-connected'),'does not select')]:
            result=self.cli("init","invalid","--platform","none","--backend","rust",*extra)
            self.assertEqual(result.returncode,1);self.assertIn(expected,result.stderr);self.assertFalse((self.output/"invalid").exists())
        for backend in ("none","go"):
            result=self.cli("init","invalid","--platform","swift","--backend",backend,"--rust-package","valid-api")
            self.assertEqual(result.returncode,1);self.assertIn("requires --backend rust",result.stderr)
        result=self.cli("init","invalid","--platform","swift","--backend","rust","--with","go-connected")
        self.assertEqual(result.returncode,1);self.assertIn("requires --backend go",result.stderr)
    def test_missing_crate_fixture_migration_and_overlay_are_refused(self):
        for path in ["backend/rust/pkg/kernel/src/lib.rs","backend/rust/pkg/kernel/Cargo.toml","contracts/fixtures/backend-realtime/hub.json","backend/rust/migrations/identity/0002_recovery.sql"]:
            profile=copy.deepcopy(self.backend.profile);profile["exclude"].append(path)
            self.assertTrue(validate_backend(profile,ROOT),path)
        profile=copy.deepcopy(self.backend.profile);profile["workspaceMembers"].pop()
        self.assertTrue(any("drift" in e for e in validate_backend(profile,ROOT)))
        profile=copy.deepcopy(self.backend.profile);profile["overlays"].append(profile["overlays"][0].copy())
        self.assertTrue(any("unique" in e for e in validate_backend(profile,ROOT)))
    def test_package_admission_is_bounded_and_avoids_workspace_collisions(self):
        for name in ["", "UPPER", "api_name", "api--name", "api;touch", "a"*65, "con", "telemetry", "serde"]:
            with self.subTest(name=name),self.assertRaises(ProfileError):select_backend(ROOT,self.registrations,"rust",None,"demo",rust_package=name)
    def test_alternate_source_excludes_private_rust_state_and_refuses_symlinks(self):
        source=self.output/"source";source.mkdir()
        for f in plan_backend_sources(ROOT,self.backend,ProductInputs.admit("source","none")):
            path=source/f.relative;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(f.data)
        for overlay in self.backend.profile["overlays"]:
            path=source/overlay["source"];path.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/overlay["source"],path)
        manifest=source/self.backend.manifest;manifest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/self.backend.manifest,manifest)
        base=source/"backend/rust/pkg/kernel"
        for name in ["target/debug/private",".cargo/credentials.toml",".data/private","smtp.private.toml",".env.production","key.p8"]:
            path=base/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text("PRIVATE")
        project=self.generate("alternate",root=source)
        for name in ["target",".cargo",".data","smtp.private.toml",".env.production","key.p8"]:self.assertFalse(any(p.name==name for p in (project/"backend/rust/pkg").rglob("*")))
        (base/"linked.rs").symlink_to(ROOT/"README.md")
        with self.assertRaisesRegex(ProfileError,"symlink"):self.generate("linked",root=source)
        self.assertFalse((self.output/"linked").exists())
    def test_rewrite_failure_and_cancellation_preserve_existing_ownership(self):
        keeper=self.output/"keeper";keeper.mkdir();(keeper/"draft").write_text("keep")
        with self.assertRaisesRegex(ProfileError,"already exists"):self.generate("keeper")
        for name,error in [("failed",OSError("rewrite")),("interrupted",KeyboardInterrupt())]:
            with patch("mobile_foundry.rust_backend.configure",side_effect=error),self.assertRaises(type(error)):self.generate(name)
            self.assertFalse((self.output/name).exists())
        self.assertEqual((keeper/"draft").read_text(),"keep")
    def test_doctor_missing_rust_tools_never_installs(self):
        from mobile_foundry.doctor import inspect
        with patch("mobile_foundry.doctor.shutil.which",return_value=None):
            observations=inspect((),{},backend="rust")
        self.assertTrue(all(not o.ok for o in observations if o.name in {"cargo","rustc","rustfmt","Clippy","openssl"}))
        with patch("mobile_foundry.doctor.probe") as probe:
            from mobile_foundry.doctor import Observation
            probe.return_value=Observation("tool",True,"fixture")
            inspect((),{"RUSTUP_AUTO_INSTALL":"1"},backend="rust")
        self.assertTrue(all(call.kwargs["env"]["RUSTUP_AUTO_INSTALL"]=="0" for call in probe.call_args_list))
    def test_cargo_path_and_literal_include_drift_fail_effective_validation(self):
        source=self.output/"closure";source.mkdir()
        for f in plan_backend_sources(ROOT,self.backend,ProductInputs.admit("closure","none")):
            path=source/f.relative;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(f.data)
        for overlay in self.backend.profile["overlays"]:
            path=source/overlay["source"];path.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/overlay["source"],path)
        cargo=source/"backend/rust/Cargo.toml";original=cargo.read_text()
        cargo.write_text(original.replace('kernel = { path = "pkg/kernel" }','kernel = { path = "../go/pkg/kernel" }'))
        self.assertTrue(any("escaping Cargo path" in e for e in validate_backend(self.backend.profile,source)))
        cargo.write_text(original)
        code=source/"backend/rust/pkg/kernel/src/lib.rs"
        code.write_text(code.read_text()+'\nconst MISSING: &str = include_str!("missing.txt");\n')
        self.assertTrue(any("unselected Rust include" in e for e in validate_backend(self.backend.profile,source)))

if __name__=="__main__":unittest.main()
