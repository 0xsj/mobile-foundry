#!/usr/bin/env python3
"""Owned memory Go/Rust Account/Identity event delivery and Audit HTTP acceptance."""
import base64
import json
from pathlib import Path
import tempfile
import time
from urllib.parse import quote
from backend_parity import ROOT, REQUEST_ID, owned_server, run
from identity_parity import PASSWORD, bearer, call, refusal
from account_parity import identity

PERSONAL = "/v1/principals/me/audit"


def cursor(value):
    return base64.urlsafe_b64encode(json.dumps(value,separators=(",",":")).encode()).decode().rstrip("=")


def eventually(read, count):
    deadline = time.monotonic()+8
    while time.monotonic() < deadline:
        result = read()
        assert result[0] == 200, "Audit read failed"
        if len(result[2]["entries"]) >= count:
            assert len(result[2]["entries"]) == count, "unexpected duplicate evidence"
            return result[2]
        time.sleep(0.05)
    raise AssertionError("Audit did not catch up within the acceptance deadline")


def rows(page, owner, account=None):
    assert set(page) == {"entries","next_cursor"}
    prior = 0
    for row in page["entries"]:
        required = {"sequence","event_id","actor_id","action","aggregate_version","occurred_at","recorded_at","request_id"}
        optional = {"account_id","principal_id","session_id","reason","correlation_id"}
        assert required <= set(row) <= required|optional
        assert row["sequence"] > prior and row["actor_id"] == owner
        prior = row["sequence"]
        assert REQUEST_ID.fullmatch(row["event_id"]) and REQUEST_ID.fullmatch(row["request_id"])
        if account is not None:
            assert row["account_id"] == account and "principal_id" not in row and "session_id" not in row
        else:
            assert row["principal_id"] == owner and "account_id" not in row
        assert row["occurred_at"].endswith("Z") and row["recorded_at"].endswith("Z")
        assert "SECRET" not in json.dumps(row)


def core(port):
    owner, session = identity(port,"secret.audit.owner")
    other, other_session = identity(port,"secret.audit.other")
    headers = bearer(session["access_token"])
    def read(path, extra=()):
        return call(port,path,"GET",headers=headers+list(extra))
    first = eventually(lambda:read(PERSONAL),2)
    rows(first,owner["id"])
    assert {r["action"] for r in first["entries"]} == {"identity.principal.registered","identity.session.issued"}
    response = call(port,"/v1/accounts/bootstrap",body={"name":"SECRET personal"},headers=headers+[("X-Correlation-ID","flow-audit")])
    assert response[0] == 201
    account = response[2]["id"]
    expected_requests = {1:response[1]["x-request-id"]}
    expected_times = {1:response[2]["created_at"]}
    for version in [1,2]:
        response = call(port,"/v1/accounts/"+account,"PATCH",body={"name":"SECRET renamed"},headers=headers+[("If-Match",f'"{version}"'),("X-Correlation-ID","flow-audit")])
        assert response[0] == 200
        expected_requests[version+1] = response[1]["x-request-id"]
        expected_times[version+1] = response[2]["updated_at"]
    repeated = call(port,"/v1/accounts/bootstrap",body={"name":"Ignored"},headers=headers)
    assert repeated[0] == 200 and repeated[2]["version"] == 3
    second = call(port,"/v1/accounts",body={"name":"SECRET separate"},headers=headers)[2]["id"]
    path = "/v1/accounts/"+account+"/audit"
    page = eventually(lambda:read(path),3)
    rows(page,owner["id"],account)
    assert [r["aggregate_version"] for r in page["entries"]] == [1,2,3]
    assert [r["action"] for r in page["entries"]] == ["account.created","account.renamed","account.renamed"]
    for row in page["entries"]:
        version = row["aggregate_version"]
        assert row["request_id"] == expected_requests[version]
        assert row["occurred_at"] == expected_times[version] and row["correlation_id"] == "flow-audit"
    seen = []
    query = "?limit=1"
    first_cursor = None
    while True:
        result = read(path+query)
        assert result[0] == 200
        seen.extend(row["event_id"] for row in result[2]["entries"])
        position = result[2]["next_cursor"]
        if position is None:
            break
        first_cursor = first_cursor or position
        query = "?limit=1&cursor="+quote(position)
    assert seen == [row["event_id"] for row in page["entries"]]
    refusal(read("/v1/accounts/"+second+"/audit?cursor="+first_cursor),400,"audit.invalid_query")
    refusal(call(port,path,"GET",headers=bearer(other_session["access_token"])),403,"audit.access_denied")
    altered = {"account_id":account,"after":page["entries"][0]["sequence"]}
    for field,value in [("after",0),("after",9007199254740992),("principal_id",owner["id"]),("extra",True)]:
        refusal(read(path+"?cursor="+cursor({**altered,field:value})),400,"audit.invalid_query")
    personal_page = eventually(lambda:read(PERSONAL),2)
    personal_cursor = cursor({"account_id":"","principal_id":owner["id"],"after":personal_page["entries"][0]["sequence"]})
    refusal(call(port,PERSONAL+"?cursor="+personal_cursor,"GET",headers=bearer(other_session["access_token"])),400,"audit.invalid_query")
    credentials = [session,other_session]
    def sign_in():
        response = call(port,"/v1/identity/sign-in",body={"login":"secret.audit.owner","password":PASSWORD})
        assert response[0] == 200
        credentials.append(response[2])
        return response[2]
    old = sign_in()
    response = call(port,"/v1/identity/renew",body={"refresh_token":old["refresh_token"]})
    assert response[0] == 200
    credentials.append(response[2])
    refusal(call(port,"/v1/identity/renew",body={"refresh_token":old["refresh_token"]}))
    signed_out = sign_in()
    assert call(port,"/v1/identity/sign-out",body={"refresh_token":signed_out["refresh_token"]})[0] == 204
    personal_page = eventually(lambda:read(PERSONAL),7)
    rows(personal_page,owner["id"])
    revoked = [r for r in personal_page["entries"] if r["action"] == "identity.session.revoked"]
    assert {r["reason"] for r in revoked} == {"sign_out","refresh_reuse"}
    assert all("session_id" in r for r in personal_page["entries"] if r["action"] != "identity.principal.registered")
    other_page = eventually(lambda:call(port,PERSONAL,"GET",headers=bearer(other_session["access_token"])),2)
    rows(other_page,other["id"])
    assert set(r["event_id"] for r in other_page["entries"]).isdisjoint(r["event_id"] for r in personal_page["entries"])
    assert call(port,"/v1/identity/sign-out",body={"refresh_token":session["refresh_token"]})[0] == 204
    refusal(read(PERSONAL))
    return [value[key] for value in credentials for key in ["access_token","refresh_token"]]


