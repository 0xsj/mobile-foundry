"""Terminal command projection for registered local native profiles."""
import argparse
from pathlib import Path
import sys

from . import VERSION
from .capabilities import discover_combinations, select_combination
from .backends import select_backend
from .connections import connection_capability
from .doctor import inspect
from .generation import ProductInputs, create_project
from .profiles import DEFAULT_SOURCE, ProfileError, discover, source_root


def parser():
    root = argparse.ArgumentParser(prog="mobile-foundry", description="Copy independent native products and Go/Rust APIs from registered local profiles.")
    root.add_argument("--version", action="version", version=VERSION)
    commands = root.add_subparsers(dest="command")
    listing = commands.add_parser("list", help="List native profiles and planned backend combinations")
    listing.add_argument("--source", default=str(DEFAULT_SOURCE))
    init = commands.add_parser("init", help="Copy a named project into an existing output parent (default: cwd)")
    init.add_argument("name")
    init.add_argument("--platform", choices=("swift", "kotlin", "both", "none"))
    init.add_argument("--profile", help="Registered shell profile (default: native-daily); omit with platform none")
    init.add_argument("--with", dest="capabilities", action="append", default=[], metavar="CAPABILITY", help="Add a registered capability (for example local-data)")
    init.add_argument("--backend", default="none")
    init.add_argument("--go-module", help="Go domain/path module identity; requires --backend go")
    init.add_argument("--rust-package", help="Rust API package identity; requires --backend rust")
    init.add_argument("--api-base-url", help="Public API base for a connected profile; defaults to Debug loopback http://127.0.0.1:8080/v1")
    init.add_argument("--output", default=".", help="Existing parent directory; output is PARENT/NAME")
    init.add_argument("--source", default=str(DEFAULT_SOURCE))
    init.add_argument("--display-name")
    init.add_argument("--app-id")
    init.add_argument("--ios-team")
    doctor = commands.add_parser("doctor", help="Report selected local toolchains without installing or building")
    doctor.add_argument("--platform", choices=("swift", "kotlin", "both", "none"), default="both")
    doctor.add_argument("--backend", choices=("none", "go", "rust"), default="none")
    return root


def choose(requested, choices, flag):
    if requested is not None:
        if requested not in choices:
            raise ProfileError(f"Unknown {flag} {requested!r}. Available: {', '.join(choices)}.")
        return requested
    if len(choices) == 1:
        return choices[0]
    if not sys.stdin.isatty() or not sys.stdout.isatty():
        raise ProfileError(f"Choose --{flag}. Available: {', '.join(choices)}.")
    print(f"Choose {flag}:")
    for index, choice in enumerate(choices, 1):
        print(f"  {index}. {choice}")
    while True:
        answer = input("Selection: ").strip()
        if answer in choices:
            return answer
        if answer.isdigit() and 1 <= int(answer) <= len(choices):
            return choices[int(answer) - 1]
        print("Enter a listed name or number.")


def main(argv=None):
    arguments = parser()
    args = arguments.parse_args(argv)
    if args.command is None:
        arguments.print_help()
        return 0
    try:
        if args.command == "doctor":
            platforms = ("swift", "kotlin") if args.platform == "both" else () if args.platform == "none" else (args.platform,)
            if not platforms and args.backend == "none":
                raise ProfileError("--platform none requires --backend go or --backend rust.")
            observations = inspect(platforms, backend=args.backend)
            for observation in observations:
                print(f"{'OK' if observation.ok else 'MISSING'}  {observation.name}: {observation.detail}")
            print("Toolchain observations only; no dependencies installed or apps built.")
            return int(any(not observation.ok for observation in observations))
        root = source_root(args.source)
        profiles, backends = discover(root)
        capabilities, combinations = discover_combinations(root, profiles)
        if args.command == "list":
            print(f"mobile-foundry {VERSION}\nNative profiles:")
            for identity, (profile, _) in sorted(profiles.items()):
                choices = list(profile["platforms"]) + (["both"] if set(profile["platforms"]) == {"swift", "kotlin"} else [])
                print(f"  {identity}: {', '.join(choices)}; shell {profile['shell']}; capabilities {', '.join(profile['capabilities'])}; {profile['status']}")
            print("Optional capabilities:")
            for identity, capability in sorted(capabilities.items()):
                compatible = sorted({key[0] for key in combinations if identity in key[1]})
                print(f"  {identity}: {capability['status']}; profiles {', '.join(compatible) or 'none'}")
            print("Backends:\n  none: available (frontend only)")
            for backend in backends:
                print(f"  {backend['id']}: {backend['status']}")
            return 0
        backend = select_backend(root, backends, args.backend, args.go_module, args.name, args.rust_package)
        if args.platform == "none":
            if args.profile or args.capabilities:
                raise ProfileError("--platform none does not select a native profile or --with capabilities.")
            if backend is None:
                raise ProfileError("--platform none requires --backend go or --backend rust.")
            identity, profile, manifest_path, platform = None, None, None, "none"
        else:
            identity = choose(args.profile or "native-daily", sorted(profiles), "profile")
            profile, manifest_path = select_combination(identity, args.capabilities, profiles, capabilities, combinations)
            choices = list(profile["platforms"]) + (["both"] if set(profile["platforms"]) == {"swift", "kotlin"} else [])
            platform = choose(args.platform, choices, "platform")
        inputs = ProductInputs.admit(args.name, platform, args.display_name, args.app_id, args.ios_team)
        destination = create_project(root, profile, manifest_path, inputs, args.output, backend=backend, api_base_url=args.api_base_url)
        print(f"Created {destination}\nProfile: {identity or 'backend only'}\nPlatforms: {', '.join(inputs.platforms) or 'none'}\nCapabilities: {', '.join(profile['capabilities']) if profile else 'backend modules'}\nBackend: {args.backend}\nApp ID: {inputs.app_id or 'none'}")
        if backend:
            print((f"Go module: {backend.module}" if backend.profile["id"] == "go" else f"Rust API package: {backend.package}")+f"\nModules: {', '.join(backend.profile['modules'])}")
            if connection_capability(profile):
                label = "Go" if backend.profile["id"] == "go" else "Rust"
                print(f"Native sign-in, accounts and remote Notes are connected to {label}. Read SETUP.md for the API endpoint and device workflow.")
            elif profile:
                print("Native shells and API are independent; session/data UI wiring is not selected.")
        print("Read SETUP.md for build/test/run commands. Dependencies are not installed automatically.")
        return 0
    except (ProfileError, OSError, ValueError) as error:
        print(f"mobile-foundry: {error}", file=sys.stderr)
        return 1
    except (KeyboardInterrupt, EOFError):
        print("mobile-foundry: cancelled.", file=sys.stderr)
        return 130
