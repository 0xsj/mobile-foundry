#!/usr/bin/env python3
"""Owned device-registry conformance; no OS tokens or external push service."""
import base64
import json
import os
from pathlib import Path
import tempfile
import uuid
from backend_parity import ROOT, REQUEST_ID, exchange, owned_server, run
from identity_parity import PASSWORD, bearer, call, refusal
from account_parity import identity
from notifications_parity import wait_for
from recovery_parity import capture

CORPUS=ROOT/"contracts/fixtures/backend-installations"
COLLECTION="/v1/installations"

def proof():return "i1."+base64.urlsafe_b64encode(os.urandom(32)).decode().rstrip("=")

def registration():
    return {"installation_id":str(uuid.uuid4()),"registration_id":str(uuid.uuid4()),
            "provider":"apns","application":"dev.example.mobile","environment":"sandbox",
            "token":"PRIVATE-target-"+str(uuid.uuid4())}

def headers(session,key):return bearer(session["access_token"])+[("X-Installation-Key",key)]

def view(result,version=None,registered=True,current=True,status=200):
    actual,h,v=result
    assert actual==status,(actual,v)
    assert set(v)=={"id","registration_id","version","provider","application","environment",
                    "registered","current_binding","created_at","updated_at","expires_at"}
    assert REQUEST_ID.fullmatch(v["id"]) and REQUEST_ID.fullmatch(v["registration_id"])
    if version is not None:assert v["version"]==version
    assert v["registered"] is registered and v["current_binding"] is current
    assert h["etag"]==f'"{v["version"]}"'
    assert all(v[k].endswith("Z") for k in ["created_at","updated_at","expires_at"])
    if status==201:assert h["location"]==COLLECTION+"/"+v["id"]
    return v

def read(port,path,session,key):return call(port,path,"GET",headers=headers(session,key))

def register(port,body,session,key,path=COLLECTION,expected=None):
    return call(port,path,"POST" if expected is None else "PUT",body=body,
                headers=headers(session,key)+([] if expected is None else [("If-Match",f'"{expected}"')]))

def retire_read(port,path,observer,key):
    return wait_for(lambda:read(port,path,observer,key),lambda r:r[0]==200 and not r[2]["registered"],
                    "Identity revocation did not retire its installation")

