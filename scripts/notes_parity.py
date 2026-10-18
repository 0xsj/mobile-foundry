#!/usr/bin/env python3
"""Owned Go/Rust Notes HTTP acceptance; PostgreSQL phases live in postgres_parity."""
from concurrent.futures import ThreadPoolExecutor
import json
from pathlib import Path
import tempfile
import uuid
from backend_parity import ROOT, REQUEST_ID, owned_server, run, exchange
from identity_parity import bearer, call, refusal
from account_parity import identity

CORPUS=ROOT/"contracts/fixtures/backend-notes"
TITLE="SECRET Café <>&\u2028\u2029\x00 🧩"


def command():
    return str(uuid.uuid4())


def setup(port,login):
    owner,session=identity(port,login)
    auth=bearer(session["access_token"])
    result=call(port,"/v1/accounts/bootstrap",body={"name":"SECRET Notes"},headers=auth)
    assert result[0]==201
    return owner,session,"/v1/accounts/"+result[2]["id"]


def receipt(result,account,owner,action,title,version,status,expected=None):
    actual,headers,value=result
    assert actual==status,"Notes mutation status differs"
    assert set(value)=={"command_id","account_id","principal_id","action","note_id","title","version"}
    assert value["account_id"]==account and value["principal_id"]==owner
    assert value["action"]==action and value["title"]==title and value["version"]==version
    assert REQUEST_ID.fullmatch(value["command_id"]) and REQUEST_ID.fullmatch(value["note_id"])
    assert headers["etag"]==f'"{version}"'
    assert headers["location"]=="/v1/accounts/"+account+"/note-receipts/"+value["command_id"]
    if expected is not None:
        assert value==expected,"historical receipt changed"
    return value


def core(port):
    owner,session,base=setup(port,"secret.notes.owner")
    other,other_session,foreign=setup(port,"secret.notes.other")
    auth=bearer(session["access_token"])
    account=base.split('/')[-1]
    def request(path="/notes",method="GET",body=None,headers=()):
        return call(port,base+path,method,body=body,headers=auth+list(headers))
    key=command()
    def create(_):
        return request(method="POST",body={"title":TITLE},headers=[("Idempotency-Key",key)])
    with ThreadPoolExecutor(max_workers=12) as pool:
        creates=list(pool.map(create,range(12)))
    original=receipt(creates[0],account,owner["id"],"create",TITLE,1,201)
    for result in creates[1:]:
        receipt(result,account,owner["id"],"create",TITLE,1,201,original)
    target="/notes/"+original["note_id"]
    assert len(request()[2]["notes"])==1
    update_key=command()
    update=request(target,"PATCH",{"title":"SECRET edited"},[("Idempotency-Key",update_key),("If-Match",'"1"')])
    changed=receipt(update,account,owner["id"],"update","SECRET edited",2,200)
    receipt(create(0),account,owner["id"],"create",TITLE,1,201,original)
    assert request(target)[2]["version"]==2
    refusal(request(method="POST",body={"title":"Different"},headers=[("Idempotency-Key",key)]),409,"notes.command_conflict")
    keys=[command(),command()]
    def rename(index):
        return request(target,"PATCH",{"title":"SECRET competing"},[("Idempotency-Key",keys[index]),("If-Match",'"2"')])
    with ThreadPoolExecutor(max_workers=2) as pool:
        results=list(pool.map(rename,range(2)))
    assert sorted(result[0] for result in results)==[200,409]
    loser=next(index for index,result in enumerate(results) if result[0]==409)
    refusal(request("/note-receipts/"+keys[loser]),404,"notes.receipt_not_found")
    deleted_key=command()
    deleted=request(target,"DELETE",headers=[("Idempotency-Key",deleted_key),("If-Match",'"3"')])
    removed=receipt(deleted,account,owner["id"],"delete","",4,200)
    receipt(create(0),account,owner["id"],"create",TITLE,1,201,original)
    receipt(request(target,"PATCH",{"title":"SECRET edited"},[("Idempotency-Key",update_key),("If-Match",'"1"')]),account,owner["id"],"update","SECRET edited",2,200,changed)
    receipt(request(target,"DELETE",headers=[("Idempotency-Key",deleted_key),("If-Match",'"3"')]),account,owner["id"],"delete","",4,200,removed)
    refusal(request(target),404,"notes.not_found")
    assert request()[2]["notes"]==[]
    for saved in [original,changed,removed]:
        result=request("/note-receipts/"+saved["command_id"])
        assert result[0]==200 and result[2]==saved and result[1]["etag"]==f'"{saved["version"]}"'
    for path,method,body,headers in [("/notes","GET",None,[]),("/note-receipts/"+key,"GET",None,[]),("/notes","POST",{"title":TITLE},[("Idempotency-Key",key)])]:
        refusal(call(port,base+path,method,body=body,headers=bearer(other_session["access_token"])+headers),403,"notes.account_forbidden")
    refusal(request("/note-receipts/"+command()),404,"notes.receipt_not_found")
    expected=[]
    for _ in range(5):
        result=request(method="POST",body={"title":"SECRET page"},headers=[("Idempotency-Key",command())])
        assert result[0]==201
        expected.append(result[2]["note_id"])
    seen=[];query="?limit=2"
    while True:
        result=request("/notes"+query)
        assert result[0]==200 and result[2]["account_id"]==account
        assert set(result[2])=={"account_id","notes","next_after"}
        seen += [n["id"] for n in result[2]["notes"]]
        after=result[2]["next_after"]
        if after is None:break
        query="?limit=2&after="+after
    assert seen==sorted(expected)
    assert call(port,foreign+"/notes","GET",headers=bearer(other_session["access_token"]))[2]["notes"]==[]
    assert call(port,"/v1/identity/sign-out",body={"refresh_token":session["refresh_token"]})[0]==204
    refusal(request("/note-receipts/"+key))
    return [TITLE,session["access_token"],session["refresh_token"],other_session["access_token"],other_session["refresh_token"]]


