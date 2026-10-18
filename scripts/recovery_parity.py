#!/usr/bin/env python3
"""Core recovery conformance using private local SMTP capture and owned API processes."""
import contextlib
import json
import queue
import re
import socketserver
import tempfile
import threading
from pathlib import Path
from collections import Counter
from backend_parity import ROOT,owned_server,run
from identity_parity import PASSWORD,bearer,call,refusal
from account_parity import identity
from audit_parity import eventually,PERSONAL,rows

@contextlib.contextmanager
def capture(directory):
    messages=queue.Queue();tokens=[]
    class SMTP(socketserver.StreamRequestHandler):
        def handle(self):
            try:self.protocol()
            except (ConnectionResetError,BrokenPipeError,TimeoutError):pass
        def protocol(self):
            self.connection.settimeout(8)
            self.wfile.write(b"220 capture\r\n");recipient=None
            while True:
                line=self.rfile.readline(16385)
                if not line:break
                if line.startswith(b"EHLO") or line.startswith(b"HELO"):self.wfile.write(b"250 capture\r\n")
                elif line.startswith(b"MAIL FROM"):self.wfile.write(b"250 sender\r\n")
                elif line.startswith(b"RCPT TO"):
                    recipient=line.decode().split('<',1)[1].split('>',1)[0];self.wfile.write(b"250 recipient\r\n")
                elif line==b"DATA\r\n":
                    self.wfile.write(b"354 data\r\n");data=b""
                    while True:
                        part=self.rfile.readline(16385)
                        if part==b".\r\n":break
                        if not part or len(data)+len(part)>16384:return
                        data+=part
                    text=data.decode();code=re.search(r"Code: ([ep]1\.[A-Za-z0-9_-]{43})\r\n",text)
                    purpose=re.search(r"Purpose: (verify_email|reset_password)\r\n",text)
                    assert code and purpose,"invalid private mail shape"
                    token=code.group(1);tokens.append(token);messages.put((recipient,purpose.group(1),token));self.wfile.write(b"250 accepted\r\n")
                elif line==b"QUIT\r\n":self.wfile.write(b"221 bye\r\n");break
                else:self.wfile.write(b"500 refused\r\n")
    class Server(socketserver.ThreadingTCPServer):
        allow_reuse_address=False
        daemon_threads=False
    server=Server(("127.0.0.1",0),SMTP);worker=threading.Thread(target=server.serve_forever,daemon=False);worker.start()
    config=directory/"mail.private.json"
    config.write_text(json.dumps({"Address":f"127.0.0.1:{server.server_address[1]}","From":"sender@example.test","Mode":"development"}))
    def receive(address,purpose):
        recipient,kind,token=messages.get(timeout=8)
        assert (recipient,kind)==(address,purpose),"unexpected private mail recipient/purpose"
        return token
    try:yield config,receive,tokens
    finally:
        server.shutdown();server.server_close();worker.join(timeout=3);assert not worker.is_alive()


