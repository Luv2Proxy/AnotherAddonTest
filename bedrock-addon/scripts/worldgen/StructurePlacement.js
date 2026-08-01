import { world } from "@minecraft/server";
import { NativeStructureAdapter } from "./NativeStructureAdapter.js";
import { StructureOverlapGuard } from "./StructureOverlapGuard.js";

/** Single placement facade used by IslandGenerator for every addon-owned structure. */
export class StructurePlacement {
  constructor(detector, options = {}) {
    this.detector = detector;
    this.overlap = options.overlapGuard ?? new StructureOverlapGuard();
    this.adapter = null;
    this.generator = options.generator ?? detector?.generator ?? null;
  }

  manager() { return world.structureManager; }

  adapterFor(dimension) {
    if (!this.adapter || this.adapter.dimension !== dimension) {
      this.adapter = new NativeStructureAdapter(dimension, this.generator, this.overlap);
    }
    return this.adapter;
  }

  placeTemplate(id, dimension, location, options = {}, category = null) {
    const adapter = this.adapterFor(dimension);
    const result = adapter.placeTemplate(
      id,
      location,
      options,
      `template:${id}:${location.x}:${location.y}:${location.z}`,
      options.overlapPadding ?? 2
    );
    if (!result.placed) return result;
    const record = this.detector?.detectPlacedStructure(
      id,
      location,
      result.transformedSize ?? result.structureSize ?? this.manager().get(id)?.size,
      category
    );
    return { ...result, record };
  }

  placeJigsawStructure(id, dimension, location, options = {}, category = null) {
    const adapter = this.adapterFor(dimension);
    const result = adapter.placeJigsawStructure(
      id,
      location,
      options,
      `jigsaw:${id}:${location.x}:${location.y}:${location.z}`,
      options.overlapPadding ?? 2
    );
    if (!result.placed) return result;
    const record = result.bounds ? this.detector?.registerJigsaw(id, result.bounds, category) : null;
    return { ...result, record };
  }

  placeJigsaw(pool, target, maxDepth, dimension, location, options = {}, category = null) {
    const adapter = this.adapterFor(dimension);
    const result = adapter.placeJigsaw(
      pool,
      target,
      maxDepth,
      location,
      options,
      `jigsaw:${pool}:${target}:${location.x}:${location.y}:${location.z}`,
      options.overlapPadding ?? 2
    );
    if (!result.placed) return result;
    const record = result.bounds ? this.detector?.registerJigsaw(`jigsaw:${pool}:${target}`, result.bounds, category) : null;
    return { ...result, record };
  }
}
