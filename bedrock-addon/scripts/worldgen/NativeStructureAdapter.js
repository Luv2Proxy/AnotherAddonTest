import { StructureCategory } from "./StructureRegistry.js";

/**
 * Bridges the native placement plans to the Bedrock APIs that are actually
 * available. It prefers native structure generation APIs when present and
 * otherwise records a deterministic plan instead of incorrectly treating a
 * procedural structure as an NBT template.
 */
export class NativeStructureAdapter {
  constructor(dimension, generator, overlapGuard) {
    this.dimension = dimension; this.generator = generator; this.overlapGuard = overlapGuard;
  }

  async placeStronghold(plan) {
    if (!plan?.accepted) return null;
    return this.placeNativeOrPlan(plan, "stronghold");
  }

  async placeMineshaft(plan) {
    if (!plan?.accepted) return null;
    return this.placeNativeOrPlan(plan, "mineshaft");
  }

  async placeNativeOrPlan(plan, type) {
    const target = plan.target;
    const api = this.dimension?.runCommandAsync;
    // Bedrock scripting does not expose Java's StructureStart/Piece graph.
    // Never substitute a converted .mcstructure here. If a future/native API
    // is available, this adapter is the single integration point for it.
    if (typeof this.dimension?.placeStructure === "function") {
      const bounds = await this.dimension.placeStructure(type, target);
      if (bounds && !this.overlapGuard.reserve(`${type}:${target.x}:${target.y}:${target.z}`, bounds, 4)) return null;
      return { type, target, bounds, native: true };
    }
    if (typeof api === "function") {
      // Keep this path disabled by default: command syntax differs between
      // versions and cannot faithfully reproduce Java StructureStart relocation.
      return { type, target, native: false, planned: true, reason: "native_structure_api_unavailable" };
    }
    return { type, target, native: false, planned: true, reason: "native_structure_api_unavailable" };
  }
}
