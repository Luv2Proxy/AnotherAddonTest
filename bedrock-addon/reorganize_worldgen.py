#!/usr/bin/env python3
"""Organize worldgen JavaScript files and repair relative imports.

This replaces the old one-shot reorganization script and the separate import
repair script. It discovers files from the current checkout, moves only files
that exist, rewrites imports based on the final filesystem layout, validates
imports, and can create a backup.

Examples (run from the addon root):
  python reorganize_worldgen.py --plan
  python reorganize_worldgen.py --organize --backup
  python reorganize_worldgen.py --check
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import sys
from datetime import datetime
from pathlib import Path

MOVE_MAP = {
    "IslandGenerator.js": "core/IslandGenerator.js",
    "IslandNoise.js": "core/IslandNoise.js",
    "IslandShapeSampler.js": "core/IslandShapeSampler.js",
    "IslandDensityEvaluator.js": "core/IslandDensityEvaluator.js",
    "IslandDescriptorFactory.js": "core/IslandDescriptorFactory.js",
    "ExactMaterialPlan.js": "terrain/ExactMaterialPlan.js",
    "ExactSurfacePipeline.js": "terrain/ExactSurfacePipeline.js",
    "ExactSurfaceSelector.js": "terrain/ExactSurfaceSelector.js",
    "IslandSurfacePass.js": "terrain/IslandSurfacePass.js",
    "TerrainProjection.js": "terrain/TerrainProjection.js",
    "TerrainAdaptationEngine.js": "terrain/TerrainAdaptationEngine.js",
    "BulkWorldWriter.js": "terrain/bulk/BulkWorldWriter.js",
    "BulkTerrainRuntime.js": "terrain/bulk/BulkTerrainRuntime.js",
    "OreFeatureEngine.js": "terrain/ores/OreFeatureEngine.js",
    "OreFeatureDefinitions.js": "terrain/ores/OreFeatureDefinitions.js",
    "LargeOreVeinEngine.js": "terrain/ores/LargeOreVeinEngine.js",
    "OreDistribution.js": "terrain/ores/OreDistribution.js",
    "StructureRegistry.js": "structures/StructureRegistry.js",
    "StructureDetection.js": "structures/StructureDetection.js",
    "StructurePlacement.js": "structures/StructurePlacement.js",
    "StructurePlacementCoordinator.js": "structures/StructurePlacementCoordinator.js",
    "StructurePlacementQueue.js": "structures/StructurePlacementQueue.js",
    "StructureSetRuntime.js": "structures/StructureSetRuntime.js",
    "StructureSetGenerator.js": "structures/StructureSetGenerator.js",
    "StructureSetPlacementPlanner.js": "structures/StructureSetPlacementPlanner.js",
    "GeneratedStructurePlanner.js": "structures/GeneratedStructurePlanner.js",
    "GeneratedWorldgenBridge.js": "structures/GeneratedWorldgenBridge.js",
    "StructureDensityField.js": "structures/StructureDensityField.js",
    "CategoryPlacementEngines.js": "structures/CategoryPlacementEngines.js",
    "JigsawRegistry.js": "jigsaw/JigsawRegistry.js",
    "JigsawGenerator.js": "jigsaw/JigsawGenerator.js",
    "JigsawLayoutPlanner.js": "jigsaw/JigsawLayoutPlanner.js",
    "JigsawDataLoader.js": "jigsaw/JigsawDataLoader.js",
    "WorldgenJigsawRuntime.js": "jigsaw/WorldgenJigsawRuntime.js",
    "JigsawPieceArchitecture.js": "jigsaw/JigsawPieceArchitecture.js",
    "JigsawTransform.js": "jigsaw/JigsawTransform.js",
    "ProcessorPipeline.js": "jigsaw/ProcessorPipeline.js",
    "StructurePieceModel.js": "jigsaw/pieces/StructurePieceModel.js",
    "NativeStructureAdapter.js": "native/NativeStructureAdapter.js",
    "NativeStructureCoordinator.js": "native/NativeStructureCoordinator.js",
    "NativeStructurePlacement.js": "native/NativeStructurePlacement.js",
    "OverlapState.js": "persistence/OverlapState.js",
    "StructureTerrainValidation.js": "validation/StructureTerrainValidation.js",
    "WorldgenSelfTest.js": "validation/WorldgenSelfTest.js",
}

RELATIVE_SPEC_RE = re.compile(r"([\"'])(\.?\.?/[^\"']+?)\1")


def parse_args():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--root", type=Path, default=None)
    p.add_argument("--scripts", type=Path, default=Path("scripts"))
    p.add_argument("--plan", action="store_true", help="Show moves/import changes without modifying files")
    p.add_argument("--organize", action="store_true", help="Move files and repair imports")
    p.add_argument("--check", action="store_true", help="Validate relative imports")
    p.add_argument("--backup", action="store_true", help="Back up scripts before organizing")
    p.add_argument("--json", action="store_true")
    return p.parse_args()


def js_files(scripts):
    return sorted(p for p in scripts.rglob("*.js") if p.is_file())


def normalize(spec):
    spec = spec.replace("\\", "/")
    return spec if spec.endswith(".js") else spec + ".js"


def index_by_name(files):
    result = {}
    for f in files:
        result.setdefault(f.name, []).append(f)
    return result


def planned_moves(scripts):
    moves, skipped, collisions = [], [], []
    for name, destination in MOVE_MAP.items():
        source = scripts / name
        dest = scripts / "worldgen" / destination
        if source.is_file():
            if dest.exists() and dest.resolve() != source.resolve():
                collisions.append((name, "destination already exists", [str(dest)]))
            else:
                moves.append((source, dest))
            continue
        existing = [p for p in scripts.rglob(name) if p.is_file()]
        if len(existing) == 1:
            if existing[0].resolve() == dest.resolve():
                skipped.append((name, "already organized", existing[0]))
            else:
                skipped.append((name, f"already moved to {existing[0].relative_to(scripts)}", existing[0]))
        elif len(existing) > 1:
            collisions.append((name, "multiple files", [str(x) for x in existing]))
        else:
            skipped.append((name, "missing", source))
    return moves, skipped, collisions


def backup_and_move(moves, scripts):
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_dir = scripts.parent / f".worldgen_reorg_backup_{stamp}"
    shutil.copytree(scripts, backup_dir)
    for source, dest in moves:
        dest.parent.mkdir(parents=True, exist_ok=True)
        print(f"MOVE {source} -> {dest}")
        shutil.move(str(source), str(dest))
    return backup_dir


def discover_imports(text):
    refs = []
    for m in RELATIVE_SPEC_RE.finditer(text):
        before = text[max(0, m.start() - 120):m.start()]
        if re.search(r"(?:\bfrom\s*|\bimport\s*\()$", before) or re.search(r"\b(?:import|export)\s*$", before):
            refs.append((m.group(2), m.start(2), m.end(2)))
    return refs


def resolve(source, spec, scripts, by_name):
    candidate = (source.parent / normalize(spec)).resolve()
    try:
        candidate.relative_to(scripts.resolve())
    except ValueError:
        return None, "outside scripts tree"
    if candidate.is_file():
        return candidate, None
    matches = by_name.get(Path(normalize(spec)).name, [])
    if len(matches) == 1:
        return matches[0], "unique basename recovery"
    if len(matches) > 1:
        return None, "ambiguous basename: " + ", ".join(str(x) for x in matches)
    return None, "module not found"


def rewrite_imports(scripts):
    files = js_files(scripts)
    by_name = index_by_name(files)
    rewrites, unresolved, changed_files = [], [], 0
    for source in files:
        text = source.read_text(encoding="utf-8")
        replacements = []
        for spec, start, end in discover_imports(text):
            target, reason = resolve(source, spec, scripts, by_name)
            if target is None:
                unresolved.append({"source": str(source), "import": spec, "reason": reason})
                continue
            new_spec = os.path.relpath(target, source.parent).replace("\\", "/")
            if not new_spec.startswith("."):
                new_spec = "./" + new_spec
            if spec != new_spec:
                replacements.append((start, end, spec, new_spec, reason or "path correction"))
        if replacements:
            for start, end, old, new, reason in sorted(replacements, reverse=True):
                text = text[:start] + new + text[end:]
                rewrites.append({"source": str(source), "old": old, "new": new, "reason": reason})
            source.write_text(text, encoding="utf-8")
            changed_files += 1
    return files, rewrites, unresolved, changed_files


def main():
    a = parse_args()
    root = (a.root or Path(__file__).resolve().parent).resolve()
    scripts = (root / a.scripts).resolve()
    worldgen = scripts / "worldgen"
    if not worldgen.is_dir():
        print(f"ERROR: worldgen directory not found: {worldgen}", file=sys.stderr)
        return 2

    moves, skipped, collisions = planned_moves(scripts)
    if collisions:
        print("ERROR: refusing to organize due to collisions/ambiguities:")
        for item in collisions:
            print(" ", item)
        return 2

    if a.plan or a.organize:
        print(f"Addon root: {root}")
        print(f"Worldgen: {worldgen}")
        print(f"Planned moves: {len(moves)}")
        for source, dest in moves:
            print(f"  {source} -> {dest}")
        for name, reason, path in skipped:
            print(f"[SKIP] {name}: {reason} ({path})")

    backup = None
    if a.organize:
        backup = backup_and_move(moves, scripts) if a.backup else None
        if not a.backup:
            for source, dest in moves:
                dest.parent.mkdir(parents=True, exist_ok=True)
                print(f"MOVE {source} -> {dest}")
                shutil.move(str(source), str(dest))

    files, rewrites, unresolved, changed_files = rewrite_imports(scripts)

    if a.plan and not a.organize:
        print("\nImport changes that would be made:")
        for r in rewrites:
            print(f"  {r['source']}: {r['old']} -> {r['new']} ({r['reason']})")

    if a.organize:
        print(f"\nMoved files: {len(moves)}")
        print(f"Rewritten imports: {len(rewrites)}")
        print(f"Files with rewritten imports: {changed_files}")
        if backup:
            print(f"Backup: {backup}")

    if a.check or a.organize:
        print(f"JS files scanned: {len(files)}")
        print(f"Unresolved imports: {len(unresolved)}")
        if unresolved:
            for r in unresolved:
                print(f"  {r['source']}: {r['import']} [{r['reason']}]")

    if a.json:
        print(json.dumps({
            "moved": len(moves),
            "rewritten_imports": len(rewrites),
            "files_scanned": len(files),
            "unresolved_imports": unresolved,
            "skipped": [{"name": n, "reason": r, "path": str(p)} for n, r, p in skipped],
            "backup": str(backup) if backup else None,
        }, indent=2))

    if not (a.plan or a.organize or a.check):
        print("No action requested. Use --plan, --organize, or --check.")

    return 1 if unresolved else 0


if __name__ == "__main__":
    raise SystemExit(main())
