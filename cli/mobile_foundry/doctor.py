"""Bounded read-only local toolchain probes; no installers or builds."""
from dataclasses import dataclass
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys


@dataclass(frozen=True)
class Observation:
    name: str
    ok: bool
    detail: str


def probe(name, arguments, minimum, pattern, env=None):
    executable = shutil.which(name)
    if executable is None:
        return Observation(name, False, "missing from PATH")
    try:
        result = subprocess.run([executable, *arguments], capture_output=True, text=True, timeout=15, env=env)
        output = (result.stdout + result.stderr).strip()
        match = re.search(pattern, output)
        if result.returncode != 0 or match is None:
            return Observation(name, False, (output.splitlines() or ["version probe failed"])[0])
        version = tuple(int(part) for part in match.groups())
        return Observation(name, version >= minimum, match.group(0) + ("" if version >= minimum else "; below required version"))
    except (OSError, subprocess.TimeoutExpired) as error:
        return Observation(name, False, str(error))


def inspect(platforms, environ=None, backend="none"):
    env = dict(os.environ if environ is None else environ)
    results = [Observation("Python", sys.version_info >= (3, 11), sys.version.split()[0] + "; requires 3.11+")]
    if backend == "go":
        results.append(probe("go", ["version"], (1, 27, 1), r"go(\d+)\.(\d+)\.(\d+)"))
        results.append(Observation("Go persistence", True, "memory needs no external service; PostgreSQL/Compose setup is separate"))
    if backend == "rust":
        rust_env=dict(env,RUSTUP_AUTO_INSTALL="0")
        clippy=probe("cargo", ["clippy","--version"], (0,1,86), r"clippy (\d+)\.(\d+)\.(\d+)", env=rust_env)
        results += [probe("rustc", ["--version"], (1,86,0), r"rustc (\d+)\.(\d+)\.(\d+)", env=rust_env),
                    probe("cargo", ["--version"], (1,86,0), r"cargo (\d+)\.(\d+)\.(\d+)", env=rust_env),
                    probe("rustfmt", ["--version"], (1,0,0), r"rustfmt (\d+)\.(\d+)\.(\d+)", env=rust_env),
                    Observation("Clippy", clippy.ok, clippy.detail),
                    probe("openssl", ["version"], (1,0,0), r"(?:OpenSSL|LibreSSL) (\d+)\.(\d+)\.(\d+)", env=rust_env),
                    Observation("Rust host", sys.platform in {"darwin","linux"}, "native runner/checks require macOS/Linux; Docker Linux builds are separate"),
                    Observation("Rust persistence", True, "memory needs no service; PostgreSQL/Docker and OpenSSL for TLS tests are separate prerequisites")]
    if "swift" in platforms:
        results += [probe("xcodebuild", ["-version"], (26, 2), r"Xcode (\d+)\.(\d+)"),
                    probe("swift", ["--version"], (6, 2), r"Swift version (\d+)\.(\d+)"),
                    probe("xcodegen", ["--version"], (2, 46), r"Version: (\d+)\.(\d+)")]
    if "kotlin" in platforms:
        java_home = env.get("JAVA_HOME")
        bundled = Path("/Applications/Android Studio.app/Contents/jbr/Contents/Home")
        if not java_home and bundled.is_dir():
            java_home = str(bundled)
        executable = str(Path(java_home) / "bin/java") if java_home else shutil.which("java")
        try:
            if not executable:
                raise OSError("Set JAVA_HOME to a Gradle 9.1-compatible JDK (17–25).")
            result = subprocess.run([executable, "-version"], capture_output=True, text=True, timeout=15, env=env)
            output = result.stderr + result.stdout
            match = re.search(r'(?:openjdk|java) version "(\d+)', output)
            ok = result.returncode == 0 and match is not None and 17 <= int(match[1]) <= 25
            results.append(Observation("Gradle JVM 17–25", ok, f"{executable}; " + ((output.strip().splitlines() or ["version unavailable"])[0])))
            results.append(Observation("Java compilation toolchain", True, "configured as JDK 17; Gradle selects/resolves it separately from its launcher JVM"))
        except (OSError, subprocess.TimeoutExpired) as error:
            results.append(Observation("Gradle JVM 17–25", False, str(error)))
        sdk = env.get("ANDROID_HOME") or env.get("ANDROID_SDK_ROOT")
        if not sdk:
            default = Path.home() / "Library/Android/sdk"
            if default.is_dir():
                sdk = str(default)
        if sdk:
            root = Path(sdk)
            results.append(Observation("Android SDK 36", (root / "platforms/android-36/android.jar").is_file(), str(root)))
            build_tools = root / "build-tools"
            available = [path for path in build_tools.glob("*") if re.fullmatch(r"\d+\.\d+\.\d+", path.name) and tuple(map(int, path.name.split("."))) >= (36, 0, 0) and (path / "aapt2").is_file()]
            results.append(Observation("Android build tools 36+", bool(available), ", ".join(sorted(path.name for path in available)) or "missing"))
            results.append(Observation("Android platform tools", (root / "platform-tools/adb").is_file(), str(root / "platform-tools")))
        else:
            results.append(Observation("Android SDK", False, "Set ANDROID_HOME or ANDROID_SDK_ROOT to an SDK with platform/build tools 36."))
    return results
