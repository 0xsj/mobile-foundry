"""Inject generic simulator pushes into an owned UI test; never contact APNs."""
import http.server
import json
import os
from pathlib import Path
import plistlib
import secrets
import subprocess
import threading
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]


def run(*args, **kwargs):
    return subprocess.run(args, cwd=ROOT, check=True, **kwargs)


def main():
    # Select explicitly when more than one simulator is available. Tests must use this same device.
    device = os.environ.get("FOUNDRY_IOS_TEST_UDID")
    if not device:
        inventory = json.loads(subprocess.check_output(["xcrun", "simctl", "list", "devices", "booted", "--json"]))
        devices = [item["udid"] for runtime, items in inventory["devices"].items() if ".iOS-" in runtime
                   for item in items if item.get("isAvailable") and item["state"] == "Booted"]
        assert len(devices) == 1, "boot one iOS simulator or set FOUNDRY_IOS_TEST_UDID explicitly"
        device = devices[0]
    destination = "platform=iOS Simulator,id=" + device
    derived = ROOT / ".cache/ios"
    run("xcodebuild", "-project", "frontend/swift/apps/FoundryCatalog/FoundryCatalog.xcodeproj",
        "-scheme", "FoundryCatalog", "-destination", destination, "-derivedDataPath", str(derived),
        "-configuration", "Debug", "CODE_SIGNING_ALLOWED=YES", "CODE_SIGN_IDENTITY=-", "-quiet", "build-for-testing")
    nonce = secrets.token_urlsafe(24)

    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def do_POST(self):
            prefix = "/" + nonce + "/"
            identity = self.path[len(prefix):] if self.path.startswith(prefix) else ""
            try:
                valid = str(uuid.UUID(identity)) == identity and uuid.UUID(identity).int != 0
            except ValueError:
                valid = False
            if not valid or self.headers.get("Content-Length", "0") != "0":
                self.send_error(400)
                return
            payload = {"aps": {"alert": {"title": "Mobile push preview", "body": "Open the app to continue."}, "sound": "default"},
                       "version": "1", "kind": "push_preview", "preview_id": identity}
            answer = subprocess.run(["xcrun", "simctl", "push", device, "dev.mobilefoundry.catalog", "-"],
                                    input=json.dumps(payload), text=True, capture_output=True, timeout=20)
            self.send_response(204 if answer.returncode == 0 else 500)
            self.end_headers()

    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        candidates = list((derived / "Build/Products").glob("FoundryCatalog_*.xctestrun"))
        assert len(candidates) == 1, "expected exactly one catalog simulator test artifact"
        specification = plistlib.loads(candidates[0].read_bytes())

        def inject(node):
            if isinstance(node, dict):
                if "TestBundlePath" in node:
                    node.setdefault("EnvironmentVariables", {})["REMOTE_PREVIEW_TEST_BASE"] = f"http://127.0.0.1:{server.server_port}/{nonce}"
                for child in node.values():
                    inject(child)
            elif isinstance(node, list):
                for child in node:
                    inject(child)

        inject(specification)
        selected = candidates[0].with_name("RemotePreview-selected.xctestrun")
        selected.write_bytes(plistlib.dumps(specification))
        result = derived / ("remote-preview-" + time.strftime("%Y%m%d-%H%M%S") + ".xcresult")
        run("xcodebuild", "-xctestrun", str(selected), "-destination", destination,
            "-parallel-testing-enabled", "NO", "-only-testing:FoundryCatalogTests/SystemNotificationAdmissionTests",
            "-only-testing:FoundryCatalogUITests/RemoteNotificationUITests",
            "-only-testing:FoundryCatalogUITests/SystemNotificationUITests",
            "-resultBundlePath", str(result), "-quiet", "test-without-building")
        summary = json.loads(subprocess.check_output(["xcrun", "xcresulttool", "get", "test-results", "summary", "--path", str(result)]))
        assert summary["failedTests"] == 0 and summary["skippedTests"] == 0 and summary["passedTests"] >= 4, summary
        print("iOS simulator notification acceptance: remote foreground receipt, warm/cold opens, local preview regression and envelope admission passed")
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)


if __name__ == "__main__":
    main()
