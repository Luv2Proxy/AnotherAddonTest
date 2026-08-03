function cloneState(state) {
  if (state == null || typeof state !== "object") return state;
  if (Array.isArray(state)) return state.map(cloneState);
  return Object.fromEntries(Object.entries(state).map(([k, v]) => [k, cloneState(v)]));
}

function hash01(seed, key) {
  let h = 2166136261 >>> 0;
  const s = `${seed}:${key}`;
  for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); }
  return (h >>> 0) / 4294967296;
}

function idOf(value) { return String(value ?? "").replace(/^minecraft:/, "").toLowerCase(); }
function stateOf(block) { return block?.state ?? block?.states ?? block?.Properties ?? {}; }
function blockName(block) { return block?.name ?? block?.block ?? block?.id ?? null; }

export class ProcessorPipeline {
  constructor(registry, options = {}) { this.registry = registry; this.blockResolver = options.blockResolver ?? null; }

  list(id) {
    const list = this.registry?.processorList?.(id) ?? this.registry?.processorLists?.get?.(id);
    const raw = list?.processors ?? list?.entries ?? list ?? [];
    return Array.isArray(raw) ? raw : raw?.processors ?? [];
  }

  apply(block, processorId, context = {}) {
    let current = { ...block, state: cloneState(block?.state ?? block?.states), nbt: cloneState(block?.nbt) };
    const processors = this.list(processorId);
    for (let i = 0; i < processors.length; i++) {
      current = this.applyOne(current, processors[i], { ...context, processorId, index: i });
      if (current == null) return null;
    }
    return current;
  }

  applyOne(block, processor, context = {}) {
    const type = idOf(processor?.processor_type ?? processor?.type ?? processor?.type_id);
    if (!type) return block;

    if (type.includes("list")) {
      let current = block;
      for (const nested of processor.processors ?? processor.list ?? processor.elements ?? []) {
        current = this.applyOne(current, nested, context);
        if (current == null) return null;
      }
      return current;
    }

    if (type.includes("block_ignore")) {
      const ignored = new Set((processor.blocks ?? processor.block_names ?? processor.targets ?? []).map(idOf));
      return ignored.has(idOf(blockName(block))) ? null : block;
    }

    if (type.includes("protected")) {
      // Protected processors are a placement veto. We retain the block and
      // mark it protected so downstream processors cannot replace it.
      return { ...block, __protected: true };
    }

    if (block.__protected && !type.includes("block_rot")) return block;

    if (type.includes("block_rot")) {
      const rotation = context.rotation ?? "none";
      if (!block.state || rotation === "none") return block;
      return { ...block, state: this.rotateState(block.state, rotation) };
    }

    if (type.includes("gravity")) {
      return { ...block, gravity: { ...(block.gravity ?? {}), offset: Number(processor.offset ?? 0), required: true } };
    }

    if (type.includes("random_block_match") || type.includes("random_block_state_match")) {
      const input = processor.input_block ?? processor.block ?? processor.input_block_state ?? processor.target;
      if (!this.matchesBlock(block, input)) return block;
      const chance = Number(processor.probability ?? processor.chance ?? 1);
      if (hash01(context.seed ?? 0, `${context.index}:${context.position?.x ?? 0}:${context.position?.y ?? 0}:${context.position?.z ?? 0}`) > chance) return block;
      return this.replaceBlock(block, processor.output_state ?? processor.output_block ?? processor.output);
    }

    if (type.includes("rule")) {
      const rules = processor.rules ?? [];
      for (const rule of rules) {
        if (!this.ruleMatches(block, rule, context)) continue;
        const output = rule.output_state ?? rule.output_block ?? rule.output;
        if (output) return this.replaceBlock(block, output);
      }
      return block;
    }

    if (type.includes("block_age")) {
      const chance = Number(processor.chance ?? processor.probability ?? 1);
      if (hash01(context.seed ?? 0, `${context.index}:age`) <= chance) return this.ageBlock(block, processor);
      return block;
    }

    if (type.includes("mossify")) {
      const chance = Number(processor.mossiness ?? processor.chance ?? processor.probability ?? 1);
      if (hash01(context.seed ?? 0, `${context.index}:moss`) <= chance) return this.mossify(block, processor);
      return block;
    }

    if (type.includes("integrity")) {
      const chance = Number(processor.integrity ?? processor.chance ?? 1);
      return hash01(context.seed ?? 0, `${context.index}:integrity`) <= chance ? block : null;
    }

    if (type.includes("append_loot") || type.includes("loot")) {
      const lootTable = processor.loot_table ?? processor.lootTable ?? processor.table;
      return lootTable ? { ...block, lootTable: lootTable } : block;
    }

    return block;
  }