def core(port,mail,login="secret.recovery.owner",address="owner@example.test"):
    profile,session=identity(port,login);secrets=[PASSWORD,address,session["access_token"],session["refresh_token"]]
    def post(path,body,auth=None):return call(port,"/v1/identity/"+path,body=body,headers=[] if auth is None else bearer(auth))
    def me(access):return call(port,"/v1/identity/me","GET",headers=bearer(access))
    def contact(access):return call(port,"/v1/identity/email","GET",headers=bearer(access))
    assert contact(session["access_token"])[2]=={"verified":False}
    refusal(post("email/request",{"email":address,"password":"SECRET-wrong-password-123"},session["access_token"]))
    assert post("email/request",{"email":" "+address.upper()+" ","password":PASSWORD},session["access_token"])[0]==204
    verification=mail(address,"verify_email");assert post("email/verify",{"token":verification})[0]==204
    refusal(post("email/verify",{"token":verification}),400,"identity.challenge_refused")
    view=contact(session["access_token"])[2];assert view["verified"] and view["email"]==address and view["verified_at"].endswith('Z')
    refusal(post("email/request",{"email":"different@example.test","password":PASSWORD},session["access_token"]),409,"identity.contact_conflict")
    other,other_session=identity(port,login+".other");secrets += [other_session["access_token"],other_session["refresh_token"]]
    assert post("email/request",{"email":address,"password":PASSWORD},other_session["access_token"])[0]==204
    collision=mail(address,"verify_email");refusal(post("email/verify",{"token":collision}),409,"identity.contact_conflict");assert contact(other_session["access_token"])[2]=={"verified":False}
    next_password="SECRET-new-password-123";third="SECRET-recovered-password-123";fourth="SECRET-final-password-123";secrets += [next_password,third,fourth]
    refusal(post("password/change",{"current_password":"SECRET-wrong-password-123","new_password":next_password},session["access_token"]))
    assert post("password/change",{"current_password":PASSWORD,"new_password":next_password},session["access_token"])[0]==204
    refusal(me(session["access_token"]));refusal(post("sign-in",{"login":login,"password":PASSWORD}))
    response=post("sign-in",{"login":login,"password":next_password});assert response[0]==200;session=response[2];secrets += [session["access_token"],session["refresh_token"]]
    unknown=post("password/request-reset",{"email":"unknown@example.test"});known=post("password/request-reset",{"email":address})
    assert unknown[0]==known[0]==202 and unknown[2]==known[2]=={"message":"If an eligible address exists, recovery instructions may arrive."}
    code=mail(address,"reset_password");refusal(post("password/reset",{"token":verification,"password":third}),400,"identity.challenge_refused")
    assert post("password/reset",{"token":code,"password":third})[0]==204
    refusal(post("password/reset",{"token":code,"password":third}),400,"identity.challenge_refused");refusal(me(session["access_token"]))
    refusal(post("sign-in",{"login":login,"password":next_password}));response=post("sign-in",{"login":login,"password":third});assert response[0]==200;session=response[2];secrets += [session["access_token"],session["refresh_token"]]
    for _ in range(2):assert post("password/request-reset",{"email":address})[0]==202
    older=mail(address,"reset_password");newer=mail(address,"reset_password")
    refusal(post("password/reset",{"token":older,"password":fourth}),400,"identity.challenge_refused")
    assert post("password/reset",{"token":newer,"password":fourth})[0]==204;refusal(me(session["access_token"]))
    response=post("sign-in",{"login":login,"password":fourth});assert response[0]==200;session=response[2];secrets += [session["access_token"],session["refresh_token"]]
    page=eventually(lambda:call(port,PERSONAL,"GET",headers=bearer(session["access_token"])),12);rows(page,profile["id"])
    counts=Counter(r["action"] for r in page["entries"])
    assert counts["identity.email.verified"]==1 and counts["identity.password.changed"]==1 and counts["identity.password.recovered"]==2
    revoked=[r for r in page["entries"] if r["action"]=="identity.session.revoked"]
    assert Counter(r["reason"] for r in revoked)=={"password_change":1,"password_reset":2}
    assert address not in json.dumps(page) and all(secret not in json.dumps(page) for secret in secrets)
    assert contact(session["access_token"])[2]==view
    return secrets


def wire_cases(port):
    _,session=identity(port,"secret.recovery.wire");auth=bearer(session["access_token"])
    for case in json.loads((ROOT/"contracts/fixtures/backend-recovery/http.json").read_text()):
        headers=(auth if case["authenticated"] else [])+case.get("headers",[])
        response=call(port,case["path"],case["method"],body=case.get("body"),wire=case.get("wire"),headers=headers)
        assert response[0]==case["status"],f'{case["name"]}: status differs'
        if "code" in case:assert response[2]["code"]==case["code"],case["name"]
    return [session["access_token"],session["refresh_token"]]