def core(port,mail):
    _,first=identity(port,"installations.owner");_,other=identity(port,"installations.other")
    secrets=[PASSWORD]
    def remember(s):secrets.extend([s["access_token"],s["refresh_token"]]);return s
    remember(first);remember(other)
    def sign_in(login,password=PASSWORD):
        r=call(port,"/v1/identity/sign-in",body={"login":login,"password":password});assert r[0]==200;return remember(r[2])
    observer=sign_in("installations.owner")
    key=proof();body=registration();secrets += [key,body["token"]]
    path=COLLECTION+"/"+body["installation_id"]
    first_view=view(register(port,body,first,key),1,status=201)
    assert first_view["expires_at"]==first["refresh_expires_at"]
    assert view(read(port,path,first,key),1)==first_view
    view(read(port,path,observer,key),1,current=False)
    refusal(read(port,path,first,proof()),404,"notifications.installation_not_found")
    refusal(register(port,body,first,key),409,"notifications.installation_conflict")
    update={k:v for k,v in body.items() if k!="installation_id"}
    refusal(register(port,update,first,key,path,1),409,"notifications.installation_conflict")
    update["registration_id"]=str(uuid.uuid4());update["token"]="PRIVATE-rotated-"+str(uuid.uuid4());secrets.append(update["token"])
    view(register(port,update,first,key,path,1),2)
    refusal(register(port,update,first,key,path,1),409,"notifications.installation_conflict")
    collision=registration();collision["token"]=update["token"]
    refusal(register(port,collision,first,proof()),409,"notifications.installation_target")
    update["registration_id"]=str(uuid.uuid4())
    rebound=view(register(port,update,other,key,path,2),3)
    assert rebound["created_at"]==first_view["created_at"]
    view(read(port,path,first,key),3,current=False)
    refusal(call(port,path,"DELETE",headers=headers(first,key)+[("If-Match",'"3"')]),403,"notifications.installation_binding")
    # Retiring an old binding must leave the new principal's binding untouched.
    assert call(port,"/v1/identity/sign-out",body={"refresh_token":first["refresh_token"]})[0]==204
    view(read(port,path,other,key),3)
    disabled=view(call(port,path,"DELETE",headers=headers(other,key)+[("If-Match",'"3"')]),4,registered=False)
    assert view(call(port,path,"DELETE",headers=headers(other,key)+[("If-Match",'"1"')]),4,registered=False)==disabled
    update["registration_id"]=str(uuid.uuid4());view(register(port,update,other,key,path,4),5)
    assert call(port,"/v1/identity/sign-out",body={"refresh_token":other["refresh_token"]})[0]==204
    view(retire_read(port,path,observer,key),6,registered=False,current=False)
    refusal(read(port,path,other,key))
    # Renewal keeps the same binding; consumed refresh reuse retires it.
    body=registration();key2=proof();secrets += [body["token"],key2];path2=COLLECTION+"/"+body["installation_id"]
    before=view(register(port,body,observer,key2),1,status=201)
    renewal=call(port,"/v1/identity/renew",body={"refresh_token":observer["refresh_token"]});assert renewal[0]==200;renewed=remember(renewal[2])
    assert renewed["session_id"]==observer["session_id"]
    assert view(read(port,path2,renewed,key2),1)==before
    refusal(call(port,"/v1/identity/renew",body={"refresh_token":observer["refresh_token"]}))
    observer=sign_in("installations.other")
    view(retire_read(port,path2,observer,key2),2,registered=False,current=False)
    # Both v2 password reasons travel through the real consumer subscription.
    owner=sign_in("installations.owner");body=registration();key3=proof();secrets += [body["token"],key3]
    path3=COLLECTION+"/"+body["installation_id"];view(register(port,body,owner,key3),1,status=201)
    address="installation@example.test";secrets.append(address)
    assert call(port,"/v1/identity/email/request",body={"email":address,"password":PASSWORD},headers=bearer(owner["access_token"]))[0]==204
    code=mail(address,"verify_email")
    assert call(port,"/v1/identity/email/verify",body={"token":code},headers=bearer(owner["access_token"]))[0]==204
    new_password="PRIVATE-installation-new-password-123";secrets.append(new_password)
    assert call(port,"/v1/identity/password/change",body={"current_password":PASSWORD,"new_password":new_password},headers=bearer(owner["access_token"]))[0]==204
    view(retire_read(port,path3,observer,key3),2,registered=False,current=False)
    owner=sign_in("installations.owner",new_password);body=registration();key4=proof();secrets += [body["token"],key4]
    path4=COLLECTION+"/"+body["installation_id"];view(register(port,body,owner,key4),1,status=201)
    assert call(port,"/v1/identity/password/request-reset",body={"email":address})[0]==202
    code=mail(address,"reset_password")
    assert call(port,"/v1/identity/password/reset",body={"token":code,"password":PASSWORD})[0]==204
    view(retire_read(port,path4,observer,key4),2,registered=False,current=False)
    return secrets