  ruleMatches(block, rule, context = {}) {
    if (!rule || typeof rule !== "object") return true;
    const input = rule.input_block ?? rule.input_block_state ?? rule.input ?? rule.match_block;
    if (input && !this.matchesBlock(block, input)) return false;
    const tag = rule.input_tag ?? rule.tag ?? rule.match_tag;
    if (tag && !this.matchesTag(block, tag)) return false;
    const state = rule.input_state ?? rule.state ?? rule.match_state;
    if (state && !this.matchesStates(stateOf(block), state)) return false;
    const chance = Number(rule.chance ?? rule.probability ?? 1);
    if (chance < 1 && hash01(context.seed ?? 0, `${context.index}:${context.position?.x ?? 0}:${context.position?.y ?? 0}:${context.position?.z ?? 0}`) > chance) return false;
    return true;
  }

  matchesBlock(block, expected) {
    if (Array.isArray(expected)) return expected.some(x => this.matchesBlock(block, x));
    if (typeof expected === "string") return idOf(blockName(block)) === idOf(expected);
    if (!expected || typeof expected !== "object") return false;
    const expectedId = expected.name ?? expected.block ?? expected.id;
    if (expectedId && idOf(blockName(block)) !== idOf(expectedId)) return false;
    const expectedState = expected.state ?? expected.states ?? expected.Properties;
    return !expectedState || this.matchesStates(stateOf(block), expectedState);
  }

  matchesStates(actual, expected) {
    if (!expected || typeof expected !== "object") return true;
    const a = actual ?? {};
    return Object.entries(expected).every(([key, value]) => {
      if (Array.isArray(value)) return value.some(v => String(a[key]) === String(v));
      return String(a[key]) === String(value);
    });
  }

  matchesTag(block, tag) {
    const tags = block?.tags ?? block?.blockTags ?? [];
    return Array.isArray(tags) ? tags.includes(tag) : Boolean(tags?.[tag]);
  }

  replaceBlock(block, output) {
    if (!output) return block;
    if (typeof output === "string") return { ...block, name: output, block: output };
    if (output?.Name) return { ...block, name: output.Name, block: output.Name, state: cloneState(output.Properties ?? stateOf(block)) };
    return { ...block, ...output, state: cloneState(output.state ?? output.states ?? stateOf(block)) };
  }

  ageBlock(block, processor) {
    const target = processor?.block_age ?? processor?.output ?? processor?.replacement;
    return target ? this.replaceBlock(block, target) : block;
  }

  mossify(block, processor) {
    const mapping = processor?.mapping ?? processor?.replacements ?? {};
    const name = blockName(block);
    const replacement = mapping[name] ?? mapping[idOf(name)];
    if (replacement) return this.replaceBlock(block, replacement);
    const normalized = idOf(name);
    if (normalized.includes("stone_bricks")) return this.replaceBlock(block, `minecraft:mossy_${normalized}`);
    if (normalized.includes("cobblestone")) return this.replaceBlock(block, "minecraft:mossy_cobblestone");
    return block;
  }

  rotateState(state, rotation) {
    const out = { ...state }, r = String(rotation).toLowerCase();
    for (const key of ["facing", "horizontal_facing", "direction"]) {
      if (!(key in out)) continue;
      const dirs = ["north", "east", "south", "west"], value = idOf(out[key]), index = dirs.indexOf(value);
      if (index < 0) continue;
      const shift = r.includes("180") ? 2 : r.includes("90") && !r.includes("counter") ? 1 : r.includes("counter") ? 3 : r.includes("270") ? 3 : 0;
      out[key] = dirs[(index + shift) % 4];
    }
    if ("axis" in out && (r.includes("90") || r.includes("270"))) out.axis = out.axis === "x" ? "z" : out.axis === "z" ? "x" : out.axis;
    return out;
  }
}
