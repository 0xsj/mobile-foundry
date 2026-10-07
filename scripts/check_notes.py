#!/usr/bin/env python3
"""Check repository Markdown links, module-note paths, and native example labels.

This does not execute code examples or validate external URLs or heading anchors.
"""

from pathlib import Path
import os
import re
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parents[1]
SKIP = {".git", ".cache", ".build", ".gradle", ".kotlin", "build", "node_modules", "xcuserdata"}
FENCE = re.compile(r"^ {0,3}(`{3,}|~{3,})(.*)$")
LINK = re.compile(r"!?\[[^\]]*\]\(\s*(?:<([^>]+)>|([^\s)]+))")
LABEL = re.compile(r"^// (Runnable|Excerpt|Conceptual|Compile-fail)(?:$|[ :])")


def markdown_files(root: Path):
    for directory, names, files in os.walk(root):
        names[:] = sorted(name for name in names if name not in SKIP)
        for name in sorted(files):
            if name.endswith(".md"):
                yield Path(directory) / name


def check_document(path: Path):
    errors = []
    links = 0
    examples = 0
    fence = None
    language = ""
    first_line = None
    start = 0
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        match = FENCE.match(line)
        if fence is not None:
            if match and match[1][0] == fence[0] and len(match[1]) >= len(fence) and not match[2].strip():
                if language in {"swift", "kotlin"}:
                    examples += 1
                    if not LABEL.match(first_line or ""):
                        errors.append(f"line {start}: {language} example needs a scope label")
                fence = None
            elif first_line is None and line.strip():
                first_line = line.strip()
            continue
        if match:
            fence = match[1]
            language = match[2].strip().split()[0] if match[2].strip() else ""
            first_line = None
            start = number
            continue
        for match in LINK.finditer(line):
            target = match[1] or match[2]
            destination = urlsplit(target)
            if destination.scheme or destination.netloc or not destination.path:
                continue
            links += 1
            local_path = Path(unquote(destination.path))
            if local_path.is_absolute():
                errors.append(f"line {number}: use a relative file link: {target}")
            elif not (path.parent / local_path).exists():
                errors.append(f"line {number}: missing link target: {target}")
    if fence is not None:
        errors.append(f"line {start}: unclosed code fence")

    for parent in path.parents:
        if parent.name == "modules" and parent.parent.name == "notes":
            source_directory = parent.parent.parent / path.parent.relative_to(parent)
            if not source_directory.is_dir():
                errors.append(f"module path does not mirror an existing directory: {source_directory}")
            break
    return errors, links, examples


def main():
    documents = list(markdown_files(ROOT))
    failures = []
    links = examples = 0
    for path in documents:
        errors, count_links, count_examples = check_document(path)
        links += count_links
        examples += count_examples
        failures.extend(f"{path.relative_to(ROOT)}: {error}" for error in errors)
    for failure in failures:
        print(failure)
    print(f"notes-check: {len(documents)} documents, {links} local links, "
          f"{examples} native example labels, {len(failures)} failures")
    return bool(failures)


if __name__ == "__main__":
    raise SystemExit(main())
