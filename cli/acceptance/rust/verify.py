#!/usr/bin/env python3
"""Verify a generated Rust project's real Make runner without importing this checkout."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import tempfile
import time
import tomllib

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("project",type=Path);args=parser.parse_args()
    project=args.project.resolve(strict=True);api=project/"backend/rust"
    metadata=json.loads((project/"mobile-foundry.json").read_text());assert metadata["backend"]=="rust"
    package=tomllib.loads((api/"cmd/server/Cargo.toml").read_text())["package"]["name"]
    assert package==metadata["rustPackage"]
    assert not (project/"backend/go").exists()
    source_root=Path(__file__).resolve().parents[3]
    for path in api.rglob("*"):
        if any(part in {"target",".git","__pycache__",".data"} for part in path.relative_to(api).parts):continue
        if path.is_file() and path.suffix in {".rs",".toml",".py",".yaml"}:
            assert str(source_root) not in path.read_text(),"source checkout path in generated build input"
    spec=importlib.util.spec_from_file_location("generated_rust_smoke",api/"scripts/smoke.py")
    smoke=importlib.util.module_from_spec(spec);spec.loader.exec_module(smoke);assert smoke.ROOT==api
    subprocess.run(["cargo","build","--locked","--workspace"],cwd=api,check=True)
    with socket.socket() as reservation:reservation.bind(("127.0.0.1",0));port=reservation.getsockname()[1]
    env=smoke.environment(HTTP_ADDR=f"127.0.0.1:{port}");env.pop("AUTH_MODE")
    hidden=[]
    with tempfile.TemporaryFile(mode="w+") as logs:
        process=subprocess.Popen(["make","rust-dev"],cwd=project,env=env,stdout=logs,stderr=logs,start_new_session=True)
        try:
            base=f"http://127.0.0.1:{port}";deadline=time.monotonic()+25
            while True:
                assert process.poll() is None,"generated Rust runner exited before ready"
                try:
                    if smoke.request(base,"GET","/v1/health/ready")[0]==200:break
                except OSError:pass
                assert time.monotonic()<deadline,"generated Rust runner readiness timed out";time.sleep(.03)
            hidden.extend(smoke.exercise(base))
        finally:
            try:os.killpg(process.pid,signal.SIGTERM)
            except ProcessLookupError:pass
            try:process.wait(timeout=15)
            except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait();raise
            logs.seek(0);text=logs.read();assert all(secret not in text for secret in hidden),"private input in generated runner logs"
        try:
            with socket.create_connection(("127.0.0.1",port),timeout=.2):raise AssertionError("owned API survived Make shutdown")
        except ConnectionRefusedError:pass
    print("Generated Rust acceptance: configured Cargo/lock identity, real Make Identity default, scoped APIs, receipt/CAS, bytes/inbox, renewal/sign-out and cleanup passed")

if __name__=="__main__":main()
