#!/usr/bin/env python3
"""Link the local command into PREFIX/bin without overwriting another command."""
import argparse
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def install(prefix):
    executable = ROOT / "bin/mobile-foundry"
    directory = Path(prefix).expanduser() / "bin"
    directory.mkdir(parents=True, exist_ok=True)
    destination = directory / executable.name
    if destination.is_symlink() and destination.resolve() == executable:
        return destination
    if destination.exists() or destination.is_symlink():
        raise FileExistsError(f"Refusing to replace an existing command: {destination}")
    destination.symlink_to(executable)
    return destination


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--prefix", type=Path, default=Path.home() / ".local")
    args = parser.parse_args()
    try:
        destination = install(args.prefix)
        print(f"Installed {destination}\nAdd its bin directory to PATH to run mobile-foundry from any folder.")
        return 0
    except OSError as error:
        print(f"mobile-foundry install: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
