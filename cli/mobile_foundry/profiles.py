"""Explicit registration, portable source paths and native dependency closure."""
from pathlib import Path, PurePosixPath
import json
import re

DEFAULT_SOURCE = Path(__file__).resolve().parents[2]


class ProfileError(ValueError):
    pass


def source_root(value):
    path = Path(value).absolute()
    if path.is_symlink():
        raise ProfileError("The source checkout cannot be a symlink.")
    path = path.resolve(strict=True)
    if not path.is_dir():
        raise ProfileError("The source must be an existing directory.")
    return path


def relative_path(value):
    if not isinstance(value, str) or not value or not re.fullmatch(r"[A-Za-z0-9_./-]+", value):
        raise ProfileError(f"Invalid inventory path: {value!r}")
    path = PurePosixPath(value)
    if path.is_absolute() or any(part in {"", ".", ".."} for part in value.split("/")):
        raise ProfileError(f"Nonportable inventory path: {value}")
    return path


def admitted_path(root, value):
    relative = relative_path(value)
    path = root
    for part in relative.parts:
        path = path / part
        if path.is_symlink():
            raise ProfileError(f"Source symlink is not supported: {value}")
    if not path.exists():
        raise ProfileError(f"Missing inventory source: {value}")
    if not path.is_file() and not path.is_dir():
        raise ProfileError(f"Source is not a regular file/directory: {value}")
    return path


def read_object(path):
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ProfileError(f"Expected a JSON object in {path.name}.")
    return value


def discover(root):
    registry = read_object(admitted_path(root, "mobile-foundry.starters.json"))
    if registry.get("schemaVersion") != 1:
        raise ProfileError("Unsupported starter registry schema; expected 1.")
    entries = registry.get("profiles")
    backends = registry.get("backends")
    if not isinstance(entries, list) or not entries or not isinstance(backends, list):
        raise ProfileError("Registry must contain profiles and backends arrays.")
    profiles = {}
    for entry in entries:
        if not isinstance(entry, dict):
            raise ProfileError("Invalid starter registration.")
        identity = entry.get("id")
        if not isinstance(identity, str) or not re.fullmatch(r"[a-z][a-z0-9-]*", identity) or identity in profiles:
            raise ProfileError("Invalid or duplicate starter profile ID.")
        profile = read_object(admitted_path(root, entry.get("manifest")))
        if profile.get("id") != identity:
            raise ProfileError(f"Profile registration/manifest ID mismatch: {identity}")
        errors = validate_profile(profile, root)
        if errors:
            raise ProfileError("; ".join(errors))
        profiles[identity] = (profile, entry["manifest"])
    seen = set()
    for backend in backends:
        if not isinstance(backend, dict) or not re.fullmatch(r"[a-z][a-z0-9-]*", str(backend.get("id", ""))) or backend["id"] in seen:
            raise ProfileError("Invalid or duplicate backend registration.")
        if backend.get("status") not in {"planned", "candidate", "standalone"}:
            raise ProfileError("Invalid backend status.")
        if backend["status"] != "planned":
            from .backends import read_backend
            read_backend(root, backend)
        seen.add(backend["id"])
    return profiles, backends


