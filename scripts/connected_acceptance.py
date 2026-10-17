#!/usr/bin/env python3
"""Exercise an independent connected product using only that product's sources."""
import argparse
from contextlib import contextmanager
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import threading
import json
import os
from pathlib import Path
import plistlib
import socket
import select
import subprocess
import tempfile
import time
import uuid
from urllib.parse import urlsplit
import urllib.error
import urllib.request
from local_mail_capture import LocalMailCapture

ROOT = Path(__file__).resolve().parents[1]
PASSWORD = "owned starter test passphrase"


def run(*args, cwd=ROOT, env=None):
    subprocess.run(args, cwd=cwd, env=env, check=True)


def request(base, method, path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    call = urllib.request.Request(base + path, method=method, headers=headers,
        data=json.dumps(body).encode() if body is not None else None)
    try:
        response = urllib.request.urlopen(call, timeout=2)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        data = response.read()
        return response.status, json.loads(data) if data else None



class RecoveryProxy:
    """Owned acceptance fault boundary; product transports stay unchanged.

    A committed first attempt loses its receipt behind a 503; an unsent first
    attempt never reaches Go. Every subsequent attempt must match original
    method, path, exact JSON body and Idempotency-Key. No credentials are kept.
    """
    def __init__(self, public_port, backend_port):
        self.lock = threading.Lock()
        self.events = {}
        self.blocked_note_accounts = set()
        self.registrations = {}
        self.workspaces = {}
        owner = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_): pass
            def do_GET(self): self.forward()
            def do_POST(self): self.forward()
            def do_PATCH(self): self.forward()
            def do_DELETE(self): self.forward()
            def forward(self):
                if self.headers.get("Upgrade", "").lower() == "websocket":
                    self.tunnel(); return
                length = int(self.headers.get("Content-Length", "0"))
                if length > 1_048_576: self.send_error(413); return
                body = self.rfile.read(length)
                if self.path == "/_acceptance/notes-reads" and self.command == "POST":
                    try:
                        selected = json.loads(body)
                        account = selected["account_id"]
                        if set(selected) != {"account_id", "blocked"} or str(uuid.UUID(account)) != account or type(selected["blocked"]) is not bool: raise ValueError()
                    except (ValueError, KeyError, TypeError, AttributeError): self.send_error(400); return
                    with owner.lock:
                        if selected["blocked"]: owner.blocked_note_accounts.add(account)
                        else: owner.blocked_note_accounts.discard(account)
                    self.send_response(204); self.send_header("Content-Length", "0"); self.end_headers(); return
                path = urlsplit(self.path).path.split("/")
                if self.command == "GET" and len(path) == 5 and path[1:3] == ["v1", "accounts"] and path[4] == "notes":
                    with owner.lock: blocked = path[3] in owner.blocked_note_accounts
                    if blocked: self.unavailable(); return
                workspace = None
                if self.command in {"POST", "PATCH"} and (self.path == "/v1/accounts" or len(path) == 4 and path[1:3] == ["v1", "accounts"]):
                    try:
                        name = json.loads(body).get("name")
                        if name in {f"{prefix} {platform}" for prefix in ("Product workspace", "Renamed workspace", "Workspace conflict", "Reviewed workspace", "Unconfirmed rename", "Unconfirmed workspace") for platform in ("iOS", "Android")}:
                            workspace = name
                            with owner.lock:
                                event = owner.workspaces.setdefault(name, {"attempts": 0, "commits": 0})
                                event["attempts"] += 1
                                assert not self.headers.get("Idempotency-Key"), "Account has no replay key"
                                if self.command == "PATCH":
                                    prefix = name.rsplit(" ", 1)[0]
                                    version = {"Renamed workspace": 1, "Workspace conflict": 2, "Reviewed workspace": 3, "Unconfirmed rename": 4}[prefix]
                                    assert self.headers.get("If-Match") == f'"{version}"', "Rename must use the newly observed strong version"
                    except (ValueError, AttributeError): pass
                registration = None
                if self.command == "POST" and self.path == "/v1/identity/register":
                    try:
                        login = json.loads(body).get("login")
                        if login in {f"starter-{platform}-registration{suffix}" for platform in ("ios", "android") for suffix in ("", "-uncertain")}:
                            registration = login
                            with owner.lock:
                                event = owner.registrations.setdefault(login, {"attempts": 0, "commits": 0})
                                event["attempts"] += 1
                    except (ValueError, AttributeError): pass
                marker = None
                if self.command == "POST" and self.path.endswith("/notes"):
                    try:
                        title = json.loads(body).get("title")
                        if title in {f"Recover {kind} {platform}" for kind in ("committed", "unsent") for platform in ("iOS", "Android")}:
                            marker = title
                    except (ValueError, AttributeError): pass
                first = False
                if marker:
                    identity = (self.command, self.path, body, self.headers.get("Idempotency-Key"))
                    with owner.lock:
                        event = owner.events.get(marker)
                        if event is None:
                            event = owner.events[marker] = {"identity": identity, "attempts": 0, "commits": 0, "receipts": 0}
                        if identity != event["identity"]:
                            event["mismatch"] = True; self.send_error(409); return
                        event["attempts"] += 1; first = event["attempts"] == 1
                    if first and marker.startswith("Recover unsent"):
                        self.unavailable(); return
                connection = http.client.HTTPConnection("127.0.0.1", backend_port, timeout=10)
                try:
                    headers = {k:v for k,v in self.headers.items() if k.lower() not in {"host", "connection", "content-length"}}
                    if workspace and workspace.startswith("Workspace conflict"):
                        connection.request("PATCH", self.path, body=json.dumps({"name": workspace.replace("Workspace conflict", "Concurrent workspace")}), headers=headers)
                        competing = connection.getresponse(); competing.read()
                        assert competing.status == 200, "Owned concurrent rename must commit first"
                    connection.request(self.command, self.path, body=body or None, headers=headers)
                    response = connection.getresponse(); payload = response.read(1_048_577)
                    if len(payload) > 1_048_576: self.send_error(502); return
                    with owner.lock:
                        if marker and response.status == 201: owner.events[marker]["commits"] += 1
                        if self.command == "GET":
                            for event in owner.events.values():
                                if self.path.endswith("/note-receipts/" + str(event["identity"][3])): event["receipts"] += 1
                    if workspace:
                        with owner.lock:
                            if response.status in {200, 201}: owner.workspaces[workspace]["commits"] += 1
                        if workspace.startswith("Unconfirmed") and response.status in {200, 201}:
                            self.unavailable(); return
                    if registration:
                        with owner.lock:
                            if response.status == 201: owner.registrations[registration]["commits"] += 1
                        if registration.endswith("-uncertain") and response.status == 201:
                            self.unavailable(); return
                    if marker and first:
                        self.unavailable(); return
                    self.send_response(response.status)
                    for key,value in response.getheaders():
                        if key.lower() not in {"content-length", "connection", "transfer-encoding"}: self.send_header(key,value)
                    self.send_header("Content-Length", str(len(payload))); self.send_header("Connection", "close")
                    self.end_headers(); self.wfile.write(payload)
                finally: connection.close()
            def tunnel(self):
                # Only the owned backend; do not interpret or log credential-bearing frames.
                with socket.create_connection(("127.0.0.1", backend_port), timeout=10) as upstream:
                    headers = {k:v for k,v in self.headers.items() if k.lower() != "host"}; headers["Host"] = f"127.0.0.1:{backend_port}"
                    head = f"GET {self.path} HTTP/1.1\r\n" + "".join(f"{k}: {v}\r\n" for k,v in headers.items()) + "\r\n"
                    upstream.sendall(head.encode("latin-1")); upstream.settimeout(None)
                    while not owner.stopping.is_set():
                        readable, _, _ = select.select([self.connection, upstream], [], [], .2)
                        for source in readable:
                            data = source.recv(8192)
                            if not data: return
                            (upstream if source is self.connection else self.connection).sendall(data)
            def unavailable(self):
                self.send_response(503); self.send_header("Content-Length", "0"); self.send_header("Connection", "close"); self.end_headers()
        self.stopping = threading.Event()
        self.server = ThreadingHTTPServer(("127.0.0.1", public_port), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)

    def __enter__(self): self.thread.start(); return self
    def __exit__(self, *_): self.stopping.set(); self.server.shutdown(); self.server.server_close(); self.thread.join(timeout=5)
    def original(self, title):
        with self.lock:
            event = self.events[title]
            assert event["attempts"] == 1 and not event.get("mismatch"), "Recovery must wait for explicit user action"
            return event["identity"][3]
    def verify(self, platform):
        with self.lock:
            for kind, attempts in (("committed", 1), ("unsent", 2)):
                event = self.events[f"Recover {kind} {platform}"]
                assert event["attempts"] == attempts and event["commits"] == 1 and event["receipts"] >= 1 and not event.get("mismatch"), "Recovery changed/replayed the original intent"
        print(f"{platform}: pending intent survived process restart, receipt lookup and exact-input retry passed", flush=True)


    def verify_workspaces(self, platform):
        with self.lock:
            for prefix in ("Product workspace", "Renamed workspace", "Workspace conflict", "Reviewed workspace", "Unconfirmed rename", "Unconfirmed workspace"):
                event = self.workspaces[f"{prefix} {platform}"]
                assert event == {"attempts": 1, "commits": 0 if prefix == "Workspace conflict" else 1}, "Account command replayed or failed to commit"
        print(f"{platform}: workspace creation, CAS conflict and lost-reply readback passed without implicit replay", flush=True)

    def verify_registration(self, platform):
        with self.lock:
            for suffix, attempts in (("", 2), ("-uncertain", 1)):
                event = self.registrations[f"starter-{platform}-registration{suffix}"]
                assert event == {"attempts": attempts, "commits": 1}, "Registration replayed or did not preserve duplicate/lost-acknowledgement semantics"
        print(f"{platform}: native registration, duplicate refusal and explicit sign-in after lost acknowledgement passed", flush=True)


