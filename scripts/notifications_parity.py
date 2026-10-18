#!/usr/bin/env python3
"""Owned Go/Rust in-app Notifications conformance, independent of external push."""
from pathlib import Path
import json
import tempfile
import time
import uuid
from backend_parity import ROOT, REQUEST_ID, exchange, owned_server, run
from identity_parity import call, bearer, refusal
from notes_parity import setup
from files_parity import spec, binary

CORPUS=ROOT/"contracts/fixtures/backend-notifications"

def wait_for(check, predicate, message):
    deadline=time.monotonic()+8
    while True:
        value=check()
        if predicate(value):return value
        assert time.monotonic()<deadline,message
        time.sleep(.025)

def inbox(port,base,auth,query=""):
    result=call(port,base+"/notifications"+query,"GET",headers=auth)
    assert result[0]==200,result
    page=result[2]
    assert set(page)=={"account_id","items","unread_count","next_cursor"}
    assert page["account_id"]==base.split("/")[-1]
    assert isinstance(page["items"],list) and isinstance(page["unread_count"],int)
    for i in page["items"]:
        assert set(i)=={"id","file_id","type","category","occurred_at","created_at","read_at"}
        assert REQUEST_ID.fullmatch(i["id"]) and REQUEST_ID.fullmatch(i["file_id"])
        assert i["type"]=="files.upload_ready" and i["category"]=="media"
        assert i["occurred_at"].endswith("Z") and i["created_at"].endswith("Z")
        assert i["read_at"] is None or i["read_at"].endswith("Z")
    return page

def preferences(port,base,auth,enabled=None):
    result=call(port,base+"/notification-preferences","GET" if enabled is None else "PUT",
                body=None if enabled is None else {"media_in_app":enabled},headers=auth)
    assert result[0]==200,result
    assert set(result[2])=={"account_id","media_in_app"}
    assert result[2]["account_id"]==base.split("/")[-1]
    if enabled is not None:assert result[2]["media_in_app"] is enabled
    return result[2]

def upload(port,base,auth,key=None):
    data=b"notice"
    result=call(port,base+"/files/uploads",body=spec(data,"SECRET notice.png"),
                headers=auth+([] if key is None else [("Idempotency-Key",key)]))
    assert result[0]==201,result
    file=result[2]["file"]
    path=base+"/files/"+file["id"]
    assert binary(port,path+"/content","PUT",auth,data,[("Content-Type","image/png")])[0]==200
    complete=call(port,path+"/complete",headers=auth+[("If-Match",'"1"'),("Idempotency-Key",str(uuid.uuid4()))])
    assert complete[0]==200,complete
    return path,file["id"]

def settled_deletion(port,base,path,file_id,auth):
    """A later same-file event is delivered only after completion's consumers finish."""
    result=call(port,path,"DELETE",headers=auth+[("If-Match",'"2"')])
    assert result[0]==200
    wait_for(lambda:call(port,base+"/audit","GET",headers=auth),
             lambda r:r[0]==200 and any(e.get("file_id")==file_id and e["action"]=="files.deleted" for e in r[2]["entries"]),
             "same-stream deletion did not establish the completed delivery boundary")

