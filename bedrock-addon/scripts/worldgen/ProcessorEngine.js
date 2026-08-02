import { getGeneratedJigsawData } from "./JigsawDataLoader.js";

const AIR = "minecraft:air";

function idOf(value) {
  if (typeof value === "string") return value.includes(":") ? value : `minecraft:${value}`;
  if (!value || typeof value !== "object") return null;
  return idOf(value.name ?? value.block ?? value.id ?? value.block_id);
}

function stateOf(value) {
  if (!value || typeof value !== "object") return {};
  return value.states ?? value.state ?? {};
}

function hashSeed(seed, text = "") {
  let h = (Number(seed) >>> 0) ^ 0x9e3779b9;
  for (let i = 0; i < String(text).length; i++) h = Math.imul(h ^ String(text).charCodeAt(i), 16777619) >>> 0;
  h ^= h >>> 16; h = Math.imul(h, 2246822507) >>> 0; h ^= h >>> 13; h = Math.imul(h, 3266489909) >>> 0; h ^= h >>> 16;
  return h >>> 0;
}

function random01(seed, index) {
  let x = hashSeed(seed, String(index));
  x = Math.imul(x ^ (x >>> 16), 0x45d9f3b) >>> 0;
  x = Math.imul(x ^ (x >>> 16), 0x45d9f3b) >>> 0;
  x ^= x >>> 16;
  return (x >>> 0) / 4294967296;
}

function statesMatch(actual, expected) {
  if (!expected || typeof expected !== "object") return true;
  const a = actual ?? {};
  return Object.entries(expected).every(([k, v]) => a[k] === v);
}

function cloneBlock(block) {
  return { id: idOf(block), states: { ...stateOf(block) }, ...block };
}

export class ProcessorEngine {
  constructor(data = getGeneratedJigsawData(), seed = 0) {
    this.data = data;
    this.seed = Number(seed) >>> 0;
  }

  processorList(id) {
    if (!id) return [];
    const key = String(id).includes(":") ? String(id) : `minecraft:${id}`;
    const raw = this.data.processors?.[key] ?? this.data.processors?.[String(id)];
    const definition = raw?.definition ?? raw;
    if (Array.isArray(definition)) return definition;
    return definition?.processors ?? definition?.list ?? [];
  }

  apply(block, context = {}) {
    let current = cloneBlock(block);
    const list = this.processorList(context.processorList ?? context.processors);
    if (!list.length) return { block: current, ignored: false, changed: false };

    for (let i = 0; i < list.length; i++) {
      const result = this.applyProcessor(current, list[i], { ...context, index: i });
      if (result.ignored) return result;
      current = result.block;
    }
    return { block: current, ignored: false, changed: true };
  }

  applyProcessor(block, processor, context = {}) {
    const type = String(processor?.processor_type ?? processor?.type ?? processor?.processor ?? "").toLowerCase();
    const rule = processor?.rule ?? processor;

    if (!type || type.includes("passthrough") || type.includes("always_true")) return { block, ignored: false, changed: false };

    if (type.includes("block_ignore")) {
      const ignored = this.matchesBlock(block, processor.blocks ?? processor.block ?? processor.targets ?? []);
      return { block, ignored, changed: false };
    }

    if (type.includes("protected")) {
      if (this.matchesBlock(block, processor.value ?? processor.blocks ?? processor.block)) return { block, ignored: false, protected: true, changed: false };
      return { block, ignored: false, changed: false };
    }

    if (type.includes("block_rule") || type.includes("block_match") || type.includes("tag_match") || type.includes("random_block_match")) {
      if (!this.ruleMatches(block, rule, context)) return { block, ignored: false, changed: false };
      const replacement = processor.output_state ?? processor.output_block ?? processor.replacement ?? processor.result ?? rule.output_state ?? rule.output_block;
      if (!replacement) return { block, ignored: false, changed: false };
      return { block: this.replace(block, replacement), ignored: false, changed: true };
    }

    if (type.includes("capped")) {
      const chance = Number(processor.probability ?? processor.chance ?? processor.cap ?? 1);
      if (random01(this.seed, `${context.index}:${context.position?.x ?? 0},${context.position?.y ?? 0},${context.position?.z ?? 0}`) > chance) return { block, ignored: false, changed: false };
      const nested = processor.delegate ?? processor.processor ?? processor.inner;
      return nested ? this.applyProcessor(block, nested, context) : { block, ignored: false, changed: false };
    }

    return { block, ignored: false, changed: false, unsupported: true };
  }

  ruleMatches(block, rule, context = {}) {
    if (!rule || typeof rule !== "object") return true;
    const input = rule.input_state ?? rule.input_block ?? rule.block ?? rule.match_block ?? rule.target;
    if (input && !this.matchesBlock(block, input)) return false;
    const tag = rule.tag ?? rule.input_tag ?? rule.match_tag;
    if (tag && !this.matchesTag(block, tag)) return false;
    const probability = Number(rule.probability ?? rule.chance ?? 1);
    if (probability < 1 && random01(this.seed, `${context.index}:${context.position?.x ?? 0},${context.position?.y ?? 0},${context.position?.z ?? 0}`) > probability) return false;
    return true;
  }

  matchesBlock(block, expected) {
    if (Array.isArray(expected)) return expected.some(x => this.matchesBlock(block, x));
    if (typeof expected === "string") return idOf(block) === idOf(expected);
    if (!expected || typeof expected !== "object") return false;
    const expectedId = idOf(expected);
    if (expectedId && idOf(block) !== expectedId) return false;
    return statesMatch(stateOf(block), stateOf(expected));
  }

  matchesTag(block, tag) {
    const tags = block?.tags ?? block?.blockTags ?? [];
    return Array.isArray(tags) ? tags.includes(tag) : Boolean(tags?.[tag]);
  }

  replace(block, replacement) {
    if (typeof replacement === "string") return { ...cloneBlock(block), id: idOf(replacement) };
    return { ...cloneBlock(block), ...replacement, id: idOf(replacement) ?? idOf(block), states: { ...stateOf(block), ...stateOf(replacement) } };
  }

  applyTemplateBlocks(blocks, context = {}) {
    const output = [];
    for (let i = 0; i < (blocks?.length ?? 0); i++) {
      const block = blocks[i];
      const result = this.apply(block, { ...context, position: block.pos ?? block.position, index: i });
      if (!result.ignored && result.block?.id && result.block.id !== AIR) output.push(result.block);
    }
    return output;
  }
}

export function createProcessorEngine(data, seed) {
  return new ProcessorEngine(data, seed);
}
