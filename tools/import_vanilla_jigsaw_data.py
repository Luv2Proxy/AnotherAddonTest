#!/usr/bin/env python3
"""Import copied vanilla-style worldgen JSON and resolve it to Bedrock pieces.

JSON is classified by its actual copied directory layout:
  structures/json/structure
  structures/json/structure_set
  structures/json/template_pool

Template pools use the Java/vanilla shape where each weighted entry wraps
its actual element in an ``element`` object. References are normalized to
Minecraft identifiers before validation and piece resolution.
"""
from __future__ import annotations

import argparse
import hashlib
import json
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
    """Minimal little-endian Bedrock NBT reader."""
    def __init__(self, data: bytes): self.data, self.pos = data, 0
    def need(self, n):
        if self.pos + n > len(self.data): raise ValueError("truncated NBT")
    def u8(self): self.need(1); x=self.data[self.pos]; self.pos+=1; return x
    def u16(self): self.need(2); x=struct.unpack_from("<H",self.data,self.pos)[0]; self.pos+=2; return x
    def i16(self): self.need(2); x=struct.unpack_from("<h",self.data,self.pos)[0]; self.pos+=2; return x
    def i32(self): self.need(4); x=struct.unpack_from("<i",self.data,self.pos)[0]; self.pos+=4; return x
    def i64(self): self.need(8); x=struct.unpack_from("<q",self.data,self.pos)[0]; self.pos+=8; return x
    def f32(self): self.need(4); x=struct.unpack_from("<f",self.data,self.pos)[0]; self.pos+=4; return x
    def f64(self): self.need(8); x=struct.unpack_from("<d",self.data,self.pos)[0]; self.pos+=8; return x
    def string(self):
        n=self.u16(); self.need(n); x=self.data[self.pos:self.pos+n].decode("utf-8","replace"); self.pos+=n; return x
    def value(self, tag):
        if tag==T_BYTE:return self.u8()
        if tag==T_SHORT:return self.i16()
        if tag==T_INT:return self.i32()
        if tag==T_LONG:return self.i64()
        if tag==T_FLOAT:return self.f32()
        if tag==T_DOUBLE:return self.f64()
        if tag==T_BA:
            n=self.i32(); self.need(n); x=list(self.data[self.pos:self.pos+n]); self.pos+=n; return x
        if tag==T_STRING:return self.string()
        if tag==T_LIST:
            et=self.u8(); n=self.i32(); return [self.value(et) for _ in range(n)]
        if tag==T_COMPOUND:
            out={}
            while True:
                ct=self.u8()
                if ct==T_END:return out
                out[self.string()]=self.value(ct)
        if tag==T_IA:
            n=self.i32(); return [self.i32() for _ in range(n)]
        if tag==T_LA:
            n=self.i32(); return [self.i64() for _ in range(n)]
        raise ValueError(f"unsupported NBT tag {tag}")
    def root(self):
        if self.u8()!=T_COMPOUND: raise ValueError("NBT root is not a compound")
        self.string(); return self.value(T_COMPOUND)


def xyz(value):
    return {"x":int(value[0]),"y":int(value[1]),"z":int(value[2])} if isinstance(value,list) and len(value)>=3 else None


def index_to_xyz(index,size):
    if not all(size): return None
    x=index%size[0]; q=index//size[0]; y=q%size[1]; z=q//size[1]
    return {"x":x,"y":y,"z":z}


def normalize_identifier(value: Any) -> str | None:
    if not isinstance(value,str): return None
    value=value.replace("\\","/")
    for suffix in (".nbt",".mcstructure",".json"):
        if value.endswith(suffix): value=value[:-len(suffix)]
    return value if ":" in value else "minecraft:"+value


def identifier_from_path(root,path):
    rel=path.relative_to(root).with_suffix("").as_posix()
    return "minecraft:"+rel


def parse_piece(structure_root,path):
    root=NBT(path.read_bytes()).root(); structure=root.get("structure",{})
    size=xyz(root.get("size")) or {"x":0,"y":0,"z":0}; dimensions=(size["x"],size["y"],size["z"])
    palette=structure.get("palette",{}).get("default",{}); block_palette=palette.get("block_palette",[])
    indices=structure.get("block_indices",[]); position_data=palette.get("block_position_data",{})
    primary=indices[0] if indices and isinstance(indices[0],list) else []; connectors=[]
    for key,entry in position_data.items():
        try:index=int(key)
        except (TypeError,ValueError):continue
        entity=entry.get("block_entity_data") if isinstance(entry,dict) else None
        if not isinstance(entity,dict) or str(entity.get("id","")).lower() not in {"jigsawblock","minecraft:jigsaw"}:continue
        pi=primary[index] if 0<=index<len(primary) else None; pe=block_palette[pi] if isinstance(pi,int) and 0<=pi<len(block_palette) else {}
        states=pe.get("states",{}) if isinstance(pe,dict) else {}; fd=states.get("facing_direction")
        connectors.append({"position":index_to_xyz(index,dimensions),"position_index":index,"facing":FACING.get(int(fd),"unknown") if isinstance(fd,(int,float)) else "unknown","facing_direction":fd if fd is not None else "unknown","rotation":states.get("rotation","unknown"),"joint":entity.get("joint","unknown"),"name":entity.get("name","unknown"),"target":entity.get("target","unknown"),"pool":entity.get("target_pool","unknown"),"final_state":entity.get("final_state","unknown"),"selection_priority":entity.get("selection_priority","unknown"),"placement_priority":entity.get("placement_priority","unknown")})
    return {"id":identifier_from_path(structure_root,path),"source":path.relative_to(structure_root).as_posix(),"source_type":path.suffix.lower().lstrip("."),"size":size,"connectors":connectors,"entities":structure.get("entities",[]),"format_version":root.get("format_version","unknown"),"sha256":sha256(path)}


