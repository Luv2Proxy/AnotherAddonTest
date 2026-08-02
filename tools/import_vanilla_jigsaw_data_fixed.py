#!/usr/bin/env python3
"""Fixed vanilla Jigsaw/worldgen importer.

This is a drop-in replacement for import_vanilla_jigsaw_data.py.

Fixes:
- strict little-endian Bedrock NBT parsing with complete standard tag support
- signed TAG_Byte handling
- bounds checks and parse-offset diagnostics
- optional gzip/zlib wrapped NBT
- local structure alias/rename resolution
- separate missing vs ambiguous vs resolved template references
- inline processor definitions are not treated as missing processor files
- processor-list references are validated separately
- connector extraction is reported independently from template resolution
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import struct
import zlib
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

T_END, T_BYTE, T_SHORT, T_INT, T_LONG, T_FLOAT, T_DOUBLE, T_BA, T_STRING, T_LIST, T_COMPOUND, T_IA, T_LA = range(13)
VALID_TAGS = set(range(13))
FACING = {0: "down", 1: "up", 2: "north", 3: "south", 4: "west", 5: "east"}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def norm_path(value: str) -> str:
    value = value.replace("\\", "/").strip("/").lower()
    while "//" in value:
        value = value.replace("//", "/")
    return value


def normalize_identifier(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    value = norm_path(value)
    for suffix in (".mcstructure", ".nbt", ".json"):
        if value.endswith(suffix):
            value = value[:-len(suffix)]
    if not value:
        return None
    return value if ":" in value else "minecraft:" + value


def identifier_from_path(root: Path, path: Path) -> str:
    return "minecraft:" + norm_path(path.relative_to(root).with_suffix("").as_posix())


class NBTError(ValueError):
    pass


class NBT:
    """Strict little-endian NBT reader for Bedrock .mcstructure/.nbt files."""
    def __init__(self, data: bytes):
        self.data = data
        self.pos = 0
        self.context: list[str] = []

    def error(self, message: str) -> NBTError:
        path = ".".join(self.context)
        suffix = f" path={path}" if path else ""
        return NBTError(f"{message} at offset 0x{self.pos:x} (remaining={len(self.data)-self.pos}){suffix}")

    def need(self, n: int) -> None:
        if n < 0 or self.pos + n > len(self.data):
            raise self.error(f"truncated NBT while reading {n} bytes")

    def u8(self) -> int:
        self.need(1); x = self.data[self.pos]; self.pos += 1; return x

    def i8(self) -> int:
        x = self.u8(); return x - 256 if x >= 128 else x

    def u16(self) -> int:
        self.need(2); x = struct.unpack_from("<H", self.data, self.pos)[0]; self.pos += 2; return x

    def i16(self) -> int:
        self.need(2); x = struct.unpack_from("<h", self.data, self.pos)[0]; self.pos += 2; return x

    def i32(self) -> int:
        self.need(4); x = struct.unpack_from("<i", self.data, self.pos)[0]; self.pos += 4; return x

    def i64(self) -> int:
        self.need(8); x = struct.unpack_from("<q", self.data, self.pos)[0]; self.pos += 8; return x

    def f32(self) -> float:
        self.need(4); x = struct.unpack_from("<f", self.data, self.pos)[0]; self.pos += 4; return x

    def f64(self) -> float:
        self.need(8); x = struct.unpack_from("<d", self.data, self.pos)[0]; self.pos += 8; return x

    def string(self) -> str:
        n = self.u16()
        self.need(n)
        raw = self.data[self.pos:self.pos+n]
        self.pos += n
        return raw.decode("utf-8", "replace")

    def length(self, what: str) -> int:
        n = self.i32()
        if n < 0:
            raise self.error(f"negative {what} length {n}")
        return n

    def value(self, tag: int) -> Any:
        if tag not in VALID_TAGS:
            raise self.error(f"unsupported NBT tag {tag}")
        if tag == T_BYTE: return self.i8()
        if tag == T_SHORT: return self.i16()
        if tag == T_INT: return self.i32()
        if tag == T_LONG: return self.i64()
        if tag == T_FLOAT: return self.f32()
        if tag == T_DOUBLE: return self.f64()
        if tag == T_BA:
            n = self.length("byte-array")
            self.need(n)
            raw = self.data[self.pos:self.pos+n]
            self.pos += n
            return list(raw)
        if tag == T_STRING: return self.string()
        if tag == T_LIST:
            element_type = self.u8()
            n = self.length("list")
            if element_type not in VALID_TAGS:
                raise self.error(f"unsupported list element tag {element_type}")
            if n == 0:
                return []
            out = []
            for i in range(n):
                self.context.append(f"[{i}]")
                try:
                    out.append(self.value(element_type))
                finally:
                    self.context.pop()
            return out
        if tag == T_COMPOUND:
            out = {}
            while True:
                child_type = self.u8()
                if child_type == T_END:
                    return out
                if child_type not in VALID_TAGS:
                    raise self.error(f"unsupported compound tag {child_type}")
                name = self.string()
                self.context.append(name)
                try:
                    out[name] = self.value(child_type)
                finally:
                    self.context.pop()
        if tag == T_IA:
            n = self.length("int-array")
            self.need(n * 4)
            return [self.i32() for _ in range(n)]
        if tag == T_LA:
            n = self.length("long-array")
            self.need(n * 8)
            return [self.i64() for _ in range(n)]
        raise self.error(f"unsupported NBT tag {tag}")

    def root(self) -> dict[str, Any]:
        root_type = self.u8()
        if root_type != T_COMPOUND:
            raise self.error(f"NBT root is tag {root_type}, expected TAG_Compound (10)")
        self.string()
        result = self.value(T_COMPOUND)
        if self.pos != len(self.data):
            raise self.error(f"trailing bytes after root compound: {len(self.data)-self.pos}")
        return result


def unwrap(data: bytes) -> tuple[bytes, str]:
    if data[:2] == b"\x1f\x8b":
        return gzip.decompress(data), "gzip"
    if len(data) >= 2 and data[0] == 0x78:
        try:
            return zlib.decompress(data), "zlib"
        except zlib.error:
            pass
    return data, "raw"


def load_nbt(path: Path) -> tuple[dict[str, Any], str]:
    raw, wrapper = unwrap(path.read_bytes())
    return NBT(raw).root(), wrapper


def xyz(value: Any) -> dict[str, int] | None:
    if isinstance(value, (list, tuple)) and len(value) >= 3:
        return {"x": int(value[0]), "y": int(value[1]), "z": int(value[2])}
    return None


def index_to_xyz(index: int, size: tuple[int, int, int]) -> dict[str, int] | None:
    sx, sy, sz = size
    if sx <= 0 or sy <= 0 or sz <= 0 or index < 0 or index >= sx * sy * sz:
        return None
    x = index % sx
    q = index // sx
    y = q % sy
    z = q // sy
    return {"x": x, "y": y, "z": z}


def parse_piece(structure_root: Path, path: Path) -> dict[str, Any]:
    root, wrapper = load_nbt(path)
    structure = root.get("structure", {})
    if not isinstance(structure, dict):
        raise ValueError("missing/invalid root structure compound")
    size = xyz(root.get("size")) or {"x": 0, "y": 0, "z": 0}
    dims = (size["x"], size["y"], size["z"])
    palette_root = structure.get("palette", {})
    palette = palette_root.get("default", {}) if isinstance(palette_root, dict) else {}
    block_palette = palette.get("block_palette", []) if isinstance(palette, dict) else []
    indices = structure.get("block_indices", [])
    primary = indices[0] if isinstance(indices, list) and indices and isinstance(indices[0], list) else []
    position_data = palette.get("block_position_data", {}) if isinstance(palette, dict) else {}
    connectors = []

    for key, entry in position_data.items() if isinstance(position_data, dict) else []:
        try:
            index = int(key)
        except (TypeError, ValueError):
            continue
        entity = entry.get("block_entity_data") if isinstance(entry, dict) else None
        if not isinstance(entity, dict):
            continue
        entity_id = str(entity.get("id", "")).lower().replace("minecraft:", "")
        if entity_id not in {"jigsawblock", "jigsaw"}:
            continue

        palette_entry = {}
        if 0 <= index < len(primary):
            pi = primary[index]
            if isinstance(pi, int) and 0 <= pi < len(block_palette):
                candidate = block_palette[pi]
                if isinstance(candidate, dict):
                    palette_entry = candidate
        states = palette_entry.get("states", {}) if isinstance(palette_entry, dict) else {}
        if not isinstance(states, dict):
            states = {}
        fd = states.get("facing_direction")
        connectors.append({
            "position": index_to_xyz(index, dims),
            "position_index": index,
            "facing": FACING.get(int(fd), "unknown") if isinstance(fd, (int, float)) else "unknown",
            "facing_direction": fd if fd is not None else "unknown",
            "rotation": states.get("rotation", "unknown"),
            "joint": entity.get("joint", "unknown"),
            "name": entity.get("name", "unknown"),
            "target": entity.get("target", "unknown"),
            "pool": entity.get("target_pool", entity.get("pool", "unknown")),
            "final_state": entity.get("final_state", entity.get("final_block", "unknown")),
            "selection_priority": entity.get("selection_priority", "unknown"),
            "placement_priority": entity.get("placement_priority", "unknown"),
            "block_entity_data": entity,
        })

    return {
        "id": identifier_from_path(structure_root, path),
        "source": path.relative_to(structure_root).as_posix(),
        "source_type": path.suffix.lower().lstrip("."),
        "compression": wrapper,
        "size": size,
        "connectors": connectors,
        "entities": structure.get("entities", []),
        "format_version": root.get("format_version", "unknown"),
        "sha256": sha256(path),
    }


def classify_json_path(path: Path, root: Path) -> str:
    rel = norm_path(path.relative_to(root).as_posix()).split("/")
    if rel and rel[0] in {"template_pool", "template_pools"}: return "template_pools"
    if rel and rel[0] in {"structure_set", "structure_sets"}: return "structure_sets"
    if rel and rel[0] in {"structure", "structures"}: return "structures"
    if any("processor" in x for x in rel): return "processors"
    if any(x in {"template_pool", "template_pools"} for x in rel): return "template_pools"
    if any(x in {"structure_set", "structure_sets"} for x in rel): return "structure_sets"
    if any(x in {"structure", "structures"} for x in rel): return "structures"
    return "unknown"


def find_identifier(path: Path, root: Path, kind: str) -> str:
    rel = norm_path(path.relative_to(root).with_suffix("").as_posix())
    prefixes = {
        "template_pools": ("template_pool/", "template_pools/"),
        "structures": ("structure/", "structures/"),
        "structure_sets": ("structure_set/", "structure_sets/"),
        "processors": ("processor/", "processors/", "processor_list/", "processor_lists/"),
    }
    if rel in {"template_pool/empty", "template_pools/empty"}: return "minecraft:empty"
    for prefix in prefixes.get(kind, ()):
        if rel.startswith(prefix):
            rel = rel[len(prefix):]
            break
    return normalize_identifier(rel) or "minecraft:unknown"


def scan_json(root: Path):
    result = {"template_pools": {}, "structures": {}, "processors": {}, "structure_sets": {}, "unknown": {}}
    source_files = {}
    for path in sorted(root.rglob("*.json")):
        rel = path.relative_to(root).as_posix()
        try:
            data = json.loads(path.read_text(encoding="utf-8-sig"))
        except Exception as exc:
            result["unknown"][rel] = {"source": rel, "error": f"JSON parse error: {exc}"}
            continue
        kind = classify_json_path(path, root)
        key = find_identifier(path, root, kind)
        entry = {"source": rel, "sha256": sha256(path), "definition": data}
        result[kind][key] = entry
        source_files[rel] = {"sha256": entry["sha256"], "kind": kind, "identifier": key}
    return result, source_files


def build_asset_index(root: Path):
    exact = defaultdict(list)
    basename = defaultdict(list)
    files = []
    for path in sorted(root.rglob("*")):
        if not path.is_file() or path.suffix.lower() not in {".mcstructure", ".nbt"}:
            continue
        if "json" in path.relative_to(root).parts:
            continue
        files.append(path)
        exact[norm_path(path.relative_to(root).with_suffix("").as_posix())].append(path)
        basename[path.stem.lower()].append(path)
    return {"exact": exact, "basename": basename, "files": files}


def resolve_structure(root: Path, identifier: Any, index) -> tuple[Path | None, str]:
    normalized = normalize_identifier(identifier)
    if not normalized:
        return None, "invalid"
    namespace, rel = normalized.split(":", 1)
    keys = [norm_path(rel), f"{namespace}/{norm_path(rel)}"]
    for key in keys:
        matches = index["exact"].get(key, [])
        if len(matches) == 1:
            return matches[0], "exact"
        if len(matches) > 1:
            return None, "ambiguous"

    base = Path(rel).name.lower()
    matches = index["basename"].get(base, [])
    if len(matches) == 1:
        return matches[0], "basename"
    if len(matches) > 1:
        return None, "ambiguous"

    normalized_stem = base.replace("_", "").replace("-", "")
    fuzzy = [p for p in index["files"] if p.stem.lower().replace("_", "").replace("-", "") == normalized_stem]
    if len(fuzzy) == 1:
        return fuzzy[0], "normalized_basename"
    if len(fuzzy) > 1:
        return None, "ambiguous"
    return None, "missing"


def template_pool_object(data):
    return data.get("minecraft:template_pool", data) if isinstance(data, dict) else {}


def structure_object(data):
    return data.get("minecraft:jigsaw", data) if isinstance(data, dict) else {}


def structure_set_object(data):
    return data.get("minecraft:structure_set", data) if isinstance(data, dict) else {}


def extract_references(data, kind):
    refs = []
    if kind == "template_pools":
        obj = template_pool_object(data)
        for wrapper in obj.get("elements", []) if isinstance(obj, dict) else []:
            if not isinstance(wrapper, dict):
                continue
            element = wrapper.get("element", wrapper)
            if not isinstance(element, dict):
                continue
            location = element.get("location") or element.get("name")
            if isinstance(location, str):
                refs.append(("template", location))
            processors = element.get("processors")
            if isinstance(processors, str):
                refs.append(("processor_list", processors))
            elif isinstance(processors, dict):
                refs.append(("inline_processor", str(processors.get("processor_type", "unknown"))))
        fallback = obj.get("fallback") if isinstance(obj, dict) else None
        if isinstance(fallback, str):
            refs.append(("pool", fallback))
    elif kind == "structures":
        obj = structure_object(data)
        if isinstance(obj, dict):
            start = obj.get("start_pool") or obj.get("startPool")
            if isinstance(start, str):
                refs.append(("pool", start))
    elif kind == "structure_sets":
        obj = structure_set_object(data)
        structures = obj.get("structures", []) if isinstance(obj, dict) else []
        if isinstance(structures, list):
            for entry in structures:
                if isinstance(entry, dict) and isinstance(entry.get("structure"), str):
                    refs.append(("structure", entry["structure"]))
    return refs


def validate(registry, structures_root, asset_index):
    errors, warnings = [], []
    resolved, aliases = {}, {}
    missing = Counter()
    seen = set()
    for kind in ("template_pools", "structures", "processors", "structure_sets"):
        for identifier, entry in registry[kind].items():
            for ref_kind, ref in extract_references(entry["definition"], kind):
                normalized = normalize_identifier(ref)
                key = (kind, identifier, ref_kind, normalized)
                if key in seen:
                    continue
                seen.add(key)
                if ref_kind == "template":
                    path, method = resolve_structure(structures_root, normalized, asset_index)
                    if path is None:
                        errors.append({"type": "missing_template" if method == "missing" else "ambiguous_template", "source": entry["source"], "identifier": identifier, "reference": ref, "normalized": normalized, "resolution": method})
                        missing["templates"] += 1
                    else:
                        resolved[normalized] = path.relative_to(structures_root).as_posix()
                        if method != "exact":
                            aliases[normalized] = {"source": resolved[normalized], "method": method}
                elif ref_kind == "pool" and normalized not in registry["template_pools"] and normalized != "minecraft:empty":
                    errors.append({"type": "missing_pool", "source": entry["source"], "identifier": identifier, "reference": ref})
                    missing["pools"] += 1
                elif ref_kind == "processor_list" and normalized not in registry["processors"]:
                    warnings.append({"type": "missing_processor_list", "source": entry["source"], "identifier": identifier, "reference": ref})
                    missing["processor_lists"] += 1
                elif ref_kind == "structure" and normalized not in registry["structures"]:
                    errors.append({"type": "missing_structure", "source": entry["source"], "identifier": identifier, "reference": ref})
                    missing["structures"] += 1
                # inline_processor is intentionally not an error.
    return resolved, aliases, errors, warnings, missing


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
    parser.add_argument("--json", default="bedrock-addon/structures/json")
    parser.add_argument("--structures", default="bedrock-addon/structures")
    parser.add_argument("--out", default="bedrock-addon/scripts/worldgen/generated")
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()

    json_root = Path(args.json).resolve()
    structures_root = Path(args.structures).resolve()
    out_root = Path(args.out).resolve()
    out_root.mkdir(parents=True, exist_ok=True)
    if not json_root.exists(): raise SystemExit(f"JSON source does not exist: {json_root}")
    if not structures_root.exists(): raise SystemExit(f"Structure source does not exist: {structures_root}")

    raw, source_files = scan_json(json_root)
    registry = {k: raw[k] for k in ("template_pools", "structures", "processors", "structure_sets")}
    asset_index = build_asset_index(structures_root)

    pieces, piece_errors = [], []
    candidate_paths = sorted(asset_index["files"])
    for path in candidate_paths:
        try:
            pieces.append(parse_piece(structures_root, path))
        except Exception as exc:
            piece_errors.append({"type": "piece_parse_error", "source": path.relative_to(structures_root).as_posix(), "error": str(exc), "sha256": sha256(path)})

    resolved, aliases, errors, warnings, missing = validate(registry, structures_root, asset_index)
    errors.extend(piece_errors)
    connector_count = sum(len(p.get("connectors", [])) for p in pieces)
    with_connectors = sum(1 for p in pieces if p.get("connectors"))

    counts = {
        "json_files": len(source_files),
        "template_pools": len(registry["template_pools"]),
        "jigsaw_structures": len(registry["structures"]),
        "processors": len(registry["processors"]),
        "structure_sets": len(registry["structure_sets"]),
        "structure_files_found": len(candidate_paths),
        "structure_templates_parsed": len(pieces),
        "structure_templates_failed": len(piece_errors),
        "pieces_with_connectors": with_connectors,
        "pieces_without_connectors": len(pieces) - with_connectors,
        "connectors": connector_count,
        "resolved_templates": len(resolved),
        "renamed_template_aliases": len(aliases),
        "errors": len(errors),
        "warnings": len(warnings),
    }

    validation = {"status": "PASS" if not errors else "FAIL", "counts": counts, "missing": dict(missing), "errors": errors, "warnings": warnings, "template_aliases": aliases}
    manifest = {"schema_version": 3, "generated_by": "tools/import_vanilla_jigsaw_data_fixed.py", "json_root": str(json_root), "structures_root": str(structures_root), "source_files": source_files, "structure_files": {p["source"]: p["sha256"] for p in pieces}}
    outputs = {
        "pools.json": registry["template_pools"],
        "structures.json": registry["structures"],
        "processors.json": registry["processors"],
        "structure-sets.json": registry["structure_sets"],
        "resolved-pieces.json": {p["id"]: p for p in pieces},
        "jigsaw-connectors.json": build_connector_index(pieces),
        "jigsaw-data.json": {"schema_version": 5, "template_pools": registry["template_pools"], "structures": registry["structures"], "processors": registry["processors"], "structure_sets": registry["structure_sets"], "pieces": {p["id"]: p for p in pieces}, "resolved_templates": resolved, "template_aliases": aliases},
        "import-manifest.json": manifest,
        "import-validation.json": validation,
    }
    for name, data in outputs.items():
        (out_root / name).write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")

    print("=== VANILLA JIGSAW IMPORT (FIXED) ===")
    print(f"JSON source: {json_root}")
    print(f"Structure source: {structures_root}")
    print()
    for label, key in [
        ("Template pools", "template_pools"), ("Jigsaw structures", "jigsaw_structures"),
        ("Processors", "processors"), ("Structure sets", "structure_sets"),
        ("Structure files found", "structure_files_found"), ("Structure templates parsed", "structure_templates_parsed"),
        ("Parse failures", "structure_templates_failed"), ("Pieces with jigsaws", "pieces_with_connectors"),
        ("Jigsaw connectors", "connectors"), ("Resolved templates", "resolved_templates"),
        ("Renamed aliases", "renamed_template_aliases"), ("Errors", "errors"), ("Warnings", "warnings")]:
        print(f"{label+':':24} {counts[key]}")
    print()
    print(f"STATUS: {validation['status']}")
    print(f"Generated: {out_root}")
    if piece_errors:
        print("\nFirst parse failures:")
        for error in piece_errors[:10]:
            print(f"  - {error['source']}: {error['error']}")
    if aliases:
        print("\nFirst renamed template resolutions:")
        for ident, info in list(aliases.items())[:10]:
            print(f"  - {ident} -> {info['source']} ({info['method']})")
    if args.strict and errors:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
