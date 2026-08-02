import { StructureCategory } from "./StructureRegistry.js";

/**
 * Functional Bedrock counterpart to the base mod's native StructureStart flows.
 * Native structures (stronghold/mineshaft/monument) are treated as procedural
 * roots; generated Jigsaw structures are handled by the generated-data path.
 */
export class NativeStructurePlacement {
  constructor(registry, hostResolver, candidateEvaluator = null) {
    this.registry = registry;
    this.hostResolver = hostResolver;
    this.candidateEvaluator = candidateEvaluator;
  }

  planStronghold(request) {
    return this.#plan(request, StructureCategory.STRONGHOLD, {
      minRadius: Math.max(request.minRadius ?? 120, (request.footprintRadius ?? 0) + 48),
      maxRadius: Math.max(request.maxRadius ?? 176, (request.minRadius ?? 120) + 56),
      verticalMin: -96,
      verticalMax: 96,
      verticalStep: 4,
      injectHost: true,
      strategy: "native_stronghold_primary_host_island"
    });
  }

  planMineshaft(request) {
    return this.#plan(request, StructureCategory.UNDERGROUND, {
      anchorMode: "first_piece_center",
      sampleRadius: 6,
      sampleStep: 3,
      strategy: "native_mineshaft_dynamic_anchor"
    });
  }

  planJigsawUnderground(request) {
    return this.#plan(request, StructureCategory.UNDERGROUND, {
      anchorMode: "jigsaw_anchor",
      sampleRadius: 6,
      sampleStep: 3,
      strategy: "native_jigsaw_dynamic_anchor"
    });
  }

  #plan(request, category, policy) {
    const anchor = request.anchor ?? { x: request.x ?? 0, y: request.y ?? 0, z: request.z ?? 0, source: "fallback" };
    const preferred = request.islandAwareTarget ?? anchor;
    const host = this.hostResolver?.selectNativeHost?.({
      category, x: preferred.x, z: preferred.z, footprint: request.footprint, requiredRadius: policy.minRadius
    }) ?? null;
    if (!host) return { accepted: false, category, strategy: policy.strategy, reason: "no_valid_host_island" };

    const candidate = this.#searchCandidates(anchor, preferred, host, request, policy, category);
    if (!candidate) return { accepted: false, category, strategy: policy.strategy, reason: "no_valid_anchor_fit", host };

    return {
      accepted: true,
      category,
      strategy: policy.strategy,
      host,
      anchor,
      target: candidate,
      relocation: { dx: candidate.x - anchor.x, dz: candidate.z - anchor.z, dy: candidate.y - anchor.y }
    };
  }

  #searchCandidates(anchor, preferred, host, request, policy, category) {
    const offsets = [];
    const radius = request.searchRadius ?? 16;
    for (let r = 0; r <= radius; r += 4) {
      for (let dx = -r; dx <= r; dx += 4) for (let dz = -r; dz <= r; dz += 4) {
        if (Math.abs(dx) !== r && Math.abs(dz) !== r) continue;
        offsets.push({ dx, dz });
      }
    }
    const ys = [];
    if (policy.verticalMin != null) for (let y = policy.verticalMin; y <= policy.verticalMax; y += policy.verticalStep) ys.push(anchor.y + y);
    else ys.push(anchor.y);

    let best = null;
    for (const o of offsets) for (const y of ys) {
      const x = preferred.x + o.dx, z = preferred.z + o.dz;
      if (!this.#insideHost(host, x, z, request.footprintRadius ?? policy.minRadius ?? 0)) continue;
      const evaluation = request.evaluateCandidate?.({ x, y, z, host, policy, category })
        ?? this.candidateEvaluator?.evaluate({ category, x, y, z, host, footprint: request.footprint, native: true });
      if (!evaluation?.valid) continue;
      const score = evaluation.score ?? this.#score(anchor, preferred, host, x, y, z, evaluation);
      if (!best || score > best.score) best = { x, y, z, score, evaluation };
      if (evaluation.earlyAccept) return { x, y, z, score, evaluation };
    }
    return best;
  }

  #insideHost(host, x, z, requiredRadius) {
    const usable = Math.max(0, (host.usableRadius ?? host.radius ?? 0) - (host.margin ?? 0));
    return usable >= requiredRadius && Math.hypot(x - host.x, z - host.z) + requiredRadius <= usable;
  }

  #score(anchor, preferred, host, x, y, z, evaluation) {
    const movement = Math.abs(x - anchor.x) + Math.abs(z - anchor.z) + Math.abs(y - anchor.y);
    const distance = Math.hypot(x - preferred.x, z - preferred.z);
    const interior = Math.max(0, (host.usableRadius ?? host.radius ?? 0) - Math.hypot(x - host.x, z - host.z));
    return (evaluation.supportRatio ?? 0) * 1000 + (evaluation.stoneRatio ?? 0) * 250 + interior - movement * 0.5 - distance * 0.1;
  }
}