def validate_profile(profile, root):
    """Check the admitted local profile format; not a general Swift/Gradle parser."""
    errors = []

    def checked(value):
        try:
            return admitted_path(root, value)
        except (ProfileError, OSError) as error:
            errors.append(str(error))
            return None

    try:
        if not isinstance(profile, dict):
            raise ProfileError("Profile must be an object.")
        if profile.get("schemaVersion") != 1 or profile.get("status") not in {"candidate", "standalone"}:
            errors.append("Expected a schemaVersion 1 candidate/standalone inventory.")
        if not isinstance(profile.get("id"), str) or not re.fullmatch(r"[a-z][a-z0-9-]*", profile["id"]) or not isinstance(profile.get("shell"), str):
            raise ProfileError("Profile must declare its ID and shell.")
        if profile["shell"] not in {"daily", "workspace", "browse"}:
            raise ProfileError("Supported generated shells are daily, workspace and browse.")
        capabilities = profile.get("capabilities")
        if capabilities not in (["ui"], ["ui", "local-data"]):
            raise ProfileError("Supported inventories select UI or UI + local-data.")
        if not isinstance(profile.get("exclude"), list) or not all(isinstance(item, str) for item in profile["exclude"]):
            errors.append("Profile exclusions must be strings.")
        shared = profile["sharedSources"]
        platforms = profile["platforms"]
        if not isinstance(shared, list) or not isinstance(platforms, dict) or not platforms or set(platforms) - {"swift", "kotlin"}:
            raise ProfileError("Profile must select native platform objects and shared sources.")
        for value in shared:
            checked(value)
        for value in profile.get("requiredSharedFiles", []):
            checked(value)
            if not any(value == item or value.startswith(item + "/") for item in shared):
                errors.append(f"Required shared file absent from inventory: {value}")
        for name, platform in platforms.items():
            paths = platform["sources"]
            if not isinstance(paths, list) or len(paths) != len(set(paths)):
                raise ProfileError(f"{name}: invalid or duplicate sources.")
            for value in paths:
                checked(value)

            overlays = platform["overlays"]
            replacements = {}
            for overlay in overlays:
                source = checked(overlay["source"])
                destination = str(relative_path(overlay["destination"]))
                if destination in replacements:
                    errors.append(f"{name}: duplicate overlay destination: {destination}")
                if source and not source.is_file():
                    errors.append(f"{name}: overlay must be a file: {overlay['source']}")
                replacements[destination] = source

            def covered(value):
                return isinstance(value, str) and (value in replacements or any(value == item or value.startswith(item + "/") for item in paths))

            def selected_tree(value):
                return covered(value) or any(item.startswith(value + "/") for item in paths + list(replacements))

            def effective(value):
                return replacements[value] if value in replacements else checked(value)

            entry = effective(platform["entry"])
            for value in [platform["entry"], *platform["identityFiles"].values()]:
                checked(value)
                if not covered(value):
                    errors.append(f"{name}: required file absent from inventory: {value}")
            for value in platform.get("requiredFiles", []):
                effective(value)
                if not covered(value):
                    errors.append(f"{name}: required capability file absent from inventory: {value}")
            for fixture in ("kernel", "query", "ui") + (("local-notes",) if "local-data" in capabilities else ()):
                if f"contracts/fixtures/{fixture}" not in shared:
                    errors.append(f"{name}: missing {fixture} test fixtures")
            graph = {}
            if name == "swift":
                names = platform["packages"]
                if not isinstance(names, list) or len(names) != len(set(names)) or not all(re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*", item) for item in names):
                    raise ProfileError("swift: invalid package selection")
                available = set(names) | {"ProductApp"}
                for package in names:
                    base = f"frontend/swift/packages/{package}"
                    manifest = effective(base + "/Package.swift")
                    for suffix in ("Package.swift", "Sources", "Tests"):
                        if not (covered(base + "/" + suffix) if suffix == "Package.swift" else selected_tree(base + "/" + suffix)):
                            errors.append(f"swift: package source/tests not selected: {base}/{suffix}")
                    if manifest:
                        graph[package] = set(re.findall(r'\.package\(path:\s*"\.\./([^"]+)"\)', manifest.read_text()))
                if entry:
                    graph["ProductApp"] = set(re.findall(r'- package: (\w+)', entry.read_text()))
                start = "ProductApp"
            else:
                modules = platform["modules"]
                if not isinstance(modules, list) or len(modules) != len(set(modules)) or not all(re.fullmatch(r":[a-z][a-z0-9]*(?::[a-z][a-z0-9]*)*", item) for item in modules):
                    raise ProfileError("kotlin: invalid module selection")
                available = set(modules)
                for module in modules:
                    base = "frontend/kotlin/project/" + module.lstrip(":").replace(":", "/")
                    build = effective(base + "/build.gradle.kts")
                    for suffix in ("build.gradle.kts", "src"):
                        if not (covered(base + "/" + suffix) if suffix == "build.gradle.kts" else selected_tree(base + "/" + suffix)):
                            errors.append(f"kotlin: module source not selected: {base}/{suffix}")
                    if build:
                        graph[module] = set(re.findall(r'project\("([^"]+)"\)', build.read_text()))
                settings = [item for item in overlays if item["destination"] == "frontend/kotlin/project/settings.gradle.kts"]
                if len(settings) != 1:
                    errors.append("kotlin: requires one product-only settings overlay")
                else:
                    path = checked(settings[0]["source"])
                    if path:
                        includes = " ".join(re.findall(r'include\((.*?)\)', path.read_text(), re.S))
                        registered = set(re.findall(r'"(:[^\"]+)"', includes))
                        if registered != available:
                            errors.append("kotlin: settings overlay differs from selected modules")
                start = ":product"
            for owner, dependencies in graph.items():
                for missing in sorted(dependencies - available):
                    errors.append(f"{name}: {owner} depends on unselected {missing}")
            reached, pending = set(), [start]
            while pending:
                node = pending.pop()
                if node not in reached:
                    reached.add(node)
                    pending.extend(graph.get(node, set()))
            if available - reached:
                errors.append(f"{name}: unneeded modules selected: {sorted(available - reached)}")
            if entry:
                directory = entry.parent / ("Sources" if name == "swift" else "src/main")
                for path in directory.rglob("*"):
                    if path.is_symlink():
                        errors.append(f"{name}: source symlink: {path.relative_to(root)}")
                    elif path.is_file() and path.suffix in {".swift", ".kt", ".xml"}:
                        text = path.read_text()
                        if "FoundryCatalog" in text or "dev.mobilefoundry.catalog" in text:
                            errors.append(f"{name}: product source references catalog: {path.relative_to(root)}")
                product_directory = str(Path(platform["entry"]).parent / ("Sources" if name == "swift" else "src/main"))
                for destination, path in replacements.items():
                    if path and destination.startswith(product_directory + "/") and Path(destination).suffix in {".swift", ".kt", ".xml"}:
                        text = path.read_text()
                        if "FoundryCatalog" in text or "dev.mobilefoundry.catalog" in text:
                            errors.append(f"{name}: product overlay references catalog: {destination}")
    except (KeyError, TypeError, ValueError, AttributeError, OSError) as error:
        errors.append(f"Invalid inventory: {error}")
    return errors
