#!/usr/bin/env python3
"""Shared job rules and owned API/worker selection. No cloud sends or host keys."""
import json
from pathlib import Path
import subprocess
import tempfile
import time
import uuid
from backend_parity import ROOT,owned_server,run
from identity_parity import call,bearer,refusal,PASSWORD
from account_parity import identity
from installations_parity import proof,registration,register

CORPUS=ROOT/"contracts/fixtures/backend-push-jobs"

def job(result,status=200):
    actual,headers,value=result
    assert actual==status,(actual,value)
    assert set(value)=={"job_id","status","attempts","result_code","retired","created_at","updated_at","expires_at"}
    assert "etag" not in headers and headers["cache-control"]=="no-store"
    return value

def wait_job(port,path,headers,want):
    deadline=time.monotonic()+8
    while True:
        value=job(call(port,path,"GET",headers=headers))
        if value["status"]==want:return value
        assert time.monotonic()<deadline,"job did not reach expected state"
        time.sleep(.02)

def cases(port,path,session,key,source):
    _,foreign=identity(port,"jobs.foreign."+str(uuid.uuid4()))
    child=str(uuid.uuid4());write=bearer(session["access_token"])+[("X-Installation-Key",key),("If-Match",'"1"')]
    for case in json.loads((CORPUS/"http.json").read_text()):
        headers=list(write)
        if case.get("auth")=="anonymous":headers=[(k,v) for k,v in headers if k!="Authorization"]
        if case.get("auth")=="foreign":headers=[(k,v) for k,v in headers if k!="Authorization"]+bearer(foreign["access_token"])
        if case.get("proof")=="missing":headers=[(k,v) for k,v in headers if k!="X-Installation-Key"]
        if case.get("proof")=="wrong":headers=[(k,v) for k,v in headers if k!="X-Installation-Key"]+[("X-Installation-Key",proof())]
        if "if_match" in case:
            headers=[(k,v) for k,v in headers if k!="If-Match"]
            if case["if_match"] is not None:headers.append(("If-Match",case["if_match"]))
        headers.extend((k,v.replace("$PROOF$",key)) for k,v in case.get("headers",[]))
        wire=case.get("body",'{"job_id":"$CHILD$","accept_possible_duplicate":true}').replace("$CHILD$",child).replace("$SOURCE$",source)
        result=call(port,path+case.get("query",""),case.get("method","POST"),wire=wire,headers=headers)
        assert result[0]==case["status"],(case["name"],result[0],result[2])
        if result[0]==405:assert result[1]["allow"]=="GET, HEAD, POST"
    assert call(port,path,"GET",headers=write[:-1])[0]==404