@contextmanager
def api(base, temporary, environment, mail_config=None):
    parts = urlsplit(base)
    if parts.scheme != "http" or parts.hostname != "127.0.0.1" or parts.path != "/v1":
        raise RuntimeError("Local acceptance requires a generated http://127.0.0.1:PORT/v1 Debug endpoint; use a separate development copy.")
    port = parts.port or 80
    with socket.socket() as probe:
        probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        probe.bind(("127.0.0.1", port))  # Refuse to replace another process.
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0)); backend_port = probe.getsockname()[1]
    binary = temporary / "server"
    run("go", "build", "-o", str(binary), "./cmd/server", cwd=ROOT / "backend/go", env=environment)
    selected = dict(environment, APP_ENV="development", HTTP_ADDR=f"127.0.0.1:{backend_port}",
        AUTH_MODE="identity", STORAGE="memory", LOG_FORMAT="json", LOG_LEVEL="info",
        FILES_DIRECTORY=str(temporary / "objects"), PUSH_CONFIG_FILE="", PUSH_JOBS_ENABLED="false", IDENTITY_MAIL_CONFIG=str(mail_config) if mail_config else "", METRICS_ADDR="", TRUSTED_PROXY_CIDRS="",
        DEV_AUTH_TOKEN="", DEV_PRINCIPAL_ID="")
    for key in list(selected):
        if key == "DATABASE_URL" or key.endswith("_DATABASE_URL"):
            selected.pop(key)
    with (temporary / "server.log").open("w+") as log:
        process = subprocess.Popen([str(binary)], cwd=ROOT / "backend/go", env=selected, stdout=log, stderr=log)
        proxy = RecoveryProxy(port, backend_port)
        proxy.__enter__()
        try:
            for _ in range(100):
                if process.poll() is not None:
                    raise RuntimeError("Owned API exited before readiness; inspect its private acceptance log.")
                try:
                    if request(f"http://127.0.0.1:{backend_port}/v1", "GET", "/health/ready")[0] == 200:
                        break
                except (OSError, urllib.error.URLError):
                    pass
                time.sleep(.05)
            else:
                raise RuntimeError("Owned API did not become ready.")
            yield port, proxy
        finally:
            proxy.__exit__()
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill(); process.wait(timeout=5)
            log.seek(0)
            if PASSWORD in log.read():
                raise RuntimeError("Acceptance credentials appeared in API logs.")


