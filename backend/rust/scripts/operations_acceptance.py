#!/usr/bin/env python3
"""Verify a portable container, SMTP recovery, metrics and a real quiesced backup/restore."""
import argparse
import base64
import hashlib
import importlib.util
import ipaddress
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import struct
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
from urllib.parse import urlsplit
import uuid

ROOT = Path(__file__).resolve().parents[1]
PASSWORD = "owned operations test passphrase"
NEXT = "changed operations test passphrase"

def run(*args, **kwargs): return subprocess.run(args, check=True, **kwargs)
def request(base, method, path, body=None, token=None, headers=None, binary=False):
    fields = dict(headers or {})
    if token: fields["Authorization"] = "Bearer " + token
    if body is not None and not isinstance(body, bytes):
        fields["Content-Type"] = "application/json"; body = json.dumps(body).encode()
    call = urllib.request.Request(base + path, method=method, data=body, headers=fields)
    try: response = urllib.request.urlopen(call, timeout=8)
    except urllib.error.HTTPError as error: response = error
    with response:
        data = response.read()
        return response.status, data if binary else json.loads(data) if data else None

def wait_ready(base):
    for _ in range(100):
        try:
            if request(base, "GET", "/v1/health/ready")[0] == 200: return
        except (OSError, urllib.error.URLError): pass
        time.sleep(.1)
    raise RuntimeError("Container API did not become ready")

def mail_code(base, purpose, previous=None):
    for _ in range(100):
        status, page = request(base, "GET", "/api/v1/messages")
        if status == 200:
            for message in page.get("messages", []):
                status, detail = request(base, "GET", "/api/v1/message/" + message["ID"])
                text = detail.get("Text", "")
                match = re.search(r"Code: ([a-z]1\.[A-Za-z0-9_-]{43})", text)
                if f"Purpose: {purpose}" in text and match and match.group(1) != previous: return match.group(1)
        time.sleep(.1)
    raise RuntimeError("Owned mail capture did not receive a new recovery code")