def core(port):
    owner,session,base=setup(port,"notifications.owner")
    other,other_session,foreign=setup(port,"notifications.other")
    auth=bearer(session["access_token"]);other_auth=bearer(other_session["access_token"])
    assert inbox(port,base,auth)=={"account_id":base.split("/")[-1],"items":[],"unread_count":0,"next_cursor":None}
    assert preferences(port,base,auth)["media_in_app"] is True
    path,file_id=upload(port,base,auth)
    page=wait_for(lambda:inbox(port,base,auth),lambda p:len(p["items"])==1,"completion did not reach inbox")
    first=page["items"][0];assert first["file_id"]==file_id and first["read_at"] is None and page["unread_count"]==1
    read_path=base+"/notifications/"+first["id"]+"/read"
    read=call(port,read_path,headers=auth);assert read[0]==200 and read[2]["read_at"] is not None
    assert call(port,read_path,headers=auth)[2]==read[2]
    assert inbox(port,base,auth)["unread_count"]==0
    assert inbox(port,base,auth,"?unread=true")["items"]==[]
    for route,method,body in [("/notifications","GET",None),("/notification-preferences","GET",None),
                              ("/notification-preferences","PUT",{"media_in_app":False}),
                              ("/notifications/"+first["id"]+"/read","POST",None)]:
        refusal(call(port,base+route,method,body=body,headers=other_auth),403,"notifications.account_forbidden")
    assert inbox(port,foreign,other_auth)["items"]==[]
    refusal(call(port,foreign+"/notifications/"+first["id"]+"/read",headers=other_auth),404,"notifications.not_found")
    preferences(port,base,auth,False)
    muted_path,muted_id=upload(port,base,auth)
    settled_deletion(port,base,muted_path,muted_id,auth)
    assert len(inbox(port,base,auth)["items"])==1
    preferences(port,base,auth,True)
    assert len(inbox(port,base,auth)["items"])==1
    new_path,new_id=upload(port,base,auth)
    page=wait_for(lambda:inbox(port,base,auth),lambda p:len(p["items"])==2,"unmuted upload missing")
    assert page["items"][0]["file_id"]==new_id and page["unread_count"]==1
    # Account preferences and inboxes are independent even for the same principal.
    second=call(port,"/v1/accounts",body={"name":"SECRET second inbox"},headers=auth)
    assert second[0]==201
    second_base="/v1/accounts/"+second[2]["id"]
    preferences(port,base,auth,False)
    assert preferences(port,second_base,auth)["media_in_app"] is True
    _,second_id=upload(port,second_base,auth)
    assert wait_for(lambda:inbox(port,second_base,auth),lambda p:len(p["items"])==1,"second account missing")["items"][0]["file_id"]==second_id
    preferences(port,base,auth,True)
    known=[new_id,file_id]
    for _ in range(4):
        _,fid=upload(port,base,auth)
        known.insert(0,fid)
        wait_for(lambda:inbox(port,base,auth),lambda p:len(p["items"])==len(known),"arrival missing")
    seen=[];query="?limit=2"
    while True:
        page=inbox(port,base,auth,query)
        seen += [i["file_id"] for i in page["items"]]
        assert page["unread_count"]==5
        cursor=page["next_cursor"]
        if cursor is None:break
        refusal(call(port,second_base+"/notifications?cursor="+cursor,"GET",headers=auth),400,"notifications.invalid_input")
        refusal(call(port,base+"/notifications?unread=true&cursor="+cursor,"GET",headers=auth),400,"notifications.invalid_input")
        query="?limit=2&cursor="+cursor
    assert seen==known
    assert call(port,base+"/notifications?unread=true","HEAD",headers=auth)[0]==200
    assert call(port,base+"/notification-preferences","HEAD",headers=auth)[0]==200
    assert call(port,"/v1/identity/sign-out",body={"refresh_token":session["refresh_token"]})[0]==204
    refusal(call(port,base+"/notifications","GET",headers=auth))
    return [session["access_token"],session["refresh_token"],other_session["access_token"],other_session["refresh_token"],"SECRET notice.png"]

def wire_cases(port):
    _,session,base=setup(port,"notifications.wire");auth=bearer(session["access_token"])
    for case in json.loads((CORPUS/"http.json").read_text()):
        path=base+case["path"].replace("ITEM",str(uuid.uuid4()))
        headers=list(auth) if case.get("authenticated",True) else []
        headers += [tuple(h) for h in case.get("headers",[])]
        wire=case.get("wire")
        if "body" in case:wire=json.dumps(case["body"])
        if wire is not None and not case.get("no_content_type"):headers.append(("Content-Type","application/json"))
        status,response,data=exchange(port,{"method":case["method"],"path":path,"headers":headers,
                                           **({"wire_body":wire} if wire is not None else {})})
        assert status==case["status"],(case["name"],status,data)
        assert response.get("cache-control")=="no-store" and response.get("x-content-type-options")=="nosniff"
        if case["method"]=="HEAD":assert not data;continue
        value=json.loads(data)
        if "code" in case:assert value["code"]==case["code"],(case["name"],value)
        if status>=400:assert value["request_id"]==response["x-request-id"]
    return [session["access_token"],session["refresh_token"]]

