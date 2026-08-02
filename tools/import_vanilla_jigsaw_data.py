#!/usr/bin/env python3
"""Import and normalize copied vanilla Bedrock Jigsaw worldgen data.

This is a build-time importer. It treats bedrock-addon/structures/json as a
versioned snapshot of vanilla JSON, resolves references against the local
structure library, extracts jigsaw connectors from .mcstructure files, and
writes normalized registries plus a validation report.

Usage:
  py tools/import_vanilla_jigsaw_data.py
  py tools/import_vanilla_jigsaw_data.py --json bedrock-addon/structures/json \
      --structures bedrock-addon/structures \
      --out bedrock-addon/scripts/worldgen/generated

The importer is deliberately schema-tolerant: it preserves unknown JSON
fields and reports unsupported/missing references instead of inventing data.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import struct
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

T_END, T_BYTE, T_SHORT, T_INT, T_LONG, T_FLOAT, T_DOUBLE, T_BA, T_STRING, T_LIST, T_COMPOUND, T_IA, T_LA = range(13)
FACING = {0: "down", 1: "up", 2: "north", 3: "south", 4: "west", 5: "east"}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


class NBT:
    """Minimal little-endian Bedrock NBT reader used for .mcstructure files."""
    def __init__(self, data: bytes):
        self.data = data
        self.pos = 0

    def need(self, n: int) -> None:
        if self.pos + n > len(self.data):
            raise ValueError("truncated NBT")

    def u8(self):
        self.need(1); x = self.data[self.pos]; self.pos += 1; return x
    def u16(self):
        self.need(2); x = struct.unpack_from("<H", self.data, self.pos)[0]; self.pos += 2; return x
    def i16(self):
        self.need(2); x = struct.unpack_from("<h", self.data, self.pos)[0]; self.pos += 2; return x
    def i32(self):
        self.need(4); x = struct.unpack_from("<i", self.data, self.pos)[0]; self.pos += 4; return x
    def i64(self):
        self.need(8); x = struct.unpack_from("<q", self.data, self.pos)[0]; self.pos += 8; return x
    def f32(self):
        self.need(4); x = struct.unpack_from("<f", self.data, self.pos)[0]; self.pos += 4; return x
    def f64(self):
        self.need(8); x = struct.unpack_from("<d", self.data, self.pos)[0]; self.pos += 8; return x

    def string(self):
        n = self.u16(); self.need(n)
        x = self.data[self.pos:self.pos+n].decode("utf-8", "replace")
        self.pos += n
        return x

    def value(self, tag):
        if tag == T_BYTE: return self.u8()
        if tag == T_SHORT: return self.i16()
        if tag == T_INT: return self.i32()
        if tag == T_LONG: return self.i64()
        if tag == T_FLOAT: return self.f32()
        if tag == T_DOUBLE: return self.f64()
        if tag == T_BA:
            n = self.i32(); self.need(n); x = list(self.data[self.pos:self.pos+n]); self.pos += n; return x
        if tag == T_STRING: return self.string()
        if tag == T_LIST:
            et = self.u8(); n = self.i32(); return [self.value(et) for _ in range(n)]
        if tag == T_COMPOUND:
            result = {}
            while True:
                ct = self.u8()
                if ct == T_END: return result
                result[self.string()] = self.value(ct)
        if tag == T_IA:
            n = self.i32(); return [self.i32() for _ in range(n)]
        if tag == T_LA:
            n = self.i32(); return [self.i64() for _ in range(n)]
        raise ValueError(f"unsupported NBT tag {tag}")

    def root(self):
        if self.u8() != T_COMPOUND:
            raise ValueError("NBT root is not a compound")
        self.string()
        return self.value(T_COMPOUND)


def xyz(value):
    if isinstance(value, list) and len(value) >= 3:
        return {"x": int(value[0]), "y": int(value[1]), "z": int(value[2])}
    return None


def index_to_xyz(index: int, size):
    if not all(size):
        return None
    x = index % size[0]
    q = index // size[0]
    y = q % size[1]
    z = q // size[1]
    return {"x": x, "y": y, "z": z}


def logical_id(root: Path, path: Path) -> str:
    rel = path.relative_to(root).as_posix()
    parts = rel.split("/")
    parts[-1] = Path(parts[-1]).stem
    if len(parts) == 1:
        return f"unknown:{parts[0]}"
    namespace = parts[0]
    return namespace + ":" + "/".join(parts[1:])


def normalize_identifier(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    return value.replace("\\", "/").removesuffix(".nbt").removesuffix(".mcstructure")


def parse_piece(structure_root: Path, path: Path) -> dict[str, Any]:
    root = NBT(path.read_bytes()).root()
    structure = root.get("structure", {})
    size = xyz(root.get("size")) or {"x": 0, "y": 0, "z": 0}
    dimensions = (size["x"], size["y"], size["z"])
    palette = structure.get("palette", {}).get("default", {})
    block_palette = palette.get("block_palette", [])
    indices = structure.get("block_indices", [])
    position_data = palette.get("block_position_data", {})
    primary = indices[0] if indices and isinstance(indices[0], list) else []
    connectors = []

    for key, entry in position_data.items():
        try:
            index = int(key)
        except (TypeError, ValueError):
            continue
        entity = entry.get("block_entity_data") if isinstance(entry, dict) else None
        if not isinstance(entity, dict):
            continue
        entity_id = str(entity.get("id", "")).lower()
        if entity_id not in {"jigsawblock", "minecraft:jigsaw"}:
            continue

        palette_index = primary[index] if 0 <= index < len(primary) else None
        palette_entry = block_palette[palette_index] if isinstance(palette_index, int) and 0 <= palette_index < len(block_palette) else {}
        states = palette_entry.get("states", {}) if isinstance(palette_entry, dict) else {}
        facing_direction = states.get("facing_direction")
        facing = FACING.get(int(facing_direction), "unknown") if isinstance(facing_direction, (int, float)) else "unknown"

        connectors.append({
            "position": index_to_xyz(index, dimensions),
            "position_index": index,
            "facing": facing,
            "facing_direction": facing_direction if facing_direction is not None else "unknown",
            "rotation": states.get("rotation", "unknown"),
            "joint": entity.get("joint", "unknown"),
            "name": entity.get("name", "unknown"),
            "target": entity.get("target", "unknown"),
            "pool": entity.get("target_pool", "unknown"),
            "final_state": entity.get("final_state", "unknown"),
            "selection_priority": entity.get("selection_priority", "unknown"),
            "placement_priority": entity.get("placement_priority", "unknown"),
        })

    return {
        "id": logical_id(structure_root, path),
        "source": path.relative_to(structure_root).as_posix(),
        "source_type": path.suffix.lower().lstrip("."),
        "size": size,
        "connectors": connectors,
        "entities": structure.get("entities", []),
        "format_version": root.get("format_version", "unknown"),
        "sha256": sha256(path),
    }


def detect_json_kind(path: Path, data: Any, source_root: Path) -> str:
    parts = {p.lower() for p in path.parts}
    if "template_pools" in parts or "templatepools" in parts:
        return "template_pools"
    if "processor_lists" in parts or "processors" in parts:
        return "processors"
    if "structure_sets" in parts or "structuresets" in parts:
        return "structure_sets"
    if "structures" in parts:
        return "structures"

    text = json.dumps(data, separators=(",", ":"), ensure_ascii=False)
    if "minecraft:template_pool" in text:
        return "template_pools"
    if "minecraft:processor_list" in text:
        return "processors"
    if "minecraft:structure_set" in text:
        return "structure_sets"
    if "minecraft:jigsaw" in text:
        return "structures"
    return "unknown"


def find_identifier(data: Any, fallback: str) -> str:
    if isinstance(data, dict):
        for key in ("description", "minecraft:template_pool", "minecraft:jigsaw", "minecraft:processor_list", "minecraft:structure_set"):
            value = data.get(key)
            if isinstance(value, dict):
                identifier = value.get("identifier")
                if isinstance(identifier, str):
                    return identifier
        for key in ("identifier", "name", "id"):
            if isinstance(data.get(key), str):
                return data[key]
    return fallback


def scan_json(root: Path):
    result = {"template_pools": {}, "structures": {}, "processors": {}, "structure_sets": {}, "unknown": {}}
    source_files = {}
    for path in sorted(root.rglob("*.json")):
        try:
            data = json.loads(path.read_text(encoding="utf-8-sig"))
        except Exception as exc:
            result["unknown"][path.relative_to(root).as_posix()] = {"source": path.relative_to(root).as_posix(), "error": f"JSON parse error: {exc}"}
            continue
        kind = detect_json_kind(path, data, root)
        key = find_identifier(data, path.stem)
        entry = {"source": path.relative_to(root).as_posix(), "sha256": sha256(path), "definition": data}
        result[kind][key] = entry
        source_files[path.relative_to(root).as_posix()] = {"sha256": entry["sha256"], "kind": kind, "identifier": key}
    return result, source_files


def candidate_structure_paths(structures_root: Path, identifier: str):
    normalized = normalize_identifier(identifier)
    if not normalized:
        return []
    if ":" in normalized:
        namespace, path = normalized.split(":", 1)
    else:
        namespace, path = "minecraft", normalized
    candidates = []
    for base in (structures_root, structures_root / namespace, structures_root / "minecraft"):
        for suffix in (".nbt", ".mcstructure"):
            candidates.append(base / (path + suffix))
    # Also support a source layout where namespace is not represented as a folder.
    candidates.append(structures_root / (path + ".nbt"))
    candidates.append(structures_root / (path + ".mcstructure"))
    return list(dict.fromkeys(candidates))


def resolve_structure(structures_root: Path, identifier: str):
    for candidate in candidate_structure_paths(structures_root, identifier):
        if candidate.is_file():
            return candidate
    # Last-resort basename/path search, useful for the user's existing generated library.
    normalized = normalize_identifier(identifier)
    if normalized:
        wanted = Path(normalized.split(":", 1)[-1])
        matches = [p for p in structures_root.rglob("*") if p.is_file() and p.suffix.lower() in {".nbt", ".mcstructure"} and Path(p.stem) == wanted]
        if len(matches) == 1:
            return matches[0]
    return None


def extract_references(data: Any, kind: str):
    refs = []
    if kind == "template_pools" and isinstance(data, dict):
        obj = data.get("minecraft:template_pool", data)
        elements = obj.get("elements", []) if isinstance(obj, dict) else []
        if isinstance(elements, list):
            for element in elements:
                if not isinstance(element, dict):
                    continue
                location = element.get("location") or element.get("name")
                if isinstance(location, str):
                    refs.append(("template", location))
                processors = element.get("processors")
                if isinstance(processors, str):
                    refs.append(("processor", processors))
        fallback = obj.get("fallback") if isinstance(obj, dict) else None
        if isinstance(fallback, str):
            refs.append(("pool", fallback))
    elif kind == "structures" and isinstance(data, dict):
        obj = data.get("minecraft:jigsaw", data)
        if isinstance(obj, dict):
            start = obj.get("start_pool") or obj.get("startPool")
            if isinstance(start, str):
                refs.append(("pool", start))
            processors = obj.get("processors")
            if isinstance(processors, str):
                refs.append(("processor", processors))
    elif kind == "structure_sets" and isinstance(data, dict):
        obj = data.get("minecraft:structure_set", data)
        structures = obj.get("structures", []) if isinstance(obj, dict) else []
        if isinstance(structures, list):
            for entry in structures:
                if isinstance(entry, dict) and isinstance(entry.get("structure"), str):
                    refs.append(("structure", entry["structure"]))
    return refs


def validate(registry, structures_root: Path):
    errors = []
    warnings = []
    resolved = {}
    missing = Counter()

    for kind in ("template_pools", "structures", "processors", "structure_sets"):
        for identifier, entry in registry[kind].items():
            for ref_kind, ref in extract_references(entry["definition"], kind):
                if ref_kind == "template":
                    path = resolve_structure(structures_root, ref)
                    if path is None:
                        errors.append({"type": "missing_template", "source": entry["source"], "identifier": identifier, "reference": ref})
                        missing["templates"] += 1
                    else:
                        resolved[ref] = path.relative_to(structures_root).as_posix()
                elif ref_kind == "pool" and ref not in registry["template_pools"]:
                    errors.append({"type": "missing_pool", "source": entry["source"], "identifier": identifier, "reference": ref})
                    missing["pools"] += 1
                elif ref_kind == "processor" and ref not in registry["processors"]:
                    warnings.append({"type": "missing_processor", "source": entry["source"], "identifier": identifier, "reference": ref})
                    missing["processors"] += 1
                elif ref_kind == "structure" and ref not in registry["structures"]:
                    errors.append({"type": "missing_structure", "source": entry["source"], "identifier": identifier, "reference": ref})
                    missing["structures"] += 1

    return resolved, errors, warnings, missing


def build_connector_index(pieces):
    index = defaultdict(list)
    for piece in pieces:
        for i, connector in enumerate(piece.get("connectors", [])):
            name = connector.get("name")
            if isinstance(name, str) and name not in {"", "unknown"}:
                index[name].append({"piece": piece["id"], "connector_index": i})
    return dict(index)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", default="bedrock-addon/structures/json", help="Copied vanilla worldgen JSON root")
    parser.add_argument("--structures", default="bedrock-addon/structures", help="Structure template root")
    parser.add_argument("--out", default="bedrock-addon/scripts/worldgen/generated", help="Generated output directory")
    parser.add_argument("--strict", action="store_true", help="Exit nonzero when validation errors are found")
    args = parser.parse_args()

    json_root = Path(args.json).resolve()
    structures_root = Path(args.structures).resolve()
    out_root = Path(args.out).resolve()
    out_root.mkdir(parents=True, exist_ok=True)

    if not json_root.exists():
        raise SystemExit(f"JSON source does not exist: {json_root}")
    if not structures_root.exists():
        raise SystemExit(f"Structure source does not exist: {structures_root}")

    raw, source_files = scan_json(json_root)
    registry = {k: raw[k] for k in ("template_pools", "structures", "processors", "structure_sets")}

    pieces = []
    piece_errors = []
    for path in sorted(structures_root.rglob("*.mcstructure")) + sorted(structures_root.rglob("*.nbt")):
        # Never treat copied worldgen JSON as structure templates.
        if json_root in path.parents:
            continue
        try:
            pieces.append(parse_piece(structures_root, path))
        except Exception as exc:
            piece_errors.append({"type": "piece_parse_error", "source": path.relative_to(structures_root).as_posix(), "error": str(exc)})

    resolved_templates, validation_errors, validation_warnings, missing = validate(registry, structures_root)
    validation_errors.extend(piece_errors)

    validation = {
        "status": "PASS" if not validation_errors else "FAIL",
        "counts": {
            "json_files": len(source_files),
            "template_pools": len(registry["template_pools"]),
            "jigsaw_structures": len(registry["structures"]),
            "processors": len(registry["processors"]),
            "structure_sets": len(registry["structure_sets"]),
            "structure_templates_parsed": len(pieces),
            "connectors": sum(len(p.get("connectors", [])) for p in pieces),
            "resolved_templates": len(resolved_templates),
            "errors": len(validation_errors),
            "warnings": len(validation_warnings),
        },
        "missing": dict(missing),
        "errors": validation_errors,
        "warnings": validation_warnings,
    }

    manifest = {
        "schema_version": 1,
        "generated_by": "tools/import_vanilla_jigsaw_data.py",
        "json_root": str(json_root),
        "structures_root": str(structures_root),
        "source_files": source_files,
        "structure_files": {p["source"]: p["sha256"] for p in pieces},
    }

    outputs = {
        "pools.json": registry["template_pools"],
        "structures.json": registry["structures"],
        "processors.json": registry["processors"],
        "structure-sets.json": registry["structure_sets"],
        "resolved-pieces.json": {p["id"]: p for p in pieces},
        "jigsaw-data.json": {
            "schema_version": 3,
            "template_pools": registry["template_pools"],
            "jigsaw_structures": registry["structures"],
            "processors": registry["processors"],
            "structure_sets": registry["structure_sets"],
            "pieces": pieces,
            "resolved_templates": resolved_templates,
            "connector_index": build_connector_index(pieces),
        },
        "validation-report.json": validation,
        "import-manifest.json": manifest,
    }

    for filename, data in outputs.items():
        (out_root / filename).write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")

    print("=== VANILLA JIGSAW IMPORT ===")
    print(f"JSON source: {json_root}")
    print(f"Structure source: {structures_root}")
    print("")
    print(f"Template pools:       {validation['counts']['template_pools']}")
    print(f"Jigsaw structures:    {validation['counts']['jigsaw_structures']}")
    print(f"Processors:           {validation['counts']['processors']}")
    print(f"Structure sets:       {validation['counts']['structure_sets']}")
    print(f"Structure templates:  {validation['counts']['structure_templates_parsed']}")
    print(f"Jigsaw connectors:    {validation['counts']['connectors']}")
    print(f"Resolved templates:   {validation['counts']['resolved_templates']}")
    print(f"Errors:               {validation['counts']['errors']}")
    print(f"Warnings:             {validation['counts']['warnings']}")
    print("")
    print(f"STATUS: {validation['status']}")
    print(f"Generated: {out_root}")

    if args.strict and validation_errors:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
