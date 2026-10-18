#!/usr/bin/env python3
"""Own a disposable database; verify Rust durability and the supported Go handoff.

No host app configuration, Compose volumes or supplied DATABASE_URL is used.
The only test database comes from the existing disposable PostgreSQL owner.
"""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
from backend_parity import ROOT, owned_server, run
from identity_parity import PASSWORD, bearer, call, refusal
from account_parity import identity
from audit_parity import core as audit_core, eventually
from notes_parity import command as note_command, TITLE as NOTE_TITLE, core as notes_core
from recovery_parity import durable as recovery_durable
from files_parity import durable as files_durable
from files_parity import spec as file_spec, binary as file_binary, DATA as FILE_DATA
from notifications_parity import durable as notifications_durable, inbox as notification_inbox
from push_jobs_parity import durable as jobs_durable
from realtime_parity import durable as realtime_durable
from installations_parity import (durable as installations_durable, registration as installation_input,
    proof as installation_proof, register as register_installation, view as installation_view,
    retire_read as retired_installation)

sys.path.insert(0, str(ROOT / "backend/go/scripts"))
from postgres_integration import local_database, docker_database


def environment(url):
    values = {k: os.environ[k] for k in ("PATH", "HOME", "TMPDIR") if k in os.environ}
    values.update(APP_ENV="test", STORAGE="postgres", AUTH_MODE="identity", DATABASE_URL=url,
                  TEST_POSTGRES_URL=url, TEST_POSTGRES_DISPOSABLE="1")
    return values


