export class StrongholdPlacementRules {
  constructor(options = {}) {
    this.minDistanceFromOrigin = Number(options.minDistanceFromOrigin ?? 256);
    this.minDistanceBetween = Number(options.minDistanceBetween ?? 512);
    this.ringCount = Math.max(1, Number(options.ringCount ?? 8));
    this.perRing = Math.max(1, Number(options.perRing ?? 8));
  }

  candidate(index, seed, origin = { x: 0, z: 0 }) {
    const ring = Math.floor(index / this.perRing);
    const slot = index % this.perRing;
    const radius = this.minDistanceFromOrigin + ring * this.minDistanceBetween;
    const angle = (slot / this.perRing) * Math.PI * 2 + this.seedOffset(seed, ring) * 0.25;
    return {
      x: Math.floor(origin.x + Math.cos(angle) * radius),
      z: Math.floor(origin.z + Math.sin(angle) * radius),
      ring,
      slot
    };
  }

  seedOffset(seed, ring) {
    let h = 2166136261 >>> 0;
    const value = `${seed}:${ring}`;
    for (let i = 0; i < value.length; i++) { h ^= value.charCodeAt(i); h = Math.imul(h, 16777619); }
    return (h >>> 0) / 4294967296;
  }

  plan(seed, origin, count = this.ringCount * this.perRing) {
    return Array.from({ length: count }, (_, index) => this.candidate(index, seed, origin));
  }
}
