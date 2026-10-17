#!/usr/bin/env python3
"""Build and exercise owned loopback processes; leave no server or binary behind."""
import contextlib
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
IDENTITY = "00000000-0000-4000-8000-000000000001"
TOKEN = "local-smoke-fixture"

def request(base, method, path, body=None, token=None, headers=None):
    supplied = dict(headers or {})
    if token:
        supplied["Authorization"] = "Bearer " + token
    data = None if body is None else json.dumps(body).encode()
    if data is not None:
        supplied["Content-Type"] = "application/json"
    req = urllib.request.Request(base + path, data=data, headers=supplied, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=2)
    except urllib.error.HTTPError as failure:
        response = failure
    with response:
        return response.status, response.headers, response.read()

@contextlib.contextmanager
def server(binary, fixture=False, database_url=None, crash=False, principal_id=IDENTITY, identity=False, forbidden=(), files_directory=None):
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        port = reservation.getsockname()[1]
    environment = dict(os.environ)
    environment.update(APP_ENV="development", HTTP_ADDR=f"127.0.0.1:{port}", LOG_FORMAT="json", LOG_LEVEL="info", DEV_AUTH_TOKEN="", DEV_PRINCIPAL_ID="")
    environment.update(STORAGE="memory", AUTH_MODE="development" if fixture else "deny", DATABASE_URL="", ACCOUNT_DATABASE_URL="", AUDIT_DATABASE_URL="",IDENTITY_DATABASE_URL="",NOTES_DATABASE_URL="")
    if identity:
        environment["AUTH_MODE"] = "identity"
    if database_url:
        environment.update(STORAGE="postgres", DATABASE_URL=database_url)
        environment.pop("ACCOUNT_DATABASE_URL")
        environment.pop("AUDIT_DATABASE_URL")
        environment.pop("IDENTITY_DATABASE_URL")
        environment.pop("NOTES_DATABASE_URL")
        environment.pop("FILES_DATABASE_URL", None)
    if fixture:
        environment.update(DEV_AUTH_TOKEN=TOKEN, DEV_PRINCIPAL_ID=principal_id)
    with tempfile.TemporaryDirectory(prefix="mobile-file-objects-") as object_temp, tempfile.TemporaryFile(mode="w+") as logs:
        environment["FILES_DIRECTORY"] = files_directory or object_temp
        process = subprocess.Popen([str(binary)], cwd=ROOT, env=environment, stdout=logs, stderr=logs)
        base = f"http://127.0.0.1:{port}"
        try:
            deadline = time.monotonic() + 5
            while True:
                if process.poll() is not None:
                    logs.seek(0)
                    raise RuntimeError("server startup failed: " + logs.read())
                try:
                    if request(base, "GET", "/v1/health/ready")[0] == 200:
                        break
                except (OSError, urllib.error.URLError):
                    pass
                if time.monotonic() > deadline:
                    raise RuntimeError("server did not become ready")
                time.sleep(0.02)
            yield base
        finally:
            if process.poll() is None:
                process.send_signal(signal.SIGKILL if crash else signal.SIGTERM)
            try:
                code = process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
                raise RuntimeError("server did not stop cooperatively")
            assert code == (-signal.SIGKILL if crash else 0), "server failed expected shutdown"
            logs.seek(0)
            lines = logs.read()
            assert TOKEN not in lines and "Private smoke account" not in lines and (not database_url or database_url not in lines), "credential/content leaked in logs"
            assert all(secret not in lines for secret in forbidden), "identity credential leaked in logs"
            for line in lines.splitlines():
                json.loads(line)

def main():
    with tempfile.TemporaryDirectory(prefix="mobile-account-audit-") as temporary:
        binary = Path(temporary) / "server"
        subprocess.run(["go", "build", "-o", str(binary), "./cmd/server"], cwd=ROOT, check=True)
        with server(binary) as base:
            assert request(base, "POST", "/v1/accounts", {"name": "Denied"}, TOKEN)[0] == 401
        with server(binary, fixture=True) as base:
            status, headers, wire = request(base, "POST", "/v1/accounts", {"name": "Private smoke account"}, TOKEN, {"X-Correlation-ID": "smoke.1"})
            assert status == 201 and headers["ETag"] == '"1"'
            account_id = json.loads(wire)["id"]
            path = "/v1/accounts/" + account_id
            status, headers, _ = request(base, "PATCH", path, {"name": "Renamed"}, TOKEN, {"If-Match": '"1"'})
            assert status == 200 and headers["ETag"] == '"2"'
            assert request(base, "PATCH", path, {"name": "Stale"}, TOKEN, {"If-Match": '"1"'})[0] == 409
            deadline = time.monotonic() + 3
            while True:
                status, _, wire = request(base, "GET", path + "/audit", token=TOKEN)
                assert status == 200
                entries = json.loads(wire)["entries"]
                if len(entries) == 2:
                    break
                assert time.monotonic() < deadline, "audit did not receive events"
                time.sleep(0.02)
            assert [entry["action"] for entry in entries] == ["account.created", "account.renamed"]
            assert entries[0]["correlation_id"] == "smoke.1" and b"Private smoke account" not in wire
            assert request(base, "HEAD", path, token=TOKEN)[2] == b""
        with server(binary, fixture=True) as base:
            assert request(base, "GET", path, token=TOKEN)[0] == 404, "memory profile unexpectedly survived restart"
        environment = dict(os.environ, APP_ENV="production", DEV_AUTH_TOKEN=TOKEN, DEV_PRINCIPAL_ID=IDENTITY)
        refused = subprocess.run([str(binary)], cwd=ROOT, env=environment, capture_output=True, text=True, timeout=5)
        assert refused.returncode == 1 and TOKEN not in refused.stderr, "production fixture was accepted/leaked"
    print("process smoke: deny default, create/read/rename/audit, stale conflict, metadata, HEAD, private logs, restart loss, SIGTERM and production refusal passed")

if __name__ == "__main__":
    main()
