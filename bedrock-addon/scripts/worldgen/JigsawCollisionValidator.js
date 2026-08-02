export class JigsawCollisionValidator {
  constructor(options = {}) {
    this.padding = Number(options.padding ?? 0);
  }

  bounds(piece, location, rotation = "none") {
    const size = piece?.size ?? piece?.dimensions ?? { x: 1, y: 1, z: 1 };
    let x = Number(size.x ?? 1), z = Number(size.z ?? 1);
    if (["clockwise_90", "counterclockwise_90", "90", "270"].includes(String(rotation).toLowerCase())) [x, z] = [z, x];
    return {
      minX: location.x - this.padding,
      minY: location.y - this.padding,
      minZ: location.z - this.padding,
      maxX: location.x + x + this.padding,
      maxY: location.y + Number(size.y ?? 1) + this.padding,
      maxZ: location.z + z + this.padding
    };
  }

  intersects(a, b) {
    return a.minX < b.maxX && a.maxX > b.minX && a.minY < b.maxY && a.maxY > b.minY && a.minZ < b.maxZ && a.maxZ > b.minZ;
  }

  canPlace(piece, location, rotation, existing) {
    const bounds = this.bounds(piece, location, rotation);
    for (const item of existing) {
      if (this.intersects(bounds, item.bounds)) return { valid: false, reason: "jigsaw_overlap", bounds, collision: item };
    }
    return { valid: true, bounds };
  }
}
