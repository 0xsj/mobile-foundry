#!/usr/bin/env python3
"""Quiesced PostgreSQL + private object backups; restore only into an unused Compose project."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tarfile
import tempfile
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
FORMAT = "mobile-backup.v1"

def digest(path):
    result = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""): result.update(chunk)
    return result.hexdigest()

def invoke(command, *args, **kwargs):
    return subprocess.run(command + list(args), check=True, **kwargs)

def tar_admission(path):
    # Retained files are application-owned UUID objects; no links/devices or traversal.
    with tarfile.open(path, "r:gz") as archive:
        for member in archive:
            item = PurePosixPath(member.name)
            if item.is_absolute() or ".." in item.parts or not (member.isdir() or member.isfile()) or member.uid != 65532 or member.gid != 65532:
                raise ValueError("Unsafe object archive; restore refused")

def backup(command, target):
    if target.exists(): raise ValueError("Backup destination already exists")
    target.parent.mkdir(parents=True, exist_ok=True)
    owned = Path(tempfile.mkdtemp(prefix=".backup-", dir=target.parent)); owned.chmod(0o700)
    was_running = "api" in subprocess.check_output(command + ["ps", "--status", "running", "--services"], text=True).splitlines()
    stopped = False
    try:
        invoke(command, "stop", "api"); stopped = True
        for name, args in (("database.dump", ("exec", "-T", "postgres", "pg_dump", "-Fc", "-U", "mobile", "mobile")),
                           ("objects.tar.gz", ("run", "--rm", "-T", "--no-deps", "archive", "-czf", "-", "-C", "/objects", "."))):
            file = owned / name
            with file.open("xb") as output:
                file.chmod(0o600); invoke(command, *args, stdout=output)
        tar_admission(owned / "objects.tar.gz")
        manifest = {"format": FORMAT, "createdAt": datetime.now(timezone.utc).isoformat(),
            "files": {name: digest(owned / name) for name in ("database.dump", "objects.tar.gz")}}
        (owned / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
        (owned / "manifest.json").chmod(0o600)
        # Another writer's destination must not be replaced.
        target.mkdir(mode=0o700)  # Exclusive reservation; never replace another directory.
        for name in ("database.dump", "objects.tar.gz", "manifest.json"):
            with (owned / name).open("rb") as source, (target / name).open("xb") as output:
                (target / name).chmod(0o600); shutil.copyfileobj(source, output); output.flush(); os.fsync(output.fileno())
        descriptor=os.open(target,os.O_RDONLY)
        try: os.fsync(descriptor)
        finally: os.close(descriptor)
    finally:
        if was_running and stopped: invoke(command, "up", "-d", "--no-deps", "api")
        if owned is not None: shutil.rmtree(owned)

def restore(command, source, project):
    manifest = json.loads((source / "manifest.json").read_text())
    if manifest.get("format") != FORMAT or set(manifest.get("files", {})) != {"database.dump", "objects.tar.gz"}: raise ValueError("Unknown backup format")
    for name, expected in manifest["files"].items():
        if digest(source / name) != expected: raise ValueError("Backup checksum mismatch")
    tar_admission(source / "objects.tar.gz")
    existing = subprocess.check_output(["docker", "ps", "-aq", "--filter", "label=com.docker.compose.project=" + project], text=True).strip()
    volumes = subprocess.check_output(["docker", "volume", "ls", "-q", "--filter", "label=com.docker.compose.project=" + project], text=True).strip()
    networks = subprocess.check_output(["docker", "network", "ls", "-q", "--filter", "label=com.docker.compose.project=" + project], text=True).strip()
    reserved = [("volume", project + "_" + name) for name in ("postgres-data", "files-data", "caddy-data", "caddy-config")] + [("network", project + "_private")]
    for kind, name in reserved:
        if subprocess.run(["docker",kind,"inspect",name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
            raise ValueError("Restore resource name is already owned; kept unchanged")
    if existing or volumes or networks: raise ValueError("Restore target must be an unused Compose project; existing resources kept")
    invoke(command, "up", "-d", "--wait", "postgres")
    with (source / "database.dump").open("rb") as data:
        invoke(command, "exec", "-T", "postgres", "pg_restore", "--exit-on-error", "--no-owner", "--no-privileges", "-U", "mobile", "-d", "mobile", stdin=data)
    with (source / "objects.tar.gz").open("rb") as data:
        invoke(command, "run", "--rm", "-T", "--no-deps", "archive", "-xzf", "-", "-C", "/objects", stdin=data)
    invoke(command, "run", "--rm", "-T", "migrate", "--check")
    print("Restore verified; API remains stopped. Review the restored copy before starting it.")

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("create", "restore"))
    parser.add_argument("directory", type=Path)
    parser.add_argument("--project", required=True)
    parser.add_argument("--env-file", type=Path, required=True)
    args = parser.parse_args()
    if str(args.directory) in {"", "."}: parser.error("Supply an explicit backup directory")
    if not re.fullmatch(r"[a-z][a-z0-9_-]{1,48}", args.project): parser.error("Use a bounded lowercase Compose project name")
    command = ["docker", "compose", "--project-name", args.project, "--env-file", str(args.env_file.resolve()), "-f", str(ROOT / "infra/production/compose.yaml")]
    if args.action == "create": backup(command, args.directory.resolve())
    else: restore(command, args.directory.resolve(), args.project)

if __name__ == "__main__": main()
