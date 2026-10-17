"""Admit product inputs and copy an owned, independent native project."""
from dataclasses import dataclass
from datetime import datetime, timezone
import fnmatch
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import xml.etree.ElementTree as ET

from . import VERSION
from .connections import connection_base, configure_connection
from .documents import write_documents
from .profiles import ProfileError, admitted_path, relative_path

EXCLUDED_NAMES = {
    ".git", ".hg", ".svn", ".idea", ".vscode", ".cache", ".build", "build",
    "DerivedData", ".gradle", ".kotlin", "xcuserdata", "node_modules", ".venv",
    "__pycache__", ".pytest_cache", ".DS_Store", "local.properties", ".push-private",
}
EXCLUDED_SUFFIXES = {".xcuserstate", ".pyc", ".log", ".jks", ".keystore", ".p12", ".pfx", ".mobileprovision", ".pem", ".key", ".cer", ".p8"}


@dataclass(frozen=True)
class ProductInputs:
    name: str
    display_name: str
    app_id: str
    platforms: tuple[str, ...]
    ios_team: str = ""

    @classmethod
    def admit(cls, name, platform, display_name=None, app_id=None, ios_team=None):
        if not re.fullmatch(r"[a-z](?:[a-z0-9-]{0,78}[a-z0-9])?", name or ""):
            raise ProfileError("Use a name of 1–80 lowercase letters/digits/hyphens, starting with a letter and ending with a letter/digit.")
        if platform not in {"swift", "kotlin", "both", "none"}:
            raise ProfileError("Choose --platform swift, kotlin, both or none.")
        label = display_name if display_name is not None else name.replace("-", " ").title()
        if not 1 <= len(label) <= 60 or label != label.strip() or any(ord(char) < 32 or ord(char) == 127 for char in label) or any(token in label for token in ("//", "/*", "*/", "$", "#")):
            raise ProfileError("Display name must be 1–60 single-line characters without surrounding whitespace or xcconfig comment/substitution syntax.")
        if platform == "none" and app_id is not None:
            raise ProfileError("--app-id requires a native platform.")
        identity = (app_id if app_id is not None else "dev.example." + name.replace("-", "")) if platform != "none" else ""
        if platform != "none" and (len(identity) > 180 or not re.fullmatch(r"[a-z][a-z0-9]*(?:\.[a-z][a-z0-9]*)+", identity)):
            raise ProfileError("App ID must have at least two lowercase dot-separated segments, each starting with a letter and containing letters/digits (max 180 characters).")
        platforms = ("swift", "kotlin") if platform == "both" else () if platform == "none" else (platform,)
        if ios_team is not None and ("swift" not in platforms or not re.fullmatch(r"[A-Z0-9]{10}", ios_team)):
            raise ProfileError("--ios-team requires Swift and a ten-character uppercase Apple team ID.")
        return cls(name, label, identity, platforms, ios_team or "")


@dataclass(frozen=True)
class SourceFile:
    relative: str
    data: bytes
    executable: bool


def excluded(relative, patterns):
    path = Path(relative)
    if any(part in EXCLUDED_NAMES for part in path.parts) or path.suffix.lower() in EXCLUDED_SUFFIXES:
        return True
    name = path.name
    if name.lower().endswith((".private.json", ".private.yaml", ".private.yml")):
        return True
    if name.lower() in {"push.local.json", "service-account.json", "serviceaccountkey.json"} or name.lower().endswith(".service-account.json"):
        return True
    if (name == ".env" or name.startswith(".env.") or name.endswith(".env")) and not name.endswith((".example", ".sample", ".template")):
        return True
    return any(fnmatch.fnmatchcase(relative, pattern) or fnmatch.fnmatchcase("/" + relative, pattern) for pattern in patterns)


def plan_sources(root, profile, inputs):
    """Snapshot admitted regular files before creating output; never follow links."""
    files = {}
    patterns = profile["exclude"]

    def visit(path, relative):
        if excluded(relative, patterns):
            return
        info = path.lstat()
        if stat.S_ISLNK(info.st_mode):
            raise ProfileError(f"Source symlink is not supported: {relative}")
        if stat.S_ISDIR(info.st_mode):
            for child in sorted(path.iterdir()):
                visit(child, relative + "/" + child.name)
        elif stat.S_ISREG(info.st_mode):
            files[relative] = SourceFile(relative, path.read_bytes(), bool(info.st_mode & 0o111))
        else:
            raise ProfileError(f"Source is not a regular file: {relative}")

    for relative in profile["sharedSources"] + [item for platform in inputs.platforms for item in profile["platforms"][platform]["sources"]]:
        visit(admitted_path(root, relative), relative)
    for platform in inputs.platforms:
        for overlay in profile["platforms"][platform]["overlays"]:
            relative = str(relative_path(overlay["destination"]))
            path = admitted_path(root, overlay["source"])
            if not path.is_file() or excluded(relative, patterns):
                raise ProfileError(f"Invalid overlay source/destination: {relative}")
            info = path.stat()
            files[relative] = SourceFile(relative, path.read_bytes(), bool(info.st_mode & 0o111))
        selected = profile["platforms"][platform]
        for required in [selected["entry"], *selected["identityFiles"].values(), *selected.get("requiredFiles", [])]:
            if required not in files:
                raise ProfileError(f"Required product file was excluded: {required}")
    for required in profile.get("requiredSharedFiles", []):
        if required not in files:
            raise ProfileError(f"Required shared file was excluded: {required}")
    return tuple(files[key] for key in sorted(files))


