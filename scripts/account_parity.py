#!/usr/bin/env python3
"""Account/credential conformance using owned, isolated memory Go/Rust processes."""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import base64
import json
from pathlib import Path
import tempfile
from urllib.parse import quote
from backend_parity import ROOT, REQUEST_ID, owned_server, run
from identity_parity import PASSWORD, bearer, call, refusal

ACCOUNTS = "/v1/accounts"


def identity(port, login):
    body = {"login": login, "display_name": "SECRET person", "password": PASSWORD}
    status, _, profile = call(port, "/v1/identity/register", body=body)
    assert status == 201
    status, _, session = call(port, "/v1/identity/sign-in", body={"login": login, "password": PASSWORD})
    assert status == 200 and session["principal"] == profile
    return profile, session


def account(result, owner, name=None, version=1, status=200, location=False):
    actual, headers, value = result
    assert actual == status, f"account status {actual}, expected {status}"
    assert set(value) == {"id", "owner_id", "name", "version", "created_at", "updated_at"}
    assert REQUEST_ID.fullmatch(value["id"]) and value["owner_id"] == owner
    assert value["version"] == version and headers["etag"] == f'"{version}"'
    if name is not None:
        assert value["name"] == name
    if location:
        assert headers["location"] == ACCOUNTS + "/" + value["id"]
    created = datetime.fromisoformat(value["created_at"].replace("Z", "+00:00"))
    updated = datetime.fromisoformat(value["updated_at"].replace("Z", "+00:00"))
    assert created.tzinfo == timezone.utc and updated.tzinfo == timezone.utc and updated >= created
    return value


def core(port):
    owner, first = identity(port, "secret.owner")
    other, other_session = identity(port, "secret.other")
    token = first["access_token"]
    def request(path=ACCOUNTS, method="GET", body=None, headers=(), access=token):
        return call(port, path, method, body=body, headers=bearer(access) + list(headers))
    ordinary = account(request(method="POST", body={"name":"SECRET ordinary"}), owner["id"], status=201, location=True)
    def bootstrap(index):
        return request(ACCOUNTS + "/bootstrap", "POST", {"name": f"SECRET personal {index}"})
    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(bootstrap, range(8)))
    assert sum(result[0] == 201 for result in results) == 1
    personal = results[0][2]
    for result in results:
        value = account(result, owner["id"], status=result[0], location=True)
        assert value == personal and result[0] in [200,201]
    assert personal["id"] != ordinary["id"]
    path = ACCOUNTS + "/" + personal["id"]
    changed = account(request(path, "PATCH", {"name":"SECRET renamed"}, [("If-Match",'"1"')]), owner["id"], "SECRET renamed", 2)
    assert changed["created_at"] == personal["created_at"]
    repeat = account(request(ACCOUNTS+"/bootstrap", "POST", {"name":"Ignored"}), owner["id"], "SECRET renamed", 2, location=True)
    assert repeat == changed
    refusal(request(path, access=other_session["access_token"]),403,"account.access_denied")
    refusal(request(path,"PATCH",{"name":"Hijack"},[("If-Match",'"2"')],access=other_session["access_token"]),403,"account.access_denied")
    def rename(index):
        return request(path, "PATCH", {"name":f"SECRET winner {index}"}, [("If-Match",'"2"')])
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(rename,range(2)))
    assert sorted(result[0] for result in results) == [200,409]
    winner = next(result for result in results if result[0] == 200)
    account(winner,owner["id"],version=3)
    assert request(path)[2] == winner[2]
    known = {ordinary["id"],personal["id"]}
    for index in range(3):
        value = account(request(method="POST",body={"name":f"Team {index}"}),owner["id"],status=201,location=True)
        known.add(value["id"])
    account(request(method="POST",body={"name":"Other"},access=other_session["access_token"]),other["id"],status=201)
    seen = []
    query = "?limit=2"
    first_cursor = None
    while True:
        status,_,page = request(ACCOUNTS + query)
        assert status == 200 and set(page) == {"accounts","next_cursor"}
        assert all(row["owner_id"] == owner["id"] for row in page["accounts"])
        seen.extend(row["id"] for row in page["accounts"])
        cursor = page["next_cursor"]
        if cursor is None:
            break
        assert "=" not in cursor and len(cursor) <= 256
        first_cursor = first_cursor or cursor
        query = "?limit=2&cursor=" + quote(cursor)
    assert seen == sorted(known)
    refusal(request(ACCOUNTS+"?cursor="+first_cursor,access=other_session["access_token"]),400,"account.invalid_page")
    position = json.loads(base64.urlsafe_b64decode(first_cursor + "=" * (-len(first_cursor)%4)))
    assert position["owner_id"] == owner["id"] and position["version"] == 1
    for field,value in [("version",2),("after","invalid"),("extra",True)]:
        altered = {**position,field:value}
        cursor = base64.urlsafe_b64encode(json.dumps(altered).encode()).decode().rstrip("=")
        refusal(request(ACCOUNTS+"?cursor="+cursor),400,"account.invalid_page")
    status,_,renewed = call(port,"/v1/identity/renew",body={"refresh_token":first["refresh_token"]})
    assert status == 200
    refusal(request(path))
    assert request(path,access=renewed["access_token"])[2] == winner[2]
    assert call(port,"/v1/identity/sign-out",body={"refresh_token":renewed["refresh_token"]})[0] == 204
    refusal(request(path,access=renewed["access_token"]))
    assert request(access=other_session["access_token"])[0] == 200
    return [value[key] for value in [first,other_session,renewed] for key in ["access_token","refresh_token"]]


