import { getGeneratedJigsawData, generatedTemplateId } from "./JigsawDataLoader.js";

export class JigsawRegistry {
  constructor(data = getGeneratedJigsawData()) {
    this.pieces = new Map();
    this.pools = new Map();
    this.structures = new Map();
    this.processors = new Map();
    this.structureSets = new Map();
    this.resolvedTemplates = new Map();
    this.templateAliases = new Map();
    this.connectorIndex = new Map();
    this.load(data);
  }

  clear() {
    this.pieces.clear();
    this.pools.clear();
    this.structures.clear();
    this.processors.clear();
    this.structureSets.clear();
    this.resolvedTemplates.clear();
    this.templateAliases.clear();
    this.connectorIndex.clear();
    return this;
  }

  load(data = getGeneratedJigsawData()) {
    this.clear();
    if (!data || typeof data !== "object") return this;

    const pieces = data.pieces ?? {};
    if (Array.isArray(pieces)) {
      for (const piece of pieces) if (piece?.id) this.pieces.set(generatedTemplateId(piece.id), piece);
    } else {
      for (const [id, piece] of Object.entries(pieces)) this.pieces.set(generatedTemplateId(id), piece);
    }

    for (const [id, entry] of Object.entries(data.template_pools ?? data.pools ?? {})) this.pools.set(generatedTemplateId(id), entry?.definition ?? entry);

    const structures = data.structures ?? data.jigsaw_structures ?? {};
    for (const [id, entry] of Object.entries(structures)) this.structures.set(generatedTemplateId(id), entry?.definition ?? entry);

    for (const [id, entry] of Object.entries(data.processors ?? {})) this.processors.set(generatedTemplateId(id), entry?.definition ?? entry);
    for (const [id, entry] of Object.entries(data.structure_sets ?? {})) this.structureSets.set(generatedTemplateId(id), entry?.definition ?? entry);
    for (const [id, path] of Object.entries(data.resolved_templates ?? {})) this.resolvedTemplates.set(generatedTemplateId(id), path);
    for (const [id, alias] of Object.entries(data.template_aliases ?? {})) this.templateAliases.set(generatedTemplateId(id), alias);

    const connectors = data.connectors ?? data.jigsaw_connectors ?? {};
    for (const [name, entries] of Object.entries(connectors)) this.connectorIndex.set(name, Array.isArray(entries) ? entries : []);
    return this;
  }

  piece(id) { return this.pieces.get(generatedTemplateId(id)) ?? null; }
  pool(id) { return this.pools.get(generatedTemplateId(id)) ?? null; }
  structure(id) { return this.structures.get(generatedTemplateId(id)) ?? null; }
  processor(id) { return this.processors.get(generatedTemplateId(id)) ?? null; }
  structureSet(id) { return this.structureSets.get(generatedTemplateId(id)) ?? null; }
  connectors(id) { return this.piece(id)?.connectors ?? this.piece(id)?.jigsaws ?? []; }
  connectorsByName(name) { return this.connectorIndex.get(name) ?? []; }
  poolElements(id) { return this.pool(id)?.elements ?? []; }
  resolvedTemplate(id) { return this.resolvedTemplates.get(generatedTemplateId(id)) ?? null; }
  alias(id) { return this.templateAliases.get(generatedTemplateId(id)) ?? null; }
  fallback(id) { return this.pool(id)?.fallback ?? null; }

  candidates(poolId, targetName = null) {
    const pool = this.pool(poolId);
    if (!pool) return [];
    const result = [];
    for (const wrapper of pool.elements ?? []) {
      const element = wrapper?.element ?? wrapper;
      if (!element || typeof element !== "object") continue;
      const type = element.element_type;
      if (type === "minecraft:empty_pool_element" || type === "minecraft:list_pool_element") {
        result.push({ weight: Number(wrapper?.weight ?? 1), element, targetName });
      } else if ((type === "minecraft:single_pool_element" || type === "minecraft:legacy_single_pool_element") && element.location) {
        result.push({
          weight: Number(wrapper?.weight ?? 1),
          element,
          targetName,
          piece: this.piece(element.location),
          resolvedPath: this.resolvedTemplate(element.location)
        });
      }
    }
    return result;
  }

  weightedCandidates(poolId, random = Math.random(), targetName = null) {
    const candidates = this.candidates(poolId, targetName);
    if (!candidates.length) return null;
    const total = candidates.reduce((sum, item) => sum + Math.max(0, item.weight), 0);
    if (total <= 0) return candidates[0];
    let cursor = Math.max(0, Math.min(0.999999999, Number(random))) * total;
    for (const candidate of candidates) {
      cursor -= Math.max(0, candidate.weight);
      if (cursor < 0) return candidate;
    }
    return candidates[candidates.length - 1];
  }

  findPieceByLocation(location) {
    const key = generatedTemplateId(location);
    if (this.pieces.has(key)) return this.pieces.get(key);
    for (const [id, path] of this.resolvedTemplates) {
      if (path === location || path?.replace(/\\/g, "/").replace(/\\.mcstructure$/i, "") === location) return this.pieces.get(id) ?? null;
    }
    return null;
  }

  validatePoolGraph(poolId, maxDepth = 20) {
    const errors = [], visited = new Set();
    const visit = (id, depth) => {
      if (!id || depth > maxDepth) return;
      const key = generatedTemplateId(id);
      if (visited.has(key)) return;
      visited.add(key);
      const pool = this.pool(key);
      if (!pool) { errors.push(`Missing pool: ${key}`); return; }
      if (pool.fallback) visit(pool.fallback, depth + 1);
      for (const candidate of this.candidates(key)) {
        const location = candidate.element?.location;
        if (location && !this.piece(location) && !this.resolvedTemplate(location)) errors.push(`${key}: missing piece ${location}`);
      }
    };
    visit(poolId, 0);
    return { valid: errors.length === 0, errors, pools: [...visited] };
  }

  snapshot() {
    return {
      pieces: this.pieces.size,
      pools: this.pools.size,
      structures: this.structures.size,
      processors: this.processors.size,
      structureSets: this.structureSets.size,
      connectorNames: this.connectorIndex.size,
      resolvedTemplates: this.resolvedTemplates.size,
      aliases: this.templateAliases.size
    };
  }
}

export function structureLocationToId(location) {
  return generatedTemplateId(location);
}
