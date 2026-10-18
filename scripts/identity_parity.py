#!/usr/bin/env python3
"""Core Identity conformance through owned Go/Rust processes; no database or mail."""
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import tempfile
from backend_parity import ROOT, REQUEST_ID, exchange, owned_server, run

PASSWORD = "SECRET-identity-password-123"
PREFIX = "/v1/identity/"


def call(port, path, method="POST", body=None, headers=(), wire=None):
    headers = list(headers)
    if body is not None:
        wire = json.dumps(body, ensure_ascii=False)
    if wire is not None:
        headers.append(("Content-Type", "application/json"))
    case = {"method":method, "path":path, "headers":headers}
    if wire is not None:
        case["wire_body"] = wire
    status, response_headers, data = exchange(port, case)
    assert response_headers.get("cache-control") == "no-store"
    assert response_headers.get("x-content-type-options") == "nosniff"
    request_id = response_headers.get("x-request-id", "")
    assert REQUEST_ID.fullmatch(request_id), "request ID missing"
    if method == "HEAD" or status == 204:
        assert not data, "unexpected body"
        return status, response_headers, None
    value = json.loads(data)
    assert "password" not in value and "password_hash" not in value
    if status >= 400:
        assert response_headers["content-type"] == "application/problem+json; charset=utf-8"
        assert value["request_id"] == request_id
        if status == 401:
            assert response_headers.get("www-authenticate") == 'Bearer realm="api"'
    else:
        assert response_headers["content-type"] == "application/json; charset=utf-8"
    return status, response_headers, value


def bearer(token):
    return [("Authorization", "Bearer " + token)]


def refusal(result, status=401, code="identity.invalid_credential"):
    assert result[0] == status and result[2]["code"] == code, "Identity refusal differs"


def grant(value, profile, version=1):
    expected = {"authority_id","principal","session_id","session_version","access_token","refresh_token","access_expires_at","refresh_expires_at"}
    assert set(value) == expected and value["authority_id"] == "go.identity.v1"
    assert value["principal"] == profile and value["session_version"] == version
    assert REQUEST_ID.fullmatch(value["session_id"])
    for key, prefix in [("access_token","a1."),("refresh_token","r1.")]:
        assert re.fullmatch(re.escape(prefix) + r"[A-Za-z0-9_-]{43}", value[key])
    access = datetime.fromisoformat(value["access_expires_at"].replace("Z", "+00:00"))
    refresh = datetime.fromisoformat(value["refresh_expires_at"].replace("Z", "+00:00"))
    assert access.tzinfo == timezone.utc and refresh.tzinfo == timezone.utc
    assert access < refresh and abs((access - datetime.now(timezone.utc)).total_seconds()-900) < 10


def core(port):
    def post(endpoint, body):
        return call(port,PREFIX+endpoint,body=body)
    def me(token, method="GET"):
        return call(port,PREFIX+"me",method,headers=bearer(token))
    refusal(call(port,PREFIX+"me","GET"),code="principal.authentication_required")
    status,_,profile = post("register",{"login":" SECRET.User ","display_name":" SECRET person ","password":PASSWORD})
    assert status == 201 and set(profile) == {"id","login","display_name"}
    assert profile["login"] == "secret.user" and profile["display_name"] == "SECRET person"
    refusal(post("register",{"login":"secret.user","display_name":"Again","password":PASSWORD}),409,"identity.login_exists")
    refusal(post("sign-in",{"login":"secret.user","password":"SECRET-wrong-password"}))
    refusal(post("sign-in",{"login":"SECRET.unknown","password":PASSWORD}))
    credentials = []
    def sign_in():
        status,_,value = post("sign-in",{"login":"SECRET.USER","password":PASSWORD})
        assert status == 200
        grant(value,profile)
        credentials.extend([value["access_token"],value["refresh_token"]])
        return value
    first = sign_in()
    assert me(first["access_token"])[2] == profile and me(first["access_token"],"HEAD")[0] == 200
    refusal(me(first["refresh_token"]))
    status,_,second = post("renew",{"refresh_token":first["refresh_token"]})
    assert status == 200
    grant(second,profile,2)
    credentials.extend([second["access_token"],second["refresh_token"]])
    assert second["session_id"] == first["session_id"] and second["refresh_expires_at"] == first["refresh_expires_at"]
    assert second["access_token"] != first["access_token"] and second["refresh_token"] != first["refresh_token"]
    refusal(me(first["access_token"]))
    assert me(second["access_token"])[2] == profile
    refusal(post("renew",{"refresh_token":first["refresh_token"]}))
    refusal(me(second["access_token"]))
    for token in [second["refresh_token"],first["refresh_token"]]:
        assert post("sign-out",{"refresh_token":token})[0] == 204
    independent, other = sign_in(), sign_in()
    assert independent["session_id"] != other["session_id"]
    assert post("sign-out",{"refresh_token":independent["refresh_token"]})[0] == 204
    assert me(other["access_token"])[2] == profile
    refusal(call(port,PREFIX+"me?token=SECRET","GET",headers=bearer(other["access_token"])),400,"identity.invalid_input")
    return credentials


def wire_cases(port):
    cases = json.loads((ROOT / "contracts/fixtures/backend-identity/http.json").read_text())
    for case in cases:
        status,headers,body = call(port,case["path"],case.get("method","POST"),wire=case.get("wire"),headers=case.get("headers",[]))
        assert status == case["status"], case["name"]
        for key in ["code","login"]:
            if key in case:
                assert body[key] == case[key], case["name"]
        if "allow" in case:
            assert headers["allow"] == case["allow"], case["name"]
    return []


def gate(port):
    for index in range(13):
        result = call(port,PREFIX+"sign-in",body={"login":"!","password":"bad"},headers=[("X-Forwarded-For",f"192.0.2.{index}")])
        refusal(result,401 if index < 12 else 429,"identity.invalid_credential" if index < 12 else "identity.attempt_limited")
    return []


def main():
    run(["go","test","./parity","-run","^TestSharedIdentity","-count=1"],ROOT/"backend/go")
    run(["cargo","test","--locked","-p","identity"],ROOT/"backend/rust")
    with tempfile.TemporaryDirectory(prefix="mobile-identity-parity-") as temporary:
        output = Path(temporary)
        go = output/"go-api"
        run(["go","build","-o",str(go),"./cmd/server"],ROOT/"backend/go")
        run(["cargo","build","--locked","-p","api-server"],ROOT/"backend/rust")
        for name,binary in [("Go",go),("Rust",ROOT/"backend/rust/target/debug/api-server")]:
            for phase,check in [("core",core),("wire",wire_cases),("gate",gate)]:
                log = output/f"{name}-{phase}.log"
                with owned_server(binary,log,authentication="identity") as port:
                    credentials = check(port)
                text = log.read_text()
                assert "SECRET" not in text and all(token not in text for token in credentials), "private Identity data logged"
                print(f"{name} Identity {phase}: passed; clean shutdown and private logs",flush=True)
    print("identity-parity: core memory flows passed; not full Identity/backend parity")


if __name__ == "__main__":
    main()
