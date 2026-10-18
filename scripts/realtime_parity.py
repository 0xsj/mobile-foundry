#!/usr/bin/env python3
"""Owned loopback WebSocket acceptance. The frame reader is test-only, not an adapter."""
import base64
from contextlib import ExitStack
import hashlib
import json
from pathlib import Path
import secrets
import socket
import struct
import tempfile
import time
import uuid
from backend_parity import ROOT, REQUEST_ID, owned_server, run
from identity_parity import PASSWORD, bearer, call
from notes_parity import setup

CORPUS = ROOT / "contracts/fixtures/backend-realtime"


class Socket:
    def __init__(self, port, path, headers=(), method="GET", omit=None):
        self.io = socket.create_connection(("127.0.0.1", port), timeout=3)
        key = base64.b64encode(secrets.token_bytes(16)).decode()
        fields = [("Host", "localhost"), ("Connection", "Upgrade"), ("Upgrade", "websocket"),
                  ("Sec-WebSocket-Version", "13"), ("Sec-WebSocket-Key", key)]
        fields = [(k, v) for k, v in fields if k.lower() != omit] + list(headers)
        self.io.sendall((f"{method} {path} HTTP/1.1\r\n" + "".join(f"{k}: {v}\r\n" for k, v in fields) + "\r\n").encode())
        response = bytearray()
        while not response.endswith(b"\r\n\r\n"):
            value = self.io.recv(1)
            assert value and len(response) < 16384, "invalid upgrade response"
            response.extend(value)
        lines = response.decode("ascii").split("\r\n")
        self.status = int(lines[0].split()[1])
        self.headers = {k.lower(): v.strip() for k, v in (line.split(":", 1) for line in lines[1:] if line)}
        # Owned problems carry the common JSON headers. Go's WS library writes
        # its own handshake failures/101; Rust's boundary also decorates those.
        if "application/problem+json" in self.headers.get("content-type", ""):
            assert self.headers["cache-control"] == "no-store" and self.headers["x-content-type-options"] == "nosniff"
        assert REQUEST_ID.fullmatch(self.headers.get("x-request-id", ""))
        if self.status == 101:
            expected = base64.b64encode(hashlib.sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()).digest()).decode()
            assert self.headers["sec-websocket-accept"] == expected
            assert "sec-websocket-extensions" not in self.headers and "sec-websocket-protocol" not in self.headers

    def __enter__(self): return self
    def __exit__(self, *_): self.close()
    def close(self): self.io.close()

    def send(self, opcode, value=b""):
        mask = secrets.token_bytes(4)
        assert len(value) < 126
        self.io.sendall(bytes([0x80 | opcode, 0x80 | len(value)]) + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(value)))

    def exact(self, length):
        data = bytearray()
        while len(data) < length:
            value = self.io.recv(length - len(data))
            if not value: raise EOFError("socket owner closed")
            data.extend(value)
        return bytes(data)

    def event(self, timeout=3):
        deadline = time.monotonic() + timeout
        while True:
            self.io.settimeout(max(.001, deadline - time.monotonic()))
            a, b = self.exact(2)
            assert a & 0xf0 == 0x80 and not b & 0x80, "unexpected server framing"
            length = b & 127
            if length == 126: length = struct.unpack("!H", self.exact(2))[0]
            if length == 127: length = struct.unpack("!Q", self.exact(8))[0]
            assert length <= 4096
            value = self.exact(length); opcode = a & 15
            if opcode == 9:
                self.send(10, value)
            elif opcode == 8:
                raise EOFError("socket owner closed")
            else:
                assert opcode == 1
                return json.loads(value)

    def hint(self, account, kind):
        assert self.event() == {"type": kind, "resource": "notes", "account_id": account, "refetch": True}

    def quiet(self, timeout=.2):
        try: self.event(timeout)
        except TimeoutError: return
        raise AssertionError("unexpected account hint")

    def closed(self, timeout=8):
        try: self.event(timeout)
        except (EOFError, ConnectionError): return
        raise AssertionError("socket remained authorized")


