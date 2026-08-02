import { BlockPermutation } from "@minecraft/server";

const AIR = "minecraft:air";
const WATER = new Set(["minecraft:water", "minecraft:flowing_water"]);
const DEFAULT_SOLID = ["minecraft:stone", "minecraft:dirt"];

function p3(p) { return { x: Math.floor(Number(p?.x ?? 0)), y: Math.floor(Number(p?.y ?? 0)), z: Math.floor(Number(p?.z ?? 0)) }; }
function id(block) { try { return block?.typeId ?? null; } catch { return null; } }
function blockAt(d,p) { try { return d?.getBlock(p) ?? null; } catch { return null; } }
function perm(idValue, states) { try { return typeof idValue === "string" ? BlockPermutation.resolve(idValue, states) : idValue; } catch { return null; } }
function air(b) { return !b || id(b) === AIR; }
function water(b) { return WATER.has(id(b)); }
function replaceable(b) { return air(b) || water(b); }

/*
 * Vanilla-inspired Beardifier / terrain adaptation.
 *
 * Minecraft does not simply fill a rectangular foundation. Its structure
 * beardifier samples a density field around the complete generated structure
 * and blends the structure's target terrain height into the existing terrain.
 * The exact Java implementation is internal, so this is a deterministic
 * Bedrock-side reconstruction of the same model:
 *
 *   - collect all generated piece bounding boxes
 *   - expand the union by the adaptation radius
 *   - compute a radial/vertical falloff (beard kernel)
 *   - blend target structure ground with sampled terrain
 *   - use a stronger kernel for beard_box
 *   - use a thin kernel for beard_thin
 *   - bury uses the structure's actual lower bounds
 *   - only replace air/water or terrain blocks selected by the density result
 *
 * This avoids the old column-fill approximation and produces smooth sloped
 * transitions around structures.
 */

export class TerrainAdaptationEngine {
  constructor(dimension, options = {}) {
    this.dimension = dimension;
    this.options = options;
    this.changes = [];
  }

  reset() { this.changes.length = 0; return this; }

  setBlock(position, blockId, states = {}) {
    const pos = p3(position), permutation = perm(blockId, states);
    if (!permutation) return false;
    try {
      this.dimension.setBlockPermutation(pos, permutation);
      this.changes.push({ position: pos, id: typeof blockId === "string" ? blockId : null });
      return true;
    } catch { return false; }
  }

  surfaceY(x, z) {
    const minY = Number(this.options.minY ?? -64), maxY = Number(this.options.maxY ?? 320);
    for (let y = maxY; y >= minY; y--) {
      const b = blockAt(this.dimension, { x, y, z });
      if (b && !air(b) && !water(b)) return y;
    }
    return minY;
  }

  boxes(candidate, origin) {
    const list = candidate?.pieceBounds ?? candidate?.boundingBoxes ?? candidate?.pieces?.map(p => p.boundingBox ?? p.bounds).filter(Boolean) ?? [];
    if (list.length) return list.map(b => ({ minX: Number(b.minX ?? b.x ?? origin.x), minY: Number(b.minY ?? b.y ?? origin.y), minZ: Number(b.minZ ?? b.z ?? origin.z), maxX: Number(b.maxX ?? (b.x ?? origin.x) + (b.sizeX ?? b.width ?? 1) - 1), maxY: Number(b.maxY ?? (b.y ?? origin.y) + (b.sizeY ?? b.height ?? 1) - 1), maxZ: Number(b.maxZ ?? (b.z ?? origin.z) + (b.sizeZ ?? b.depth ?? 1) - 1) }));
    const size = candidate?.size ?? candidate?.transformedSize ?? { x: 1, y: 1, z: 1 };
    const hx = Math.max(1, Math.ceil(Number(candidate?.footprint?.x ?? candidate?.footprintRadius ?? size.x / 2)));
    const hz = Math.max(1, Math.ceil(Number(candidate?.footprint?.z ?? candidate?.footprintRadius ?? size.z / 2)));
    return [{ minX: origin.x - hx, minY: origin.y, minZ: origin.z - hz, maxX: origin.x + hx, maxY: origin.y + Number(size.y ?? 1) - 1, maxZ: origin.z + hz }];
  }

  union(boxes) {
    return boxes.reduce((a,b) => ({ minX:Math.min(a.minX,b.minX), minY:Math.min(a.minY,b.minY), minZ:Math.min(a.minZ,b.minZ), maxX:Math.max(a.maxX,b.maxX), maxY:Math.max(a.maxY,b.maxY), maxZ:Math.max(a.maxZ,b.maxZ) }), boxes[0]);
  }

  kernel(dx, dy, dz, mode) {
    const horizontal = Math.sqrt(dx * dx + dz * dz);
    const radius = mode === "beard_box" ? 8 : 4;
    const vertical = mode === "beard_box" ? 8 : 4;
    if (horizontal > radius || dy < -vertical || dy > 2) return 0;
    const h = Math.max(0, 1 - horizontal / radius);
    const v = Math.max(0, 1 - Math.abs(dy) / vertical);
    // Smoothstep is close to the smooth density falloff used by vanilla's
    // beardifier rather than the hard rectangular fill used previously.
    const smooth = t => t * t * (3 - 2 * t);
    return smooth(h) * smooth(v);
  }

