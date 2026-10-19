#!/usr/bin/env python3
"""Self-contained, owned memory API acceptance; no running development service is used."""
from contextlib import contextmanager
import hashlib
import http.client
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import tempfile
import time
import tomllib
import uuid

ROOT=Path(__file__).resolve().parents[1]
PACKAGE=tomllib.loads((ROOT/"cmd/server/Cargo.toml").read_text())["package"]["name"]
PASSWORD="private starter acceptance passphrase"

def request(base,method,path,body=None,token=None,headers=None):
    from urllib.parse import urlsplit
    address=urlsplit(base)
    fields=dict(headers or {})
    if token:fields["Authorization"]="Bearer "+token
    if body is not None and not isinstance(body,bytes):fields["Content-Type"]="application/json";body=json.dumps(body).encode()
    connection=http.client.HTTPConnection(address.hostname,address.port,timeout=17)
    try:
        connection.request(method,path,body=body,headers=fields)
        response=connection.getresponse()
        return response.status,dict(response.getheaders()),response.read()
    finally:connection.close()

def environment(**settings):
    env={k:os.environ[k] for k in ("PATH","HOME","TMPDIR","SystemRoot") if k in os.environ}
    env.update(APP_ENV="test",AUTH_MODE="identity",STORAGE="memory",LOG_FORMAT="json")
    env.update(settings);return env

@contextmanager
def server(binary,database_url=None,files_directory=None,crash=False,forbidden=()):
    with socket.socket() as reservation:reservation.bind(("127.0.0.1",0));port=reservation.getsockname()[1]
    env=environment(HTTP_ADDR=f"127.0.0.1:{port}")
    if database_url:
        assert files_directory is not None,"owned durable API needs its private object directory"
        env.update(STORAGE="postgres",DATABASE_URL=database_url,FILES_DIRECTORY=str(files_directory))
    with tempfile.TemporaryFile(mode="w+") as logs:
        process=subprocess.Popen([str(binary)],cwd=ROOT,env=env,stdout=logs,stderr=logs)
        base=f"http://127.0.0.1:{port}"
        try:
            deadline=time.monotonic()+25
            while True:
                assert process.poll() is None,"owned API refused startup"
                try:
                    if request(base,"GET","/v1/health/ready")[0]==200:break
                except (OSError,http.client.HTTPException):pass
                assert time.monotonic()<deadline,"owned readiness timed out"
                time.sleep(.03)
            yield base
            process.send_signal(signal.SIGKILL if crash else signal.SIGTERM)
            assert process.wait(timeout=15)==(-signal.SIGKILL if crash else 0),"unclean owned shutdown"
            try:
                with socket.create_connection(("127.0.0.1",port),timeout=.2):raise AssertionError("listener survived shutdown")
            except ConnectionRefusedError:pass
        finally:
            if process.poll() is None:
                process.terminate()
                try:process.wait(timeout=15)
                except subprocess.TimeoutExpired:process.kill();process.wait();raise
            logs.seek(0);text=logs.read()
            assert all(secret not in text for secret in forbidden),"private input logged"

def identity(base,login):
    assert request(base,"POST","/v1/identity/register",{"login":login,"display_name":"Starter","password":PASSWORD})[0]==201
    status,_,wire=request(base,"POST","/v1/identity/sign-in",{"login":login,"password":PASSWORD})
    assert status==200;return json.loads(wire)

def exercise(base):
    first=identity(base,"starter."+uuid.uuid4().hex);other=identity(base,"other."+uuid.uuid4().hex)
    token=first["access_token"]
    status,_,wire=request(base,"POST","/v1/accounts/bootstrap",{"name":"Starter"},token)
    assert status==201;path="/v1/accounts/"+json.loads(wire)["id"]
    assert request(base,"GET",path,token=other["access_token"])[0]==403
    command=str(uuid.uuid4());input={"title":"PRIVATE Unicode 🧱\u0000 note"};headers={"Idempotency-Key":command}
    created=request(base,"POST",path+"/notes",input,token,headers);assert created[0]==201
    receipt=json.loads(created[2]);note=path+"/notes/"+receipt["note_id"]
    assert request(base,"POST",path+"/notes",input,token,headers)[2]==created[2]
    assert json.loads(request(base,"GET",path+"/note-receipts/"+command,token=token)[2])==receipt
    assert request(base,"PATCH",note,{"title":"Edited"},token,{"Idempotency-Key":str(uuid.uuid4()),"If-Match":'"1"'})[0]==200
    assert request(base,"PATCH",note,{"title":"Stale"},token,{"Idempotency-Key":str(uuid.uuid4()),"If-Match":'"1"'})[0]==409
    assert request(base,"DELETE",note,token=token,headers={"Idempotency-Key":str(uuid.uuid4()),"If-Match":'"2"'})[0]==200
    assert json.loads(request(base,"GET",path+"/note-receipts/"+command,token=token)[2])==receipt
    data=b"private starter bytes\x00\xff";spec={"name":"PRIVATE-object.bin","content_type":"application/octet-stream","byte_size":len(data),"sha256":hashlib.sha256(data).hexdigest()}
    status,_,wire=request(base,"POST",path+"/files/uploads",spec,token,{"Idempotency-Key":str(uuid.uuid4())});assert status==201
    file=path+"/files/"+json.loads(wire)["file"]["id"]
    assert request(base,"PUT",file+"/content",data,token,{"Content-Type":"application/octet-stream"})[0]==200
    assert request(base,"POST",file+"/complete",token=token,headers={"If-Match":'"1"',"Idempotency-Key":str(uuid.uuid4())})[0]==200
    assert request(base,"GET",file+"/content",token=token)[2]==data
    deadline=time.monotonic()+8
    while True:
        page=json.loads(request(base,"GET",path+"/notifications",token=token)[2])
        if page["items"]:break
        assert time.monotonic()<deadline,"independent inbox delivery stalled";time.sleep(.02)
    assert page["unread_count"]==1
    assert request(base,"POST",path+"/notifications/"+page["items"][0]["id"]+"/read",token=token)[0]==200
    assert request(base,"GET",file+"/content",token=other["access_token"])[0]==403
    status,_,wire=request(base,"POST","/v1/identity/renew",{"refresh_token":first["refresh_token"]});assert status==200
    renewed=json.loads(wire);assert request(base,"GET",path,token=token)[0]==401
    assert request(base,"GET",path,token=renewed["access_token"])[0]==200
    assert request(base,"POST","/v1/identity/sign-out",{"refresh_token":renewed["refresh_token"]})[0]==204
    assert request(base,"GET",path,token=renewed["access_token"])[0]==401
    return [PASSWORD,"PRIVATE",*[g[k] for g in (first,other,renewed) for k in ("access_token","refresh_token")]]

def main():
    subprocess.run(["cargo","build","--locked","--workspace"],cwd=ROOT,check=True)
    hidden=[]
    with server(ROOT/"target/debug/api-server",forbidden=hidden) as base:hidden.extend(exercise(base))
    print("Rust smoke: owned Identity/account/Notes receipts/CAS/Files/inbox/renewal/sign-out and private logs passed")

if __name__=="__main__":main()
