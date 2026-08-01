import { StructureCategory } from "./StructureRegistry.js";

/** Island host selection ported from the Java placement resolver concepts. */
export class StructureHostSelector {
  constructor(generator) { this.generator = generator; }

  selectNativeHost({ category, x, z, footprint = null, requiredRadius = 0 }) {
    const descriptors = this.generator.descriptorsNear(x, z);
    const candidates = [];
    for (const island of descriptors) {
      const radius = Math.min(island.rx ?? island.maxR ?? 0, island.rz ?? island.maxR ?? 0);
      const footprintRadius = footprint?.radius ?? requiredRadius ?? 0;
      const margin = footprint?.margin ?? this.marginFor(category);
      if (radius - margin < footprintRadius) continue;
      const distance = Math.hypot(x - island.x, z - island.z);
      const stable = this.stableTopCells(island, x, z, footprintRadius);
      if (!stable.valid) continue;
      const plateau = island.plateau ?? 0;
      const score = this.score(category, distance, radius, plateau, stable);
      candidates.push({ x: island.x, z: island.z, radius, usableRadius: radius - margin, margin, plateau, island, stable, score });
    }
    candidates.sort((a, b) => b.score - a.score);
    return candidates[0] ?? null;
  }

  marginFor(category) {
    switch (category) {
      case StructureCategory.STRONGHOLD: return 12;
      case StructureCategory.UNDERGROUND: return 8;
      case StructureCategory.GROUND_VILLAGE: return 4;
      case StructureCategory.HAMLET_SKY: return 6;
      default: return 2;
    }
  }

  stableTopCells(island, x, z, radius) {
    const samples = Math.max(4, Math.min(32, Math.ceil(radius / 3)));
    let valid = 0, total = 0, minY = Infinity, maxY = -Infinity;
    for (let i = 0; i < samples; i++) {
      const a = (Math.PI * 2 * i) / samples;
      const sx = Math.round(x + Math.cos(a) * radius * 0.75);
      const sz = Math.round(z + Math.sin(a) * radius * 0.75);
      const column = this.generator.column(sx, sz);
      if (!column.length) continue;
      total++;
      const y = Math.max(...column.map(s => s[1]));
      minY = Math.min(minY, y); maxY = Math.max(maxY, y); valid++;
    }
    const variance = total ? maxY - minY : Infinity;
    return { valid: total > 0 && valid / total >= 0.75 && variance <= 12, ratio: total ? valid / total : 0, variance, minY, maxY };
  }

  score(category, distance, radius, plateau, stable) {
    const stability = stable.ratio * 500 - stable.variance * 8;
    const interior = radius * 2;
    const plateauBonus = category === StructureCategory.GROUND_VILLAGE || category === StructureCategory.STRONGHOLD ? plateau * 4 : 0;
    const distancePenalty = category === StructureCategory.SMALL_SKY ? distance * 0.1 : distance * 0.35;
    return stability + interior + plateauBonus - distancePenalty;
  }
}
