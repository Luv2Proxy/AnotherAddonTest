import { world } from "@minecraft/server";

export class StructurePlacement {
  constructor(detector) { this.detector = detector; }
  manager() { return world.structureManager; }

  placeTemplate(id, dimension, location, options = {}, category = null) {
    const sm = this.manager();
    if (!sm?.place) throw new Error("StructureManager.place is unavailable");
    const structure = sm.get?.(id) ?? id;
    sm.place(structure, dimension, location, { includeBlocks: true, includeEntities: true, ...options });
    let size = null;
    try { size = typeof structure === "string" ? sm.get(id)?.size : structure.size; } catch {}
    return this.detector?.detectPlacedStructure(id, location, size, category);
  }

  placeJigsawStructure(id, dimension, location, options = {}, category = null) {
    const sm = this.manager();
    if (!sm?.placeJigsawStructure) throw new Error("StructureManager.placeJigsawStructure is unavailable");
    const bounds = sm.placeJigsawStructure(id, dimension, location, { includeEntities: true, keepJigsaws: false, ...options });
    return this.detector?.registerJigsaw(id, bounds, category);
  }

  placeJigsaw(pool, target, maxDepth, dimension, location, options = {}, category = null) {
    const sm = this.manager();
    if (!sm?.placeJigsaw) throw new Error("StructureManager.placeJigsaw is unavailable");
    const bounds = sm.placeJigsaw(pool, target, Math.max(1, Math.min(20, maxDepth)), dimension, location, { includeEntities: true, keepJigsaws: false, ...options });
    return this.detector?.registerJigsaw(`jigsaw:${pool}:${target}`, bounds, category);
  }
}