def classify_json_path(path,root):
    rel=path.relative_to(root).as_posix().lower().split("/")
    if rel and rel[0] in {"template_pool","template_pools"}:return "template_pools"
    if rel and rel[0] in {"structure_set","structure_sets"}:return "structure_sets"
    if rel and rel[0] in {"structure","structures"}:return "structures"
    if "processor" in rel or "processor_list" in rel or "processor_lists" in rel:return "processors"
    if "template_pool" in rel or "template_pools" in rel:return "template_pools"
    if "structure_set" in rel or "structure_sets" in rel:return "structure_sets"
    if "structure" in rel:return "structures"
    return "unknown"


def find_identifier(path,root,kind):
    rel=path.relative_to(root).with_suffix("").as_posix()
    prefixes={"template_pools":"template_pool/","structures":"structure/","structure_sets":"structure_set/","processors":"processor/"}
    prefix=prefixes.get(kind,"")
    if rel=="template_pool/empty":return "minecraft:empty"
    return "minecraft:"+(rel[len(prefix):] if prefix and rel.startswith(prefix) else rel)


def scan_json(root):
    result={"template_pools":{},"structures":{},"processors":{},"structure_sets":{},"unknown":{}}; source_files={}
    for path in sorted(root.rglob("*.json")):
        try:data=json.loads(path.read_text(encoding="utf-8-sig"))
        except Exception as exc:
            result["unknown"][path.relative_to(root).as_posix()]={"source":path.relative_to(root).as_posix(),"error":f"JSON parse error: {exc}"};continue
        kind=classify_json_path(path,root); key=find_identifier(path,root,kind)
        entry={"source":path.relative_to(root).as_posix(),"sha256":sha256(path),"definition":data}
        (result[kind] if kind!="unknown" else result["unknown"])[key]=entry
        source_files[path.relative_to(root).as_posix()]={"sha256":entry["sha256"],"kind":kind,"identifier":key}
    return result,source_files


def resolve_structure(structures_root,identifier):
    normalized=normalize_identifier(identifier)
    if not normalized:return None
    namespace,rel=normalized.split(":",1)
    candidates=[]
    for base in (structures_root,structures_root/namespace):
        for suffix in (".mcstructure",".nbt"):candidates.append(base/(rel+suffix))
    for candidate in candidates:
        if candidate.is_file():return candidate
    wanted=Path(rel); matches=[]
    for p in structures_root.rglob("*"):
        if p.is_file() and p.suffix.lower() in {".nbt",".mcstructure"}:
            rp=p.relative_to(structures_root).with_suffix("").as_posix()
            if rp in {wanted.as_posix(),f"{namespace}/{wanted.as_posix()}"}:matches.append(p)
    return matches[0] if len(matches)==1 else None


def template_pool_object(data):return data.get("minecraft:template_pool",data) if isinstance(data,dict) else {}
def structure_object(data):return data.get("minecraft:jigsaw",data) if isinstance(data,dict) else {}
def structure_set_object(data):return data.get("minecraft:structure_set",data) if isinstance(data,dict) else {}


def extract_references(data,kind):
    refs=[]
    if kind=="template_pools":
        obj=template_pool_object(data)
        for wrapper in obj.get("elements",[]) if isinstance(obj,dict) else []:
            if not isinstance(wrapper,dict):continue
            element=wrapper.get("element",wrapper)
            if not isinstance(element,dict):continue
            location=element.get("location") or element.get("name")
            if isinstance(location,str):refs.append(("template",location))
            processors=element.get("processors")
            if isinstance(processors,str):refs.append(("processor",processors))
        fallback=obj.get("fallback") if isinstance(obj,dict) else None
        if isinstance(fallback,str):refs.append(("pool",fallback))
    elif kind=="structures":
        obj=structure_object(data)
        if isinstance(obj,dict):
            start=obj.get("start_pool") or obj.get("startPool")
            if isinstance(start,str):refs.append(("pool",start))
    elif kind=="structure_sets":
        obj=structure_set_object(data); structures=obj.get("structures",[]) if isinstance(obj,dict) else []
        if isinstance(structures,list):
            for entry in structures:
                if isinstance(entry,dict) and isinstance(entry.get("structure"),str):refs.append(("structure",entry["structure"]))
    return refs


