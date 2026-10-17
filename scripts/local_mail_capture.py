"""Loopback-only, in-memory SMTP/code fixture for owned native acceptance. Never production mail."""
from contextlib import AbstractContextManager
from email.parser import BytesParser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import re
import socketserver
import threading
from urllib.parse import parse_qs, urlsplit


class LocalMailCapture(AbstractContextManager):
    def __init__(self, directory: Path):
        self.lock = threading.Lock()
        self.codes = {}
        owner = self

        class SMTP(socketserver.StreamRequestHandler):
            def handle(self):
                self.connection.settimeout(10)
                self.wfile.write(b"220 local acceptance SMTP\r\n")
                recipient = None
                while True:
                    line = self.rfile.readline(2048)
                    if not line: return
                    command = line.decode("ascii").strip()
                    if command.upper().startswith(("EHLO ", "HELO ")):
                        self.wfile.write(b"250-local acceptance\r\n250 SIZE 16384\r\n")
                    elif command.upper().startswith("MAIL FROM:"):
                        self.wfile.write(b"250 OK\r\n")
                    elif command.upper().startswith("RCPT TO:"):
                        recipient = command.split(":", 1)[1].strip("<>").lower()
                        self.wfile.write(b"250 OK\r\n")
                    elif command.upper() == "DATA" and recipient:
                        self.wfile.write(b"354 End with dot\r\n")
                        data = bytearray()
                        while True:
                            part = self.rfile.readline(2048)
                            if part == b".\r\n": break
                            if not part or len(data) + len(part) > 16384: return
                            data.extend(part)
                        message = BytesParser().parsebytes(bytes(data))
                        text = message.get_payload()
                        purpose = re.search(r"^Purpose: (verify_email|reset_password)\r?$", text, re.M)
                        code = re.search(r"^Code: ([ep]1\.[A-Za-z0-9_-]{43})\r?$", text, re.M)
                        if purpose is None or code is None: return
                        with owner.lock:
                            if len(owner.codes) >= 128 and (recipient, purpose[1]) not in owner.codes: return
                            owner.codes[(recipient, purpose[1])] = code[1]
                        self.wfile.write(b"250 Accepted\r\n")
                    elif command.upper() == "QUIT":
                        self.wfile.write(b"221 Bye\r\n"); return
                    elif command.upper() in {"RSET", "NOOP"}:
                        self.wfile.write(b"250 OK\r\n")
                    else:
                        self.wfile.write(b"500 Unsupported\r\n")

        class Mailbox(BaseHTTPRequestHandler):
            def log_message(self, *_): pass
            def do_GET(self):
                query = parse_qs(urlsplit(self.path).query)
                with owner.lock:
                    token = owner.codes.get((query.get("email", [""])[0], query.get("purpose", [""])[0]))
                body = json.dumps({"token": token}).encode()
                self.send_response(200 if token else 404)
                self.send_header("Content-Type", "application/json")
                self.send_header("Cache-Control", "no-store")
                self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)

        self.smtp = socketserver.ThreadingTCPServer(("127.0.0.1", 0), SMTP)
        self.smtp.daemon_threads = True
        self.mailbox = ThreadingHTTPServer(("127.0.0.1", 0), Mailbox)
        self.base = f"http://127.0.0.1:{self.mailbox.server_port}"
        self.config = directory / "mail.private.json"
        self.config.write_text(json.dumps({"Address": f"127.0.0.1:{self.smtp.server_address[1]}", "From": "security@example.invalid", "Mode": "development"}))
        self.config.chmod(0o600)
        self.threads = [threading.Thread(target=server.serve_forever, daemon=True) for server in (self.smtp, self.mailbox)]

    def __enter__(self):
        for thread in self.threads: thread.start()
        return self

    def __exit__(self, *_):
        for server in (self.smtp, self.mailbox): server.shutdown(); server.server_close()
        for thread in self.threads: thread.join(timeout=5)
        self.config.unlink(missing_ok=True)
