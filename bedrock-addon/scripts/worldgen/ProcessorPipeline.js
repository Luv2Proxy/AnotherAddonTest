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

function idOf(value) {
  return String(value ?? "").replace(/^minecraft:/, "").toLowerCase();
}

export class ProcessorPipeline {
  constructor(registry, options = {}) {
    this.registry = registry;
    this.blockResolver = options.blockResolver ?? null;
  }

  list(id) {
    const list = this.registry?.processorList?.(id) ?? this.registry?.processorLists?.get?.(id);
    return list?.processors ?? list?.entries ?? list ?? [];
  }

  apply(block, processorId, context = {}) {
    let current = { ...block, state: cloneState(block?.state), nbt: cloneState(block?.nbt) };
    const processors = this.list(processorId);
    for (let i = 0; i < processors.length; i++) {
      current = this.applyOne(current, processors[i], { ...context, processorId, index: i });
      if (current == null) return null;
    }
    return current;
  }

  applyOne(block, processor, context) {
    const type = idOf(processor?.processor_type ?? processor?.type ?? processor?.type_id);
    if (!type) return block;

    if (type.includes("block_ignore")) {
      const ignored = new Set((processor.blocks ?? processor.block_names ?? []).map(idOf));
      return ignored.has(idOf(block.name ?? block.block)) ? null : block;
    }

    if (type.includes("block_rot")) {
      const rotation = context.rotation ?? "none";
      if (!block.state || rotation === "none") return block;
      return { ...block, state: this.rotateState(block.state, rotation) };
    }

    if (type.includes("gravity")) {
      return { ...block, gravity: { ...(block.gravity ?? {}), offset: Number(processor.offset ?? 0), required: true } };
    }

    if (type.includes("protected")) {
      return block;
    }

    if (type.includes("rule")) {
      const rules = processor.rules ?? [];
      for (const rule of rules) {
        const input = idOf(rule.input_block ?? rule.input_block_state ?? rule.input);
        if (input && input !== idOf(block.name ?? block.block)) continue;
        const chance = Number(rule.chance ?? 1);
        if (hash01(context.seed ?? 0, `${context.position?.x ?? 0}:${context.position?.y ?? 0}:${context.position?.z ?? 0}`) > chance) continue;
        const output = rule.output_state ?? rule.output_block ?? rule.output;
        if (output) return this.replaceBlock(block, output);
      }
      return block;
    }

    if (type.includes("block_age")) {
      const chance = Number(processor.chance ?? processor.probability ?? 1);
      if (hash01(context.seed ?? 0, context.index ?? 0) <= chance) return this.ageBlock(block, processor);
      return block;
    }

    if (type.includes("mossify")) {
      const chance = Number(processor.mossiness ?? processor.chance ?? processor.probability ?? 1);
      if (hash01(context.seed ?? 0, context.index ?? 0) <= chance) return this.mossify(block, processor);
      return block;
    }

    if (type.includes("integrity")) {
      const chance = Number(processor.integrity ?? processor.chance ?? 1);
      return hash01(context.seed ?? 0, context.index ?? 0) <= chance ? block : null;
    }

    return block;
  }

  replaceBlock(block, output) {
    if (typeof output === "string") return { ...block, name: output, block: output };
    if (output?.Name) return { ...block, name: output.Name, block: output.Name, state: cloneState(output.Properties ?? block.state) };
    return { ...block, ...output };
  }

  ageBlock(block, processor) {
    const target = processor?.block_age ?? processor?.output ?? processor?.replacement;
    return target ? this.replaceBlock(block, target) : block;
  }

  mossify(block, processor) {
    const mapping = processor?.mapping ?? processor?.replacements ?? {};
    const replacement = mapping[block.name] ?? mapping[idOf(block.name)];
    if (replacement) return this.replaceBlock(block, replacement);
    const name = idOf(block.name);
    if (name.includes("stone_bricks")) return this.replaceBlock(block, `minecraft:mossy_${name}`);
    if (name.includes("cobblestone")) return this.replaceBlock(block, "minecraft:mossy_cobblestone");
    return block;
  }

  rotateState(state, rotation) {
    const out = { ...state };
    const r = String(rotation).toLowerCase();
    for (const key of ["facing", "horizontal_facing", "axis"]) {
      if (!(key in out)) continue;
      if (key === "axis") {
        if (r.includes("90")) out[key] = out[key] === "x" ? "z" : out[key] === "z" ? "x" : out[key];
        continue;
      }
      const dirs = ["north", "east", "south", "west"];
      const value = idOf(out[key]);
      const index = dirs.indexOf(value);
      if (index < 0) continue;
      const shift = r.includes("180") ? 2 : r.includes("90") && !r.includes("counter") ? 1 : r.includes("counter") ? 3 : 0;
      out[key] = dirs[(index + shift) % 4];
    }
    return out;
  }
}