def validate(registry,structures_root):
    errors=[]; warnings=[]; resolved={}; missing=Counter()
    for kind in ("template_pools","structures","processors","structure_sets"):
        for identifier,entry in registry[kind].items():
            for ref_kind,ref in extract_references(entry["definition"],kind):
                normalized=normalize_identifier(ref)
                if ref_kind=="template":
                    path=resolve_structure(structures_root,normalized)
                    if path is None:errors.append({"type":"missing_template","source":entry["source"],"identifier":identifier,"reference":ref});missing["templates"]+=1
                    else:resolved[normalized]=path.relative_to(structures_root).as_posix()
                elif ref_kind=="pool" and normalized not in registry["template_pools"] and normalized!="minecraft:empty":
                    errors.append({"type":"missing_pool","source":entry["source"],"identifier":identifier,"reference":ref});missing["pools"]+=1
                elif ref_kind=="processor" and normalized not in registry["processors"]:
                    warnings.append({"type":"missing_processor","source":entry["source"],"identifier":identifier,"reference":ref});missing["processors"]+=1
                elif ref_kind=="structure" and normalized not in registry["structures"]:
                    errors.append({"type":"missing_structure","source":entry["source"],"identifier":identifier,"reference":ref});missing["structures"]+=1
    return resolved,errors,warnings,missing


def build_connector_index(pieces):
    index=defaultdict(list)
    for piece in pieces:
        for i,connector in enumerate(piece.get("connectors",[])):
            name=connector.get("name")
            if isinstance(name,str) and name not in {"","unknown"}:index[name].append({"piece":piece["id"],"connector_index":i})
    return dict(index)


def main():
    parser=argparse.ArgumentParser();parser.add_argument("--json",default="bedrock-addon/structures/json");parser.add_argument("--structures",default="bedrock-addon/structures");parser.add_argument("--out",default="bedrock-addon/scripts/worldgen/generated");parser.add_argument("--strict",action="store_true");args=parser.parse_args()
    json_root,structures_root,out_root=Path(args.json).resolve(),Path(args.structures).resolve(),Path(args.out).resolve();out_root.mkdir(parents=True,exist_ok=True)
    if not json_root.exists():raise SystemExit(f"JSON source does not exist: {json_root}")
    if not structures_root.exists():raise SystemExit(f"Structure source does not exist: {structures_root}")
    raw,source_files=scan_json(json_root);registry={k:raw[k] for k in ("template_pools","structures","processors","structure_sets")}
    pieces=[];piece_errors=[]
    for path in sorted(structures_root.rglob("*.mcstructure"))+sorted(structures_root.rglob("*.nbt")):
        if json_root in path.parents:continue
        try:pieces.append(parse_piece(structures_root,path))
        except Exception as exc:piece_errors.append({"type":"piece_parse_error","source":path.relative_to(structures_root).as_posix(),"error":str(exc)})
    resolved,errors,warnings,missing=validate(registry,structures_root);errors.extend(piece_errors)
    counts={"json_files":len(source_files),"template_pools":len(registry["template_pools"]),"jigsaw_structures":len(registry["structures"]),"processors":len(registry["processors"]),"structure_sets":len(registry["structure_sets"]),"structure_templates_parsed":len(pieces),"connectors":sum(len(p.get("connectors",[])) for p in pieces),"resolved_templates":len(resolved),"errors":len(errors),"warnings":len(warnings)}
    validation={"status":"PASS" if not errors else "FAIL","counts":counts,"missing":dict(missing),"errors":errors,"warnings":warnings}
    manifest={"schema_version":2,"generated_by":"tools/import_vanilla_jigsaw_data.py","json_root":str(json_root),"structures_root":str(structures_root),"source_files":source_files,"structure_files":{p["source"]:p["sha256"] for p in pieces}}
    outputs={"pools.json":registry["template_pools"],"structures.json":registry["structures"],"processors.json":registry["processors"],"structure-sets.json":registry["structure_sets"],"resolved-pieces.json":{p["id"]:p for p in pieces},"jigsaw-data.json":{"schema_version":4,"template_pools":registry["template_pools"],"jigsaw_structures":registry["structures"],"processors":registry["processors"],"structure_sets":registry["structure_sets"],"pieces":pieces,"resolved_templates":resolved,"connector_index":build_connector_index(pieces)},"validation-report.json":validation,"import-manifest.json":manifest}
    for filename,data in outputs.items():(out_root/filename).write_text(json.dumps(data,indent=2,ensure_ascii=False),encoding="utf-8")
    print("=== VANILLA JIGSAW IMPORT ===");print(f"JSON source: {json_root}");print(f"Structure source: {structures_root}\n")
    for label,key in (("Template pools","template_pools"),("Jigsaw structures","jigsaw_structures"),("Processors","processors"),("Structure sets","structure_sets"),("Structure templates","structure_templates_parsed"),("Jigsaw connectors","connectors"),("Resolved templates","resolved_templates"),("Errors","errors"),("Warnings","warnings")):print(f"{label+':':22} {counts[key]}")
    print(f"\nSTATUS: {validation['status']}");print(f"Generated: {out_root}")
    if args.strict and errors:raise SystemExit(2)

if __name__=="__main__":main()
