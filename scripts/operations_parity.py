#!/usr/bin/env python3
"""Owned local API/metrics/proxy acceptance, separate from portable Docker recovery."""
import os
from pathlib import Path
import socket
import subprocess
import tempfile
from backend_parity import ROOT, owned_server, exchange, run
from identity_parity import call, gate

def free_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1",0));return s.getsockname()[1]

def closed(port):
    try:
        with socket.create_connection(("127.0.0.1",port),timeout=.2):raise AssertionError("metrics listener survived shutdown")
    except ConnectionRefusedError: pass

def exercise(binary,folder,name):
    metric=free_port();log=folder/(name+"-untrusted.log")
    with owned_server(binary,log,"identity",metrics_addr=f"127.0.0.1:{metric}") as port:
        assert call(port,"/metrics","GET")[0]==404
        gate(port)  # Rotating spoofed headers still shares the accepted peer's gate.
        assert call(port,"/v1/health/ready?token=PRIVATE-query","CUSTOM",headers=[("Authorization","Bearer PRIVATE-token")])[0]==405
        status,headers,body=exchange(metric,{"method":"GET","path":"/metrics"})
        text=body.decode();assert status==200 and headers["content-type"].startswith("text/plain; version=0.0.4")
        assert 'api_http_requests_total{method="OTHER",status="4xx"} 1' in text
        assert 'api_http_requests_total{method="POST",status="4xx"} 13' in text
        assert "api_http_active 0\n" in text and "api_http_duration_seconds_count" in text
        assert "PRIVATE" not in text and "192.0.2." not in text
        for method,path in [("HEAD","/metrics"),("POST","/metrics"),("GET","/unknown")]:assert exchange(metric,{"method":method,"path":path})[0]==404
    closed(metric);assert "PRIVATE" not in log.read_text()
    metric=free_port();log=folder/(name+"-trusted.log")
    with owned_server(binary,log,"identity",metrics_addr=f"127.0.0.1:{metric}",proxy_cidrs="127.0.0.0/8") as port:
        for headers in [[],[("X-Forwarded-For","")],[("X-Forwarded-For","203.0.113.1, 192.0.2.1")],[("X-Forwarded-For","203.0.113.1"),("X-Forwarded-For","203.0.113.2")]]:
            status,response,body=exchange(port,{"method":"GET","path":"/v1/health/ready","headers":headers})
            assert status==400 and response["cache-control"]=="no-store"
        for index in range(13):
            assert call(port,"/v1/identity/sign-in",body={"login":"!","password":"bad"},headers=[("X-Forwarded-For",f"203.0.113.{index+1}")])[0]==401
        for index in range(13):
            result=call(port,"/v1/identity/sign-in",body={"login":"!","password":"bad"},headers=[("X-Forwarded-For","203.0.113.250")])
            assert result[0]==(401 if index<12 else 429)
    closed(metric)
    # A reserved monitor address must refuse startup and leave the API unbound.
    with socket.socket() as held:
        held.bind(("127.0.0.1",0));held.listen();port=free_port()
        env={k:os.environ[k] for k in ("PATH","HOME","TMPDIR") if k in os.environ}
        env.update(APP_ENV="test",STORAGE="memory",AUTH_MODE="deny",HTTP_ADDR=f"127.0.0.1:{port}",METRICS_ADDR=f"127.0.0.1:{held.getsockname()[1]}")
        result=subprocess.run([str(binary)],env=env,capture_output=True,text=True,timeout=20)
        assert result.returncode==1;closed(port)
    print(name+" operations: trusted/untrusted source gates, private bounded metrics, bind refusal and joined shutdown passed",flush=True)

def main():
    run(["go","test","-race","./parity","-run","^TestSharedTrustedProxy$","-count=1"],ROOT/"backend/go")
    run(["cargo","test","--locked","-p","httpx","-p","telemetry","-p","runtime-config","-p","runtime-server","-p","api-maintenance"],ROOT/"backend/rust")
    with tempfile.TemporaryDirectory(prefix="mobile-operations-parity-") as temporary:
        folder=Path(temporary);go=folder/"go-api"
        run(["go","build","-o",str(go),"./cmd/server"],ROOT/"backend/go")
        run(["cargo","build","--locked","--workspace"],ROOT/"backend/rust")
        for name,binary in [("Go",go),("Rust",ROOT/"backend/rust/target/debug/api-server")]:exercise(binary,folder,name)
    print("Operations parity: 17 shared proxy cases and actual API/metrics processes; Docker/backup acceptance is separate",flush=True)

if __name__=="__main__":main()
