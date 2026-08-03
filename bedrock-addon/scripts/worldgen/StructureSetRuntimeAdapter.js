import { StructureSetPlacementPlanner } from "./StructureSetPlacementPlanner.js";

export class StructureSetRuntimeAdapter {
  constructor(runtime, options = {}) {
    this.runtime = runtime;
    this.registry = options.registry ?? runtime?.registry;
    this.planner = options.planner ?? new StructureSetPlacementPlanner(this.registry, options);
  }

  refresh(registry = this.runtime?.registry) {
    if (registry) {
      this.registry = registry;
      this.planner.refresh?.(registry);
      this.planner.registry = registry;
    }
    return this;
  }

  plan(job, seed) {
    const origin = { x: job.x, y: job.y ?? 128, z: job.z };
    const definition = this.registry?.structureSet?.(job.setId) ?? {};
    const placement = definition?.placement ?? definition ?? {};
    const spacing = Math.max(1, Number(placement.spacing ?? 34));
    // A player entering a chunk should only evaluate the local random-spread
    // neighborhood. The previous 512-block radius could make one set scan over
    // 1000 grid cells synchronously, which is enough to hit the Bedrock watchdog.
    // Nearby chunks are queued again as the player moves, so this remains
    // incremental while preserving coverage.
    const radius = Math.min(this.runtime?.radius ?? 128, Math.max(spacing * 1.5, 48));
    const placements = this.planner.plan(job.setId, origin, seed, {
      radius,
      count: 1,
      host: job.host,
      dimension: this.runtime?.generator?.dimension
    });
    return { ...job, placements };
  }
}