def core(port, long=False):
    owner, session, base = setup(port, "realtime.owner." + str(uuid.uuid4()))
    _, foreign, other = setup(port, "realtime.other." + str(uuid.uuid4()))
    account = base.split("/")[-1]; auth = bearer(session["access_token"])
    path = base + "/realtime"
    for case in json.loads((CORPUS / "http.json").read_text()):
        headers = list(auth)
        if case.get("auth") == "anonymous": headers = []
        if case.get("auth") == "foreign": headers = bearer(foreign["access_token"])
        headers += case.get("headers", [])
        target = path if "account" not in case else "/v1/accounts/" + case["account"] + "/realtime"
        with Socket(port, target + case.get("query", ""), headers, case.get("method", "GET"), case.get("omit")) as client:
            assert client.status == case["status"], (case["name"], client.status)
            assert client.headers.get("allow") == case.get("allow"), case["name"]
    with Socket(port, path, auth) as client, Socket(port, other + "/realtime", bearer(foreign["access_token"])) as isolated:
        assert client.status == isolated.status == 101
        client.hint(account, "ready"); isolated.hint(other.split("/")[-1], "ready")
        key = str(uuid.uuid4()); title = "PRIVATE-live-title"
        headers = auth + [("Idempotency-Key", key)]
        created = call(port, base + "/notes", body={"title": title}, headers=headers)
        assert created[0] == 201
        client.hint(account, "changed"); isolated.quiet()
        assert call(port, base + "/notes", "GET", headers=auth)[2]["notes"][0]["title"] == title
        # Exact historical replay is a successful write and may repeat the hint.
        assert call(port, base + "/notes", body={"title": title}, headers=headers)[2] == created[2]
        client.hint(account, "changed")
        target = base + "/notes/" + created[2]["note_id"]
        assert call(port, target, "PATCH", body={"title": "PRIVATE-rejected"}, headers=auth + [("Idempotency-Key", str(uuid.uuid4())), ("If-Match", '"2"')])[0] == 409
        client.quiet()
        for method, body, expected, version in [("PATCH", {"title": "PRIVATE-updated"}, 200, 1), ("DELETE", None, 200, 2)]:
            assert call(port, target, method, body=body, headers=auth + [("Idempotency-Key", str(uuid.uuid4())), ("If-Match", f'"{version}"')])[0] == expected
            client.hint(account, "changed")
        if long: client.quiet(16)  # Handles real Ping/Pong while exceeding HTTP's 15s deadline.
        renewed = call(port, "/v1/identity/renew", body={"refresh_token": session["refresh_token"]})
        assert renewed[0] == 200
        client.closed()
    auth = bearer(renewed[2]["access_token"])
    with Socket(port, path, auth) as client:
        assert client.status == 101; client.hint(account, "ready")
        assert call(port, base + "/notes", "GET", headers=auth)[2]["notes"] == []
        assert call(port, "/v1/identity/sign-out", body={"refresh_token": renewed[2]["refresh_token"]})[0] == 204
        client.closed()
    return [PASSWORD, session["access_token"], session["refresh_token"], foreign["access_token"], renewed[2]["access_token"], title]


def capacity(port):
    _, session, base = setup(port, "realtime.capacity." + str(uuid.uuid4()))
    path = base + "/realtime"; auth = bearer(session["access_token"]); account = base.split("/")[-1]
    with ExitStack() as stack:
        clients = [stack.enter_context(Socket(port, path, auth)) for _ in range(8)]
        for client in clients:
            assert client.status == 101; client.hint(account, "ready")
        with Socket(port, path, auth) as full: assert full.status == 429
        clients[0].close()
        deadline = time.monotonic() + 3
        while True:
            replacement = Socket(port, path, auth)
            if replacement.status == 101:
                replacement.hint(account, "ready"); replacement.close(); break
            replacement.close()
            assert time.monotonic() < deadline, "disconnect did not release capacity"
            time.sleep(.01)
    return session, path


def durable(owned, url, go_binary, rust_binary):
    for name, binary in [("go", go_binary), ("rust", rust_binary)]:
        log = owned / ("realtime-" + name + "-postgres.log")
        with owned_server(binary, log, "identity", url) as port:
            private = core(port)
        assert all(value not in log.read_text() for value in private)
    print("Realtime PostgreSQL: Go/Rust confirmed writes/replays, failed CAS silence, scope isolation, renewal/sign-out closure passed", flush=True)


def main():
    run(["go", "test", "-race", "./parity", "-run", "^TestSharedRealtimeHub$", "-count=1"], ROOT / "backend/go")
    run(["cargo", "test", "--locked", "-p", "broadcast", "-p", "realtime"], ROOT / "backend/rust")
    with tempfile.TemporaryDirectory(prefix="mobile-realtime-parity-") as temporary:
        owned = Path(temporary); go = owned / "go-api"
        run(["go", "build", "-o", str(go), "./cmd/server"], ROOT / "backend/go")
        run(["cargo", "build", "--locked", "-p", "api-server"], ROOT / "backend/rust")
        for name, binary in [("Go", go), ("Rust", ROOT / "backend/rust/target/debug/api-server")]:
            print(name + " Realtime: starting owned process acceptance", flush=True)
            log = owned / (name + ".log")
            with owned_server(binary, log, "identity") as port:
                private = core(port, long=True)
                session, path = capacity(port)
                client = Socket(port, path, bearer(session["access_token"]))
                try: client.hint(path.split("/")[3], "ready")
                except BaseException: client.close(); raise
            try: client.closed()
            finally: client.close()
            assert all(value not in log.read_text() for value in private + [session["access_token"], session["refresh_token"], "PRIVATE"])
            print(name + " Realtime: shared wire guards, live Notes, 16s connection, bounded capacity, revocation and joined shutdown passed", flush=True)
    print("Realtime: 8 shared hub scenarios/38 operations and 15 shared HTTP admissions; local recoverable hints only", flush=True)


if __name__ == "__main__": main()
