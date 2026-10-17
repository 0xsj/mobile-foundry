"""Own a disposable database and API processes; never reset the local stack."""
import contextlib
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time
import uuid

from smoke import ROOT, TOKEN, request, server

IMAGE = "postgres:18.6-alpine"

def run(*arguments, **options):
    return subprocess.run(arguments, check=True, **options)

@contextlib.contextmanager
def docker_database():
    name = "mobile-foundry-pg-test-" + uuid.uuid4().hex[:12]
    run("docker", "run", "--rm", "--detach", "--name", name,
        "--env", "POSTGRES_USER=mobile_foundry", "--env", "POSTGRES_DB=mobile_foundry",
        "--env", "POSTGRES_PASSWORD=disposable-test-only",
        "--tmpfs", "/var/lib/postgresql", "--publish", "127.0.0.1::5432", IMAGE,
        stdout=subprocess.DEVNULL)
    try:
        port = json.loads(run("docker", "inspect", "--format", '{{json .NetworkSettings.Ports}}', name,
                              capture_output=True, text=True).stdout)["5432/tcp"][0]["HostPort"]
        deadline = time.monotonic() + 30
        while subprocess.run(["docker", "exec", name, "pg_isready", "-U", "mobile_foundry", "-d", "mobile_foundry"],
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
            if time.monotonic() > deadline:
                raise RuntimeError("isolated PostgreSQL startup timed out")
            time.sleep(0.1)
        url = f"postgres://mobile_foundry:disposable-test-only@127.0.0.1:{port}/mobile_foundry?sslmode=disable"
        yield url, ["docker", "exec", name, "psql", "-U", "mobile_foundry", "-d", "mobile_foundry"]
    finally:
        run("docker", "rm", "--force", name, stdout=subprocess.DEVNULL)

@contextlib.contextmanager
def local_database():
    def executable(name):
        directory = os.environ.get("POSTGRES_BIN_DIR")
        path = str(Path(directory) / name) if directory else shutil.which(name)
        if not path or not Path(path).is_file():
            raise RuntimeError(f"local test mode requires {name}; set POSTGRES_BIN_DIR")
        return path
    initdb, pg_ctl, psql = [executable(name) for name in ("initdb", "pg_ctl", "psql")]
    with tempfile.TemporaryDirectory(prefix="mobile-foundry-pg-", dir="/tmp") as temporary:
        data = str(Path(temporary) / "data")
        with socket.socket() as reservation:
            reservation.bind(("127.0.0.1", 0))
            port = reservation.getsockname()[1]
        run(initdb, "-D", data, "--username=mobile_foundry", "--auth-local=trust", "--auth-host=trust", "--encoding=UTF8", "--no-locale", stdout=subprocess.DEVNULL)
        started = False
        try:
            run(pg_ctl, "-D", data, "-l", str(Path(temporary) / "postgres.log"), "-o",
                f"-h 127.0.0.1 -p {port} -k {temporary}", "-w", "start", stdout=subprocess.DEVNULL)
            started = True
            arguments = [psql, "-h", "127.0.0.1", "-p", str(port), "-U", "mobile_foundry"]
            run(*arguments, "-d", "postgres", "-v", "ON_ERROR_STOP=1", "-c", "CREATE DATABASE mobile_foundry", stdout=subprocess.DEVNULL)
            yield f"postgres://mobile_foundry@127.0.0.1:{port}/mobile_foundry?sslmode=disable", arguments + ["-d", "mobile_foundry"]
        finally:
            if started:
                run(pg_ctl, "-D", data, "-m", "immediate", "-w", "stop", stdout=subprocess.DEVNULL)

def main():
    mode = os.environ.get("POSTGRES_TEST_MODE", "docker")
    if mode not in ("docker", "local"):
        raise RuntimeError("POSTGRES_TEST_MODE must be docker or local")
    database = docker_database if mode == "docker" else local_database
    with database() as (url, psql):
        environment = dict(os.environ, STORAGE="postgres", AUTH_MODE="deny", PUSH_CONFIG_FILE="", PUSH_JOBS_ENABLED="false", DATABASE_URL=url, ACCOUNT_DATABASE_URL="", AUDIT_DATABASE_URL="",IDENTITY_DATABASE_URL="",NOTES_DATABASE_URL="",
                           TEST_POSTGRES_URL=url, TEST_POSTGRES_DISPOSABLE="1", DEV_AUTH_TOKEN="", DEV_PRINCIPAL_ID="")
        # Empty module overrides deliberately mean empty in application config;
        # omit them here so each falls back to the common database URL.
        environment.pop("ACCOUNT_DATABASE_URL")
        environment.pop("AUDIT_DATABASE_URL")
        environment.pop("IDENTITY_DATABASE_URL")
        environment.pop("NOTES_DATABASE_URL")
        environment.pop("FILES_DATABASE_URL", None)
        environment.pop("NOTIFICATIONS_DATABASE_URL", None)
        with tempfile.TemporaryDirectory(prefix="mobile-foundry-pg-") as temporary:
            binary = Path(temporary) / "server"
            run("go", "build", "-o", str(binary), "./cmd/server", cwd=ROOT)
            refused = subprocess.run([str(binary)], cwd=ROOT, env=environment, capture_output=True, text=True, timeout=20)
            assert refused.returncode == 1 and "postgres.schema" in refused.stderr and url not in refused.stderr, "unmigrated schema not refused safely: " + refused.stderr
            run("go", "test", "-race", "-count=1", "-v", "./tests/integration", cwd=ROOT, env=environment)
            run("go", "test", "-race", "-count=1", "./tests", cwd=ROOT, env=environment)
            run("go", "run", "./cmd/migrate", "--check", cwd=ROOT, env=environment)
            # Stop Audit recording inside the isolated DB to retain a committed
            # Account event before killing its producer process.
            run(*psql, "-v", "ON_ERROR_STOP=1", "-c",
                "ALTER TABLE audit.inbox ADD CONSTRAINT pause_delivery CHECK (consumer <> 'audit.account') NOT VALID",
                stdout=subprocess.DEVNULL)
            with server(binary, fixture=True, database_url=url, crash=True) as base:
                status, _, wire = request(base, "POST", "/v1/accounts", {"name": "Private smoke account"}, TOKEN)
                assert status == 201
                account = json.loads(wire)["id"]
                path = "/v1/accounts/" + account
                status, headers, _ = request(base, "PATCH", path, {"name": "Renamed"}, TOKEN, {"If-Match": '"1"'})
                assert status == 200 and headers["ETag"] == '"2"'
                assert request(base, "PATCH", path, {"name": "Stale"}, TOKEN, {"If-Match": '"1"'})[0] == 409
                assert request(base, "GET", path + "/audit", token=TOKEN)[0] == 200
                command_id = "00000000-0000-4000-8000-000000000051"
                note_title = "Durable 🧱\u0000 receipt"
                status, _, wire = request(base, "POST", path + "/notes", {"title": note_title}, TOKEN, {"Idempotency-Key": command_id})
                assert status == 201
                committed_note = json.loads(wire)
            run(*psql, "-v", "ON_ERROR_STOP=1", "-c",
                "ALTER TABLE audit.inbox DROP CONSTRAINT pause_delivery", stdout=subprocess.DEVNULL)
            with server(binary, fixture=True, database_url=url) as base:
                status, headers, _ = request(base, "GET", path, token=TOKEN)
                assert status == 200 and headers["ETag"] == '"2"', "account lost across process restart"
                receipt_path = path + "/note-receipts/" + command_id
                assert json.loads(request(base, "GET", receipt_path, token=TOKEN)[2]) == committed_note, "receipt lost after killed API"
                status, _, wire = request(base, "POST", path + "/notes", {"title": note_title}, TOKEN, {"Idempotency-Key": command_id})
                assert status == 201 and json.loads(wire) == committed_note, "restart replay duplicated write"
                assert len(json.loads(request(base, "GET", path + "/notes", token=TOKEN)[2])["notes"]) == 1
                deadline = time.monotonic() + 10
                while True:
                    status, _, wire = request(base, "GET", path + "/audit", token=TOKEN)
                    assert status == 200
                    entries = json.loads(wire)["entries"]
                    if len(entries) == 2:
                        break
                    assert time.monotonic() < deadline, "pending outbox was not recovered"
                    time.sleep(0.02)
                assert [e["action"] for e in entries] == ["account.created", "account.renamed"]
            with server(binary, fixture=True, database_url=url) as base:
                assert len(json.loads(request(base, "GET", path + "/audit", token=TOKEN)[2])["entries"]) == 2, "audit evidence lost or duplicated on restart"
            with server(binary, fixture=True, database_url=url, principal_id="00000000-0000-4000-8000-000000000002") as base:
                assert request(base, "GET", path, token=TOKEN)[0] == 403
                assert request(base, "GET", path + "/audit", token=TOKEN)[0] == 403
            identity_processes(binary, url, psql)
            files_processes(binary, url, str(Path(temporary) / "objects"), psql)
            installations_processes(binary, url, psql)
        print("PostgreSQL integration: schema guards, atomic writes, CAS, leases, inbox replay, cursor isolation and process recovery passed")

def files_processes(binary, url, directory, psql):
    import hashlib
    import urllib.request
    data = b"private media bytes\x00\xff"
    name = "private-photo.bin"
    def raw(base, path, method="GET", body=None):
        req = urllib.request.Request(base + path, data=body, method=method,
            headers={"Authorization": "Bearer " + TOKEN, "Content-Type": "application/octet-stream"})
        with urllib.request.urlopen(req, timeout=10) as response:
            return response.status, response.read()
    with server(binary, fixture=True, database_url=url, crash=True, files_directory=directory, forbidden=(name,)) as base:
        status, _, wire = request(base, "POST", "/v1/accounts", {"name": "Media restart"}, TOKEN)
        assert status == 201; account = json.loads(wire)["id"]
        path = "/v1/accounts/" + account + "/files"
        body = {"name": name, "content_type": "application/octet-stream", "byte_size": len(data), "sha256": hashlib.sha256(data).hexdigest()}
        status, _, wire = request(base, "POST", path + "/uploads", body, TOKEN, {"Idempotency-Key": "restart.request"})
        assert status == 201; pending = json.loads(wire); target = path + "/" + pending["file"]["id"]
        assert raw(base, target + "/content", "PUT", data)[0] == 200
    # Block only Notifications so Audit can accept the same committed fact.
    run(*psql, "-v", "ON_ERROR_STOP=1", "-c",
        "ALTER TABLE notifications.inbox ADD CONSTRAINT pause_notifications CHECK(false) NOT VALID", stdout=subprocess.DEVNULL)
    with server(binary, fixture=True, database_url=url, crash=True, files_directory=directory, forbidden=(name,)) as base:
        receipt = "/v1/receipts/files/restart.request?account_id=" + account
        assert json.loads(request(base, "GET", receipt, token=TOKEN)[2]) == pending
        status, _, wire = request(base, "POST", target + "/complete", token=TOKEN, headers={"If-Match": '\"1\"', "Idempotency-Key": "restart.complete"})
        assert status == 200; completed = json.loads(wire)
        assert raw(base, target + "/content")[1] == data
        inbox = "/v1/accounts/" + account + "/notifications"
        assert json.loads(request(base, "GET", inbox, token=TOKEN)[2])["items"] == []
    run(*psql, "-v", "ON_ERROR_STOP=1", "-c",
        "ALTER TABLE notifications.inbox DROP CONSTRAINT pause_notifications", stdout=subprocess.DEVNULL)
    with server(binary, fixture=True, database_url=url, files_directory=directory, forbidden=(name,)) as base:
        deadline = time.monotonic() + 10
        while True:
            page = json.loads(request(base, "GET", inbox, token=TOKEN)[2])
            if len(page["items"]) == 1:
                break
            assert time.monotonic() < deadline, "notification delivery not recovered after producer crash"
            time.sleep(0.02)
        assert page["unread_count"] == 1 and page["items"][0]["file_id"] == pending["file"]["id"]
        item = inbox + "/" + page["items"][0]["id"] + "/read"
        status, _, wire = request(base, "POST", item, token=TOKEN)
        assert status == 200; read = json.loads(wire)
        preferences = "/v1/accounts/" + account + "/notification-preferences"
        assert request(base, "PUT", preferences, {"media_in_app":False}, TOKEN)[0] == 200
        assert raw(base, target + "/content")[1] == data
        assert json.loads(request(base, "POST", target + "/complete", token=TOKEN, headers={"If-Match": '\"1\"', "Idempotency-Key": "restart.complete"})[2]) == completed
        status, _, _ = request(base, "DELETE", target, token=TOKEN, headers={"If-Match": '\"2\"', "Idempotency-Key": "restart.delete"})
        assert status == 200
        assert request(base, "GET", target + "/content", token=TOKEN)[0] == 404
    with server(binary, fixture=True, database_url=url, files_directory=directory, forbidden=(name,)) as base:
        page = json.loads(request(base, "GET", inbox, token=TOKEN)[2])
        assert len(page["items"]) == 1 and page["unread_count"] == 0, "notification lost/duplicated on restart"
        assert json.loads(request(base, "POST", item, token=TOKEN)[2]) == read, "read acknowledgement not retained"
        assert json.loads(request(base, "GET", preferences, token=TOKEN)[2])["media_in_app"] is False
    with server(binary, fixture=True, database_url=url, files_directory=directory, principal_id="00000000-0000-4000-8000-000000000002") as base:
        assert request(base, "GET", inbox, token=TOKEN)[0] == 403
    print("Files PostgreSQL: pending content, completion, receipts and private bytes survive API SIGKILL; deletion closes access")
    print("Notifications PostgreSQL: blocked consumer recovers after SIGKILL; one inbox item, read acknowledgement and preference survive restart")

def installations_processes(binary, url, psql):
    import secrets
    proof = "i1." + secrets.token_urlsafe(32)
    token = "private-installation-target-" + uuid.uuid4().hex
    password = "private installation passphrase"
    hidden = [proof, token, password]
    identity, registration = str(uuid.uuid4()), str(uuid.uuid4())
    path = "/v1/installations/" + identity
    headers = {"X-Installation-Key": proof}
    with server(binary, identity=True, database_url=url, crash=True, forbidden=hidden) as base:
        assert request(base,"POST","/v1/identity/register",{"login":"installation-process","display_name":"Device","password":password})[0] == 201
        status,_,wire = request(base,"POST","/v1/identity/sign-in",{"login":"installation-process","password":password})
        assert status == 200; grant = json.loads(wire); hidden += [grant["access_token"],grant["refresh_token"]]
        body = {"installation_id":identity,"registration_id":registration,"provider":"fcm","application":"catalog.android","environment":"production","token":token}
        status,hdr,wire = request(base,"POST","/v1/installations",body,grant["access_token"],headers)
        assert status == 201 and hdr["ETag"] == '"1"'; committed = json.loads(wire)
        assert all(secret.encode() not in wire for secret in hidden)
    run(*psql,"-v","ON_ERROR_STOP=1","-c",
        "ALTER TABLE notifications.installation_inbox ADD CONSTRAINT pause_installation_revoke CHECK(false) NOT VALID",stdout=subprocess.DEVNULL)
    with server(binary, identity=True, database_url=url, crash=True, forbidden=hidden) as base:
        status,_,wire = request(base,"GET",path,token=grant["access_token"],headers=headers)
        assert status == 200 and json.loads(wire) == committed, "installation lost on crash"
        assert request(base,"POST","/v1/identity/sign-out",{"refresh_token":grant["refresh_token"]})[0] == 204
    run(*psql,"-v","ON_ERROR_STOP=1","-c",
        "ALTER TABLE notifications.installation_inbox DROP CONSTRAINT pause_installation_revoke",stdout=subprocess.DEVNULL)
    with server(binary, identity=True, database_url=url, forbidden=hidden) as base:
        status,_,wire = request(base,"POST","/v1/identity/sign-in",{"login":"installation-process","password":password})
        assert status == 200; fresh = json.loads(wire); hidden += [fresh["access_token"],fresh["refresh_token"]]
        deadline = time.monotonic() + 10
        while True:
            status,_,wire = request(base,"GET",path,token=fresh["access_token"],headers=headers)
            assert status == 200; current = json.loads(wire)
            if not current["registered"]: break
            assert time.monotonic() < deadline, "installation revoke not recovered"
            time.sleep(0.02)
        assert current["version"] == 2 and current["current_binding"] is False
    state = run(*psql,"-tA","-v","ON_ERROR_STOP=1","-c",
        "SELECT token || ':' || proof_digest FROM notifications.installations WHERE id='" + identity + "'",capture_output=True,text=True).stdout.strip()
    assert state.startswith(":") and proof not in state and token not in state, "routing token/proof retained after revoke"
    print("Installation process acceptance: registration survives SIGKILL; blocked Identity revocation recovers, erases token and preserves hashed proof")

def identity_processes(binary, url, psql):
    password = "private process passphrase"
    secrets = [password]
    with server(binary, identity=True, database_url=url, crash=True, forbidden=secrets) as base:
        status, _, wire = request(base, "POST", "/v1/identity/register", {"login":"process-alex", "display_name":"Alex", "password":password})
        assert status == 201 and "password" not in json.loads(wire)
        status, _, wire = request(base, "POST", "/v1/identity/sign-in", {"login":"process-alex", "password":password})
        assert status == 200
        grant = json.loads(wire)
        secrets.extend([grant["access_token"],grant["refresh_token"]])
        status, _, wire = request(base, "POST", "/v1/accounts/bootstrap", {"name":"Authenticated personal account"}, grant["access_token"])
        assert status == 201
        personal = json.loads(wire)
        path = "/v1/accounts/" + personal["id"]
        status, _, wire = request(base, "POST", "/v1/accounts", {"name":"Second workspace"}, grant["access_token"])
        assert status == 201
        workspace = json.loads(wire)
    with server(binary, identity=True, database_url=url, forbidden=secrets) as base:
        assert request(base, "GET", path, token=grant["access_token"])[0] == 200, "session lost on API crash"
        status, _, wire = request(base, "POST", "/v1/accounts/bootstrap", {"name":"Do not overwrite"}, grant["access_token"])
        assert status == 200 and json.loads(wire) == personal, "personal mapping lost or changed after API crash"
        status, _, wire = request(base, "GET", "/v1/accounts?limit=1", token=grant["access_token"])
        first = json.loads(wire)
        assert status == 200 and len(first["accounts"]) == 1 and first["next_cursor"]
        status, _, wire = request(base, "GET", "/v1/accounts?limit=1&cursor=" + first["next_cursor"], token=grant["access_token"])
        second = json.loads(wire)
        assert status == 200 and len(second["accounts"]) == 1 and second["next_cursor"] is None
        assert {first["accounts"][0]["id"],second["accounts"][0]["id"]} == {personal["id"],workspace["id"]}
        for selected in (personal, workspace):
            assert request(base, "GET", "/v1/accounts/" + selected["id"], token=grant["access_token"])[0] == 200
        status, _, wire = request(base, "POST", "/v1/identity/renew", {"refresh_token":grant["refresh_token"]})
        assert status == 200
        renewed = json.loads(wire)
        secrets.extend([renewed["access_token"],renewed["refresh_token"]])
        assert renewed["session_id"] == grant["session_id"] and renewed["principal"]["id"] == grant["principal"]["id"]
        assert renewed["refresh_expires_at"] == grant["refresh_expires_at"]
        assert request(base, "GET", path, token=grant["access_token"])[0] == 401
        assert request(base, "GET", path, token=renewed["access_token"])[0] == 200
        assert request(base, "POST", "/v1/identity/renew", {"refresh_token":grant["refresh_token"]})[0] == 401
        assert request(base, "GET", path, token=renewed["access_token"])[0] == 401
    with server(binary, identity=True, database_url=url, forbidden=secrets) as base:
        assert request(base, "GET", path, token=renewed["access_token"])[0] == 401, "revocation lost after restart"
        status, _, wire = request(base, "POST", "/v1/identity/sign-in", {"login":"process-alex", "password":password})
        assert status == 200
        fresh = json.loads(wire)
        secrets.extend([fresh["access_token"],fresh["refresh_token"]])
        deadline = time.monotonic() + 10
        while True:
            status, _, wire = request(base, "GET", "/v1/principals/me/audit", token=fresh["access_token"])
            assert status == 200 and all(secret.encode() not in wire for secret in secrets)
            entries = json.loads(wire)["entries"]
            if len(entries) == 5 and any(e.get("reason") == "refresh_reuse" for e in entries):
                break
            assert time.monotonic() < deadline, "Identity facts were not recovered into scoped Audit"
            time.sleep(0.02)
        assert request(base, "POST", "/v1/identity/sign-out", {"refresh_token":fresh["refresh_token"]})[0] == 204
    with server(binary, identity=True, database_url=url, forbidden=secrets) as base:
        assert request(base, "GET", path, token=fresh["access_token"])[0] == 401, "sign out lost after restart"
    snapshot = run(*psql, "-tA", "-v", "ON_ERROR_STOP=1", "-c",
        "SELECT jsonb_build_object('principals',(SELECT jsonb_agg(t) FROM identity.principals t),'sessions',(SELECT jsonb_agg(t) FROM identity.sessions t),'refresh',(SELECT jsonb_agg(t) FROM identity.refresh_credentials t),'outbox',(SELECT jsonb_agg(t) FROM identity.outbox t))",
        capture_output=True,text=True).stdout
    assert all(secret not in snapshot for secret in secrets), "raw credentials persisted"
    print("Identity process acceptance: principal credentials, personal bootstrap/discovery/selection after API crash, renewal/reuse, durable revoke/sign-out, scoped Audit and private storage/logs passed")

if __name__ == "__main__":
    main()
