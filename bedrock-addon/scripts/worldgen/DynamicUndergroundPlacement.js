import { StructureCategory } from "./StructureRegistry.js";

/** Dynamic underground placement for procedural mineshafts/jigsaw-like structures. */
export class DynamicUndergroundPlacement {
  constructor(generator, nativePlacement, hostSelector, evaluator, overlapGuard) {
    this.generator = generator; this.nativePlacement = nativePlacement; this.hostSelector = hostSelector;
    this.evaluator = evaluator; this.overlapGuard = overlapGuard;
  }

  planMineshaft(anchor) {
    const host = this.hostSelector.selectNativeHost({ category: StructureCategory.UNDERGROUND, x: anchor.x, z: anchor.z, requiredRadius: 10 });
    if (!host) return null;
    const candidates = this.search(anchor, host, 48, 4);
    if (!candidates.length) return null;
    candidates.sort((a, b) => b.score - a.score);
    const best = candidates[0];
    return this.nativePlacement.planMineshaft({ anchor, islandAwareTarget: { x: best.x, y: best.y, z: best.z }, footprint: { radius: 10, margin: 8 }, footprintRadius: 10, searchRadius: 12, evaluateCandidate: c => this.evaluate(c, host) });
  }

  planJigsaw(anchor, footprint = { radius: 12, margin: 6 }) {
    const host = this.hostSelector.selectNativeHost({ category: StructureCategory.UNDERGROUND, x: anchor.x, z: anchor.z, footprint });
    if (!host) return null;
    return this.nativePlacement.planJigsawUnderground({ anchor, islandAwareTarget: { x: host.x, y: host.plateau ?? anchor.y, z: host.z }, footprint, footprintRadius: footprint.radius, searchRadius: 16, evaluateCandidate: c => this.evaluate(c, host) });
  }

  search(anchor, host, radius, step) {
    const out = [];
    for (let dx = -radius; dx <= radius; dx += step) for (let dz = -radius; dz <= radius; dz += step) {
      const x = host.x + dx, z = host.z + dz;
      for (const y of this.yCandidates(anchor.y, host.plateau ?? anchor.y)) {
        const e = this.evaluate({ x, y, z }, host);
        if (e.valid) out.push({ x, y, z, score: e.score, evaluation: e });
      }
    }
    return out;
  }

  yCandidates(anchorY, plateauY) {
    const center = Math.min(anchorY - 16, plateauY - 24);
    const ys = [];
    for (let d = -48; d <= 48; d += 8) ys.push(center + d);
    return ys;
  }

  evaluate(candidate, host) {
    const result = this.evaluator.evaluate({ category: StructureCategory.UNDERGROUND, ...candidate, host, footprint: { radius: 10, margin: 8 }, native: true });
    if (!result.valid) return result;
    const overburden = this.overburden(candidate.x, candidate.y, candidate.z);
    if (overburden < 8) return { valid: false, reason: "insufficient_overburden" };
    return { ...result, overburden, score: result.score + Math.min(100, overburden) * 2 + (candidate.y < 0 ? 20 : 0) };
  }

  overburden(x, y, z) {
    const segments = this.generator.column(x, z);
    if (!segments.length) return 0;
    const top = Math.max(...segments.map(s => s[1]));
    const containing = segments.find(s => y >= s[0] && y <= s[1]);
    return containing ? top - y : 0;
  }
}
