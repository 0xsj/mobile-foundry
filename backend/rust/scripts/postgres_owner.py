"""Disposable PostgreSQL ownership adapted from the Go profile; no app DSN is used."""
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