def durable(owned,url,go_binary,rust_binary):
    """Called only by the disposable PostgreSQL owner, after explicit migrations."""
    subprocess.run(["openssl","genpkey","-algorithm","EC","-pkeyopt","ec_paramgen_curve:P-256","-out",str(owned/"jobs-apns.p8")],check=True,capture_output=True)
    config=owned/"jobs-push.json";config.write_text(json.dumps({"routes":[{"provider":"apns","application":"dev.example.mobile","environment":"sandbox","team_id":"TEAMID0001","key_id":"KEYID00001","key_file":"jobs-apns.p8"}]}))
    key=proof();source=str(uuid.uuid4());child=str(uuid.uuid4())
    with owned_server(rust_binary,owned/"jobs-rust-first.log","identity",url,push_config=config,push_jobs=True) as port:
        _,session=identity(port,"jobs.runtime."+str(uuid.uuid4()));input=registration()
        # Valid registry address, deliberately unsupported APNs encoding: the adapter
        # refuses it before network. Post-marker errors still become uncertain.
        input["token"]="PRIVATE-not-hex-"+str(uuid.uuid4());assert register(port,input,session,key)[0]==201
        base="/v1/installations/"+input["installation_id"]+"/push-jobs";read=bearer(session["access_token"])+[("X-Installation-Key",key)];write=read+[("If-Match",'"1"')]
        job(call(port,base,body={"job_id":source},headers=write),202)
        saved=wait_job(port,base+"/"+source,read,"uncertain");assert saved["attempts"]==1 and saved["result_code"]=="provider_unconfirmed"
        cases(port,base+"/"+source+"/replacement",session,key,source)
        assert job(call(port,base,body={"job_id":source},headers=write),202)==saved
        assert call(port,base+"/"+source+"/cancel",headers=read)[0]==409
        assert call(port,base+"/"+source+"/cancel",wire="{}",headers=read)[0]==400
    with owned_server(go_binary,owned/"jobs-go-handoff.log","identity",url,push_config=config,push_jobs=True) as port:
        assert job(call(port,base+"/"+source,"GET",headers=read))==saved
        cases(port,base+"/"+source+"/replacement",session,key,source)
        replacement={"job_id":child,"accept_possible_duplicate":True}
        for _ in range(2):job(call(port,base+"/"+source+"/replacement",body=replacement,headers=write),202)
        saved_child=wait_job(port,base+"/"+child,read,"uncertain");assert saved_child["attempts"]==1
        assert job(call(port,base+"/"+source+"/replacement","GET",headers=read))==saved_child
    with owned_server(rust_binary,owned/"jobs-rust-return.log","identity",url,push_config=config,push_jobs=True) as port:
        assert job(call(port,base+"/"+source,"GET",headers=read))==saved
        assert job(call(port,base+"/"+source+"/replacement","GET",headers=read))==saved_child
        assert job(call(port,base+"/"+source+"/replacement",body=replacement,headers=write),202)==saved_child
        assert call(port,base,body={"job_id":child},headers=write)[0]==409
        assert call(port,base+"/"+source+"/replacement",body={"job_id":str(uuid.uuid4()),"accept_possible_duplicate":True},headers=write)[0]==409
        assert call(port,base+"/"+child,"HEAD",headers=read)[0]==200
    for name in ["jobs-rust-first.log","jobs-go-handoff.log","jobs-rust-return.log"]:
        text=(owned/name).read_text()
        for secret in [PASSWORD,key,session["access_token"],session["refresh_token"],input["token"],str(config)]:assert secret not in text,"private job diagnostic leaked"
    print("Jobs PostgreSQL APIs: real workers, shared HTTP guards, no automatic resend and Rust→Go→Rust outcome/lineage handoff passed; no cloud request",flush=True)

def main():
    run(["go","test","-race","./parity","-run","^TestSharedJobTransitions$","-count=1"],ROOT/"backend/go")
    run(["go","test","-race","./tests","-run","^TestPushJob","-count=1"],ROOT/"backend/go")
    run(["cargo","test","--locked","-p","jobs"],ROOT/"backend/rust")
    run(["cargo","test","--locked","-p","notifications","--test","job_transitions","--test","jobs","--test","job_execution"],ROOT/"backend/rust")
    with tempfile.TemporaryDirectory(prefix="mobile-jobs-parity-") as temporary:
        owned=Path(temporary);go=owned/"go-api";run(["go","build","-o",str(go),"./cmd/server"],ROOT/"backend/go");run(["cargo","build","--locked","-p","api-server"],ROOT/"backend/rust")
        for name,binary in [("Go",go),("Rust",ROOT/"backend/rust/target/debug/api-server")]:
            with owned_server(binary,owned/(name+".log"),"identity") as port:
                refusal(call(port,"/v1/installations/"+str(uuid.uuid4())+"/push-jobs",body={"job_id":str(uuid.uuid4())}),404,"http.route_not_found")
    print("Jobs: 57 shared transitions, memory execution/fencing/recovery and default route absence passed; run backend-postgres-parity-test for durability",flush=True)

if __name__=="__main__":main()
