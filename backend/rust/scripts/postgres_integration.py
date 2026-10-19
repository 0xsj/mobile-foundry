#!/usr/bin/env python3
"""Self-contained Rust durability tests, using only a newly owned disposable database."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import uuid
from smoke import ROOT, PACKAGE, PASSWORD, environment, identity, request, server
from postgres_owner import docker_database, local_database

def main():
    mode=os.environ.get("POSTGRES_TEST_MODE","docker")
    if mode not in {"docker","local"}:raise ValueError("POSTGRES_TEST_MODE must be docker or local")
    subprocess.run(["cargo","build","--locked","--workspace"],cwd=ROOT,check=True)
    with (local_database if mode=="local" else docker_database)() as (url,psql), tempfile.TemporaryDirectory(prefix="rust-pg-api-") as temporary:
        folder=Path(temporary);objects=folder/"objects";binary=ROOT/"target/debug/api-server"
        env=environment(STORAGE="postgres",DATABASE_URL=url,FILES_DIRECTORY=str(objects),TEST_POSTGRES_URL=url,TEST_POSTGRES_DISPOSABLE="1")
        refused=subprocess.run([str(binary)],env=env,capture_output=True,text=True,timeout=20,cwd=ROOT)
        assert refused.returncode==1 and url not in refused.stderr,"unmigrated API was not safely refused"
        for test,filters in [("postgres",[]),("jobs",["postgres_jobs"])]:
            subprocess.run(["cargo","test","--locked","-p",PACKAGE,"--test",test,*filters,"--","--ignored","--nocapture"],cwd=ROOT,env=env,check=True)
        subprocess.run([str(ROOT/"target/debug/db-migrate"),"--check"],cwd=ROOT,env=env,check=True)
        hidden=[PASSWORD,url,"PRIVATE"]
        command=str(uuid.uuid4());input={"title":"PRIVATE restart 🧱\u0000 note"}
        with server(binary,url,objects,crash=True,forbidden=hidden) as base:
            grant=identity(base,"durable."+uuid.uuid4().hex);hidden += [grant["access_token"],grant["refresh_token"]]
            token=grant["access_token"]
            status,_,wire=request(base,"POST","/v1/accounts/bootstrap",{"name":"PRIVATE durable"},token);assert status==201
            path="/v1/accounts/"+json.loads(wire)["id"]
            status,_,wire=request(base,"POST",path+"/notes",input,token,{"Idempotency-Key":command});assert status==201
            receipt=json.loads(wire)
            data=b"private durable bytes\x00\xff"
            import hashlib
            spec={"name":"PRIVATE.bin","content_type":"application/octet-stream","byte_size":len(data),"sha256":hashlib.sha256(data).hexdigest()}
            status,_,wire=request(base,"POST",path+"/files/uploads",spec,token,{"Idempotency-Key":"durable.upload"});assert status==201
            file=path+"/files/"+json.loads(wire)["file"]["id"]
            assert request(base,"PUT",file+"/content",data,token,{"Content-Type":"application/octet-stream"})[0]==200
        def sql(text):subprocess.run(psql+["-X","-v","ON_ERROR_STOP=1","-c",text],check=True,stdout=subprocess.DEVNULL)
        sql("ALTER TABLE notifications.inbox ADD CONSTRAINT starter_pause CHECK(false) NOT VALID")
        with server(binary,url,objects,crash=True,forbidden=hidden) as base:
            assert json.loads(request(base,"GET",path+"/note-receipts/"+command,token=token)[2])==receipt
            assert request(base,"POST",file+"/complete",token=token,headers={"If-Match":'"1"',"Idempotency-Key":"durable.complete"})[0]==200
        sql("ALTER TABLE notifications.inbox DROP CONSTRAINT starter_pause")
        with server(binary,url,objects,forbidden=hidden) as base:
            assert json.loads(request(base,"POST",path+"/notes",input,token,{"Idempotency-Key":command})[2])==receipt
            assert len(json.loads(request(base,"GET",path+"/notes",token=token)[2])["notes"])==1
            assert request(base,"GET",file+"/content",token=token)[2]==data
            deadline=time.monotonic()+8
            while True:
                page=json.loads(request(base,"GET",path+"/notifications",token=token)[2])
                if page["items"]:break
                assert time.monotonic()<deadline,"pending delivery did not recover";time.sleep(.02)
            assert len(page["items"])==1 and page["unread_count"]==1
            item=path+"/notifications/"+page["items"][0]["id"]+"/read"
            assert request(base,"POST",item,token=token)[0]==200
            assert request(base,"POST","/v1/identity/sign-out",{"refresh_token":grant["refresh_token"]})[0]==204
        with server(binary,url,objects,forbidden=hidden) as base:
            assert request(base,"GET",path,token=token)[0]==401
            fresh=identity(base,"observer."+uuid.uuid4().hex)
            assert request(base,"GET",path,token=fresh["access_token"])[0]==403
        print("Rust PostgreSQL: schema refusal, copied store/jobs rollback and SIGKILL checks, retained Notes/bytes/inbox and sign-out passed")

if __name__=="__main__":main()
