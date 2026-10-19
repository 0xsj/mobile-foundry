"""Public deployment configuration for explicitly connected native products."""
import re
from urllib.parse import urlsplit

from .profiles import ProfileError

DEFAULT_API_BASE = "http://127.0.0.1:8080/v1"


def connection_capability(profile):
    if profile is None:
        return None
    return next((item for item in profile["capabilities"] if item in {"go-connected", "rust-connected"}), None)


def connection_base(profile, backend, requested=None):
    connected = connection_capability(profile)
    if not connected:
        if requested is not None:
            raise ProfileError("--api-base-url requires --with go-connected or --with rust-connected.")
        return None
    required = profile["requiresBackend"]
    if backend is None or backend.profile["id"] != required:
        raise ProfileError(f"--with {connected} requires --backend {required}.")
    value = DEFAULT_API_BASE if requested is None else requested
    try:
        if not isinstance(value, str) or not 1 <= len(value) <= 2048 or not value.isascii() or any(ord(c) <= 32 or ord(c) == 127 for c in value):
            raise ValueError()
        parts = urlsplit(value)
        host, port = parts.hostname, parts.port
        if not host or parts.username is not None or parts.password is not None or "?" in value or "#" in value:
            raise ValueError()
        if not (host == "::1" or re.fullmatch(r"[a-z0-9.-]+", host)) or port is not None and not 1 <= port <= 65535:
            raise ValueError()
        if parts.scheme != "https" and not (parts.scheme == "http" and host in {"localhost", "127.0.0.1", "::1"}):
            raise ValueError()
        path = parts.path.rstrip("/")
        if path and any(not re.fullmatch(r"[A-Za-z0-9._~-]+", segment) or segment in {".", ".."} for segment in path.split("/")[1:]):
            raise ValueError()
        authority = "[::1]" if host == "::1" else host
        if port is not None and (parts.scheme, port) not in {("https", 443), ("http", 80)}:
            authority += f":{port}"
        return f"{parts.scheme}://{authority}{path}"
    except ValueError:
        raise ProfileError("Use an HTTPS API base, or loopback HTTP for Debug, without credentials, query, fragment or escaped path segments.") from None


def configure_connection(destination, profile, inputs, base):
    if base is None:
        return
    for platform in inputs.platforms:
        path = destination / profile["platforms"][platform]["connectionFile"]
        text = path.read_text()
        if text.count(DEFAULT_API_BASE) != 1:
            raise ProfileError("Expected one default connection endpoint; inventory drifted.")
        path.write_text(text.replace(DEFAULT_API_BASE, base))