def main():
    for owner in ("account", "identity", "audit", "notes", "files", "notifications"):
        source = ROOT / "backend/go/migrations" / owner
        target = ROOT / "backend/rust/migrations" / owner
        assert sorted(p.name for p in source.glob("*.sql")) == sorted(p.name for p in target.glob("*.sql"))
        for path in source.glob("*.sql"):
            assert path.read_bytes() == (target / path.name).read_bytes(), "migration checksum provenance changed"
    mode = os.environ.get("POSTGRES_TEST_MODE", "docker")
    assert mode in ("docker", "local"), "POSTGRES_TEST_MODE must be docker or local"
    with tempfile.TemporaryDirectory(prefix="mobile-postgres-parity-") as temporary:
        owned = Path(temporary)
        go_binary, go_migrate = owned / "go-api", owned / "go-migrate"
        run(["go", "build", "-o", str(go_binary), "./cmd/server"], ROOT / "backend/go")
        run(["go", "build", "-o", str(go_migrate), "./cmd/migrate"], ROOT / "backend/go")
        rust = ROOT / "backend/rust"
        run(["cargo", "build", "--locked", "--workspace"], rust)
        rust_binary, migrate = rust / "target/debug/api-server", rust / "target/debug/db-migrate"
        database = local_database if mode == "local" else docker_database
        with database() as (url, psql):
            env = environment(url)
            env["FILES_DIRECTORY"] = str(owned / "objects")
            def execute(command):
                return subprocess.run(psql+["-X", "-v", "ON_ERROR_STOP=1", "-At", "-c", command],check=True,capture_output=True,text=True).stdout.strip()
            refused = subprocess.run([str(rust_binary)],env=env,capture_output=True,text=True,timeout=20)
            assert refused.returncode == 1 and url not in refused.stderr, "unmigrated startup was not safely refused"
            check = subprocess.run([str(migrate),"--check"],env=env,capture_output=True,text=True,timeout=20)
            assert check.returncode == 1 and "postgres.schema" in check.stderr and url not in check.stderr
            subprocess.run(["cargo","test","--locked","-p","api-server","--test","postgres","--","--ignored","--nocapture"],cwd=rust,env=env,check=True)
            subprocess.run(["cargo","test","--locked","-p","api-server","--test","jobs","postgres_jobs","--","--ignored","--nocapture"],cwd=rust,env=env,check=True)
            subprocess.run(["go","test","./tests","-run","^Test(PushProviders|PushPreviewHTTPProofScopeVersionAndOutcomes|SharedPushPreviewHTTP)$","-count=1"],cwd=ROOT/"backend/go",env=env,check=True)
            subprocess.run([str(migrate),"--check"],env=env,check=True)
            # Go needs its other selected modules migrated too; core owner histories are identical.
            subprocess.run([str(go_migrate)],env=env,cwd=ROOT/"backend/go",check=True)
            jobs_durable(owned,url,go_binary,rust_binary)
            realtime_durable(owned,url,go_binary,rust_binary)
            secrets = [PASSWORD,url]
            with owned_server(rust_binary,owned/"rust-core.log","identity",url) as port:
                secrets += audit_core(port)
                secrets += notes_core(port)
                deadline=time.monotonic()+5
                while execute("SELECT count(*) FROM account.outbox WHERE delivered_at IS NULL") != "0":
                    assert time.monotonic()<deadline,"core delivery did not settle"
                    time.sleep(.03)
            execute("ALTER TABLE audit.inbox ADD CONSTRAINT pause_delivery CHECK (consumer <> 'audit.account') NOT VALID")
            with owned_server(rust_binary,owned/"rust-crash.log","identity",url,crash=True) as port:
                owner,session=identity(port,"postgres.restart")
                secrets += [session["access_token"],session["refresh_token"]]
                headers=bearer(session["access_token"])
                created=call(port,"/v1/accounts/bootstrap",body={"name":"SECRET durable"},headers=headers)
                assert created[0]==201
                account=created[2]["id"];path="/v1/accounts/"+account
                changed=call(port,path,"PATCH",body={"name":"SECRET persisted"},headers=headers+[("If-Match",'"1"')])
                assert changed[0]==200
                note_key=note_command()
                first_note=call(port,path+"/notes",body={"title":NOTE_TITLE},headers=headers+[("Idempotency-Key",note_key)])
                assert first_note[0]==201
                original_note=first_note[2]
                note_path=path+"/notes/"+original_note["note_id"]
                pending=execute(f"SELECT event_id FROM account.outbox WHERE account_id='{account}' ORDER BY sequence").splitlines()
                assert len(pending)==2
            execute("ALTER TABLE audit.inbox DROP CONSTRAINT pause_delivery")
            with owned_server(go_binary,owned/"go-handoff.log","identity",url) as port:
                go_owner,go_session=identity(port,"postgres.go.owner")
                secrets += [go_session["access_token"],go_session["refresh_token"]]
                headers=bearer(session["access_token"])
                assert call(port,"/v1/identity/me","GET",headers=headers)[2]==owner
                assert call(port,path,"GET",headers=headers)[2]==changed[2]
                assert call(port,path+"/note-receipts/"+note_key,"GET",headers=headers)[2]==original_note
                assert call(port,path+"/notes",body={"title":NOTE_TITLE},headers=headers+[("Idempotency-Key",note_key)])[2]==original_note
                update_key=note_command()
                updated_note=call(port,note_path,"PATCH",body={"title":"SECRET Go updated <>&\u2028\0"},headers=headers+[("Idempotency-Key",update_key),("If-Match",'"1"')])
                assert updated_note[0]==200
                delete_key=note_command()
                deleted_note=call(port,note_path,"DELETE",headers=headers+[("Idempotency-Key",delete_key),("If-Match",'"2"')])
                assert deleted_note[0]==200
                go_note_key=note_command()
                go_note=call(port,path+"/notes",body={"title":NOTE_TITLE},headers=headers+[("Idempotency-Key",go_note_key)])
                assert go_note[0]==201
                page=eventually(lambda:call(port,path+"/audit","GET",headers=headers),2)
                assert [r["event_id"] for r in page["entries"]]==pending
                assert call(port,"/v1/identity/sign-in",body={"login":"postgres.restart","password":PASSWORD})[0]==200
                renewed=call(port,"/v1/identity/renew",body={"refresh_token":session["refresh_token"]})
                assert renewed[0]==200
                session=renewed[2];headers=bearer(session["access_token"])
                secrets += [session["access_token"],session["refresh_token"]]
                assert call(port,"/v1/accounts/bootstrap",body={"name":"Ignored"},headers=headers)[2]==changed[2]
                assert call(port,path,"PATCH",body={"name":"Go continued"},headers=headers+[("If-Match",'"2"')])[0]==200
                saved=eventually(lambda:call(port,path+"/audit","GET",headers=headers),3)
            # Lose the producer acknowledgement after evidence/inbox committed in Go.
            execute(f"UPDATE account.outbox SET delivered_at=NULL,lease_owner=NULL,lease_until=NULL WHERE account_id='{account}'")
            with owned_server(rust_binary,owned/"rust-handoff.log","identity",url) as port:
                signed_in=call(port,"/v1/identity/sign-in",body={"login":"postgres.go.owner","password":PASSWORD})
                assert signed_in[0]==200 and signed_in[2]["principal"]==go_owner
                secrets += [signed_in[2]["access_token"],signed_in[2]["refresh_token"]]
                headers=bearer(session["access_token"])
                refusal(call(port,note_path,"GET",headers=headers),404,"notes.not_found")
                assert call(port,path+"/notes",body={"title":NOTE_TITLE},headers=headers+[("Idempotency-Key",note_key)])[2]==original_note
                assert call(port,note_path,"PATCH",body={"title":"SECRET Go updated <>&\u2028\0"},headers=headers+[("Idempotency-Key",update_key),("If-Match",'"1"')])[2]==updated_note[2]
                assert call(port,note_path,"DELETE",headers=headers+[("Idempotency-Key",delete_key),("If-Match",'"2"')])[2]==deleted_note[2]
                assert call(port,path+"/notes",body={"title":NOTE_TITLE},headers=headers+[("Idempotency-Key",go_note_key)])[2]==go_note[2]
                assert call(port,path,"GET",headers=headers)[2]["version"]==3
                deadline=time.monotonic()+8
                while execute(f"SELECT count(*) FROM account.outbox WHERE account_id='{account}' AND delivered_at IS NULL")!="0":
                    assert time.monotonic()<deadline,"replayed evidence failed deduplication"
                    time.sleep(.03)
                assert call(port,path+"/audit","GET",headers=headers)[2]==saved
                assert call(port,path,"PATCH",body={"name":"Rust continued"},headers=headers+[("If-Match",'"3"')])[0]==200
                eventually(lambda:call(port,path+"/audit","GET",headers=headers),4)
                assert call(port,"/v1/identity/sign-out",body={"refresh_token":session["refresh_token"]})[0]==204
            with owned_server(rust_binary,owned/"rust-final.log","identity",url) as port:
                refusal(call(port,"/v1/identity/me","GET",headers=bearer(session["access_token"])))
            module_urls={}
            for owner_name in ("account","audit","identity","notes","files","notifications"):
                database_name="separate_"+owner_name
                execute("CREATE DATABASE "+database_name)
                module_urls[owner_name.upper()+"_DATABASE_URL"]=url.replace("/mobile_foundry?","/"+database_name+"?")
            subprocess.run([str(migrate)],env={**env,**module_urls},check=True)
            subprocess.run(["cargo","test","--locked","-p","api-server","--test","push_dispatch","separate_database_dispatch","--","--ignored","--nocapture"],cwd=rust,
                env={**env,"TEST_IDENTITY_DATABASE_URL":module_urls["IDENTITY_DATABASE_URL"],"TEST_NOTIFICATIONS_DATABASE_URL":module_urls["NOTIFICATIONS_DATABASE_URL"]},check=True)
            subprocess.run(["cargo","test","--locked","-p","api-server","--test","push_http","separate_database_preview_http","--","--ignored","--nocapture"],cwd=rust,
                env={**env,"TEST_IDENTITY_DATABASE_URL":module_urls["IDENTITY_DATABASE_URL"],"TEST_NOTIFICATIONS_DATABASE_URL":module_urls["NOTIFICATIONS_DATABASE_URL"]},check=True)
            with owned_server(rust_binary,owned/"rust-separate.log","identity",url,module_urls=module_urls) as port:
                _,separate=identity(port,"postgres.separate")
                secrets += [separate["access_token"],separate["refresh_token"]]
                headers=bearer(separate["access_token"])
                created=call(port,"/v1/accounts/bootstrap",body={"name":"Separate stores"},headers=headers)
                assert created[0]==201
                note_base="/v1/accounts/"+created[2]["id"]
                separate_note=call(port,note_base+"/notes",body={"title":NOTE_TITLE},headers=headers+[("Idempotency-Key",note_command())])
                assert separate_note[0]==201
                assert call(port,note_base+"/notes/"+separate_note[2]["note_id"],"GET",headers=headers)[2]["title"]==NOTE_TITLE
                file=call(port,note_base+"/files/uploads",body=file_spec(),headers=headers+[("Idempotency-Key","separate-file")])
                assert file[0]==201
                file_path=note_base+"/files/"+file[2]["file"]["id"]
                assert file_binary(port,file_path+"/content","PUT",headers,FILE_DATA,[("Content-Type","image/png")])[0]==200
                assert call(port,file_path+"/complete",headers=headers+[("If-Match",'"1"')])[0]==200
                assert file_binary(port,file_path+"/content","GET",headers)[2]==FILE_DATA
                deadline=time.monotonic()+8
                while len(notification_inbox(port,note_base,headers)["items"])!=1:
                    assert time.monotonic()<deadline,"separate Notifications database delivery failed"
                    time.sleep(.025)
                eventually(lambda:call(port,"/v1/accounts/"+created[2]["id"]+"/audit","GET",headers=headers),3)
                device=installation_input();proof=installation_proof()
                installation_view(register_installation(port,device,separate,proof),1,status=201)
                observer=call(port,"/v1/identity/sign-in",body={"login":"postgres.separate","password":PASSWORD})
                assert observer[0]==200
                secrets += [proof,device["token"],observer[2]["access_token"],observer[2]["refresh_token"]]
                assert call(port,"/v1/identity/sign-out",body={"refresh_token":separate["refresh_token"]})[0]==204
                installation_view(retired_installation(port,"/v1/installations/"+device["installation_id"],observer[2],proof),2,registered=False,current=False)
            recovery_durable(rust_binary,go_binary,owned,url)
            files_durable(rust_binary,go_binary,owned,url,execute)
            notifications_durable(rust_binary,go_binary,owned,url,execute)
            installations_durable(rust_binary,go_binary,owned,url,execute)
            for log in owned.glob("*.log"):
                text=log.read_text()
                assert all(secret not in text for secret in secrets),"private input leaked into API logs"
            print("PostgreSQL: unmigrated refusal, Rust Audit/Notes HTTP flows, forced restart/pending delivery, Go↔Rust core state, historical Notes receipts and inbox fingerprint replay, persisted sign-out, separate module databases, private logs and clean shutdown passed",flush=True)


if __name__=="__main__":
    main()