def durable(rust_binary,go_binary,owned,url):
    with capture(owned) as (config,receive,tokens):
        with owned_server(rust_binary,owned/"rust-recovery-crash.log","identity",url,crash=True,mail_config=config) as port:
            owner,session=identity(port,"secret.recovery.durable")
            secrets=[PASSWORD,session["access_token"],session["refresh_token"]]
            assert call(port,"/v1/identity/email/request",body={"email":"durable@example.test","password":PASSWORD},headers=bearer(session["access_token"]))[0]==204
            verification=receive("durable@example.test","verify_email")
        with owned_server(go_binary,owned/"go-recovery-handoff.log","identity",url,mail_config=config) as port:
            assert call(port,"/v1/identity/email/verify",body={"token":verification})[0]==204
            assert call(port,"/v1/identity/password/request-reset",body={"email":"durable@example.test"})[0]==202
            reset=receive("durable@example.test","reset_password")
        next_password="SECRET-durable-password-123";secrets.append(next_password)
        with owned_server(rust_binary,owned/"rust-recovery-restored.log","identity",url,mail_config=config) as port:
            assert call(port,"/v1/identity/password/reset",body={"token":reset,"password":next_password})[0]==204
            refusal(call(port,"/v1/identity/me","GET",headers=bearer(session["access_token"])))
            refusal(call(port,"/v1/identity/password/reset",body={"token":reset,"password":next_password}),400,"identity.challenge_refused")
            signed=call(port,"/v1/identity/sign-in",body={"login":"secret.recovery.durable","password":next_password});assert signed[0]==200
            secrets += [signed[2]["access_token"],signed[2]["refresh_token"]]
            page=eventually(lambda:call(port,PERSONAL,"GET",headers=bearer(signed[2]["access_token"])),6)
            rows(page,owner["id"]);assert any(r.get("reason")=="password_reset" for r in page["entries"])
            assert call(port,"/v1/identity/email","GET",headers=bearer(signed[2]["access_token"]))[2]["email"]=="durable@example.test"
    for log in ["rust-recovery-crash.log","go-recovery-handoff.log","rust-recovery-restored.log"]:
        assert all(secret not in (owned/log).read_text() for secret in secrets+tokens+["durable@example.test"])
    print("Recovery PostgreSQL: unconsumed challenge survived SIGKILL, Rust→Go verification and Go→Rust reset/revocation/Audit passed",flush=True)


def main():
    run(["go","test","./parity","-run","^TestSharedRecovery","-count=1"],ROOT/"backend/go")
    run(["cargo","test","--locked","-p","identity"],ROOT/"backend/rust")
    run(["cargo","test","--locked","-p","audit","--test","admission"],ROOT/"backend/rust")
    with tempfile.TemporaryDirectory(prefix="mobile-recovery-parity-") as temporary:
        owned=Path(temporary);go_binary=owned/"go-api";run(["go","build","-o",str(go_binary),"./cmd/server"],ROOT/"backend/go")
        run(["cargo","build","--locked","-p","api-server"],ROOT/"backend/rust")
        for name,binary in [("Go",go_binary),("Rust",ROOT/"backend/rust/target/debug/api-server")]:
            with capture(owned) as (config,receive,tokens):
                with owned_server(binary,owned/(name+"-core.log"),"identity",mail_config=config) as port:secrets=core(port,receive)
            assert all(s not in (owned/(name+"-core.log")).read_text() for s in secrets+tokens)
            with owned_server(binary,owned/(name+"-wire.log"),"identity") as port:secrets=wire_cases(port)
            assert all(s not in (owned/(name+"-wire.log")).read_text() for s in secrets)
            print(f"{name} recovery: local SMTP lifecycle, 21 HTTP cases, private logs and clean shutdown passed",flush=True)
    print("recovery-parity: core contact/password recovery passed; live provider acceptance remains separate")


if __name__=="__main__":main()