def wire_cases(port):
    _,session=identity(port,"installations.wire");key=json.loads((CORPUS/"proofs.json").read_text())[0]["raw"]
    item=registration();view(register(port,item,session,key),1,status=201)
    for case in json.loads((CORPUS/"http.json").read_text()):
        body=registration();update={k:v for k,v in body.items() if k!="installation_id"}
        bodies={"CREATE":body,"CREATE_BAD_ID":body|{"installation_id":"bad"},"CREATE_BAD_ROUTE":body|{"provider":"other"},"UPDATE":update,"UPDATE_WITH_ID":body}
        payload=case.get("body");wire=case.get("wire_body")
        if payload=="CREATE_ORDERED":wire=json.dumps(body)[:-1]+',"TOKEN":"PRIVATE-final-token","token":null}'
        elif isinstance(payload,str):wire=json.dumps(bodies[payload])
        elif "body" in case:wire=json.dumps(payload)
        h=bearer(session["access_token"]) if case.get("authenticated",True) else []
        selected=case.get("proof",key)
        if selected is not None:h.append(("X-Installation-Key",selected))
        h += [tuple(v) for v in case.get("headers",[])]
        if wire is not None and not case.get("no_content_type"):h.append(("Content-Type","application/json"))
        status,response,data=exchange(port,{"method":case["method"],"path":case["path"].replace("ITEM",item["installation_id"]),"headers":h,**({"wire_body":wire} if wire is not None else {})})
        assert status==case["status"],(case["name"],status,data)
        assert response.get("cache-control")=="no-store" and response.get("x-content-type-options")=="nosniff"
        if case["method"]=="HEAD":assert not data;continue
        value=json.loads(data)
        if "code" in case:assert value["code"]==case["code"],(case["name"],value)
        if status>=400:assert value["request_id"]==response["x-request-id"]
        else:assert "token" not in value and "proof_digest" not in value and "principal_id" not in value and "session_id" not in value
    return [session["access_token"],session["refresh_token"],key,item["token"],"PRIVATE-final-token"]