def wire_cases(port):
    _,session = identity(port,"secret.audit.wire")
    _,other = identity(port,"secret.audit.foreign")
    auth = bearer(session["access_token"])
    account = call(port,"/v1/accounts/bootstrap",body={"name":"Personal"},headers=auth)[2]["id"]
    path = "/v1/accounts/"+account+"/audit"
    eventually(lambda:call(port,path,"GET",headers=auth),1)
    for case in json.loads((ROOT/"contracts/fixtures/backend-audit/http.json").read_text()):
        selected = case.get("auth",True)
        headers = (bearer(other["access_token"]) if selected == "other" else auth if selected else [])+case.get("headers",[])
        status,response,body = call(port,case["path"].replace("{account}",account),case.get("method","GET"),headers=headers)
        assert status == case["status"],f'{case["name"]}: {status}, expected {case["status"]}'
        if "code" in case:
            assert body["code"] == case["code"],case["name"]
        if "allow" in case:
            assert response["allow"] == case["allow"]
    return [session["access_token"],session["refresh_token"],other["access_token"],other["refresh_token"]]


def deny(port):
    for path in [PERSONAL,"/v1/accounts/00000000-0000-4000-8000-000000000001/audit"]:
        refusal(call(port,path,"GET",headers=bearer("a1."+"A"*43)),401,"principal.authentication_required")
    return []


def main():
    run(["go","test","./parity","-run","^TestSharedAudit","-count=1"],ROOT/"backend/go")
    run(["cargo","test","--locked","-p","audit","-p","events","-p","api-server"],ROOT/"backend/rust")
    with tempfile.TemporaryDirectory(prefix="mobile-audit-parity-") as temporary:
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
                assert "SECRET" not in text and all(token not in text for token in credentials),"private event/Audit input logged"
                print(f"{name} Audit {phase}: passed; clean shutdown and private logs",flush=True)
    print("audit-parity: core memory delivery passed; database/broker recovery remains separate")


if __name__ == "__main__":
    main()
