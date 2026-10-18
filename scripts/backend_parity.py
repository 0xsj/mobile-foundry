#!/usr/bin/env python3
"""Run the canonical-path runtime corpus against isolated, owned Go/Rust APIs.

No provider credentials, host app configuration, database, or migrations are used.
This establishes only the scope recorded in backend/rust/PARITY.md.
"""
from contextlib import contextmanager
import http.client
import json
import os
from pathlib import Path
import re
import signal
import socket
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / "contracts/fixtures/backend-runtime"
REQUEST_ID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\Z")


def run(command, cwd):
    subprocess.run(command, cwd=cwd, check=True)


def exchange(port, case):
    # Observe the APIs' 15-second request deadline plus transport margin. A two-
    # second client cutoff can abandon valid debug-build KDF work under CPU load.
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=17)
    try:
        connection.putrequest(case["method"], case["path"])
        for name, value in case.get("headers", []):
            connection.putheader(name, value)
        body = case.get("wire_body")
        if body is not None:
            body = body.encode("utf-8") if isinstance(body, str) else body
            connection.putheader("Content-Length", str(len(body)))
        connection.endheaders(body)
        response = connection.getresponse()
        return response.status, dict((k.lower(), v) for k, v in response.getheaders()), response.read()
    finally:
        connection.close()


@contextmanager
def owned_server(binary, log_path, authentication="deny", database_url=None, crash=False, module_urls=None, mail_config=None, push_config=None, push_jobs=False):
    # Release an ephemeral loopback reservation immediately before process launch.
    # If another process wins that port, the child fails startup; never reuse it.
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        port = reservation.getsockname()[1]
    environment = {key: os.environ[key] for key in ("PATH", "HOME", "TMPDIR") if key in os.environ}
    environment.update(APP_ENV="test", HTTP_ADDR=f"127.0.0.1:{port}", LOG_FORMAT="json",
                       LOG_LEVEL="debug", STORAGE="memory", AUTH_MODE=authentication)
    if database_url is not None:
        environment.update(STORAGE="postgres",DATABASE_URL=database_url,FILES_DIRECTORY=str(log_path.parent / "objects"))
    if mail_config is not None:
        environment["IDENTITY_MAIL_CONFIG"]=str(mail_config)
    if push_config is not None:
        environment["PUSH_CONFIG_FILE"]=str(push_config)
    if push_jobs:
        environment["PUSH_JOBS_ENABLED"]="true"
    if module_urls:
        assert set(module_urls) <= {"ACCOUNT_DATABASE_URL", "AUDIT_DATABASE_URL", "IDENTITY_DATABASE_URL", "NOTES_DATABASE_URL", "FILES_DATABASE_URL", "NOTIFICATIONS_DATABASE_URL"}
        environment.update(module_urls)
    with log_path.open("wb") as logs:
        process = subprocess.Popen([str(binary)], env=environment, stdout=logs, stderr=logs)
        try:
            deadline = time.monotonic() + 20
            while True:
                if process.poll() is not None:
                    raise AssertionError(f"{binary.name}: startup failed ({process.returncode})")
                # Both runtimes emit their startup marker only after binding.
                startup = log_path.read_bytes()
                if b"server serving" in startup or b"server listening" in startup:
                    try:
                        status, _, _ = exchange(port, {"method": "GET", "path": "/v1/health/ready"})
                        if status == 200 and process.poll() is None:
                            break
                    except (OSError, http.client.HTTPException):
                        pass
                if time.monotonic() > deadline:
                    raise AssertionError(f"{binary.name}: readiness timed out")
                time.sleep(0.02)
            yield port
            process.send_signal(signal.SIGKILL if crash else signal.SIGTERM)
            assert process.wait(timeout=12) == (-signal.SIGKILL if crash else 0), f"{binary.name}: unclean owned shutdown"
            try:
                with socket.create_connection(("127.0.0.1", port), timeout=0.2):
                    raise AssertionError(f"{binary.name}: listener survived shutdown")
            except ConnectionRefusedError:
                pass
        finally:
            if process.poll() is None:
                process.send_signal(signal.SIGTERM)
                try:
                    process.wait(timeout=12)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=3)


def check_http(port, cases):
    seen = set()
    for case in cases:
        status, headers, body = exchange(port, case)
        name = case["name"]
        assert status == case["status"], f"{name}: status {status}"
        assert headers.get("cache-control") == "no-store", name
        assert headers.get("x-content-type-options") == "nosniff", name
        request_id = headers.get("x-request-id", "")
        assert REQUEST_ID.fullmatch(request_id) and request_id not in seen, f"{name}: request ID"
        assert all(request_id != value for key, value in case.get("headers", []) if key.lower() == "x-request-id"), name
        seen.add(request_id)
        assert headers.get("x-correlation-id") == case.get("correlation"), f"{name}: correlation"
        assert headers.get("allow") == case.get("allow"), f"{name}: Allow"
        expected_type = "application/json" if status == 200 else "application/problem+json"
        assert headers.get("content-type") == expected_type + "; charset=utf-8", name
        if case["method"] == "HEAD":
            assert body == b"" and int(headers.get("content-length", "0")) > 0, name
        else:
            expected = dict(case["body"])
            if status != 200:
                expected["request_id"] = request_id
            assert json.loads(body) == expected, f"{name}: unexpected public body"
            assert int(headers["content-length"]) == len(body), f"{name}: length"
    return len(cases)


def main():
    cases = json.loads((CORPUS / "http.json").read_text())
    run(["go", "test", "./parity", "-run", "^TestSharedRuntime", "-count=1"], ROOT / "backend/go")
    run(["cargo", "test", "--locked", "-p", "httpx", "--tests"], ROOT / "backend/rust")
    run(["cargo", "test", "--locked", "-p", "kernel", "--tests"], ROOT / "backend/rust")
    with tempfile.TemporaryDirectory(prefix="mobile-backend-parity-") as temporary:
        owned = Path(temporary)
        go_binary = owned / "go-api"
        run(["go", "build", "-o", str(go_binary), "./cmd/server"], ROOT / "backend/go")
        run(["cargo", "build", "--locked", "-p", "api-server"], ROOT / "backend/rust")
        for name, binary in [("Go", go_binary), ("Rust", ROOT / "backend/rust/target/debug/api-server")]:
            log_path = owned / f"{name.lower()}.log"
            with owned_server(binary, log_path) as port:
                count = check_http(port, cases)
            logs = log_path.read_text()
            assert "SECRET" not in logs, f"{name}: sensitive input leaked into logs"
            assert "request completed" in logs, f"{name}: missing completion logs"
            print(f"{name}: {count} HTTP cases; private input absent from logs; clean SIGTERM/listener close", flush=True)
    print("backend-parity: canonical HTTP, failures, JSON/header admission and IDs passed (not full backend parity)")


if __name__ == "__main__":
    main()
