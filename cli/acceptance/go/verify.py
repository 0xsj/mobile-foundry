"""Inspect and exercise an already-generated Go project using only its copied API.

Run with a generated project path; this script does not generate, reset databases,
or contact any running development API. Build artifacts are temporary.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import signal
import shlex
import socket
import subprocess
import tempfile
import time
import uuid
import hashlib
import urllib.request
import urllib.error


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("project", type=Path)
    args = parser.parse_args()
    project = args.project.resolve(strict=True)
    api = project / "backend/go"
    metadata = json.loads((project / "mobile-foundry.json").read_text())
    assert metadata["backend"] == "go"
    module = metadata["goModule"]
    assert (api / "go.mod").read_text().splitlines()[0] == "module " + module
    # Inspect source/build inputs, excluding caches/reports that tools can populate.
    for path in api.rglob("*"):
        if any(part in {".cache", ".git", "__pycache__", "bin"} for part in path.relative_to(api).parts):
            continue
        if path.is_file() and path.suffix in {".go", ".mod", ".py", ".yaml"}:
            text = path.read_text()
            assert "github.com/0xsj/mobile-foundry/backend" not in text, "source module survived extraction"
            assert "/Users/" not in text and "/private/tmp/" not in text, "machine/source path in build input"
    spec = importlib.util.spec_from_file_location("generated_smoke", api / "scripts/smoke.py")
    smoke = importlib.util.module_from_spec(spec); spec.loader.exec_module(smoke)
    assert smoke.ROOT == api
    request = smoke.request
    password = "generated acceptance passphrase"
    private = [password]
    with tempfile.TemporaryDirectory(prefix="generated-go-probe-") as temporary:
        binary = Path(temporary) / "server"
        subprocess.run(["go", "build", "-o", str(binary), "./cmd/server"], cwd=api, check=True)
        # Build once, then test the generated root's make runner and its default auth.
        # Replacing only `go` with a narrow owned shim avoids a second compilation;
        # the exported runner environment and API binary are real.
        shim = Path(temporary) / "go"
        shim.write_text('#!/bin/sh\nif [ "$#" -eq 2 ] && [ "$1" = run ] && [ "$2" = ./cmd/server ]; then\n  exec ' + shlex.quote(str(binary)) + '\nfi\nexit 2\n')
        shim.chmod(0o755)
        with socket.socket() as reservation:
            reservation.bind(("127.0.0.1", 0)); port = reservation.getsockname()[1]
        base = f"http://127.0.0.1:{port}"
        env = dict(os.environ, HTTP_ADDR=f"127.0.0.1:{port}", LOG_FORMAT="json", STORAGE="memory", DEV_AUTH_TOKEN="", DEV_PRINCIPAL_ID="", PUSH_CONFIG_FILE="", PUSH_JOBS_ENABLED="false")
        env.pop("AUTH_MODE", None)
        env["PATH"] = str(shim.parent) + os.pathsep + env["PATH"]
        with tempfile.TemporaryFile(mode="w+") as logs:
            process = subprocess.Popen(["make", "go-dev"], cwd=project, env=env, stdout=logs, stderr=logs, start_new_session=True)
            try:
                deadline = time.monotonic() + 15
                while True:
                    assert process.poll() is None, "generated go-dev exited before ready"
                    try:
                        if request(base, "GET", "/v1/health/ready")[0] == 200: break
                    except OSError: pass
                    assert time.monotonic() < deadline, "generated go-dev readiness timeout"
                    time.sleep(0.05)
                grants = []
                for login in ("starter-alex", "starter-sam"):
                    assert request(base, "POST", "/v1/identity/register", {"login":login,"display_name":login,"password":password})[0] == 201
                    status, _, wire = request(base, "POST", "/v1/identity/sign-in", {"login":login,"password":password})
                    assert status == 200; grant = json.loads(wire); grants.append(grant)
                    private += [grant["access_token"], grant["refresh_token"]]
                alex, sam = grants
                status, _, wire = request(base, "POST", "/v1/accounts/bootstrap", {"name":"Personal"}, alex["access_token"])
                assert status == 201; account = json.loads(wire)["id"]; path = "/v1/accounts/" + account
                assert request(base, "GET", path, token=sam["access_token"])[0] == 403
                key = str(uuid.uuid4()); title = "Generated 🧱\u0000 note"
                status, _, wire = request(base, "POST", path + "/notes", {"title":title}, alex["access_token"], {"Idempotency-Key":key})
                assert status == 201; receipt = json.loads(wire); note = path + "/notes/" + receipt["note_id"]
                # Treat the first acknowledgement as discarded; recover from its stable key.
                assert json.loads(request(base,"GET",path + "/note-receipts/" + key,token=alex["access_token"])[2]) == receipt
                assert json.loads(request(base,"POST",path + "/notes",{"title":title},alex["access_token"],{"Idempotency-Key":key})[2]) == receipt
                assert request(base,"GET",path + "/notes",token=sam["access_token"])[0] == 403
                assert request(base,"GET",path + "/note-receipts/" + key,token=sam["access_token"])[0] == 403
                assert request(base,"PATCH",note,{"title":"Edited"},alex["access_token"],{"Idempotency-Key":str(uuid.uuid4()),"If-Match":'"1"'})[0] == 200
                assert request(base,"PATCH",note,{"title":"Stale"},alex["access_token"],{"Idempotency-Key":str(uuid.uuid4()),"If-Match":'"1"'})[0] == 409
                assert request(base,"DELETE",note,token=alex["access_token"],headers={"Idempotency-Key":str(uuid.uuid4()),"If-Match":'"2"'})[0] == 200
                assert json.loads(request(base,"GET",path + "/note-receipts/" + key,token=alex["access_token"])[2]) == receipt
                # Exercise the copied file module through authenticated raw bytes.
                data = bytes([0, 255, 1, 128]); file_key = str(uuid.uuid4())
                spec = {"name":"Generated media.bin", "content_type":"application/octet-stream", "byte_size":len(data), "sha256":hashlib.sha256(data).hexdigest()}
                status, _, wire = request(base,"POST",path + "/files/uploads",spec,alex["access_token"],{"Idempotency-Key":file_key})
                assert status == 201; pending = json.loads(wire); file = path + "/files/" + pending["file"]["id"]
                assert json.loads(request(base,"GET","/v1/receipts/files/" + file_key + "?account_id=" + account,token=alex["access_token"])[2]) == pending
                req = urllib.request.Request(base + file + "/content",data=data,method="PUT",headers={"Authorization":"Bearer " + alex["access_token"],"Content-Type":"application/octet-stream"})
                with urllib.request.urlopen(req,timeout=10) as response: assert response.status == 200
                assert request(base,"POST",file + "/complete",token=alex["access_token"],headers={"If-Match": '\"1\"',"Idempotency-Key":file_key + ".complete"})[0] == 200
                req = urllib.request.Request(base + file + "/content",headers={"Authorization":"Bearer " + alex["access_token"]})
                with urllib.request.urlopen(req,timeout=10) as response: assert response.read() == data
                assert request(base,"GET",file + "/content",token=sam["access_token"])[0] == 403
                assert request(base,"DELETE",file,token=alex["access_token"],headers={"If-Match": '\"2\"',"Idempotency-Key":file_key + ".delete"})[0] == 200
                assert request(base,"GET",file + "/content",token=alex["access_token"])[0] == 404
                inbox = path + "/notifications"
                deadline = time.monotonic() + 5
                while True:
                    status, _, wire = request(base,"GET",inbox,token=alex["access_token"])
                    assert status == 200; page = json.loads(wire)
                    if len(page["items"]) == 1: break
                    assert time.monotonic() < deadline, "generated notification not delivered"
                    time.sleep(0.02)
                assert page["unread_count"] == 1 and page["items"][0]["file_id"] == pending["file"]["id"]
                assert request(base,"GET",inbox,token=sam["access_token"])[0] == 403
                item = inbox + "/" + page["items"][0]["id"] + "/read"
                assert request(base,"POST",item,token=alex["access_token"])[0] == 200
                assert json.loads(request(base,"GET",inbox,token=alex["access_token"])[2])["unread_count"] == 0
                preferences = path + "/notification-preferences"
                assert request(base,"PUT",preferences,{"media_in_app":False},alex["access_token"])[0] == 200
                assert json.loads(request(base,"GET",preferences,token=alex["access_token"])[2])["media_in_app"] is False
                # Installation proof is independent of both auth and push token.
                import secrets
                proof = "i1." + secrets.token_urlsafe(32); target = "private-target-" + uuid.uuid4().hex
                private += [proof,target]
                installation = str(uuid.uuid4()); registration = str(uuid.uuid4()); route = "/v1/installations/" + installation
                binding = {"installation_id":installation,"registration_id":registration,"provider":"apns","application":"generated.ios","environment":"sandbox","token":target}
                installation_headers = {"X-Installation-Key":proof}
                status,hdr,wire = request(base,"POST","/v1/installations",binding,alex["access_token"],installation_headers)
                assert status == 201 and hdr["ETag"] == '"1"'
                assert proof.encode() not in wire and target.encode() not in wire and json.loads(wire)["current_binding"]
                assert request(base,"GET",route,token=sam["access_token"],headers={"X-Installation-Key":"i1." + secrets.token_urlsafe(32)})[0] == 404
                update = dict(binding);update.pop("installation_id");update["registration_id"] = str(uuid.uuid4())
                status,_,wire = request(base,"PUT",route,update,sam["access_token"],dict(installation_headers,**{"If-Match":'"1"'}))
                assert status == 200 and json.loads(wire)["version"] == 2
                assert request(base,"DELETE",route,token=alex["access_token"],headers=dict(installation_headers,**{"If-Match":'"2"'}))[0] == 403
                assert request(base,"DELETE",route,token=sam["access_token"],headers=dict(installation_headers,**{"If-Match":'"2"'}))[0] == 200
                assert request(base,"DELETE",route,token=sam["access_token"],headers=dict(installation_headers,**{"If-Match":'"2"'}))[0] == 200
                status, _, wire = request(base,"POST","/v1/identity/renew",{"refresh_token":alex["refresh_token"]})
                assert status == 200; renewed = json.loads(wire); private += [renewed["access_token"],renewed["refresh_token"]]
                assert request(base,"GET",path,token=alex["access_token"])[0] == 401
                assert request(base,"GET",path,token=renewed["access_token"])[0] == 200
                assert request(base,"POST","/v1/identity/sign-out",{"refresh_token":renewed["refresh_token"]})[0] == 204
                assert request(base,"GET",path,token=renewed["access_token"])[0] == 401
                assert request(base,"POST","/v1/identity/sign-out",{"refresh_token":sam["refresh_token"]})[0] == 204
            finally:
                # TERM the isolated process group; wait for Make and its API child.
                try: os.killpg(process.pid, signal.SIGTERM)
                except ProcessLookupError: pass
                try: process.wait(timeout=8)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL); process.wait(); raise
                logs.seek(0); text = logs.read()
                assert all(secret not in text for secret in private), "credentials leaked in generated runner logs"
    print("Generated Go acceptance: auth/account/Notes/Files/Notifications, installation proof/rebind/unregister, renewal and sign-out passed")


if __name__ == "__main__":
    main()