def set_assignment(path, key, value):
    text, count = re.subn(r"^" + re.escape(key) + r"[ \t]*=.*$", lambda _: key + " =" + (" " + value if value else ""),
                          path.read_text(encoding="utf-8"), flags=re.M)
    if count != 1:
        raise ProfileError(f"Expected exactly one {key} assignment in {path.name}; source configuration has drifted.")
    path.write_text(text, encoding="utf-8")


def configure_product(destination, profile, inputs):
    if "swift" in inputs.platforms:
        path = destination / profile["platforms"]["swift"]["identityFiles"]["build"]
        set_assignment(path, "PRODUCT_APP_IDENTIFIER", inputs.app_id)
        set_assignment(path, "PRODUCT_DISPLAY_NAME", inputs.display_name)
        set_assignment(path, "DEVELOPMENT_TEAM", inputs.ios_team)
    if "kotlin" in inputs.platforms:
        config = profile["platforms"]["kotlin"]["identityFiles"]
        set_assignment(destination / config["build"], "applicationId", inputs.app_id)
        path = destination / config["label"]
        root = ET.fromstring(path.read_text(encoding="utf-8"))
        labels = root.findall("./string[@name='app_name']")
        if len(labels) != 1 or list(labels[0]):
            raise ProfileError("Expected one plain app_name string; source configuration has drifted.")
        escaped = inputs.display_name.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
        labels[0].text = '"' + escaped + '"'
        ET.indent(root, space="    ")
        path.write_text(ET.tostring(root, encoding="unicode") + "\n", encoding="utf-8")


def source_provenance(root, manifest, files):
    digest = hashlib.sha256()
    for item in files:
        digest.update(item.relative.encode() + b"\0" + (b"x" if item.executable else b"-") + b"\0")
        digest.update(hashlib.sha256(item.data).digest())
    revision, dirty = None, None
    try:
        result = subprocess.run(["git", "-C", str(root), "rev-parse", "HEAD"], capture_output=True, text=True, timeout=5)
        if result.returncode == 0:
            revision = result.stdout.strip()
            status = subprocess.run(["git", "-C", str(root), "status", "--porcelain"], capture_output=True, text=True, timeout=5)
            if status.returncode == 0:
                dirty = bool(status.stdout)
    except (OSError, subprocess.TimeoutExpired):
        pass
    return {"revision": revision, "dirty": dirty, "profileSha256": hashlib.sha256(manifest).hexdigest(),
            "contentSha256": digest.hexdigest(), "fileCount": len(files)}


def plan_backend_sources(root, selection, inputs):
    profile = selection.profile
    # Reuse the same regular-file, exclusion and symlink policy as native copies.
    backend_inputs = ProductInputs.admit(inputs.name, "none", inputs.display_name)
    files = {item.relative: item for item in plan_sources(root, {
        "sharedSources": profile["sources"] + profile["sharedSources"], "platforms": {}, "exclude": profile["exclude"],
    }, backend_inputs)}
    for overlay in profile["overlays"]:
        relative = str(relative_path(overlay["destination"]))
        path = admitted_path(root, overlay["source"])
        if not path.is_file() or excluded(relative, profile["exclude"]):
            raise ProfileError(f"Invalid backend overlay: {relative}")
        files[relative] = SourceFile(relative, path.read_bytes(), bool(path.stat().st_mode & 0o111))
    for required in profile["requiredFiles"]:
        if required not in files:
            raise ProfileError(f"Required backend file was excluded: {required}")
    return tuple(files[key] for key in sorted(files))


