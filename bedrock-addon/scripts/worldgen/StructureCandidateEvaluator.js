import { StructureCategory } from "./StructureRegistry.js";

export class StructureCandidateEvaluator {
  constructor(generator) { this.generator = generator; }

  evaluate({ category, x, y, z, host, footprint = null, native = false }) {
    const radius = footprint?.radius ?? (native ? 16 : 8);
    const margin = footprint?.margin ?? 2;
    const island = host?.island;
    if (!island) return { valid: false, reason: "missing_host" };

    const samples = this.sampleFootprint(x, z, radius);
    if (!samples.length) return { valid: false, reason: "empty_footprint" };

    let supported = 0, stoneLike = 0, water = 0, maxDelta = 0;
    let baseY = null;
    for (const p of samples) {
      const segments = this.generator.column(p.x, p.z);
      if (!segments.length) continue;
      const top = Math.max(...segments.map(s => s[1]));
      if (baseY == null) baseY = top;
      maxDelta = Math.max(maxDelta, Math.abs(top - baseY));
      const bottom = Math.min(...segments.map(s => s[0]));
      if (top >= y - 8 && top <= y + 16) supported++;
      if (top - bottom >= 4) stoneLike++;
      if (this.generator.settings.oceanEnabled && top < y) water++;
    }

    const supportRatio = supported / samples.length;
    const stoneRatio = stoneLike / samples.length;
    const waterRatio = water / samples.length;
    const maxAllowedDelta = category === StructureCategory.GROUND_VILLAGE ? 8 : category === StructureCategory.STRONGHOLD ? 16 : 12;
    if (supportRatio < (native ? 0.70 : 0.55)) return { valid: false, reason: "insufficient_support", supportRatio };
    if (maxDelta > maxAllowedDelta) return { valid: false, reason: "unstable_surface", maxDelta };
    if (category !== StructureCategory.WATER && waterRatio > 0.35) return { valid: false, reason: "water_dominated", waterRatio };

    const hostDistance = Math.hypot(x - island.x, z - island.z);
    const interior = Math.max(0, (host.usableRadius ?? host.radius) - hostDistance - margin);
    const score = supportRatio * 1000 + stoneRatio * 300 + interior * 4 - maxDelta * 15 - waterRatio * 400;
    return { valid: true, supportRatio, stoneRatio, waterRatio, maxDelta, baseY, score, earlyAccept: supportRatio > 0.95 && maxDelta <= 4 };
  }

  sampleFootprint(x, z, radius) {
    const points = [{ x: Math.floor(x), z: Math.floor(z) }];
    const n = Math.max(8, Math.min(64, Math.ceil(radius * 2)));
    for (let i = 0; i < n; i++) {
      const a = i * Math.PI * 2 / n;
      points.push({ x: Math.floor(x + Math.cos(a) * radius), z: Math.floor(z + Math.sin(a) * radius) });
    }
    for (const q of [0.5, 0.75]) for (let i = 0; i < n; i += 2) {
      const a = i * Math.PI * 2 / n;
      points.push({ x: Math.floor(x + Math.cos(a) * radius * q), z: Math.floor(z + Math.sin(a) * radius * q) });
    }
    return points;
  }
}
