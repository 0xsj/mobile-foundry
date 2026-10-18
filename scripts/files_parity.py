#!/usr/bin/env python3
"""Owned Go/Rust Files wire, bytes, receipts and Audit acceptance."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import tempfile
import time
import uuid
from email.message import Message
from backend_parity import ROOT, REQUEST_ID, exchange, owned_server, run
from identity_parity import bearer, call, refusal
from notes_parity import setup
from audit_parity import eventually

CORPUS = ROOT / "contracts/fixtures/backend-files"
NAME = "SECRET Café <>& 🧩.png"
DATA = bytes(range(256)) * 256

def spec(data=DATA, name=NAME):
    return dict(name=name, content_type="image/png", byte_size=len(data), sha256=hashlib.sha256(data).hexdigest())

def binary(port, path, method, auth, data=None, extra=()):
    case = dict(method=method, path=path, headers=list(auth)+list(extra))
    if data is not None:
        case["wire_body"] = data
    status, headers, body = exchange(port, case)
    assert headers.get("cache-control") == "no-store"
    assert headers.get("x-content-type-options") == "nosniff"
    assert REQUEST_ID.fullmatch(headers["x-request-id"])
    return status, headers, body

def checked(result, status, action, key, actor, account):
    actual, headers, r = result
    assert actual == status, (actual, r)
    assert set(r) == {"key", "action", "principal_id", "file"}
    assert r["key"] == key and r["action"] == action and r["principal_id"] == actor
    f = r["file"]
    assert set(f) == {"id","account_id","owner_id","name","content_type","byte_size","sha256","status","version","created_at","updated_at"}
    assert f["account_id"] == account and f["owner_id"] == actor
    assert REQUEST_ID.fullmatch(f["id"])
    assert headers["etag"] == '"'+str(f["version"])+'"'
    return r

def core(port):
    owner, session, base = setup(port, "files.owner")
    other, other_session, foreign = setup(port, "files.other")
    auth = bearer(session["access_token"]); other_auth = bearer(other_session["access_token"])
    account = base.split("/")[-1]; key = str(uuid.uuid4())
    def request():
        return call(port, base+"/files/uploads", body=spec(), headers=auth+[("Idempotency-Key", key),("X-Correlation-ID","files-correlation")])
    with ThreadPoolExecutor(max_workers=12) as pool:
        results = list(pool.map(lambda _:request(), range(12)))
    original = checked(results[0],201,"request_upload",key,owner["id"],account)
    assert all(r[2] == original for r in results)
    path = base+"/files/"+original["file"]["id"]
    assert results[0][1]["location"] == path
    assert original["file"]["status"] == "pending_upload"
    refusal(call(port,path+"/content","GET",headers=auth),409,"files.invalid_transition")
    refusal(call(port,path+"/complete",headers=auth+[("If-Match",'"1"')]),503,"files.object_missing")
    for _ in range(2):
        assert binary(port,path+"/content","PUT",auth,DATA,[("Content-Type","image/png")])[0] == 200
    bad = binary(port,path+"/content","PUT",auth,b"bad",[("Content-Type","image/png")])
    assert bad[0] == 400 and json.loads(bad[2])["code"] == "files.content_mismatch"
    complete_key = str(uuid.uuid4())
    def complete():
        return call(port,path+"/complete",headers=auth+[("If-Match",'"1"'),("Idempotency-Key",complete_key)])
    with ThreadPoolExecutor(max_workers=4) as pool:
        completed = list(pool.map(lambda _:complete(), range(4)))
    available = checked(completed[0],200,"complete_upload",complete_key,owner["id"],account)
    assert all(r[2] == available for r in completed)
    downloaded = binary(port,path+"/content","GET",auth)
    assert downloaded[0] == 200 and downloaded[2] == DATA
    assert downloaded[1]["content-type"] == "image/png" and downloaded[1]["etag"] == '"2"'
    message = Message(); message["Content-Disposition"] = downloaded[1]["content-disposition"]
    assert message.get_filename() == NAME
    head = binary(port,path+"/content","HEAD",auth)
    assert head[0] == 200 and not head[2] and head[1]["content-length"] == str(len(DATA))
    assert call(port,path,"HEAD",headers=auth)[1]["etag"] == '"2"'
    refusal(call(port,path+"/complete",headers=auth+[("If-Match",'"1"'),("Idempotency-Key","loser")]),409,"files.version_conflict")
    refusal(call(port,"/v1/receipts/files/loser?account_id="+account,"GET",headers=auth),404,"files.receipt_not_found")
    refusal(call(port,base+"/files/uploads",body=spec(name="Changed"),headers=auth+[("Idempotency-Key",key)]),409,"files.command_conflict")
    for p in [base+"/files",path,path+"/content","/v1/receipts/files/"+key+"?account_id="+account]:
        refusal(call(port,p,"GET",headers=other_auth),403,"files.account_forbidden")
    delete_key = str(uuid.uuid4())
    def delete():
        return call(port,path,"DELETE",headers=auth+[("If-Match",'"2"'),("Idempotency-Key",delete_key)])
    removed = checked(delete(),200,"delete",delete_key,owner["id"],account)
    assert removed["file"]["status"] == "deleted" and removed["file"]["version"] == 3
    assert delete()[2] == removed
    assert request()[2] == original and complete()[2] == available
    for r in [original,available,removed]:
        assert call(port,"/v1/receipts/files/"+r["key"]+"?account_id="+account,"GET",headers=auth)[2] == r
    refusal(call(port,path,"GET",headers=auth),404,"files.not_found")
    refusal(call(port,path+"/content","GET",headers=auth),404,"files.not_found")
    assert call(port,base+"/files","GET",headers=auth)[2]["files"] == []
    evidence = eventually(lambda:call(port,base+"/audit","GET",headers=auth),4)
    rows = [e for e in evidence["entries"] if e["action"].startswith("files.")]
    assert [e["action"] for e in rows] == ["files.upload_requested","files.upload_completed","files.deleted"]
    assert [e["aggregate_version"] for e in rows] == [1,2,3]
    assert all(e["file_id"] == original["file"]["id"] and e["actor_id"] == owner["id"] for e in rows)
    assert rows[0]["correlation_id"] == "files-correlation"
    assert all(field not in e for e in rows for field in ["name","content_type","sha256","bytes"])
    expected=[]
    for index in range(5):
        result=call(port,base+"/files/uploads",body=spec(name="Page "+str(index)),headers=auth)
        assert result[0] == 201
        expected.append(result[2]["file"]["id"])
    seen=[]; query="?limit=2"; cursor=None
    while True:
        page=call(port,base+"/files"+query,"GET",headers=auth)[2]
        seen += [f["id"] for f in page["files"]]
        cursor=page["next_cursor"]
        if cursor is None:break
        refusal(call(port,foreign+"/files?cursor="+cursor,"GET",headers=other_auth),400,"files.invalid_input")
        query="?limit=2&cursor="+cursor
    assert seen == sorted(expected)
    # Pending deletion has version two and requires no byte upload.
    pending_path=base+"/files/"+expected[0]
    assert call(port,pending_path,"DELETE",headers=auth+[("If-Match",'"1"')])[2]["file"]["version"] == 2
    assert call(port,foreign+"/files","GET",headers=other_auth)[2]["files"] == []
    assert call(port,"/v1/identity/sign-out",body={"refresh_token":session["refresh_token"]})[0] == 204
    refusal(call(port,"/v1/receipts/files/"+key+"?account_id="+account,"GET",headers=auth))
    return [NAME,session["access_token"],session["refresh_token"],other_session["access_token"],other_session["refresh_token"]]

def wire_cases(port):
    owner,session,base=setup(port,"files.wire")
    auth=bearer(session["access_token"]); account=base.split("/")[-1]
    cases=json.loads((CORPUS/"http.json").read_text())
    for case in cases:
        created=call(port,base+"/files/uploads",body=spec(b"abc","Baseline"),headers=auth)
        assert created[0] == 201
        path=case["path"].replace("FILE",created[2]["file"]["id"])
        path="/v1/receipts/files/absent?account_id="+account if path=="RECEIPT" else base+path
        headers=list(auth) if case.get("authenticated",True) else []
        headers += [tuple(v) for v in case.get("headers",[])]
        wire=case.get("wire",case.get("raw"))
        if "body" in case:
            wire=json.dumps(case["body"],ensure_ascii=False);headers.append(("Content-Type","application/json"))
        elif "wire" in case and case["name"]!="missing-json-type":
            headers.append(("Content-Type","application/json"))
        status,response,data=exchange(port,dict(method=case["method"],path=path,headers=headers,**({"wire_body":wire} if wire is not None else {})))
        value=json.loads(data)
        assert status==case["status"], (case["name"],status,value)
        assert value.get("code")==case.get("code"), (case["name"],value)
        if "canonical_name" in case:
            assert value["file"]["name"]==case["canonical_name"]
    big=exchange(port,dict(method="PUT",path=base+"/files/"+created[2]["file"]["id"]+"/content",headers=auth+[("Content-Type","image/png")],wire_body=b"x"*10485761))
    assert big[0]==413 and json.loads(big[2])["code"]=="http.body_too_large"
    return [session["access_token"],session["refresh_token"]]

def durable(rust_binary,go_binary,owned,url,execute):
    """Persist private bytes plus metadata/receipts; replay facts across language handoff."""
    with owned_server(rust_binary,owned/"files-rust-killed.log","identity",url,crash=True) as port:
        owner,session,base=setup(port,"files.durable");auth=bearer(session["access_token"])
        key="durable-request"; original=call(port,base+"/files/uploads",body=spec(),headers=auth+[("Idempotency-Key",key)])[2]
        path=base+"/files/"+original["file"]["id"]
        assert binary(port,path+"/content","PUT",auth,DATA,[("Content-Type","image/png")])[0]==200
    with owned_server(go_binary,owned/"files-go-handoff.log","identity",url) as port:
        assert call(port,base+"/files/uploads",body=spec(),headers=auth+[("Idempotency-Key",key)])[2]==original
        completed=call(port,path+"/complete",headers=auth+[("If-Match",'"1"'),("Idempotency-Key","durable-complete")])
        assert completed[0]==200
        assert binary(port,path+"/content","GET",auth)[2]==DATA
        page=eventually(lambda:call(port,base+"/audit","GET",headers=auth),3)
    account=base.split("/")[-1]
    execute("UPDATE files.outbox SET delivered_at=NULL,lease_owner=NULL,lease_until=NULL WHERE stream_id='"+original["file"]["id"]+"'")
    with owned_server(rust_binary,owned/"files-rust-handoff.log","identity",url) as port:
        assert binary(port,path+"/content","GET",auth)[2]==DATA
        assert call(port,path+"/complete",headers=auth+[("If-Match",'"1"'),("Idempotency-Key","durable-complete")])[2]==completed[2]
        deadline=time.monotonic()+8
        while execute("SELECT count(*) FROM files.outbox WHERE stream_id='"+original["file"]["id"]+"' AND delivered_at IS NULL")!="0":
            assert time.monotonic()<deadline,"file Audit replay failed"
            time.sleep(.03)
        assert call(port,base+"/audit","GET",headers=auth)[2]==page
        removed=call(port,path,"DELETE",headers=auth+[("If-Match",'"2"'),("Idempotency-Key","durable-delete")])
        assert removed[0]==200
        eventually(lambda:call(port,base+"/audit","GET",headers=auth),4)
    with owned_server(go_binary,owned/"files-go-final.log","identity",url) as port:
        assert call(port,path,"DELETE",headers=auth+[("If-Match",'"2"'),("Idempotency-Key","durable-delete")])[2]==removed[2]
        refusal(call(port,path+"/content","GET",headers=auth),404,"files.not_found")
        assert call(port,"/v1/receipts/files/durable-complete?account_id="+account,"GET",headers=auth)[2]==completed[2]
    print("Files PostgreSQL: killed upload, Rust→Go completion, Go→Rust download/Audit replay, Rust→Go deletion/receipts passed",flush=True)
    for log in owned.glob("files-*.log"):
        assert all(secret not in log.read_text() for secret in [NAME,session["access_token"],session["refresh_token"],url]), "private durable Files input leaked"

def main():
    run(["go","test","./parity","-run","TestSharedFiles","-count=1"],ROOT/"backend/go")
    run(["cargo","test","--locked","-p","files"],ROOT/"backend/rust")
    run(["cargo","test","--locked","-p","audit","--test","admission"],ROOT/"backend/rust")
    with tempfile.TemporaryDirectory(prefix="mobile-files-parity-") as temporary:
        owned=Path(temporary);go=owned/"go-api"
        run(["go","build","-o",str(go),"./cmd/server"],ROOT/"backend/go")
        run(["cargo","build","--locked","-p","api-server"],ROOT/"backend/rust")
        rust=ROOT/"backend/rust/target/debug/api-server"
        for name,binary_path in [("Go",go),("Rust",rust)]:
            for phase,check in [("core",core),("wire",wire_cases)]:
                log=owned/(name+"-"+phase+".log")
                with owned_server(binary_path,log,"identity") as port:
                    secrets=check(port)
                assert all(s not in log.read_text() for s in secrets),"private Files input leaked"
            print(name+": Files lifecycle/bytes/receipts/Audit and 40 HTTP cases passed",flush=True)

if __name__=="__main__":main()

