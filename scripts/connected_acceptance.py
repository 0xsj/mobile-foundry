#!/usr/bin/env python3
"""Exercise an independent connected product using only that product's sources."""
import argparse
import hashlib
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
from urllib.parse import parse_qs, urlsplit
import urllib.error
import urllib.request
from local_mail_capture import LocalMailCapture

ROOT = Path(__file__).resolve().parents[1]
PASSWORD = "owned starter test passphrase"


def run(*args, cwd=ROOT, env=None):
    subprocess.run(args, cwd=cwd, env=env, check=True)


def request(base, method, path, body=None, token=None, raw_body=None, extra_headers=None):
    headers = {"Content-Type": "application/octet-stream" if raw_body is not None else "application/json", **(extra_headers or {})}
    if token:
        headers["Authorization"] = "Bearer " + token
    call = urllib.request.Request(base + path, method=method, headers=headers,
        data=raw_body if raw_body is not None else json.dumps(body).encode() if body is not None else None)
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
    attempt never reaches the API. Every subsequent attempt must match original
    method, path, exact JSON body and Idempotency-Key. No credentials are kept.
    """
    def __init__(self, public_port, backend_port):
        self.lock = threading.Lock()
        self.events = {}
        self.blocked_note_accounts = set()
        self.registrations = {}
        self.workspaces = {}
        self.inbox_accounts = {}
        self.inbox_mutations = {}
        self.files_accounts = {}
        self.files_mutations = {}
        self.file_reviews = {}
        self.pending_file_accounts = {}
        self.pending_file_mutations = {}
        self.media_accounts = {}
        self.media_mutations = {}
        self.media_receipts = {}
        self.media_preferences = {}
        self.destination_reads = {}
        owner = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_): pass
            def do_GET(self): self.forward()
            def do_POST(self): self.forward()
            def do_PUT(self): self.forward()
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
                media_change = None
                if len(path) >= 5 and path[1:3] == ["v1", "accounts"]:
                    with owner.lock: media_platform = owner.media_accounts.get(path[3])
                    action = "complete" if self.command == "POST" and path[-1] == "complete" else "content" if self.command == "PUT" and path[-1] == "content" else None
                    if media_platform and path[4] == "files" and action:
                        media_change = (media_platform, action, path[5])
                        identity = (self.command, self.path, body, self.headers.get("Idempotency-Key"), self.headers.get("If-Match"))
                        with owner.lock:
                            event = owner.media_mutations.setdefault(media_change, {"identity": identity, "attempts": 0, "successes": 0})
                            assert event["identity"] == identity, "Media recovery changed the original command/bytes/version"
                            event["attempts"] += 1
                    if media_platform and self.command == "PUT" and path[4] == "notification-preferences":
                        with owner.lock: owner.media_preferences.setdefault(media_platform, []).append(json.loads(body))
                if self.command == "GET" and len(path) == 5 and path[1:4] == ["v1", "receipts", "files"]:
                    account = parse_qs(urlsplit(self.path).query).get("account_id", [None])[0]
                    with owner.lock:
                        media_platform = owner.media_accounts.get(account)
                        if media_platform:
                            marker = (media_platform, path[4]); owner.media_receipts[marker] = owner.media_receipts.get(marker, 0) + 1
                pending_file_change = None
                if len(path)>=5 and path[1:3]==["v1","accounts"] and path[4]=="files":
                    with owner.lock: pending_platform=owner.pending_file_accounts.get(path[3])
                    pending_action = "request" if self.command=="POST" and path[-1]=="uploads" else "content" if self.command=="PUT" and path[-1]=="content" else "complete" if self.command=="POST" and path[-1]=="complete" else None
                    if pending_platform and pending_action:
                        pending_file_change=(pending_platform,pending_action)
                        # JSON object order can change with a fresh Swift process.
                        # Compare original field values exactly; photo content remains raw bytes.
                        original_input = json.dumps(json.loads(body),sort_keys=True,separators=(",", ":")).encode() if pending_action=="request" else body
                        identity=(self.command,self.path,original_input,self.headers.get("Idempotency-Key"),self.headers.get("If-Match"))
                        with owner.lock:
                            event=owner.pending_file_mutations.setdefault(pending_file_change,{"identity":identity,"attempts":0,"commits":0})
                            assert event["identity"]==identity,"Restored Files changed original key/bytes/specification/precondition"
                            event["attempts"]+=1
                            unsent=pending_action=="content" and event["attempts"]==1
                        if unsent: self.unavailable();return
                file_change = None
                file_review = None
                if len(path) >= 5 and path[1:3] == ["v1", "accounts"] and path[4] == "files":
                    with owner.lock: platform = owner.files_accounts.get(path[3])
                    action = "complete" if self.command == "POST" and path[-1] == "complete" else "delete" if self.command == "DELETE" else "content" if self.command == "PUT" else None
                    if platform and action:
                        file_change = (platform, action)
                        identity = (self.command, self.path, body, self.headers.get("Idempotency-Key"), self.headers.get("If-Match"))
                        with owner.lock:
                            original = owner.files_mutations.get(file_change)
                            if action == "delete" and original and original["attempts"] >= 2 and original["identity"][1] != self.path:
                                file_review = platform; file_change = None
                                event = owner.file_reviews.setdefault(platform, {"identity": identity, "attempts": 0, "external_commits": 0, "not_found": 0})
                                assert event["identity"] == identity, "Review must not silently replace the original command"
                                event["attempts"] += 1
                            else:
                                event = owner.files_mutations.setdefault(file_change, {"identity": identity, "attempts": 0, "commits": 0})
                                assert event["identity"] == identity, "Files recovery changed original command/bytes/precondition"
                                event["attempts"] += 1
                inbox_change = None
                if len(path) >= 5 and path[1:3] == ["v1", "accounts"]:
                    with owner.lock:
                        platform = owner.inbox_accounts.get(path[3])
                    if platform and self.command == "POST" and len(path) == 7 and path[4] == "notifications" and path[6] == "read":
                        inbox_change = (platform, "read")
                    elif platform and self.command == "PUT" and len(path) == 5 and path[4] == "notification-preferences":
                        try:
                            if json.loads(body) == {"media_in_app": False}: inbox_change = (platform, "preference")
                        except ValueError: pass
                    if inbox_change:
                        with owner.lock:
                            event = owner.inbox_mutations.setdefault(inbox_change, {"attempts": 0, "commits": 0})
                            event["attempts"] += 1
                            assert not self.headers.get("Idempotency-Key") and not self.headers.get("If-Match"), "Inbox writes have no receipt/CAS contract"
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
                    destination = None
                    if self.command == "GET" and len(path) == 6 and path[1:3] == ["v1", "accounts"] and path[4] == "files":
                        with owner.lock:
                            destination = owner.media_accounts.get(path[3])
                            if destination:
                                assert not self.headers.get("Idempotency-Key") and not self.headers.get("If-Match"), "Opening a notice is read-only"
                                event = owner.destination_reads.setdefault(destination, {"reads": 0, "available": 0, "deleted": 0, "not_found": 0})
                                event["reads"] += 1
                                remove_destination = event["reads"] == 2
                        if destination and remove_destination:
                            # An independent authorized client removes the target before
                            # the second open. The app must consume a real backend 404.
                            connection.request("GET", self.path, headers=headers)
                            observed = connection.getresponse(); observed.read()
                            assert observed.status == 200
                            delete_headers = dict(headers, **{"If-Match": observed.getheader("ETag"), "Idempotency-Key": "acceptance-destination-delete"})
                            connection.request("DELETE", self.path, headers=delete_headers)
                            removed = connection.getresponse(); removed.read(); assert removed.status == 200
                            with owner.lock: owner.destination_reads[destination]["deleted"] += 1
                    if file_review:
                        # A second authorized client deletes the observed file first.
                        # The product's original command then receives a real 404.
                        competing_headers = {k:v for k,v in headers.items() if k.lower() != "idempotency-key"}
                        competing_headers["Idempotency-Key"] = "acceptance-other-delete"
                        connection.request("DELETE", self.path, headers=competing_headers)
                        competing = connection.getresponse(); competing.read()
                        assert competing.status == 200, "Owned competing delete must commit first"
                        with owner.lock: owner.file_reviews[file_review]["external_commits"] += 1
                    if workspace and workspace.startswith("Workspace conflict"):
                        connection.request("PATCH", self.path, body=json.dumps({"name": workspace.replace("Workspace conflict", "Concurrent workspace")}), headers=headers)
                        competing = connection.getresponse(); competing.read()
                        assert competing.status == 200, "Owned concurrent rename must commit first"
                    connection.request(self.command, self.path, body=body or None, headers=headers)
                    response = connection.getresponse(); payload = response.read(1_048_577)
                    if destination:
                        with owner.lock:
                            if response.status == 200: owner.destination_reads[destination]["available"] += 1
                            if response.status == 404: owner.destination_reads[destination]["not_found"] += 1
                    if len(payload) > 1_048_576: self.send_error(502); return
                    with owner.lock:
                        if pending_file_change and response.status in {200,201}: owner.pending_file_mutations[pending_file_change]["commits"]+=1
                        if file_review and response.status == 404: owner.file_reviews[file_review]["not_found"] += 1
                        if marker and response.status == 201: owner.events[marker]["commits"] += 1
                        if inbox_change and response.status == 200: owner.inbox_mutations[inbox_change]["commits"] += 1
                        if self.command == "GET":
                            for event in owner.events.values():
                                if self.path.endswith("/note-receipts/" + str(event["identity"][3])): event["receipts"] += 1
                    if file_change and response.status == 200:
                        with owner.lock:
                            event = owner.files_mutations[file_change]; event["commits"] += 1
                            first_file_attempt = event["attempts"] == 1
                        if file_change[1] in {"complete", "delete"} and first_file_attempt: self.unavailable(); return
                    if media_change and response.status == 200:
                        with owner.lock:
                            event = owner.media_mutations[media_change]; event["successes"] += 1
                            completions = [key for key in owner.media_mutations if key[:2] == (media_change[0], "complete")]
                            drop_completion = media_change[1] == "complete" and len(completions) == 1 and event["attempts"] == 1
                        if drop_completion: self.unavailable(); return
                    if inbox_change and response.status == 200:
                        with owner.lock: first_inbox_attempt = owner.inbox_mutations[inbox_change]["attempts"] == 1
                        if first_inbox_attempt: self.unavailable(); return
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


    def verify_files(self, platform):
        with self.lock:
            for action, attempts in (("complete", 1), ("content", 1), ("delete", 2)):
                event = self.files_mutations[(platform, action)]
                assert event["attempts"] == attempts and event["commits"] == attempts, "Files receipt settlement or cleanup retry failed"
            review = self.file_reviews[platform]
            assert review["attempts"] == review["external_commits"] == review["not_found"] == 1, "Terminal delete review replayed or lacked a real competing commit"
            for action,attempts,commits in (("request",2,2),("content",2,1),("complete",1,1)):
                event=self.pending_file_mutations[(platform,action)]
                assert event["attempts"]==attempts and event["commits"]==commits,"Disk recovery must replay exact original input once after unsent bytes"
        print(f"{platform}: real gallery import, verified download, receipt/cleanup retry and explicit terminal-delete abandonment passed", flush=True)

    def verify_media(self, platform):
        self.verify_inbox(platform + "-media")
        with self.lock:
            completed = []
            for action in ("content", "complete"):
                events = [event for key, event in self.media_mutations.items() if key[:2] == (platform, action)]
                assert len(events) == 3 and all(event["attempts"] == 1 and event["successes"] == 1 for event in events), "Media writes replayed or did not settle three distinct uploads"
                if action == "complete": completed = events
            keys = [event["identity"][3] for event in completed]
            assert len(set(keys)) == 3 and all(keys), "Each accepted upload needs its own completion command"
            assert self.media_receipts.get((platform, keys[0]), 0) >= 2, "Lost completion requires receipt settlement before another PUT"
            assert self.media_preferences[platform] == [{"media_in_app": False}, {"media_in_app": True}], "Preference uncertainty or restore silently replayed a write"
            assert self.destination_reads[platform] == {"reads": 2, "available": 1, "deleted": 1, "not_found": 1}, "Notice destinations must use current metadata, including a real removed target"
        print(f"{platform}: three real uploads, committed delivery/readback, mute/re-enable without resurrection and combined scope/restore passed", flush=True)

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

    def verify_inbox(self, platform):
        with self.lock:
            for action in ("read", "preference"):
                assert self.inbox_mutations[(platform, action)] == {"attempts": 1, "commits": 1}, "Inbox write replayed after an uncertain acknowledgement or did not commit"
        print(f"{platform}: inbox paging, scope/restore and committed lost-reply readback passed without mutation replay", flush=True)


def build_api(temporary, environment, backend, package=None):
    directory = ROOT / "backend" / backend
    if backend == "go":
        binary = temporary / "server"
        run("go", "build", "-o", str(binary), "./cmd/server", cwd=directory, env=environment)
    elif backend == "rust":
        import tomllib
        source = tomllib.loads((directory / "cmd/server/Cargo.toml").read_text())["package"]["name"]
        if package != source:
            raise RuntimeError("Rust metadata/package identity mismatch.")
        # Build only this output's locked API, independent of inherited target dirs.
        target = directory / "target"
        build_environment = dict(environment, CARGO_TARGET_DIR=str(target), RUSTUP_AUTO_INSTALL="0")
        run("cargo", "build", "--locked", "-p", package, "--bin", "api-server", cwd=directory, env=build_environment)
        binary = target / "debug/api-server"
    else:
        raise RuntimeError("Acceptance requires an explicitly selected Go or Rust backend.")
    return directory, binary


@contextmanager
def api(base, temporary, environment, mail_config=None, backend="go", package=None):
    parts = urlsplit(base)
    if parts.scheme != "http" or parts.hostname != "127.0.0.1" or parts.path != "/v1":
        raise RuntimeError("Local acceptance requires a generated http://127.0.0.1:PORT/v1 Debug endpoint; use a separate development copy.")
    port = parts.port or 80
    with socket.socket() as probe:
        probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        probe.bind(("127.0.0.1", port))  # Refuse to replace another process.
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0)); backend_port = probe.getsockname()[1]
    directory, binary = build_api(temporary, environment, backend, package)
    selected = dict(environment, APP_ENV="development", HTTP_ADDR=f"127.0.0.1:{backend_port}",
        AUTH_MODE="identity", STORAGE="memory", LOG_FORMAT="json", LOG_LEVEL="info",
        FILES_DIRECTORY=str(temporary / "objects"), PUSH_CONFIG_FILE="", PUSH_JOBS_ENABLED="false", IDENTITY_MAIL_CONFIG=str(mail_config) if mail_config else "", METRICS_ADDR="", TRUSTED_PROXY_CIDRS="",
        DEV_AUTH_TOKEN="", DEV_PRINCIPAL_ID="")
    for key in list(selected):
        if key == "DATABASE_URL" or key.endswith("_DATABASE_URL"):
            selected.pop(key)
    with (temporary / "server.log").open("w+") as log:
        process = subprocess.Popen([str(binary)], cwd=directory, env=selected, stdout=log, stderr=log)
        proxy = RecoveryProxy(port, backend_port)
        started = False
        last_probe = "no response"
        try:
            proxy.__enter__()
            deadline = time.monotonic() + 30
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError("Owned API exited before readiness; inspect its private acceptance log.")
                try:
                    status = request(f"http://127.0.0.1:{backend_port}/v1", "GET", "/health/ready")[0]
                    last_probe = f"HTTP {status}"
                    if status == 200:
                        break
                except (OSError, urllib.error.URLError) as error:
                    last_probe = type(error).__name__
                time.sleep(.05)
            else:
                raise RuntimeError(f"Owned API did not become ready within 30 seconds ({last_probe}); inspect .cache/connected-runtime startup logs.")
            started = True
            yield port, proxy
        finally:
            proxy.__exit__()
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill(); process.wait(timeout=5)
            log.seek(0)
            contents = log.read()
            if PASSWORD in contents:
                raise RuntimeError("Acceptance credentials appeared in API logs.")
            if not started:
                # Before readiness no fixture account/credential has been submitted.
                # Preserve the owned startup log before the temporary runtime is removed.
                reports = ROOT / ".cache/connected-runtime"
                reports.mkdir(parents=True, exist_ok=True, mode=0o700)
                path = reports / f"startup-{backend}-{time.time_ns()}.log"
                with path.open("x") as report: report.write(contents)
                path.chmod(0o600)


def seed(base, platform):
    login = "starter-" + platform
    assert request(base, "POST", "/identity/register", {"login": login, "display_name": "Starter user", "password": PASSWORD})[0] == 201
    status, grant = request(base, "POST", "/identity/sign-in", {"login": login, "password": PASSWORD})
    assert status == 200, f"Seed sign-in failed: {status}"
    assert request(base, "POST", "/accounts", {"name": "Starter workspace"}, grant["access_token"])[0] == 201
    assert request(base, "POST", "/identity/sign-out", {"refresh_token": grant["refresh_token"]})[0] == 204


def seed_inbox(base, platform):
    login = "starter-" + platform + "-inbox"
    assert request(base, "POST", "/identity/register", {"login": login, "display_name": "Inbox user", "password": PASSWORD})[0] == 201
    status, grant = request(base, "POST", "/identity/sign-in", {"login": login, "password": PASSWORD})
    assert status == 200
    token = grant["access_token"]
    status, account = request(base, "POST", "/accounts/bootstrap", {"name": "Personal"}, token)
    assert status == 201
    account_id = account["id"]
    assert request(base, "POST", "/accounts", {"name": "Starter workspace"}, token)[0] == 201
    content = b"owned inbox acceptance bytes"
    for index in range(25):
        status, receipt = request(base, "POST", f"/accounts/{account_id}/files/uploads",
            {"name": f"inbox-{index}.bin", "content_type": "application/octet-stream", "byte_size": len(content), "sha256": hashlib.sha256(content).hexdigest()}, token)
        assert status == 201
        file_id = receipt["file"]["id"]
        assert request(base, "PUT", f"/accounts/{account_id}/files/{file_id}/content", token=token, raw_body=content)[0] == 200
        assert request(base, "POST", f"/accounts/{account_id}/files/{file_id}/complete", token=token, extra_headers={"If-Match": '"1"'})[0] == 200
    for _ in range(200):
        status, page = request(base, "GET", f"/accounts/{account_id}/notifications", token=token)
        if status == 200 and page["unread_count"] == 25: break
        time.sleep(.05)
    else: raise RuntimeError("Owned upload events did not reach the inbox")
    assert request(base, "POST", "/identity/sign-out", {"refresh_token": grant["refresh_token"]})[0] == 204
    return account_id


def seed_files(base, platform, suffix="files", count=25):
    login = "starter-" + platform + "-" + suffix
    assert request(base,"POST","/identity/register",{"login":login,"display_name":"Files user","password":PASSWORD})[0] == 201
    status,grant = request(base,"POST","/identity/sign-in",{"login":login,"password":PASSWORD}); assert status == 200
    token=grant["access_token"]
    status,account=request(base,"POST","/accounts/bootstrap",{"name":"Personal"},token); assert status == 201
    account_id=account["id"]
    assert request(base,"POST","/accounts",{"name":"Starter workspace"},token)[0] == 201
    content=(ROOT/"contracts/fixtures/files/photo.png").read_bytes()
    for index in range(count):
        status,receipt=request(base,"POST",f"/accounts/{account_id}/files/uploads",{"name":f"File-{index:02}.png","content_type":"image/png","byte_size":len(content),"sha256":hashlib.sha256(content).hexdigest()},token); assert status == 201
        file_id=receipt["file"]["id"]
        assert request(base,"PUT",f"/accounts/{account_id}/files/{file_id}/content",token=token,raw_body=content,extra_headers={"Content-Type":"image/png"})[0] == 200
        assert request(base,"POST",f"/accounts/{account_id}/files/{file_id}/complete",token=token,extra_headers={"If-Match":'"1"'})[0] == 200
    assert request(base,"POST","/identity/sign-out",{"refresh_token":grant["refresh_token"]})[0] == 204
    return account_id


def seed_media(base, platform, proxy):
    login = "starter-" + platform + "-media"
    assert request(base, "POST", "/identity/register", {"login": login, "display_name": "Media user", "password": PASSWORD})[0] == 201
    status, grant = request(base, "POST", "/identity/sign-in", {"login": login, "password": PASSWORD}); assert status == 200
    token = grant["access_token"]
    status, account = request(base, "POST", "/accounts/bootstrap", {"name": "Personal"}, token); assert status == 201
    assert request(base, "POST", "/accounts", {"name": "Starter workspace"}, token)[0] == 201
    assert request(base, "POST", "/identity/sign-out", {"refresh_token": grant["refresh_token"]})[0] == 204
    proxy.media_accounts[account["id"]] = platform
    proxy.inbox_accounts[account["id"]] = platform + "-media"


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
        inbox_selected = environment.get("CONNECTED_INBOX") == "1"
        files_selected = environment.get("CONNECTED_FILES") == "1"
        owners = 31 + (4 if inbox_selected else 0) + (12 if files_selected else 0) + (4 if inbox_selected and files_selected else 0)
        if scenario in {"files", "inbox", "media"}:
            suite = {"files": "FilesUITests", "inbox": "InboxUITests", "media": "MediaUITests"}[scenario]
            selection = ["-only-testing:ProductAppUITests/" + suite, "-only-testing:ProductAppTests"]
            expected = owners + (2 if scenario=="files" else 1)
        elif scenario == "full":
            selection = ["-only-testing:ProductAppUITests/ConnectedUITests", "-only-testing:ProductAppTests"]
            if inbox_selected: selection.append("-only-testing:ProductAppUITests/InboxUITests")
            if files_selected: selection.append("-only-testing:ProductAppUITests/FilesUITests")
            if inbox_selected and files_selected: selection.append("-only-testing:ProductAppUITests/MediaUITests")
            expected = owners + 5 + int(inbox_selected) + 2 * int(files_selected) + int(inbox_selected and files_selected)
        else:
            selection = ["-only-testing:ProductAppUITests/ConnectedUITests/" + {"cache": "testSavedReadCacheThroughOutageAndProcessRelaunch", "lifecycle": "testSecurityFlowsAndLiveReadFromAnotherClient", "registration": "testRegistrationOnboardingDuplicateAndUncertainAcknowledgement", "workspaces": "testWorkspacesCreationRenameConflictAndUncertainty"}[scenario]]
            expected = 1
        run("xcodebuild", "-xctestrun", str(selected), "-destination", destination,
            *selection, "-parallel-testing-enabled", "NO",
            "-resultBundlePath", str(result), "-quiet", "test-without-building", env=environment)
        summary = json.loads(subprocess.check_output(["xcrun", "xcresulttool", "get", "test-results", "summary", "--path", str(result)]))
        assert summary["passedTests"] == expected and summary["failedTests"] == 0 and summary["skippedTests"] == 0
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
    if "files" in metadata["capabilities"] and scenario in {"full", "files", "media"}:
        gallery = "/sdcard/Pictures/connected-starter-" + metadata["appId"] + ".png"
        run(adb,"push",str(ROOT/"contracts/fixtures/files/photo.png"),gallery,env=environment)
        run(adb,"shell","am","broadcast","-a","android.intent.action.MEDIA_SCANNER_SCAN_FILE","-d","file://"+gallery,env=environment)
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
        inbox_stages = [("inbox-write", "inboxPagingUncertainWritesAndAccountIsolation"), ("inbox-restore", "inboxRestoresServerStateWithoutReplayingAndSignOutHidesIt")]
        if scenario == "inbox": stages = inbox_stages
        elif scenario == "full" and "inbox" in metadata["capabilities"]: stages += inbox_stages
        files_stages = [("files-write", "photoUploadReceiptRecoveryPreviewDeleteAndAccountIsolation"), ("files-completion-restore", "restoreCompletionReceiptWithoutReupload"), ("files-delete-restore", "restoreOriginalDeleteThenReviewTerminalConflict"), ("files-restore", "restoreFilesAndSignOutHidesThem"), ("files-pending-write", "stagePhotoBeforeUnsentContentAndProcessExit"), ("files-pending-restore", "restorePhotoBytesAndExplicitlyResumeUnsentContent")]
        if scenario == "files": stages = files_stages
        elif scenario == "full" and "files" in metadata["capabilities"]: stages += files_stages
        media_stages = [("media-write", "uploadsCreateScopedNoticesAndRespectPreferencesWithoutReplay"), ("media-restore", "restoreBothFeaturesWithoutReplayingAndSignOutHidesThem")]
        if scenario == "media": stages = media_stages
        elif scenario == "full" and {"files", "inbox"} <= set(metadata["capabilities"]): stages += media_stages
        for stage, method in stages:
            arguments = []
            if stage in {"restore", "retry"}:
                kind = "committed" if stage == "restore" else "unsent"
                arguments = ["-e", "pendingCommand", proxy.original(f"Recover {kind} Android")]
                run(adb, "shell", "am", "force-stop", metadata["appId"], env=environment)
            if stage == "security" or stage.startswith(("cache-", "registration-", "workspace-", "inbox-", "files-", "media-")):
                run(adb, "shell", "am", "force-stop", metadata["appId"], env=environment)
                arguments = ["-e", "apiBase", metadata["apiBaseURL"], "-e", "mailBase", environment["GO_LIFECYCLE_MAIL_BASE"]]
            result = subprocess.run([adb, "shell", "am", "instrument", "-w", "-r", "-e", "class",
                "dev.mobilefoundry.product." + ("MediaScreenTest" if stage.startswith("media-") else "FilesScreenTest" if stage.startswith("files-") else "InboxScreenTest" if stage.startswith("inbox-") else "ConnectedScreenTest") + "#" + method, "-e", "connectedStage", stage,
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
    parser.add_argument("--scenario", choices=("full", "lifecycle", "cache", "registration", "workspaces", "inbox", "files", "media"), default="full")
    args = parser.parse_args()
    metadata = json.loads((ROOT / "mobile-foundry.json").read_text())
    backend = metadata.get("backend")
    assert backend in {"go", "rust"} and metadata.get("nativeBackendConnection") == backend + "-connected", "Run in an independently generated connected product with a matching backend"
    selected = metadata["platforms"] if args.platform in {"host", "both"} else [args.platform]
    assert set(selected) <= set(metadata["platforms"])
    inbox_selected = "inbox" in metadata["capabilities"]
    files_selected = "files" in metadata["capabilities"]
    media_selected = files_selected and inbox_selected
    assert args.scenario != "media" or media_selected, "The media scenario requires --with files --with inbox"
    assert args.scenario != "files" or files_selected, "The files scenario requires --with files"
    assert args.scenario != "inbox" or inbox_selected, "The inbox scenario requires --with inbox"
    environment = dict(os.environ, GO_SESSION_TEST_BASE=metadata["apiBaseURL"])
    environment["CONNECTED_INBOX"] = "1" if inbox_selected else "0"
    environment["CONNECTED_FILES"] = "1" if files_selected else "0"
    if environment.get("ANDROID_JAVA_HOME"): environment["JAVA_HOME"] = environment["ANDROID_JAVA_HOME"]
    if environment.get("ANDROID_SDK"): environment["ANDROID_HOME"] = environment["ANDROID_SDK"]
    swift_test = ("swift", "test", *(["--disable-sandbox"] if environment.get("CONNECTED_SWIFT_DISABLE_SANDBOX") == "1" else []))
    with tempfile.TemporaryDirectory(prefix="connected-starter-") as temporary:
        with LocalMailCapture(Path(temporary)) as mailbox:
            environment["GO_LIFECYCLE_MAIL_BASE"] = mailbox.base
            for host_platform in selected:
                host_runtime = Path(temporary) / ("host-" + host_platform)
                host_runtime.mkdir()
                with api(metadata["apiBaseURL"], host_runtime, environment, mailbox.config, backend, metadata.get("rustPackage")):
                    # These methods use real native transports; their accounts are unique per test.
                    if host_platform == "swift":
                        run(*swift_test, "--package-path", "frontend/swift/packages/FoundrySession", "--filter", "nativeGoSessionSignInScopeRotationReuseAndSignOut|nativeGoLifecycleMailPasswordRevocationAndRealSocket", env=environment)
                        # The API admits two simultaneous password computations. Keep
                        # the third integration scenario separate rather than relaxing it.
                        run(*swift_test, "--package-path", "frontend/swift/packages/FoundrySession", "--filter", "nativeGoRegistrationExplicitSignInAndDuplicateNeverChangesCredentials|registration|canceledRegistration", env=environment)
                        run(*swift_test, "--package-path", "frontend/swift/packages/FoundrySession", "--filter", "nativeGoWorkspaceCreationCASOwnershipAndExplicitSelection|workspace", env=environment)
                        run(*swift_test, "--package-path", "frontend/swift/packages/FoundryServices", "--filter", "nativeGoNotesReceiptsRecoverLostAcknowledgementAndGuardVersions|readCache|memoryReadCache" + ("|inbox" if inbox_selected else "") + ("|nativeGoFilesBinaryTransferAndReceiptRecovery|pendingFiles|fileDetailAdmission" if files_selected else ""), env=environment)
                        run(*swift_test, "--package-path", "frontend/swift/packages/FoundryData", env=environment)
                    if host_platform == "kotlin":
                        run("./gradlew", ":core:session:testDebugUnitTest", "--tests", "dev.mobilefoundry.session.GoNativeSessionTest", "--tests", "dev.mobilefoundry.session.GoNativeLifecycleTest", "--tests", "dev.mobilefoundry.session.GoNativeRegistrationTest", "--tests", "dev.mobilefoundry.session.IdentityRegistrationTest", "--tests", "dev.mobilefoundry.session.AccountManagementTest", "--tests", "dev.mobilefoundry.session.GoNativeAccountTest",
                            ":core:services:test", "--tests", "dev.mobilefoundry.services.GoNativeNotesTest", "--tests", "dev.mobilefoundry.services.NotesReadCacheTest", *(["--tests", "dev.mobilefoundry.services.NotificationInboxTest"] if inbox_selected else []), *(["--tests", "dev.mobilefoundry.services.GoNativeFilesTest", "--tests", "dev.mobilefoundry.services.PendingFilesTest"] if files_selected else []),
                            ":product:testDebugUnitTest", "--tests", "dev.mobilefoundry.product.connection.RemoteNotesStoreTest", "--tests", "dev.mobilefoundry.product.connection.RegistrationModelTest", "--tests", "dev.mobilefoundry.product.connection.WorkspaceModelTest", *(["--tests", "dev.mobilefoundry.product.connection.InboxStoreTest"] if inbox_selected else []), *(["--tests", "dev.mobilefoundry.product.connection.FilesStoreTest"] if files_selected else []), *(["--tests", "dev.mobilefoundry.product.connection.FileDestinationStoreTest"] if media_selected else []), "--rerun-tasks", "--no-build-cache", cwd=ROOT / "frontend/kotlin/project", env=environment)
            if args.platform != "host":
                # Each native host suite and screen scenarios own their rate budget/state. Native
                # UI fixtures start in a separate owned process, without changing
                # production guards or adding retries to product requests.
                screen_runtime = Path(temporary) / "screens"
                screen_runtime.mkdir()
                with api(metadata["apiBaseURL"], screen_runtime, environment, mailbox.config, backend, metadata.get("rustPackage")) as (port, proxy):
                    if "swift" in selected:
                        if media_selected and args.scenario in {"full", "media"}: seed_media(metadata["apiBaseURL"], "ios", proxy)
                        if inbox_selected and args.scenario in {"full", "inbox"}: proxy.inbox_accounts[seed_inbox(metadata["apiBaseURL"], "ios")] = "ios"
                        if files_selected and args.scenario in {"full", "files"}: proxy.files_accounts[seed_files(metadata["apiBaseURL"], "ios")] = "ios"
                        if files_selected and args.scenario in {"full", "files"}: proxy.pending_file_accounts[seed_files(metadata["apiBaseURL"], "ios", suffix="pending-files", count=0)] = "ios"
                        if files_selected and args.scenario in {"full", "files", "media"}: run("xcrun","simctl","addmedia",environment.get("IOS_TEST_UDID","booted"),str(ROOT/"contracts/fixtures/files/photo.png"),env=environment)
                        if args.scenario in {"full", "lifecycle"}: seed(metadata["apiBaseURL"], "ios-security")
                        if args.scenario in {"full", "cache"}: seed(metadata["apiBaseURL"], "ios-cache")
                        if args.scenario == "full": seed(metadata["apiBaseURL"], "ios")
                        if args.scenario in {"full", "workspaces"}: seed(metadata["apiBaseURL"], "ios-workspaces")
                        ios(environment, args.scenario)
                        if args.scenario == "full": proxy.verify("iOS")
                        if args.scenario in {"full", "registration"}: proxy.verify_registration("ios")
                        if args.scenario in {"full", "workspaces"}: proxy.verify_workspaces("iOS")
                        if inbox_selected and args.scenario in {"full", "inbox"}: proxy.verify_inbox("ios")
                        if files_selected and args.scenario in {"full", "files"}: proxy.verify_files("ios")
                        if media_selected and args.scenario in {"full", "media"}: proxy.verify_media("ios")
                    if "kotlin" in selected:
                        if media_selected and args.scenario in {"full", "media"}: seed_media(metadata["apiBaseURL"], "android", proxy)
                        if inbox_selected and args.scenario in {"full", "inbox"}: proxy.inbox_accounts[seed_inbox(metadata["apiBaseURL"], "android")] = "android"
                        if files_selected and args.scenario in {"full", "files"}: proxy.files_accounts[seed_files(metadata["apiBaseURL"], "android")] = "android"
                        if files_selected and args.scenario in {"full", "files"}: proxy.pending_file_accounts[seed_files(metadata["apiBaseURL"], "android", suffix="pending-files", count=0)] = "android"
                        if args.scenario in {"full", "lifecycle"}: seed(metadata["apiBaseURL"], "android-security")
                        if args.scenario in {"full", "cache"}: seed(metadata["apiBaseURL"], "android-cache")
                        if args.scenario == "full": seed(metadata["apiBaseURL"], "android")
                        if args.scenario in {"full", "workspaces"}: seed(metadata["apiBaseURL"], "android-workspaces")
                        android(environment, metadata, port, proxy, args.scenario)
                        if args.scenario == "full": proxy.verify("Android")
                        if args.scenario in {"full", "registration"}: proxy.verify_registration("android")
                        if args.scenario in {"full", "workspaces"}: proxy.verify_workspaces("Android")
                        if inbox_selected and args.scenario in {"full", "inbox"}: proxy.verify_inbox("android")
                        if files_selected and args.scenario in {"full", "files"}: proxy.verify_files("android")
                        if media_selected and args.scenario in {"full", "media"}: proxy.verify_media("android")
    print("Independent connected acceptance passed", flush=True)


if __name__ == "__main__":
    main()