def configure_backend(destination, selection, inputs, files):
    old, new = selection.profile["sourceModule"], selection.module
    mod = destination / "backend/go/go.mod"
    text, count = re.subn(r"(?m)^module " + re.escape(old) + r"$", lambda _: "module " + new, mod.read_text())
    if count != 1:
        raise ProfileError("Expected one source Go module declaration; inventory drifted.")
    mod.write_text(text, encoding="utf-8")
    for item in files:
        if item.relative.startswith("backend/go/") and item.relative.endswith(".go"):
            path = destination / item.relative
            text = path.read_text().replace(old + "/", new + "/").replace('"' + old + '"', '"' + new + '"')
            # A module beginning with e.g. example.com can sort before "fmt".
            # Preserve existing import groups and canonical ordering without Go
            # installed; this profile contains ordinary comment-free imports.
            def sort_imports(match):
                groups = match.group(1).split("\n\n")
                ordered = []
                for group in groups:
                    rows = [line for line in group.splitlines() if line.strip()]
                    admitted = [re.fullmatch(r'\t(?:(\w+|\.)\s+)?"([^"\n]+)"', line) for line in rows]
                    if not all(admitted):
                        raise ProfileError("Backend import syntax changed; update the portable import rewriter.")
                    ordered.append("\n".join(row for _, row in sorted(zip((item[2] for item in admitted), rows))))
                return "import (\n" + "\n\n".join(ordered) + "\n)"
            text = re.sub(r"(?m)^import \(\n(.*?)\n\)", sort_imports, text, flags=re.S)
            path.write_text(text, encoding="utf-8")
    compose = destination / "backend/go/infra/local/compose.yaml"
    text, count = re.subn(r"(?m)^name: mobile-foundry-go$", lambda _: "name: " + inputs.name + "-api", compose.read_text())
    if count != 1:
        raise ProfileError("Expected one owned Compose project name; inventory drifted.")
    compose.write_text(text.replace("mobile_foundry", "app"), encoding="utf-8")
    production = destination / "backend/go/Makefile"
    production.write_text(production.read_text().replace("PROD_PROJECT ?= mobile-foundry-prod", "PROD_PROJECT ?= " + inputs.name + "-prod"), encoding="utf-8")



def create_project(root, profile, manifest_path, inputs, output, backend=None, api_base_url=None):
    base = connection_base(profile, backend, api_base_url)
    if not inputs.platforms and backend is None:
        raise ProfileError("--platform none requires a backend.")
    if bool(inputs.platforms) != bool(profile):
        raise ProfileError("Native platform selection must match the native profile.")
    for platform in inputs.platforms:
        if platform not in profile["platforms"]:
            raise ProfileError(f"Profile {profile['id']} does not support {platform}.")
    parent = Path(output).resolve(strict=True)
    if not parent.is_dir():
        raise ProfileError("The output parent must be an existing directory.")
    destination = parent / inputs.name
    if destination.is_relative_to(root) or root.is_relative_to(destination):
        raise ProfileError("Destination and source checkout must not contain one another; choose an output parent outside the source checkout.")
    if os.path.lexists(destination):
        raise ProfileError(f"Destination already exists: {destination}. Choose a new name/output parent.")
    native_files = plan_sources(root, profile, inputs) if profile else ()
    backend_files = plan_backend_sources(root, backend, inputs) if backend else ()
    merged = {item.relative: item for item in native_files}
    for item in backend_files:
        if item.relative in merged and merged[item.relative] != item:
            raise ProfileError(f"Native/backend source conflict: {item.relative}")
        merged[item.relative] = item
    files = tuple(merged[key] for key in sorted(merged))
    manifest = admitted_path(root, manifest_path if profile else backend.manifest).read_bytes()
    provenance = source_provenance(root, manifest, files)
    if backend:
        provenance["backendProfileSha256"] = hashlib.sha256(admitted_path(root, backend.manifest).read_bytes()).hexdigest()
    try:
        destination.mkdir(mode=0o755)
    except FileExistsError:
        raise ProfileError(f"Destination already exists: {destination}.") from None
    owned = destination.stat(follow_symlinks=False)
    try:
        for item in files:
            path = destination / item.relative
            path.parent.mkdir(parents=True, exist_ok=True)
            with path.open("xb") as stream:
                stream.write(item.data)
            path.chmod(0o755 if item.executable else 0o644)
        if profile:
            configure_product(destination, profile, inputs)
            configure_connection(destination, profile, inputs, base)
        if backend:
            configure_backend(destination, backend, inputs, backend_files)
        if backend:
            write_documents(destination, profile, inputs, backend=backend)
        else:
            write_documents(destination, profile, inputs)
        metadata = {
            "schemaVersion": 1, "generatorVersion": VERSION, "name": inputs.name,
            "displayName": inputs.display_name, "appId": inputs.app_id or None, "iosTeam": inputs.ios_team,
            "profile": profile["id"] if profile else None, "platforms": list(inputs.platforms), "backend": "go" if backend else "none",
            "shell": profile["shell"] if profile else None, "capabilities": profile["capabilities"] if profile else [],
            "createdAt": datetime.now(timezone.utc).isoformat(), "source": provenance,
        }
        if backend:
            metadata.update(goModule=backend.module, backendModules=backend.profile["modules"], nativeBackendConnection="go-connected" if base else "unconnected" if profile else None)
        if base:
            metadata["apiBaseURL"] = base
        (destination / "mobile-foundry.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
        return destination
    except BaseException:
        if os.path.lexists(destination):
            current = destination.stat(follow_symlinks=False)
            if (current.st_dev, current.st_ino) != (owned.st_dev, owned.st_ino):
                raise ProfileError("Generation failed and the destination was replaced externally; it was left untouched.") from None
            shutil.rmtree(destination)
        raise