def durable(rust,go,owned,url,execute):
    """Immutable registry rows/markers and producer retries cross language boundaries."""
    keys=[proof(),proof()];bodies=[registration(),registration()]
    paths=[COLLECTION+"/"+b["installation_id"] for b in bodies]
    secrets=[url,PASSWORD]+keys+[b["token"] for b in bodies]
    def remember(s):secrets.extend([s["access_token"],s["refresh_token"]]);return s
    def pending(session):
        source=execute("SELECT event_id FROM identity.outbox WHERE stream_id='"+session["session_id"]+"' AND envelope->>'name' LIKE 'identity.session.revoked.%'")
        assert REQUEST_ID.fullmatch(source),"revocation was not committed with Identity"
        assert execute("SELECT dead FROM identity.outbox WHERE event_id='"+source+"'")=="f","consumer exhausted before crash"
        assert execute("SELECT count(*) FROM notifications.installation_inbox WHERE event_id='"+source+"'")=="0"
        return source
    with owned_server(rust,owned/"installations-rust-killed.log","identity",url,crash=True) as port:
        _,first=identity(port,"installations.durable.owner");remember(first)
        _,next_session=identity(port,"installations.durable.next");remember(next_session)
        for body,key in zip(bodies,keys):view(register(port,body,first,key),1,status=201)
        execute("ALTER TABLE notifications.installation_inbox ADD CONSTRAINT pause_installation_delivery CHECK(false) NOT VALID")
        assert call(port,"/v1/identity/sign-out",body={"refresh_token":first["refresh_token"]})[0]==204
    old_event=pending(first)
    # The old fact remains blocked while Go explicitly rebinds one installation.
    with owned_server(go,owned/"installations-go-killed.log","identity",url,crash=True) as port:
        view(read(port,paths[0],next_session,keys[0]),1,current=False)
        update={k:v for k,v in bodies[1].items() if k!="installation_id"};update["registration_id"]=str(uuid.uuid4())
        update["token"]="PRIVATE-go-rotation-"+str(uuid.uuid4());secrets.append(update["token"])
        rebound=view(register(port,update,next_session,keys[1],paths[1],1),2)
        execute("ALTER TABLE notifications.installation_inbox DROP CONSTRAINT pause_installation_delivery")
        view(retire_read(port,paths[0],next_session,keys[0]),2,registered=False,current=False)
        assert view(read(port,paths[1],next_session,keys[1]),2)==rebound
        wait_for(lambda:execute("SELECT delivered_at IS NOT NULL FROM identity.outbox WHERE event_id='"+old_event+"'"),lambda v:v=="t","old revocation did not settle")
        # Independent observer survives the next principal's password mutation.
        _,observer=identity(port,"installations.durable.observer");remember(observer)
        execute("ALTER TABLE notifications.installation_inbox ADD CONSTRAINT pause_installation_delivery CHECK(false) NOT VALID")
        password="PRIVATE-durable-installation-password-123";secrets.append(password)
        assert call(port,"/v1/identity/password/change",body={"current_password":PASSWORD,"new_password":password},headers=bearer(next_session["access_token"]))[0]==204
    new_event=pending(next_session)
    execute("ALTER TABLE notifications.installation_inbox DROP CONSTRAINT pause_installation_delivery")
    # Simulate lost acknowledgement of the old Go-applied source as well.
    execute("UPDATE identity.outbox SET delivered_at=NULL,lease_owner=NULL,lease_until=NULL WHERE event_id='"+old_event+"'")
    with owned_server(rust,owned/"installations-rust-handoff.log","identity",url) as port:
        view(retire_read(port,paths[1],observer,keys[1]),3,registered=False,current=False)
        for source in [old_event,new_event]:
            wait_for(lambda:execute("SELECT delivered_at IS NOT NULL FROM identity.outbox WHERE event_id='"+source+"'"),lambda v:v=="t","cross-language revocation fingerprint replay failed")
            assert execute("SELECT count(*) FROM notifications.installation_inbox WHERE event_id='"+source+"'")=="1"
        update["registration_id"]=str(uuid.uuid4());update["token"]="PRIVATE-rust-rotation-"+str(uuid.uuid4());secrets.append(update["token"])
        continued=view(register(port,update,observer,keys[1],paths[1],3),4)
        execute("UPDATE identity.outbox SET delivered_at=NULL,lease_owner=NULL,lease_until=NULL WHERE event_id='"+new_event+"'")
        wait_for(lambda:execute("SELECT delivered_at IS NOT NULL FROM identity.outbox WHERE event_id='"+new_event+"'"),lambda v:v=="t","v2 replay failed")
        assert view(read(port,paths[1],observer,keys[1]),4)==continued
    with owned_server(go,owned/"installations-go-final.log","identity",url) as port:
        assert view(read(port,paths[1],observer,keys[1]),4)==continued
        view(read(port,paths[0],observer,keys[0]),2,registered=False,current=False)
    for log in owned.glob("installations-*.log"):
        assert all(s not in log.read_text() for s in secrets),"private installation input leaked"
    print("Installations PostgreSQL: pending v1/v2 revocation after SIGKILL, Go↔Rust proof/target/marker handoff and late-event rebinding safety passed",flush=True)

def main():
    run(["go","test","./parity","-run","^TestSharedInstallations$","-count=1"],ROOT/"backend/go")
    run(["cargo","test","--locked","-p","notifications"],ROOT/"backend/rust")
    with tempfile.TemporaryDirectory(prefix="mobile-installations-parity-") as temporary:
        owned=Path(temporary);go=owned/"go-api"
        run(["go","build","-o",str(go),"./cmd/server"],ROOT/"backend/go")
        run(["cargo","build","--locked","-p","api-server"],ROOT/"backend/rust")
        for name,binary in [("Go",go),("Rust",ROOT/"backend/rust/target/debug/api-server")]:
            with capture(owned) as (config,receive,tokens):
                with owned_server(binary,owned/(name+"-core.log"),"identity",mail_config=config) as port:secrets=core(port,receive)
            assert all(s not in (owned/(name+"-core.log")).read_text() for s in secrets+tokens),"private installation input leaked"
            with owned_server(binary,owned/(name+"-wire.log"),"identity") as port:secrets=wire_cases(port)
            assert all(s not in (owned/(name+"-wire.log")).read_text() for s in secrets),"private installation wire input leaked"
            print(name+": installation checkpoints/proofs, rebinding/rotation, four revocation reasons and 42 HTTP cases passed",flush=True)

if __name__=="__main__":main()