def edge_socket(base, path, account, token):
    """Test-only bounded upgrade/frame reader, through the actual Caddy service."""
    address=urlsplit(base)
    with socket.create_connection((address.hostname,address.port),timeout=5) as connection:
        key=base64.b64encode(secrets.token_bytes(16)).decode()
        # The edge must overwrite this hostile chain, rather than append to it.
        wire=f"GET {path}/realtime HTTP/1.1\r\nHost: localhost\r\nConnection: Upgrade\r\nUpgrade: websocket\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: {key}\r\nAuthorization: Bearer {token}\r\nX-Forwarded-For: PRIVATE-spoof, 203.0.113.1\r\n\r\n"
        connection.sendall(wire.encode())
        headers=bytearray()
        while not headers.endswith(b"\r\n\r\n"):
            value=connection.recv(1);assert value and len(headers)<16384;headers.extend(value)
        assert headers.startswith(b"HTTP/1.1 101 "),"edge did not admit a native upgrade"
        def exact(n):
            data=bytearray()
            while len(data)<n:
                value=connection.recv(n-len(data));assert value;data.extend(value)
            return bytes(data)
        def hint(kind):
            while True:
                a,b=exact(2);assert a&0xf0==0x80 and not b&0x80
                length=b&127
                if length==126:length=struct.unpack("!H",exact(2))[0]
                assert length<=4096
                value=exact(length)
                if a&15==9:
                    mask=secrets.token_bytes(4);connection.sendall(bytes([0x8a,0x80|len(value)])+mask+bytes(v^mask[i%4] for i,v in enumerate(value)));continue
                assert a&15==1
                assert json.loads(value)=={"type":kind,"resource":"notes","account_id":account,"refetch":True};return
        hint("ready")
        assert request(base,"POST",path+"/notes",{"title":"Edge refetch"},token,{"Idempotency-Key":str(uuid.uuid4())})[0]==201
        hint("changed")
        assert request(base,"GET",path+"/notes",token=token)[0]==200

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", help="Use an already built owned API image")
    args = parser.parse_args()
    suffix = uuid.uuid4().hex[:12]
    image = args.image or "mobile-foundry-rust-accept:" + suffix
    if not args.image: run("docker", "build", "-t", image, str(ROOT))
    module = importlib.util.spec_from_file_location("owned_backup", ROOT / "scripts/backup.py")
    backup = importlib.util.module_from_spec(module); module.loader.exec_module(backup)
    with tempfile.TemporaryDirectory(prefix="mobile-ops-") as temporary:
        folder = Path(temporary)
        mail = folder / "mail.private.json"
        mail.write_text(json.dumps({"Address":"mailpit:1025", "From":"security@example.test", "Mode":"development"}))
        mail.chmod(0o444)  # Private 0700 parent; container's non-root user can read the bind secret.
        override = folder / "compose.test.yaml"
        override.write_text('''services:
  api:
    environment:
      APP_ENV: development
    ports: ["127.0.0.1::8080", "127.0.0.1::9090"]
  mailpit:
    image: axllent/mailpit:v1.31.5
    ports: ["127.0.0.1::8025"]
    networks: [private]
''')
        network_ids = subprocess.check_output(["docker","network","ls","-q"],text=True).split()
        used=[]
        if network_ids:
            for network in json.loads(subprocess.check_output(["docker","network","inspect",*network_ids],text=True)):
                for config in network.get("IPAM",{}).get("Config",[]) or []:
                    if config.get("Subnet"): used.append(ipaddress.ip_network(config["Subnet"],strict=False))
        candidates=[ipaddress.ip_network(f"10.254.{index}.0/24") for index in range(16,250)]
        available=[network for network in candidates if not any(network.version==other.version and network.overlaps(other) for other in used)]
        if len(available)<2: raise RuntimeError("No unused acceptance subnets; existing networks kept")
        projects = ["ops"+suffix, "restore"+suffix]
        environments = []
        commands = []
        for index, project in enumerate(projects):
            subnet = available[index]
            edge = subnet.network_address+2
            env = folder / (project + ".env")
            dynamic=list(subnet.subnets(new_prefix=25))[1]
            env.write_text(f"POSTGRES_PASSWORD={secrets.token_hex(24)}\nPUBLIC_DOMAIN=http://:80\nHTTP_BIND=127.0.0.1:\nHTTPS_BIND=127.0.0.1:\nMAIL_CONFIG_FILE={mail}\nAPI_IMAGE={image}\nPRIVATE_SUBNET={subnet}\nPRIVATE_IP_RANGE={dynamic}\nEDGE_IP={edge}\nTRUSTED_PROXY_CIDRS={edge}/32\n")
            env.chmod(0o600); environments.append(env)
            commands.append(["docker","compose","--project-name",project,"--env-file",str(env),"-f",str(ROOT/"infra/production/compose.yaml"),"-f",str(override)])
        primary, restored = commands
        try:
            run(*primary, "config", "--quiet")
            run(*primary, "up", "-d", "--wait", "postgres", "mailpit")
            run(*primary, "run", "--rm", "-T", "migrate")
            run(*primary, "up", "-d", "--wait", "api")
            def endpoint(command, service, port):
                return "http://" + subprocess.check_output(command+["port",service,str(port)], text=True).strip()
            base = endpoint(primary,"api",8080); capture = endpoint(primary,"mailpit",8025); wait_ready(base)
            container=subprocess.check_output(primary+["ps","-q","api"],text=True).strip()
            admitted=json.loads(subprocess.check_output(["docker","inspect","--format",'{"user":{{json .Config.User}},"readonly":{{json .HostConfig.ReadonlyRootfs}}}',container],text=True))
            assert admitted["user"]=="65532:65532" and admitted["readonly"] is True

            assert request(base,"POST","/v1/identity/register",{"login":"ops-user","display_name":"Operations","password":PASSWORD})[0]==201
            status, grant = request(base,"POST","/v1/identity/sign-in",{"login":"ops-user","password":PASSWORD}); assert status==200
            token = grant["access_token"]
            assert request(base,"POST","/v1/identity/email/request",{"email":"operator@example.test","password":PASSWORD},token)[0]==204
            code = mail_code(capture,"verify_email")
            assert request(base,"POST","/v1/identity/email/verify",{"token":code})[0]==204
            assert request(base,"POST","/v1/identity/password/request-reset",{"email":"operator@example.test"})[0]==202
            reset = mail_code(capture,"reset_password")
            assert request(base,"POST","/v1/identity/password/reset",{"token":reset,"password":NEXT})[0]==204
            assert request(base,"GET","/v1/identity/me",token=token)[0]==401
            status, grant = request(base,"POST","/v1/identity/sign-in",{"login":"ops-user","password":NEXT}); assert status==200
            token = grant["access_token"]
            status, account = request(base,"POST","/v1/accounts/bootstrap",{"name":"Restorable"},token); assert status==201
            account_id=account["id"]; command_id=str(uuid.uuid4())
            path="/v1/accounts/"+account_id
            status, note=request(base,"POST",path+"/notes",{"title":"Survives restore"},token,{"Idempotency-Key":command_id});assert status==201
            run(*primary,"up","-d","--wait","edge")
            edge_socket(endpoint(primary,"edge",80),path,account_id,token)
            content=b"private restorable object\x00\xff"
            spec={"name":"owned.bin","content_type":"application/octet-stream","byte_size":len(content),"sha256":hashlib.sha256(content).hexdigest()}
            status,pending=request(base,"POST",path+"/files/uploads",spec,token,{"Idempotency-Key":"ops.upload"});assert status==201
            target=path+"/files/"+pending["file"]["id"]
            assert request(base,"PUT",target+"/content",content,token,{"Content-Type":"application/octet-stream"})[0]==200
            assert request(base,"POST",target+"/complete",token=token,headers={"If-Match":'"1"',"Idempotency-Key":"ops.complete"})[0]==200
            metrics=endpoint(primary,"api",9090)
            with urllib.request.urlopen(metrics+"/metrics",timeout=5) as response: text=response.read().decode()
            assert "api_http_requests_total" in text and token not in text and "operator@example.test" not in text
            # Preserve a pending recovery challenge through backup and verify single-use after restore.
            assert request(base,"POST","/v1/identity/password/request-reset",{"email":"operator@example.test"})[0]==202
            pending_reset=mail_code(capture,"reset_password",previous=reset)
            bundle=folder/"backup"
            backup.backup(primary,bundle)
            base=endpoint(primary,"api",8080);wait_ready(base)
            assert request(base,"GET",target+"/content",token=token,binary=True)[1]==content
            damaged=folder/"damaged-backup";shutil.copytree(bundle,damaged)
            with (damaged/"database.dump").open("ab") as output:output.write(b"damage")
            try: backup.restore(restored,damaged,projects[1])
            except ValueError: pass
            else: raise AssertionError("Damaged backup was not refused")
            backup.restore(restored,bundle,projects[1])
            # Never overwrite even our own existing restore target.
            try: backup.restore(restored,bundle,projects[1])
            except ValueError: pass
            else: raise AssertionError("Existing restore project was not refused")
            run(*restored,"up","-d","--wait","api")
            cloned=endpoint(restored,"api",8080);wait_ready(cloned)
            assert request(cloned,"GET",path+"/note-receipts/"+command_id,token=token)[1]==note
            assert request(cloned,"GET",target+"/content",token=token,binary=True)[1]==content
            assert request(cloned,"GET","/v1/identity/email",token=token)[1]["verified"]
            assert request(cloned,"POST","/v1/identity/password/reset",{"token":pending_reset,"password":PASSWORD})[0]==204
            assert request(cloned,"POST","/v1/identity/password/reset",{"token":pending_reset,"password":PASSWORD})[0]==400
            assert request(cloned,"GET","/v1/identity/me",token=token)[0]==401
            run(*restored,"run","--rm","-T","maintenance","--limit","128")
            logs=subprocess.check_output(primary+["logs","--no-color","api"],text=True)
            for secret in (PASSWORD,NEXT,token,code,reset,pending_reset,"operator@example.test"):
                assert secret not in logs,"Private identity input appeared in API logs"
            # Validate the checked-in edge configuration without obtaining certificates.
            run("docker","run","--rm","--network","none","-e","PUBLIC_DOMAIN=api.example.test","-v",str(ROOT/"infra/production/Caddyfile")+":/etc/caddy/Caddyfile:ro","caddy:2.10.2-alpine","caddy","validate","--config","/etc/caddy/Caddyfile")
            print("Rust operations acceptance: non-root/read-only image, actual Caddy proxy/native WebSocket, SMTP verification/reset, private metrics, quiesced DB+objects backup, unused-project restore, retained receipts/challenge, maintenance, revoked sessions and secret-free logs passed; public TLS issuance is separate")
        finally:
            for command in reversed(commands): subprocess.run(command+["down","--volumes","--remove-orphans"],check=False)
    if not args.image: subprocess.run(["docker","image","rm",image],check=False)

if __name__=="__main__":main()
