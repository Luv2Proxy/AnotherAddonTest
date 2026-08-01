import { StructureCategory } from "./StructureRegistry.js";
import { StructureStartRelocator } from "./StructureStartRelocator.js";

export class StrongholdPlacementEngine {
  constructor(generator, hostSelector, evaluator, overlapGuard) {
    this.generator = generator; this.hostSelector = hostSelector; this.evaluator = evaluator; this.overlapGuard = overlapGuard; this.relocator = new StructureStartRelocator();
  }

  plan(request) {
    const host = this.hostSelector.selectNativeHost({ category: StructureCategory.STRONGHOLD, x: request.x, z: request.z, footprint: { radius: request.footprintRadius ?? 24, margin: 12 }, requiredRadius: request.footprintRadius ?? 24 });
    if (!host) return { accepted: false, reason: "no_host_island" };
    const anchor = request.anchor ?? { x: request.x, y: request.y ?? host.plateau ?? 64, z: request.z };
    const candidates = this.verticalCandidates(anchor, host, request);
    let best = null;
    for (const candidate of candidates) {
      const evaluation = this.evaluator.evaluate({ category: StructureCategory.STRONGHOLD, ...candidate, host, footprint: { radius: request.footprintRadius ?? 24, margin: 12 }, native: true });
      if (!evaluation.valid) continue;
      const score = evaluation.score + (candidate.y < 0 ? 100 : 0) - Math.abs(candidate.y - (host.plateau ?? candidate.y));
      if (!best || score > best.score) best = { ...candidate, score, evaluation };
    }
    if (!best) return { accepted: false, reason: "no_valid_vertical_anchor", host };
    return { accepted: true, category: StructureCategory.STRONGHOLD, host, target: best, strategy: "native_stronghold_vertical_relocation" };
  }

  verticalCandidates(anchor, host, request) {
    const out = [], min = request.minY ?? -64, max = request.maxY ?? 96, step = request.step ?? 4;
    for (let y = min; y <= max; y += step) {
      for (const [dx, dz] of [[0,0], [8,0], [-8,0], [0,8], [0,-8]]) {
        const x = host.x + dx, z = host.z + dz;
        if (Math.hypot(x - host.x, z - host.z) + (request.footprintRadius ?? 24) <= host.usableRadius) out.push({ x, y, z });
      }
    }
    return out;
  }

  relocate(start, plan) {
    if (!plan?.accepted) return null;
    const moved = this.relocator.relocate(start, plan.target);
    if (!moved) return null;
    const validation = this.relocator.validate(moved, piece => this.pieceSupport(piece, plan.host));
    if (!validation.valid) return null;
    if (moved.bounds && !this.overlapGuard.reserve(`stronghold:${plan.target.x}:${plan.target.y}:${plan.target.z}`, moved.bounds, 8)) return null;
    return moved;
  }

  pieceSupport(piece, host) {
    const box = piece.boundingBox;
    if (!box) return { valid: true };
    const centerX = (box.min.x + box.max.x) / 2, centerZ = (box.min.z + box.max.z) / 2;
    return Math.hypot(centerX - host.x, centerZ - host.z) <= host.usableRadius ? { valid: true } : { valid: false, reason: "piece_outside_host" };
  }
}
