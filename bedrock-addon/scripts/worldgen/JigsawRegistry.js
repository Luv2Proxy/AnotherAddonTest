import { world } from "@minecraft/server";

export class JigsawRegistry {
  constructor(data = null) { this.pieces=new Map(); this.pools=new Map(); this.structures=new Map(); this.processors=new Map(); this.load(data); }
  load(data) {
    if (!data) return this;
    for (const p of data.pieces ?? []) this.pieces.set(p.id,p);
    for (const [id,p] of Object.entries(data.template_pools ?? {})) this.pools.set(id,p.definition ?? p);
    for (const [id,s] of Object.entries(data.jigsaw_structures ?? {})) this.structures.set(id,s.definition ?? s);
    for (const [id,p] of Object.entries(data.processors ?? {})) this.processors.set(id,p.definition ?? p);
    return this;
  }
  piece(id) { return this.pieces.get(id) ?? null; }
  pool(id) { return this.pools.get(id) ?? null; }
  structure(id) { return this.structures.get(id) ?? null; }
  connectors(id) { return this.piece(id)?.jigsaws ?? []; }
  poolElements(id) {
    const p=this.pool(id); return p?.elements ?? [];
  }
  candidates(poolId,targetName) {
    return this.poolElements(poolId).filter(e=>e?.element && (e.element.element_type === "minecraft:single_pool_element" || e.element.element_type === "minecraft:legacy_single_pool_element" || e.element.element_type === "minecraft:list_pool_element" || e.element.element_type === "minecraft:empty_pool_element"))
      .filter(e=>e.element.element_type === "minecraft:empty_pool_element" || e.element.element_type === "minecraft:list_pool_element" || e.element.location)
      .map(e=>({weight:Number(e.weight??1),element:e.element,targetName}));
  }
}

export function structureLocationToId(location) {
  if (!location) return null;
  return location.includes(":") ? location : `minecraft:${location}`;
}