def seed(base, platform):
    login = "starter-" + platform
    assert request(base, "POST", "/identity/register", {"login": login, "display_name": "Starter user", "password": PASSWORD})[0] == 201
    status, grant = request(base, "POST", "/identity/sign-in", {"login": login, "password": PASSWORD})
    assert status == 200, f"Seed sign-in failed: {status}"
    assert request(base, "POST", "/accounts", {"name": "Starter workspace"}, grant["access_token"])[0] == 201
    assert request(base, "POST", "/identity/sign-out", {"refresh_token": grant["refresh_token"]})[0] == 204


def ios(environment, scenario="full"):
    project = ROOT / "frontend/swift/apps/ProductApp/ProductApp.xcodeproj"
    derived = ROOT / ".cache/connected-ios"
    run("xcodegen", "generate", cwd=project.parent, env=environment)
    destination = environment.get("IOS_TEST_DESTINATION", "platform=iOS Simulator,name=iPhone 17 Pro")
    run("xcodebuild", "-project", str(project), "-scheme", "ProductApp", "-destination", destination,
        "-derivedDataPath", str(derived), "-configuration", "Debug", "CODE_SIGNING_ALLOWED=YES", "CODE_SIGN_IDENTITY=-", "-quiet", "build-for-testing", env=environment)
    candidates = list((derived / "Build/Products").glob("*.xctestrun"))
    candidates = [p for p in candidates if p.name != "Connected-selected.xctestrun"]
    assert len(candidates) == 1, "Expected one simulator test specification"
    specification = plistlib.loads(candidates[0].read_bytes())

    def inject(node):
        if isinstance(node, dict):
            if "TestBundlePath" in node:
                node.setdefault("EnvironmentVariables", {}).update({"CONNECTED_ACCEPTANCE": "1",
                    "GO_SESSION_TEST_BASE": environment["GO_SESSION_TEST_BASE"], "GO_LIFECYCLE_MAIL_BASE": environment["GO_LIFECYCLE_MAIL_BASE"]})
            for child in node.values(): inject(child)
        elif isinstance(node, list):
            for child in node: inject(child)
    inject(specification)
    selected = candidates[0].with_name("Connected-selected.xctestrun")
    selected.write_bytes(plistlib.dumps(specification))
    results = derived / "Results"
    results.mkdir(exist_ok=True)
    result = results / f"connected-{time.time_ns()}.xcresult"
    try:
        selection = ["-only-testing:ProductAppUITests/ConnectedUITests", "-only-testing:ProductAppTests"] if scenario == "full" else ["-only-testing:ProductAppUITests/ConnectedUITests/" + {"cache": "testSavedReadCacheThroughOutageAndProcessRelaunch", "lifecycle": "testSecurityFlowsAndLiveReadFromAnotherClient", "registration": "testRegistrationOnboardingDuplicateAndUncertainAcknowledgement", "workspaces": "testWorkspacesCreationRenameConflictAndUncertainty"}[scenario]]
        run("xcodebuild", "-xctestrun", str(selected), "-destination", destination,
            *selection, "-parallel-testing-enabled", "NO",
            "-resultBundlePath", str(result), "-quiet", "test-without-building", env=environment)
        summary = json.loads(subprocess.check_output(["xcrun", "xcresulttool", "get", "test-results", "summary", "--path", str(result)]))
        assert summary["passedTests"] == (36 if scenario == "full" else 1) and summary["failedTests"] == 0 and summary["skippedTests"] == 0
    finally:
        selected.unlink(missing_ok=True)
    print(f"iOS {scenario}: all selected native screens passed", flush=True)


