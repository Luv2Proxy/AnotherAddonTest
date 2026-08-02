export function sampleTerrain(generator, x, z, options = {}) {
  const minY = Number(options.minY ?? -64), maxY = Number(options.maxY ?? 320);
  try {
    const column = generator?.column?.(Math.floor(x), Math.floor(z), minY, maxY);
    if (Array.isArray(column) && column.length) {
      let top = -Infinity, bottom = Infinity;
      for (const segment of column) {
        if (Array.isArray(segment)) {
          if (Number.isFinite(segment[0])) bottom = Math.min(bottom, Number(segment[0]));
          if (Number.isFinite(segment[1])) top = Math.max(top, Number(segment[1]));
        } else if (segment) {
          if (Number.isFinite(segment.bottom)) bottom = Math.min(bottom, Number(segment.bottom));
          if (Number.isFinite(segment.top)) top = Math.max(top, Number(segment.top));
        }
      }
      if (Number.isFinite(top)) return { top, bottom: Number.isFinite(bottom) ? bottom : minY };
    }
  } catch {}
  return { top: Number(options.fallbackY ?? 128), bottom: minY };
}

export function validateTerrain(generator, location, options = {}) {
  const radius = Math.max(1, Number(options.radius ?? 4));
  const samples = Math.max(4, Number(options.samples ?? 8));
  const heights = [];
  for (let i = 0; i < samples; i++) {
    const angle = i / samples * Math.PI * 2;
    const x = location.x + Math.cos(angle) * radius;
    const z = location.z + Math.sin(angle) * radius;
    heights.push(sampleTerrain(generator, x, z, options).top);
  }
  const min = Math.min(...heights), max = Math.max(...heights);
  const slope = max - min;
  return { valid: slope <= Number(options.maxSlope ?? 8), slope, minY: min, maxY: max, centerY: Math.round((min + max) / 2) };
}

export function validateWater(generator, location, options = {}) {
  const terrain = sampleTerrain(generator, location.x, location.z, options);
  const waterY = Number(options.waterY ?? 62);
  const tolerance = Number(options.tolerance ?? 8);
  return { valid: Math.abs(terrain.top - waterY) <= tolerance, terrainY: terrain.top, waterY, delta: terrain.top - waterY };
}

export function validateUnderground(generator, location, options = {}) {
  const terrain = sampleTerrain(generator, location.x, location.z, options);
  const depth = Number(options.depth ?? 24);
  const targetY = terrain.top - depth;
  return { valid: targetY >= Number(options.minY ?? -32), terrainY: terrain.top, targetY, depth };
}