def durable(rust_binary,go_binary,owned,url,execute):
    # A failed independent consumer leaves the Files producer's committed event pending.
    with owned_server(rust_binary,owned/"notices-rust-killed.log","identity",url,crash=True) as port:
        _,session,base=setup(port,"notifications.durable");auth=bearer(session["access_token"])
        preferences(port,base,auth,False)
        execute("ALTER TABLE notifications.inbox ADD CONSTRAINT pause_notice_delivery CHECK(false) NOT VALID")
        _,muted_id=upload(port,base,auth)
    account=base.split("/")[-1]
    source=execute("SELECT event_id FROM files.outbox WHERE stream_id='"+muted_id+"' AND aggregate_version=2")
    assert source and execute("SELECT count(*) FROM notifications.inbox WHERE event_id='"+source+"'")=="0"
    assert execute("SELECT dead FROM files.outbox WHERE event_id='"+source+"'")=="f","test consumer exhausted before crash"
    execute("ALTER TABLE notifications.inbox DROP CONSTRAINT pause_notice_delivery")
    with owned_server(go_binary,owned/"notices-go-handoff.log","identity",url) as port:
        wait_for(lambda:execute("SELECT count(*) FROM notifications.inbox WHERE event_id='"+source+"'"),lambda n:n=="1","pending source did not recover")
        assert inbox(port,base,auth)["items"]==[]
        preferences(port,base,auth,True)
        execute("UPDATE files.outbox SET delivered_at=NULL,lease_owner=NULL,lease_until=NULL WHERE event_id='"+source+"'")
        wait_for(lambda:execute("SELECT delivered_at IS NOT NULL FROM files.outbox WHERE event_id='"+source+"'"),lambda v:v=="t","suppressed replay did not settle")
        assert inbox(port,base,auth)["items"]==[]
        _,go_file=upload(port,base,auth)
        page=wait_for(lambda:inbox(port,base,auth),lambda p:len(p["items"])==1,"Go notice missing")
        item=page["items"][0]
        saved_read=call(port,base+"/notifications/"+item["id"]+"/read",headers=auth)[2]
        preferences(port,base,auth,False)
        saved=inbox(port,base,auth)
    execute("UPDATE files.outbox SET delivered_at=NULL,lease_owner=NULL,lease_until=NULL WHERE stream_id IN ('"+muted_id+"','"+go_file+"')")
    with owned_server(rust_binary,owned/"notices-rust-handoff.log","identity",url) as port:
        assert preferences(port,base,auth)["media_in_app"] is False
        wait_for(lambda:execute("SELECT count(*) FROM files.outbox WHERE stream_id IN ('"+muted_id+"','"+go_file+"') AND delivered_at IS NULL"),lambda n:n=="0","cross-language source fingerprint replay failed")
        assert inbox(port,base,auth)==saved
        assert call(port,base+"/notifications/"+item["id"]+"/read",headers=auth)[2]==saved_read
        preferences(port,base,auth,True)
        _,rust_file=upload(port,base,auth)
        page=wait_for(lambda:inbox(port,base,auth),lambda p:len(p["items"])==2,"Rust notice missing")
        assert page["items"][0]["file_id"]==rust_file
        final=call(port,base+"/notifications/"+page["items"][0]["id"]+"/read",headers=auth)
        assert final[0]==200
        saved_final=inbox(port,base,auth)
    with owned_server(go_binary,owned/"notices-go-final.log","identity",url) as port:
        assert preferences(port,base,auth)["media_in_app"] is True
        assert inbox(port,base,auth)==saved_final
        assert inbox(port,base,auth)["unread_count"]==0
    for log in owned.glob("notices-*.log"):
        assert all(s not in log.read_text() for s in [session["access_token"],session["refresh_token"],"SECRET notice.png",url]),"private Notifications input leaked"
    print("Notifications PostgreSQL: killed pending delivery, retained suppression/read/preferences and Go↔Rust inbox fingerprints passed",flush=True)

def main():
    run(["go","test","./parity","-run","TestSharedNotifications","-count=1"],ROOT/"backend/go")
    run(["cargo","test","--locked","-p","notifications"],ROOT/"backend/rust")
    with tempfile.TemporaryDirectory(prefix="mobile-notifications-parity-") as temporary:
        owned=Path(temporary);go=owned/"go-api"
        run(["go","build","-o",str(go),"./cmd/server"],ROOT/"backend/go")
        run(["cargo","build","--locked","-p","api-server"],ROOT/"backend/rust")
        rust=ROOT/"backend/rust/target/debug/api-server"
        for name,binary_path in [("Go",go),("Rust",rust)]:
            for phase,check in [("core",core),("wire",wire_cases)]:
                log=owned/(name+"-"+phase+".log")
                with owned_server(binary_path,log,"identity") as port:secrets=check(port)
                assert all(s not in log.read_text() for s in secrets),"private Notifications input leaked"
            print(name+": Notifications Files delivery, read/preferences, scope/paging and 43 HTTP cases passed",flush=True)

if __name__=="__main__":main()