def android(environment, metadata, port, proxy, scenario="full"):
    sdk = Path(environment.get("ANDROID_HOME") or environment["ANDROID_SDK"])
    adb = str(sdk / "platform-tools/adb")
    mapping = "tcp:" + str(port)
    mail_mapping = "tcp:" + str(urlsplit(environment["GO_LIFECYCLE_MAIL_BASE"]).port)
    existing = subprocess.check_output([adb, "reverse", "--list"], env=environment, text=True)
    if any(any(item in line.split()[1:2] for item in (mapping, mail_mapping)) for line in existing.splitlines()):
        raise RuntimeError("Acceptance refuses to replace an existing ADB reverse mapping.")
    run(adb, "reverse", mapping, mapping, env=environment)
    try:
        run(adb, "reverse", mail_mapping, mail_mapping, env=environment)
        # UTP's connected-test task uninstalls the app after each invocation.
        # Install once and use raw instrumentation so the second process sees
        # the first process's actual private credential file/Keystore identity.
        run("./gradlew", ":core:data:connectedDebugAndroidTest", ":product:assembleDebug", ":product:assembleDebugAndroidTest",
            cwd=ROOT / "frontend/kotlin/project", env=environment)
        product = ROOT / "frontend/kotlin/project/product/build/outputs/apk"
        run(adb, "install", "-r", str(product / "debug/product-debug.apk"), env=environment)
        run(adb, "install", "-r", str(product / "androidTest/debug/product-debug-androidTest.apk"), env=environment)
        reports = ROOT / ".cache/connected-android"
        reports.mkdir(parents=True, exist_ok=True)
        stages = [("security", "securityFlowsAndLiveReadFromAnotherClient")]
        if scenario == "full": stages = [("write", "signInSelectWriteAndRenew"), ("restore", "restoreCommittedAndStageUnsent"), ("retry", "restoreUnsentRetryAndSignOut")] + stages
        cache_stages = [("cache-write", "saveReadCacheAndStageOutage"), ("cache-restore", "restoreReadCacheAndForgetWithoutReplaying")]
        if scenario == "cache": stages = cache_stages
        elif scenario == "full": stages += cache_stages
        registration_stages = [("registration-create", "registrationCreateAndExplicitSignIn"), ("registration-restore", "registrationRestoreDuplicateAndUncertainAcknowledgement")]
        if scenario == "registration": stages = registration_stages
        elif scenario == "full": stages += registration_stages
        workspace_stages = [("workspace-write", "workspaceCreationRenameConflictAndUncertainty"), ("workspace-restore", "workspaceRestoreSelectionAndData")]
        if scenario == "workspaces": stages = workspace_stages
        elif scenario == "full": stages += workspace_stages
        for stage, method in stages:
            arguments = []
            if stage in {"restore", "retry"}:
                kind = "committed" if stage == "restore" else "unsent"
                arguments = ["-e", "pendingCommand", proxy.original(f"Recover {kind} Android")]
                run(adb, "shell", "am", "force-stop", metadata["appId"], env=environment)
            if stage == "security" or stage.startswith(("cache-", "registration-", "workspace-")):
                run(adb, "shell", "am", "force-stop", metadata["appId"], env=environment)
                arguments = ["-e", "apiBase", metadata["apiBaseURL"], "-e", "mailBase", environment["GO_LIFECYCLE_MAIL_BASE"]]
            result = subprocess.run([adb, "shell", "am", "instrument", "-w", "-r", "-e", "class",
                "dev.mobilefoundry.product.ConnectedScreenTest#" + method, "-e", "connectedStage", stage,
                *arguments, metadata["appId"] + ".test/androidx.test.runner.AndroidJUnitRunner"], env=environment, capture_output=True, text=True, timeout=180)
            output = result.stdout + result.stderr
            report = reports / f"{stage}-{time.time_ns()}.log"
            report.write_text(output)
            assert result.returncode == 0 and "OK (1 test)" in output and output.count("INSTRUMENTATION_STATUS_CODE: 0") == 1, f"Android {stage} must execute once without skips; inspect {report}"
            print(f"Android {stage}: one test passed; report {report}", flush=True)
    finally:
        run(adb, "reverse", "--remove", mapping, env=environment)
        run(adb, "reverse", "--remove", mail_mapping, env=environment)
    print(f"Android {scenario}: all selected native screens passed", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--platform", choices=("host", "swift", "kotlin", "both"), default="both")
    parser.add_argument("--scenario", choices=("full", "lifecycle", "cache", "registration", "workspaces"), default="full")
    args = parser.parse_args()
    metadata = json.loads((ROOT / "mobile-foundry.json").read_text())
    assert metadata.get("nativeBackendConnection") == "go-connected", "Run in an independently generated go-connected product"
    selected = metadata["platforms"] if args.platform in {"host", "both"} else [args.platform]
    assert set(selected) <= set(metadata["platforms"])
    environment = dict(os.environ, GO_SESSION_TEST_BASE=metadata["apiBaseURL"])
    if environment.get("ANDROID_JAVA_HOME"): environment["JAVA_HOME"] = environment["ANDROID_JAVA_HOME"]
    if environment.get("ANDROID_SDK"): environment["ANDROID_HOME"] = environment["ANDROID_SDK"]
    with tempfile.TemporaryDirectory(prefix="connected-starter-") as temporary:
        with LocalMailCapture(Path(temporary)) as mailbox:
            environment["GO_LIFECYCLE_MAIL_BASE"] = mailbox.base
            for host_platform in selected:
                host_runtime = Path(temporary) / ("host-" + host_platform)
                host_runtime.mkdir()
                with api(metadata["apiBaseURL"], host_runtime, environment, mailbox.config):
                    # These methods use real native transports; their accounts are unique per test.
                    if host_platform == "swift":
                        run("swift", "test", "--package-path", "frontend/swift/packages/FoundrySession", "--filter", "nativeGoSessionSignInScopeRotationReuseAndSignOut|nativeGoLifecycleMailPasswordRevocationAndRealSocket", env=environment)
                        # The API admits two simultaneous password computations. Keep
                        # the third integration scenario separate rather than relaxing it.
                        run("swift", "test", "--package-path", "frontend/swift/packages/FoundrySession", "--filter", "nativeGoRegistrationExplicitSignInAndDuplicateNeverChangesCredentials|registration|canceledRegistration", env=environment)
                        run("swift", "test", "--package-path", "frontend/swift/packages/FoundrySession", "--filter", "nativeGoWorkspaceCreationCASOwnershipAndExplicitSelection|workspace", env=environment)
                        run("swift", "test", "--package-path", "frontend/swift/packages/FoundryServices", "--filter", "nativeGoNotesReceiptsRecoverLostAcknowledgementAndGuardVersions|readCache|memoryReadCache", env=environment)
                        run("swift", "test", "--package-path", "frontend/swift/packages/FoundryData", env=environment)
                    if host_platform == "kotlin":
                        run("./gradlew", ":core:session:testDebugUnitTest", "--tests", "dev.mobilefoundry.session.GoNativeSessionTest", "--tests", "dev.mobilefoundry.session.GoNativeLifecycleTest", "--tests", "dev.mobilefoundry.session.GoNativeRegistrationTest", "--tests", "dev.mobilefoundry.session.IdentityRegistrationTest", "--tests", "dev.mobilefoundry.session.AccountManagementTest", "--tests", "dev.mobilefoundry.session.GoNativeAccountTest",
                            ":core:services:test", "--tests", "dev.mobilefoundry.services.GoNativeNotesTest", "--tests", "dev.mobilefoundry.services.NotesReadCacheTest", ":product:testDebugUnitTest", "--tests", "dev.mobilefoundry.product.connection.RemoteNotesStoreTest", "--tests", "dev.mobilefoundry.product.connection.RegistrationModelTest", "--tests", "dev.mobilefoundry.product.connection.WorkspaceModelTest", "--rerun-tasks", "--no-build-cache", cwd=ROOT / "frontend/kotlin/project", env=environment)
            if args.platform != "host":
                # Each native host suite and screen scenarios own their rate budget/state. Native
                # UI fixtures start in a separate owned process, without changing
                # production guards or adding retries to product requests.
                screen_runtime = Path(temporary) / "screens"
                screen_runtime.mkdir()
                with api(metadata["apiBaseURL"], screen_runtime, environment, mailbox.config) as (port, proxy):
                    if "swift" in selected:
                        if args.scenario in {"full", "lifecycle"}: seed(metadata["apiBaseURL"], "ios-security")
                        if args.scenario in {"full", "cache"}: seed(metadata["apiBaseURL"], "ios-cache")
                        if args.scenario == "full": seed(metadata["apiBaseURL"], "ios")
                        if args.scenario in {"full", "workspaces"}: seed(metadata["apiBaseURL"], "ios-workspaces")
                        ios(environment, args.scenario)
                        if args.scenario == "full": proxy.verify("iOS")
                        if args.scenario in {"full", "registration"}: proxy.verify_registration("ios")
                        if args.scenario in {"full", "workspaces"}: proxy.verify_workspaces("iOS")
                    if "kotlin" in selected:
                        if args.scenario in {"full", "lifecycle"}: seed(metadata["apiBaseURL"], "android-security")
                        if args.scenario in {"full", "cache"}: seed(metadata["apiBaseURL"], "android-cache")
                        if args.scenario == "full": seed(metadata["apiBaseURL"], "android")
                        if args.scenario in {"full", "workspaces"}: seed(metadata["apiBaseURL"], "android-workspaces")
                        android(environment, metadata, port, proxy, args.scenario)
                        if args.scenario == "full": proxy.verify("Android")
                        if args.scenario in {"full", "registration"}: proxy.verify_registration("android")
                        if args.scenario in {"full", "workspaces"}: proxy.verify_workspaces("Android")
    print("Independent connected acceptance passed", flush=True)


if __name__ == "__main__":
    main()
