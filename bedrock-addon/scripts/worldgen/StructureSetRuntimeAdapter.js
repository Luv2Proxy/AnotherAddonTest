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
      this.planner.registry = registry;
    }
  }

  plan(job, seed) {
    const origin = { x: job.x, y: job.y ?? 128, z: job.z };
    const placements = this.planner.plan(job.setId, origin, seed, {
      radius: this.runtime?.radius ?? 512,
      count: this.runtime?.maxPlacementsPerTick ?? 2
    });
    return { ...job, placements };
  }
}