def wire_cases(port):
    _,session,base=setup(port,"secret.notes.wire")
    auth=bearer(session["access_token"])
    cases=json.loads((CORPUS/"http.json").read_text())
    for case in cases:
        key=command()
        baseline=call(port,base+"/notes",body={"title":"Baseline"},headers=auth+[("Idempotency-Key",command())])
        assert baseline[0]==201
        note=baseline[2]["note_id"]
        path=base+case["path"].replace("NOTE",note).replace("COMMAND",key)
        headers=list(auth) if case.get("authenticated",True) else []
        headers += [(name,value.replace("COMMAND",key)) for name,value in case.get("headers",[("Idempotency-Key","COMMAND")])]
        wire=case.get("wire")
        if wire is not None and not any(name.lower()=="content-type" for name,_ in headers):
            headers.append(("Content-Type","application/json"))
        status,response,data=exchange(port,{"method":case["method"],"path":path,"headers":headers,**({"wire_body":wire} if wire is not None else {})})
        assert status==case["status"],f'{case["name"]}: status {status}, expected {case["status"]}'
        assert response.get("cache-control")=="no-store" and response.get("x-content-type-options")=="nosniff"
        assert REQUEST_ID.fullmatch(response.get("x-request-id",""))
        assert response.get("allow")==case.get("allow"),case["name"]
        if case["method"]=="HEAD":
            assert not data and int(response["content-length"])>0
        elif "code" in case:
            value=json.loads(data)
            assert value["code"]==case["code"] and value["request_id"]==response["x-request-id"],case["name"]
    return [session["access_token"],session["refresh_token"]]


def main():
    run(["go","test","./parity","-run","^TestSharedNotes","-count=1"],ROOT/"backend/go")
    run(["cargo","test","--locked","-p","notes"],ROOT/"backend/rust")
    with tempfile.TemporaryDirectory(prefix="mobile-notes-parity-") as temporary:
        owned=Path(temporary);go_binary=owned/"go-api"
        run(["go","build","-o",str(go_binary),"./cmd/server"],ROOT/"backend/go")
        run(["cargo","build","--locked","-p","api-server"],ROOT/"backend/rust")
        for name,binary in [("Go",go_binary),("Rust",ROOT/"backend/rust/target/debug/api-server")]:
            for phase,exercise in [("core",core),("wire",wire_cases)]:
                log=owned/f"{name.lower()}-{phase}.log"
                with owned_server(binary,log,"identity") as port:
                    secrets=exercise(port)
                assert all(secret not in log.read_text() for secret in secrets) and "SECRET" not in log.read_text()
                print(f"{name} Notes {phase}: passed; private logs and clean shutdown",flush=True)
    print("notes-parity: core memory receipts/CAS/scope and 47 shared HTTP cases passed; durable recovery has separate acceptance")


if __name__=="__main__":
    main()
