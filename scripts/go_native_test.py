"""Own a temporary Go Identity API for real native HTTP/session acceptance."""
import os
import argparse
import json
import plistlib
import shutil
from pathlib import Path
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "backend/go"
sys.path.insert(0, str(BACKEND / "scripts"))
from smoke import server, request

def run(*arguments, cwd=ROOT, env=None):
    subprocess.run(arguments, cwd=cwd, env=env, check=True)

def main():
    parser = argparse.ArgumentParser()
    selection = parser.add_mutually_exclusive_group()
    selection.add_argument("--devices", action="store_true", help="Also run iOS simulator and connected Android UI acceptance")
    selection.add_argument("--android-device-only", action="store_true", help="Run host checks and Android device acceptance without repeating iOS UI")
    selection.add_argument("--ios-device-only", action="store_true", help="Run host checks and iOS device acceptance without requiring Android")
    arguments = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="mobile-foundry-native-go-") as temporary:
        binary = Path(temporary) / "server"
        run("go", "build", "-o", str(binary), "./cmd/server", cwd=BACKEND)
        with server(binary, identity=True, forbidden=("native integration passphrase",)) as base:
            environment = dict(os.environ, GO_SESSION_TEST_BASE=base + "/v1")
            environment.setdefault("JAVA_HOME", "/Applications/Android Studio.app/Contents/jbr/Contents/Home")
            environment.setdefault("ANDROID_HOME", str(Path.home() / "Library/Android/sdk"))
            run("swift", "test", "--package-path", "frontend/swift/packages/FoundrySession", "--filter", "nativeGoSessionSignInScopeRotationReuseAndSignOut", env=environment)
            run("./gradlew", ":core:session:testDebugUnitTest", "--tests", "dev.mobilefoundry.session.GoNativeSessionTest", cwd=ROOT / "frontend/kotlin/project", env=environment)
            run("swift", "test", "--package-path", "frontend/swift/packages/FoundryServices", "--filter", "nativeGoNotesReceiptsRecoverLostAcknowledgementAndGuardVersions", env=environment)
            run("./gradlew", ":core:services:test", "--tests", "dev.mobilefoundry.services.GoNativeNotesTest", cwd=ROOT / "frontend/kotlin/project", env=environment)
            if arguments.devices or arguments.android_device_only or arguments.ios_device_only:
                device_tests(base, environment, Path(temporary), run_ios=not arguments.android_device_only,run_android=not arguments.ios_device_only)
    print("Native Go acceptance: sessions/accounts and Notes CRUD, version conflicts, receipt recovery and replay passed on URLSession/OkHttp")

def device_tests(base, environment, temporary, run_ios=True,run_android=True):
    # These accounts and workspaces exist only in this owned memory API.
    for login, name in (("native-ui-ios", "iOS workspace"), ("native-ui-android", "Android workspace")):
        assert request(base, "POST", "/v1/identity/register", {"login": login, "display_name": "Native UI", "password": "native integration passphrase"})[0] == 201
        status, _, wire = request(base, "POST", "/v1/identity/sign-in", {"login": login, "password": "native integration passphrase"})
        assert status == 200
        grant = json.loads(wire)
        assert request(base, "POST", "/v1/accounts", {"name": name}, grant["access_token"])[0] == 201
        assert request(base, "POST", "/v1/identity/sign-out", {"refresh_token": grant["refresh_token"]})[0] == 204
    if run_ios:
        ios_device_test(environment, temporary)
    if not run_android:
        print("iOS Go catalog: remote Notes CRUD and selected workspace/Keychain after process relaunch passed")
        return
    sdk = Path(environment["ANDROID_HOME"])
    adb = str(sdk / "platform-tools/adb")
    port = base.rsplit(":", 1)[1]
    existing = subprocess.check_output([adb, "reverse", "--list"], text=True)
    assert "tcp:" + port not in existing, "test does not replace an existing reverse mapping"
    run(adb, "reverse", "tcp:" + port, "tcp:" + port)
    try:
        run("./gradlew", ":app:connectedDebugAndroidTest", "-Pandroid.testInstrumentationRunnerArguments.class=dev.mobilefoundry.catalog.ui.session.GoSessionCatalogScreenTest",
            "-Pandroid.testInstrumentationRunnerArguments.goSessionBase=" + environment["GO_SESSION_TEST_BASE"], cwd=ROOT / "frontend/kotlin/project", env=environment)
        reports = ROOT / "frontend/kotlin/project/app/build/outputs/androidTest-results/connected"
        suites = [ET.parse(path).getroot() for path in reports.rglob("TEST-*.xml")]
        selected = [suite for suite in suites if suite.attrib.get("name") == "dev.mobilefoundry.catalog.ui.session.GoSessionCatalogScreenTest"]
        assert sum(int(suite.attrib["tests"]) for suite in selected) == 1 and all(
            int(suite.attrib.get(key, "0")) == 0 for suite in selected for key in ("failures", "errors", "skipped")
        ), "Android Go acceptance must execute once without failures/skips"
    finally:
        run(adb, "reverse", "--remove", "tcp:" + port)
    print("Device Go acceptance: selected workspace, renewal and sign-out passed" + (" including iOS Keychain process relaunch" if run_ios else " on Android Keystore"))

def ios_device_test(environment, temporary):
    project = ROOT / "frontend/swift/apps/FoundryCatalog/FoundryCatalog.xcodeproj"
    derived = ROOT / ".cache/go-native-ios"
    destination = os.environ.get("FOUNDRY_IOS_TEST_DESTINATION", "platform=iOS Simulator,name=iPhone 17 Pro")
    run("xcodebuild", "-project", str(project), "-scheme", "FoundryCatalog", "-destination", destination,
        "-derivedDataPath", str(derived), "-configuration", "Debug", "CODE_SIGNING_ALLOWED=YES", "CODE_SIGN_IDENTITY=-", "-quiet", "build-for-testing", env=environment)
    candidates = list((derived / "Build/Products").glob("*.xctestrun"))
    assert len(candidates) == 1, "expected one selected simulator test artifact"
    specification = plistlib.loads(candidates[0].read_bytes())
    def inject(node):
        if isinstance(node, dict):
            if "TestBundlePath" in node:
                node.setdefault("EnvironmentVariables", {})["GO_SESSION_TEST_BASE"] = environment["GO_SESSION_TEST_BASE"]
            for child in node.values(): inject(child)
        elif isinstance(node, list):
            for child in node: inject(child)
    inject(specification)
    # Keep paths relative to the original test root so framework locations survive.
    selected = candidates[0].with_name("GoSession-selected.xctestrun")
    selected.write_bytes(plistlib.dumps(specification))
    result = temporary / "ios-go-session.xcresult"
    try:
        run("xcodebuild", "-xctestrun", str(selected), "-destination", destination,
            "-only-testing:FoundryCatalogUITests/GoSessionUITests", "-resultBundlePath", str(result), "-quiet", "test-without-building", env=environment)
        summary = json.loads(subprocess.check_output(["xcrun", "xcresulttool", "get", "test-results", "summary", "--path", str(result)]))
        assert summary["passedTests"] == 1 and summary["failedTests"] == 0 and summary["skippedTests"] == 0, "iOS Go acceptance must execute without skips"
    except Exception:
        if result.exists():
            saved = derived / "Failures" / (temporary.name + ".xcresult")
            saved.parent.mkdir(parents=True,exist_ok=True)
            shutil.copytree(result,saved)
            print("Failed iOS result retained for diagnosis:",saved,flush=True)
        raise
    finally:
        selected.unlink(missing_ok=True)

if __name__ == "__main__":
    main()
