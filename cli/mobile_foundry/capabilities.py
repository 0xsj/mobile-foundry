"""Admit additive selections through explicit, independently verified inventories."""
import re

from .profiles import ProfileError, admitted_path, read_object, validate_profile


def discover_combinations(root, profiles):
    registry = read_object(admitted_path(root, "mobile-foundry.starters.json"))
    registrations = registry.get("capabilities", [])
    entries = registry.get("combinations", [])
    if not isinstance(registrations, list) or not isinstance(entries, list):
        raise ProfileError("Registry capabilities/combinations must be arrays.")
    capabilities = {}
    for entry in registrations:
        if not isinstance(entry, dict):
            raise ProfileError("Invalid capability registration.")
        identity = entry.get("id")
        if not isinstance(identity, str) or not re.fullmatch(r"[a-z][a-z0-9-]*", identity) or identity in capabilities:
            raise ProfileError("Invalid or duplicate capability ID.")
        if entry.get("status") not in {"candidate", "standalone", "planned"}:
            raise ProfileError(f"Invalid capability status: {identity}")
        capabilities[identity] = entry
    combinations = {}
    for entry in entries:
        if not isinstance(entry, dict) or entry.get("profile") not in profiles:
            raise ProfileError("Combination must name a registered profile.")
        selected = entry.get("capabilities")
        if not isinstance(selected, list) or not selected or not all(isinstance(item, str) for item in selected) or len(set(selected)) != len(selected):
            raise ProfileError("Combination must select unique capability IDs.")
        if any(item not in capabilities or capabilities[item]["status"] == "planned" for item in selected):
            raise ProfileError("Combination selects unknown/planned capabilities.")
        key = (entry["profile"], tuple(sorted(selected)))
        if key in combinations:
            raise ProfileError("Duplicate capability combination.")
        profile = read_object(admitted_path(root, entry.get("manifest")))
        base = profiles[entry["profile"]][0]
        if profile.get("id") != base["id"] or profile.get("shell") != base["shell"] or profile.get("capabilities") != base["capabilities"] + sorted(selected):
            raise ProfileError("Combination inventory does not match its registration.")
        if not isinstance(profile.get("platforms"), dict) or set(profile["platforms"]) != set(base["platforms"]):
            raise ProfileError("Combination platforms must match the base profile.")
        for platform in base["platforms"]:
            if not isinstance(profile["platforms"][platform], dict):
                raise ProfileError("Combination platform must be an object.")
            if any(profile["platforms"][platform].get(field) != base["platforms"][platform].get(field) for field in ("entry", "identityFiles")):
                raise ProfileError("Combination must retain the base product identity configuration.")
        errors = validate_profile(profile, root)
        if errors:
            raise ProfileError("; ".join(errors))
        combinations[key] = (profile, entry["manifest"])
    return capabilities, combinations


def select_combination(identity, selected, profiles, capabilities, combinations):
    if len(set(selected)) != len(selected):
        raise ProfileError("Do not repeat a --with capability.")
    for item in selected:
        if item not in capabilities:
            raise ProfileError(f"Unknown capability {item!r}. Available: {', '.join(sorted(capabilities)) or 'none'}.")
        if capabilities[item]["status"] == "planned":
            raise ProfileError(f"Capability {item!r} is planned and not yet available.")
    if not selected:
        return profiles[identity]
    key = (identity, tuple(sorted(selected)))
    if key not in combinations:
        raise ProfileError(f"No registered combination for {identity} with {', '.join(sorted(selected))}.")
    return combinations[key]