  adapt(candidate, context = {}) {
    const mode = String(context.mode ?? candidate?.terrain_adaptation ?? candidate?.terrainAdaptation?.mode ?? "none").toLowerCase();
    const origin = p3(context.location ?? candidate?.location ?? candidate ?? {});
    const boxes = this.boxes(candidate, origin);
    const bounds = this.union(boxes);
    const targetY = Number(context.targetY ?? candidate?.targetY ?? bounds.minY);
    const foundation = context.foundationBlock ?? candidate?.foundationBlock ?? "minecraft:dirt";
    const surfaceBlocks = context.surfaceBlocks ?? candidate?.surfaceBlocks ?? DEFAULT_SOLID;
    const radius = mode === "beard_box" ? 8 : mode === "beard_thin" ? 4 : 0;
    const result = { mode, targetY, bounds, changed: 0, columns: 0, operations: [], densitySamples: 0 };
    if (mode === "none") return result;

    if (mode === "bury") {
      const depth = Number(context.depth ?? candidate?.buryDepth ?? 8);
      for (let x = bounds.minX - 1; x <= bounds.maxX + 1; x++) for (let z = bounds.minZ - 1; z <= bounds.maxZ + 1; z++) {
        const surface = this.surfaceY(x,z), bottom = Math.max(-64, bounds.maxY - depth);
        for (let y = bottom; y < Math.min(surface, bounds.minY); y++) {
          const b = blockAt(this.dimension,{x,y,z});
          if (replaceable(b)) { if (this.setBlock({x,y,z}, foundation)) result.changed++; }
        }
        result.columns++;
      }
      result.operations.push("bury");
      return result;
    }

    if (radius > 0) {
      for (let x = bounds.minX - radius; x <= bounds.maxX + radius; x++) for (let z = bounds.minZ - radius; z <= bounds.maxZ + radius; z++) {
        const surface = this.surfaceY(x,z);
        for (let y = Math.max(-64, Math.min(surface, targetY + 2) - radius); y <= Math.min(320, targetY + 2); y++) {
          const nearestX = Math.max(bounds.minX, Math.min(x, bounds.maxX));
          const nearestY = Math.max(bounds.minY, Math.min(y, bounds.maxY));
          const nearestZ = Math.max(bounds.minZ, Math.min(z, bounds.maxZ));
          const density = this.kernel(x-nearestX, y-nearestY, z-nearestZ, mode);
          if (density <= 0) continue;
          result.densitySamples++;
          const desired = targetY + Math.round((1 - density) * (surface - targetY));
          if (y < desired) {
            const b = blockAt(this.dimension,{x,y,z});
            if (replaceable(b) && this.setBlock({x,y,z}, foundation)) result.changed++;
          } else if (y > desired && y <= surface && density > 0.72) {
            const b = blockAt(this.dimension,{x,y,z});
            if (b && !air(b) && !water(b)) {
              const top = perm(AIR);
              try { this.dimension.setBlockPermutation({x,y,z}, top); result.changed++; } catch {}
            }
          }
        }
        result.columns++;
      }
      result.operations.push(mode);
    }

    if (mode === "encapsulate") {
      const top = bounds.maxY + 1, bottom = bounds.minY - 1;
      for (let x=bounds.minX;x<=bounds.maxX;x++) for(let z=bounds.minZ;z<=bounds.maxZ;z++) {
        const below=blockAt(this.dimension,{x,bottom,z}); if(replaceable(below)) this.setBlock({x,bottom,z},foundation);
        const above=blockAt(this.dimension,{x,top,z}); if(air(above)) this.setBlock({x,top,z},surfaceBlocks[0] ?? DEFAULT_SOLID[0]);
      }
      result.operations.push("encapsulate");
    }
    return result;
  }

  flatten(candidate, context = {}) {
    const origin = p3(context.location ?? candidate?.location ?? candidate ?? {}), boxes = this.boxes(candidate,origin), bounds=this.union(boxes), targetY=Number(context.targetY ?? bounds.minY), foundation=context.foundationBlock ?? "minecraft:dirt", airPerm=perm(AIR);
    let changed=0;
    for(let x=bounds.minX;x<=bounds.maxX;x++) for(let z=bounds.minZ;z<=bounds.maxZ;z++) {
      const surface=this.surfaceY(x,z);
      if(surface<targetY) for(let y=surface;y<targetY;y++) if(this.setBlock({x,y,z},foundation))changed++;
      else if(surface>targetY && airPerm) for(let y=targetY;y<surface;y++){const b=blockAt(this.dimension,{x,y,z});if(b&&!air(b)&&!water(b))try{this.dimension.setBlockPermutation({x,y,z},airPerm);changed++;}catch{}}
    }
    return {mode:"flatten",targetY,changed,bounds};
  }

  waterline(candidate, context = {}) {
    const origin=p3(context.location ?? candidate?.location ?? candidate ?? {}), boxes=this.boxes(candidate,origin), bounds=this.union(boxes), waterLevel=Number(context.waterLevel ?? 63), floorY=Number(context.seaFloorY ?? waterLevel-1), wp=perm("minecraft:water");
    let changed=0; if(!wp)return {mode:"waterline",changed:0};
    for(let x=bounds.minX;x<=bounds.maxX;x++)for(let z=bounds.minZ;z<=bounds.maxZ;z++)for(let y=floorY+1;y<=waterLevel;y++){const b=blockAt(this.dimension,{x,y,z});if(air(b))try{this.dimension.setBlockPermutation({x,y,z},wp);changed++;}catch{}}
    return {mode:"waterline",waterLevel,floorY,changed,bounds};
  }
}

export function applyTerrainAdaptation(dimension,candidate,context={}) {
  if(!dimension)return {mode:context.mode??"none",changed:0,skipped:"no_dimension"};
  const engine=new TerrainAdaptationEngine(dimension,context.options??{}), adaptation=engine.adapt(candidate,context);
  if(context.flatten || adaptation.mode==="beard_thin" || adaptation.mode==="beard_box") adaptation.flatten=engine.flatten(candidate,{...context,targetY:adaptation.targetY});
  if(context.waterline) adaptation.waterline=engine.waterline(candidate,context);
  return adaptation;
}
