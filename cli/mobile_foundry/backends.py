"""Registered backend inventories and portable Go module identity admission."""
from dataclasses import dataclass
import fnmatch
from pathlib import Path
import re

from .profiles import ProfileError, admitted_path, read_object, relative_path


def admit_go_module(value):
    # A deliberately bounded ASCII subset; initialization runs without Go installed.
    if not isinstance(value, str) or len(value) > 240 or not re.fullmatch(r"[a-z0-9]+(?:[.-][a-z0-9]+)+(?:/[a-z0-9][a-z0-9._-]*)+", value):
        raise ProfileError("Go module must be a lowercase domain/path, for example github.com/your-org/canopy/backend (max 240 characters).")
    for segment in value.split("/"):
        if segment.endswith(".") or segment.split(".")[0].upper() in {"CON", "PRN", "AUX", "NUL", *(f"COM{i}" for i in range(1, 10)), *(f"LPT{i}" for i in range(1, 10))}:
            raise ProfileError("Go module contains an unsupported path segment.")
    suffix = value.rsplit("/", 1)[1]
    if re.fullmatch(r"v[0-9]+", suffix) and (suffix[1:].startswith("0") or int(suffix[1:]) < 2):
        raise ProfileError("Go major-version suffixes must be v2 or higher without leading zeros.")
    return value


@dataclass(frozen=True)
class BackendSelection:
    profile: dict
    manifest: str
    module: str


def read_backend(root, registration):
    profile = read_object(admitted_path(root, registration.get("manifest")))
    if profile.get("id") != registration["id"] or profile.get("status") != registration["status"]:
        raise ProfileError("Backend registration and inventory must match.")
    errors = validate_backend(profile, root)
    if errors:
        raise ProfileError("; ".join(errors))
    return profile


def select_backend(root, registrations, identity, module, name):
    if identity == "none":
        if module is not None:
            raise ProfileError("--go-module requires --backend go.")
        return None
    registration = next((r for r in registrations if r["id"] == identity), None)
    if registration is None:
        raise ProfileError(f"Backend {identity!r} is not registered.")
    if registration["status"] == "planned":
        raise ProfileError(f"Backend {identity!r} is planned and not yet available.")
    profile = read_backend(root, registration)
    return BackendSelection(profile, registration["manifest"], admit_go_module(module if module is not None else f"example.com/{name}/backend"))


def validate_backend(profile, root):
    """Validate the declared Go closure; real compiler/runtime acceptance is separate."""
    errors = []
    try:
        if profile.get("schemaVersion") != 1 or profile.get("id") != "go" or profile.get("status") not in {"candidate", "standalone"}:
            raise ProfileError("Expected a registered schemaVersion 1 Go backend inventory.")
        if profile.get("directory") != "backend/go" or profile.get("modules") != ["health", "identity", "account", "audit", "notes", "files", "notifications"]:
            raise ProfileError("This Go profile selects Health, Identity, Account, Audit, Notes, Files and Notifications.")
        sources, shared, required, exclusions = (profile[key] for key in ("sources", "sharedSources", "requiredFiles", "exclude"))
        if any(not isinstance(items, list) or not all(isinstance(item, str) for item in items) or len(items) != len(set(items)) for items in (sources, shared, required, exclusions)):
            raise ProfileError("Backend source, shared, required and exclusion lists must contain unique strings.")
        if any(not path.startswith("backend/go/") for path in sources):
            raise ProfileError("Backend sources must stay within backend/go.")
        for path in sources + shared:
            admitted_path(root, path)
        selected = {}
        for source in sources + shared:
            path = admitted_path(root, source)
            candidates = [path] if path.is_file() else path.rglob("*")
            for candidate in candidates:
                if candidate.is_file() and not candidate.is_symlink():
                    selected[str(candidate.relative_to(root))] = candidate
        overlay_destinations = set()
        for overlay in profile["overlays"]:
            source = admitted_path(root, overlay["source"])
            destination = str(relative_path(overlay["destination"]))
            if not source.is_file() or not destination.startswith("backend/go/") or destination in overlay_destinations:
                raise ProfileError("Backend overlays must have unique backend/go destinations and regular sources.")
            overlay_destinations.add(destination)
            selected[destination] = source
        for path in required:
            if path not in selected:
                errors.append(f"Required backend file absent from inventory: {path}")
        mod = selected.get("backend/go/go.mod")
        if not mod:
            raise ProfileError("Backend must select go.mod.")
        text = mod.read_text()
        expected = profile["sourceModule"]
        if len(re.findall(r"^module " + re.escape(expected) + r"$", text, re.M)) != 1 or "replace " in text:
            errors.append("Go source module drift or local replace directive.")
        if not re.search(r"^go " + re.escape(profile["goVersion"]) + r"$", text, re.M):
            errors.append("Go inventory toolchain differs from go.mod.")
        # Import and embed omissions are common silent extraction failures.
        for relative, path in selected.items():
            if not relative.endswith(".go"):
                continue
            source = path.read_text()
            for imported in re.findall(r'"(' + re.escape(expected) + r'/[^"\s]+)"', source):
                directory = "backend/go/" + imported[len(expected) + 1:]
                if not any(item.startswith(directory + "/") and item.endswith(".go") for item in selected):
                    errors.append(f"{relative}: unselected Go import {imported}")
            for directive in re.findall(r"(?m)^//go:embed (.+)$", source):
                for pattern in directive.split():
                    prefix = str(Path(relative).parent) + "/"
                    if not any(fnmatch.fnmatchcase(item, prefix + pattern) for item in selected):
                        errors.append(f"{relative}: unselected embedded migration {pattern}")
        if "contracts/fixtures/account/names.json" not in selected:
            errors.append("Go Account tests require the copied name fixture.")
    except (KeyError, TypeError, ValueError, AttributeError, OSError) as error:
        errors.append(f"Invalid backend inventory: {error}")
    return errors