def wire_cases(port):
    profile,session = identity(port,"secret.wire")
    headers = bearer(session["access_token"])
    _,_,personal = call(port,ACCOUNTS+"/bootstrap",body={"name":"Personal"},headers=headers)
    cases = json.loads((ROOT/"contracts/fixtures/backend-account/http.json").read_text())
    for case in cases:
        result = call(port,case["path"].replace("{id}",personal["id"]),case.get("method","POST"),
                      headers=(headers if case.get("auth",True) else [])+case.get("headers",[]),wire=case.get("wire"))
        status,response_headers,body = result
        assert status == case["status"], f'{case["name"]}: {status}, expected {case["status"]}'
        for key in ["code","allow","etag"]:
            if key in case:
                assert (body if key == "code" else response_headers)[key] == case[key], case["name"]
        if "account_name" in case:
            account(result,profile["id"],case["account_name"],status=status,location=True)
        if case.get("code") == "account.invalid_name":
            assert body["fields"] == {"name":"Use 1 to 80 characters without control characters."}
    return [session["access_token"],session["refresh_token"]]


def deny(port):
    for path,method in [(ACCOUNTS,"GET"),(ACCOUNTS,"POST"),(ACCOUNTS+"/bootstrap","POST")]:
        refusal(call(port,path,method,headers=bearer("a1."+"A"*43)),401,"principal.authentication_required")
    return []


def main():
    run(["go","test","./parity","-run","^TestSharedAccount","-count=1"],ROOT/"backend/go")
    run(["cargo","test","--locked","-p","account","-p","capability"],ROOT/"backend/rust")
    with tempfile.TemporaryDirectory(prefix="mobile-account-parity-") as temporary:
        output = Path(temporary)
        go = output/"go-api"
        run(["go","build","-o",str(go),"./cmd/server"],ROOT/"backend/go")
        run(["cargo","build","--locked","-p","api-server"],ROOT/"backend/rust")
        for name,binary in [("Go",go),("Rust",ROOT/"backend/rust/target/debug/api-server")]:
            for phase,check,auth in [("core",core,"identity"),("wire",wire_cases,"identity"),("deny",deny,"deny")]:
                log = output/f"{name}-{phase}.log"
                with owned_server(binary,log,authentication=auth) as port:
                    credentials = check(port)
                text = log.read_text()
                assert "SECRET" not in text and all(token not in text for token in credentials), "private Account data logged"
                print(f"{name} Account {phase}: passed; clean shutdown and private logs",flush=True)
    print("account-parity: core memory/credential flows passed; Audit and PostgreSQL durability have separate acceptance")


if __name__ == "__main__":
    main()
