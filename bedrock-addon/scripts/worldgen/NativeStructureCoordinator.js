import { StructureCategory } from "./StructureRegistry.js";
import { StructureHostSelector } from "./StructureHostSelector.js";
import { StructureCandidateEvaluator } from "./StructureCandidateEvaluator.js";
import { StructureOverlapGuard } from "./StructureOverlapGuard.js";
import { StructureStartRelocator } from "./StructureStartRelocator.js";
import { StrongholdPlacementEngine } from "./StrongholdPlacementEngine.js";
import { DynamicUndergroundPlacement } from "./DynamicUndergroundPlacement.js";
import { NativeStructurePlacement } from "./NativeStructurePlacement.js";
import { NativeStructureAdapter } from "./NativeStructureAdapter.js";

/** Single coordinator for the native, non-NBT structure pipelines. */
export class NativeStructureCoordinator {
  constructor(generator, dimension) {
    this.generator = generator; this.dimension = dimension;
    this.overlap = new StructureOverlapGuard();
    this.hosts = new StructureHostSelector(generator);
    this.evaluator = new StructureCandidateEvaluator(generator);
    this.nativePlacement = new NativeStructurePlacement(null, this.hosts);
    this.stronghold = new StrongholdPlacementEngine(generator, this.hosts, this.evaluator, this.overlap);
    this.underground = new DynamicUndergroundPlacement(generator, this.nativePlacement, this.hosts, this.evaluator, this.overlap);
    this.adapter = new NativeStructureAdapter(dimension, generator, this.overlap);
    this.relocator = new StructureStartRelocator();
  }

  plan(type, request) {
    switch (type) {
      case "stronghold": return this.stronghold.plan(request);
      case "mineshaft": return this.underground.planMineshaft(request);
      case "jigsaw": return this.underground.planJigsaw(request, request.footprint);
      default: return { accepted: false, reason: "unsupported_native_structure" };
    }
  }

  async commit(type, plan, generatedStart = null) {
    if (!plan?.accepted) return null;
    if (generatedStart) {
      const moved = this.relocator.relocate(generatedStart, plan.target);
      if (!moved) return null;
      if (moved.bounds && !this.overlap.reserve(`${type}:${plan.target.x}:${plan.target.y}:${plan.target.z}`, moved.bounds, 8)) return null;
      return { type, native: true, relocated: moved };
    }
    if (type === "stronghold") return this.adapter.placeStronghold(plan);
    if (type === "mineshaft") return this.adapter.placeMineshaft(plan);
    return { type, planned: true, target: plan.target };
  }

  snapshot() { return { overlap: this.overlap.serialize(), version: 1 }; }
  restore(snapshot) { this.overlap.restore(snapshot?.overlap); }
}
