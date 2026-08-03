#!/usr/bin/env python3
"""Repair and validate relative ES-module imports after worldgen reorganization.

Run from the addon root, or pass --root explicitly:
    python reorganize_worldgen.py --check
    python reorganize_worldgen.py --dry-run
    python reorganize_worldgen.py --fix

The script does not move files. It discovers the current filesystem layout and
rewrites relative JS imports to point at the actual module locations.
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
from dataclasses import dataclass
from pathlib import Path

IMPORT_RE = re.compile(
    r"(?P<prefix>\b(?:import\s+(?:[\s\S]*?\s+from\s+|)|export\s+(?:[\s\S]*?\s+from\s+))|\bimport\s*\()"
    r"(?P<quote>[\"'])(?P<spec>\.?\.?/[^\"']+)(?P=quote)"
)
STATIC_RE = re.compile(r"(?P<quote>[\"'])(?P<spec>\.?\.?/[^\"']+)(?P=quote)")

@dataclass
class ImportRef:
    source: Path
    spec: str
    start: int
    end: int


def parse_args():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--root", type=Path, default=None, help="Addon root (default: directory containing this script)")
    p.add_argument("--scripts", type=Path, default=None, help="Scripts directory relative to root")
    p.add_argument("--check", action="store_true", help="Only validate imports; do not modify files")
    p.add_argument("--dry-run", action="store_true", help="Show planned rewrites without modifying files")
    p.add_argument("--fix", action="store_true", help="Rewrite imports in place")
    p.add_argument("--backup", action="store_true", help="Create a timestamp-free .import_repair_backup before --fix")
    p.add_argument("--json", action="store_true", help="Emit machine-readable report")
    return p.parse_args()


def normalize_spec(spec: str) -> str:
    spec = spec.replace("\\", "/")
    if spec.endswith(".js"):
        return spec
    return spec + ".js"


def module_files(scripts: Path):
    return sorted(p for p in scripts.rglob("*.js") if p.is_file())


def build_indexes(files):
    by_rel = {}
    by_name = {}
    for f in files:
        rel = f.as_posix()
        by_rel[rel] = f
        by_name.setdefault(f.name, []).append(f)
    return by_rel, by_name


def extract_imports(path: Path, text: str):
    # Ignore obvious comments enough to avoid the most common false positives.
    # We intentionally do not attempt a full JS parser; only quoted relative
    # module specifiers are candidates.
    refs = []
    for m in STATIC_RE.finditer(text):
        spec = m.group("spec")
        # Avoid matching strings that are clearly not module declarations.
        before = text[max(0, m.start() - 80):m.start()]
        if re.search(r"(?:from\s*|import\s*\()$", before) or re.search(r"^\s*(?:import|export)\b", before):
            refs.append(ImportRef(path, spec, m.start("spec"), m.end("spec")))
    # Deduplicate overlapping/static matches.
    seen = set()
    out = []
    for r in refs:
        key = (r.start, r.end, r.spec)
        if key not in seen:
            seen.add(key)
            out.append(r)
    return out


def resolve_import(source: Path, spec: str, scripts: Path, by_rel, by_name):
    normalized = normalize_spec(spec)
    candidate = (source.parent / normalized).resolve()
    try:
        candidate.relative_to(scripts.resolve())
    except ValueError:
        return None, "outside scripts tree"
    if candidate.is_file():
        return candidate, None

    # Handle stale imports that still contain worldgen/...
    parts = normalized.split("/")
    if "worldgen" in parts:
        idx = parts.index("worldgen")
        tail = parts[idx + 1:]
        if tail:
            rel_tail = "/".join(tail)
            matches = [f for rel, f in by_rel.items() if rel.endswith("/" + rel_tail) or rel == rel_tail]
            if len(matches) == 1:
                return matches[0], "stale worldgen path"

    # Last-resort unique basename lookup. Never guess if ambiguous.
    name = Path(normalized).name
    matches = by_name.get(name, [])
    if len(matches) == 1:
        return matches[0], "unique basename recovery"
    if len(matches) > 1:
        return None, "ambiguous basename: " + ", ".join(str(x) for x in matches)
    return None, "module not found"


def relative_spec(source: Path, target: Path):
    rel = Path(__import__("os").path.relpath(target, source.parent))
    spec = rel.as_posix()
    if not spec.startswith("."):
        spec = "./" + spec
    return spec


def scan(scripts: Path):
    files = module_files(scripts)
    by_rel, by_name = build_indexes(files)
    rewrites = []
    unresolved = []
    checked = 0

    for source in files:
        text = source.read_text(encoding="utf-8")
        refs = extract_imports(source, text)
        checked += len(refs)
        for ref in refs:
            target, reason = resolve_import(source, ref.spec, scripts, by_rel, by_name)
            if target is None:
                unresolved.append({"source": str(source), "import": ref.spec, "reason": reason})
                continue
            new_spec = relative_spec(source, target)
            if normalize_spec(ref.spec) != normalize_spec(new_spec) or ref.spec != new_spec:
                rewrites.append({"source": source, "old": ref.spec, "new": new_spec, "reason": reason or "path correction"})

    return files, rewrites, unresolved, checked


def apply_rewrites(scripts: Path, rewrites, backup: bool):
    grouped = {}
    for r in rewrites:
        grouped.setdefault(Path(r["source"]), []).append(r)

    backup_dir = scripts.parent / ".import_repair_backup"
    if backup:
        if backup_dir.exists():
            shutil.rmtree(backup_dir)
        for source in grouped:
            rel = source.relative_to(scripts.parent)
            dest = backup_dir / rel
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, dest)

    changed = 0
    for source, items in grouped.items():
        text = source.read_text(encoding="utf-8")
        # Apply from right to left so offsets stay valid.
        # Re-find exact occurrences to avoid relying on regex parsing after edits.
        for item in sorted(items, key=lambda x: text.rfind(x["old"]), reverse=True):
            old = item["old"]
            new = item["new"]
            # Prefer exact quoted module specifier.
            pattern = re.compile(r"([\"'])" + re.escape(old) + r"\1")
            m = pattern.search(text)
            if not m:
                continue
            text = text[:m.start(0)] + m.group(1) + new + m.group(1) + text[m.end(0):]
            changed += 1
        source.write_text(text, encoding="utf-8")
    return changed, backup_dir if backup else None


def main():
    args = parse_args()
    root = (args.root or Path(__file__).resolve().parent).resolve()
    scripts = (root / (args.scripts or "scripts")).resolve()
    if not scripts.is_dir():
        print(f"ERROR: scripts directory not found: {scripts}", file=sys.stderr)
        return 2

    files, rewrites, unresolved, checked = scan(scripts)
    report = {
        "root": str(root),
        "scripts": str(scripts),
        "files": len(files),
        "imports_checked": checked,
        "rewrites": rewrites,
        "unresolved": unresolved,
    }

    if args.json:
        print(json.dumps(report, indent=2))
    else:
        print(f"Addon root: {root}")
        print(f"Scripts: {scripts}")
        print(f"JS files scanned: {len(files)}")
        print(f"Relative imports checked: {checked}")
        print(f"Import rewrites needed: {len(rewrites)}")
        print(f"Unresolved imports: {len(unresolved)}")
        if rewrites:
            print("\nPlanned rewrites:")
            for r in rewrites:
                print(f"  {r['source']}: {r['old']} -> {r['new']} ({r['reason']})")
        if unresolved:
            print("\nUnresolved imports:")
            for r in unresolved:
                print(f"  {r['source']}: {r['import']} [{r['reason']}]")

    if args.check:
        return 1 if unresolved else 0

    if args.fix:
        changed, backup_dir = apply_rewrites(scripts, rewrites, args.backup)
        print(f"\nRewritten imports: {changed}")
        if backup_dir:
            print(f"Backup: {backup_dir}")
        return 1 if unresolved else 0

    if args.dry_run:
        print("\nDry run only; no files changed.")
        return 1 if unresolved else 0

    print("\nNo action requested. Use --check, --dry-run, or --fix.")
    return 1 if unresolved else 0


if __name__ == "__main__":
    raise SystemExit(main())
