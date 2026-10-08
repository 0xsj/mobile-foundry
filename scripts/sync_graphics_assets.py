#!/usr/bin/env python3
"""Author the tiny lamp mesh, and synchronize canonical preview assets to apps."""
import argparse
import hashlib
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets/source"
DESTINATIONS = [ROOT / "frontend/swift/apps/FoundryCatalog/Resources/Graphics",
                ROOT / "frontend/kotlin/project/app/src/main/assets/graphics"]


def lamp():
    vertices = []

    def lathe(profile, slot=0, flip=1, offset=(0, 0, 0)):
        for (y0, r0), (y1, r1) in zip(profile, profile[1:]):
            dy, dr = y1 - y0, r1 - r0
            norm = math.hypot(dy, dr)
            if norm == 0:
                continue
            for j in range(64):
                a, b = j * math.tau / 64, (j + 1) * math.tau / 64

                def vertex(y, r, angle):
                    return [r * math.cos(angle) + offset[0], y + offset[1], r * math.sin(angle) + offset[2],
                            flip * dy / norm * math.cos(angle), -flip * dr / norm,
                            flip * dy / norm * math.sin(angle), slot]

                quad = [vertex(y0, r0, a), vertex(y1, r1, a), vertex(y1, r1, b), vertex(y0, r0, b)]
                for index in (0, 1, 2, 0, 2, 3):
                    vertices.extend(quad[index])

    lathe([(-1.08, 0), (-1.08, .40), (-1.04, .44), (-.97, .44), (-.92, .38), (-.92, 0)])
    lathe([(-.93, .055), (.72, .055)], slot=1)
    lathe([(.30, .66), (.35, .68), (.95, .23), (1.02, .18), (1.02, 0)])
    lathe([(.34, .63), (.92, .20)], slot=1, flip=-1)
    lathe([(.39, 0), (.39, .54)], slot=2)
    lathe([(-.92, .06), (-.88, .06), (-.88, 0)], slot=1, offset=(.25, 0, 0))
    return json.dumps({"version": 1, "name": "Studio lamp", "layout": "position3-normal3-slot1",
                       "vertices": [round(v, 6) for v in vertices]}, separators=(",", ":")).encode() + b"\n"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    generated = lamp()
    mesh = SOURCE / "studio-lamp.json"
    if not args.check:
        SOURCE.mkdir(parents=True, exist_ok=True)
        mesh.write_bytes(generated)
    failures = []
    if not mesh.exists() or mesh.read_bytes() != generated:
        failures.append("canonical lamp differs from the authored mesh")
    for name in ("studio-still-life.png", "studio-lamp.json"):
        source = SOURCE / name
        if not source.exists():
            failures.append(f"missing {source.relative_to(ROOT)}")
            continue
        for destination in DESTINATIONS:
            target = destination / name
            if args.check:
                if not target.exists() or hashlib.sha256(target.read_bytes()).digest() != hashlib.sha256(source.read_bytes()).digest():
                    failures.append(f"outdated {target.relative_to(ROOT)}")
            else:
                destination.mkdir(parents=True, exist_ok=True)
                target.write_bytes(source.read_bytes())
    if failures:
        raise SystemExit("\n".join(failures))
    manifest_path = ROOT / "assets/manifests/studio-previews.json"
    manifest = json.loads(manifest_path.read_text())
    for asset in manifest["assets"]:
        digest = hashlib.sha256((SOURCE / asset["source"]).read_bytes()).hexdigest()
        if args.check and asset["sha256"] != digest:
            failures.append(f"manifest hash differs for {asset['source']}")
        asset["sha256"] = digest
    if failures:
        raise SystemExit("\n".join(failures))
    if not args.check:
        manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n")
    print("graphics assets: canonical mesh, manifest hashes and both app copies match")


if __name__ == "__main__":
    main()
