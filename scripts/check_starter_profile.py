#!/usr/bin/env python3
"""Validate a local native inventory; does not generate or build apps."""
import argparse
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "cli"))
from mobile_foundry.profiles import validate_profile, discover
from mobile_foundry.capabilities import discover_combinations
from mobile_foundry.backends import validate_backend


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", type=Path, help="Check one inventory; default checks all registrations/combinations")
    args = parser.parse_args()
    try:
        if args.profile:
            profile = json.loads(args.profile.read_text())
            errors = validate_backend(profile, ROOT) if profile.get("id") in {"go","rust"} else validate_profile(profile, ROOT)
        else:
            profiles, _ = discover(ROOT)
            discover_combinations(ROOT, profiles)
            errors = []
    except (OSError, ValueError, TypeError) as error:
        errors = [f"Invalid inventory: {error}"]
    for error in errors:
        print(error)
    print(f"starter-check: {len(errors)} failures (inventory only; native readiness requires independent verification)")
    return bool(errors)


if __name__ == "__main__":
    raise SystemExit(main())
