/**
 * Relocation abstraction matching the original mod's StructureStartRelocator.
 * It operates on a generated piece list/bounding boxes supplied by a native
 * adapter. This is deliberately independent from .mcstructure placement.
 */
export class StructureStartRelocator {
  relocate(start, target) {
    if (!start || !target) return null;
    const anchor = start.anchor ?? this.center(start.bounds);
    const delta = { x: Math.floor(target.x - anchor.x), y: Math.floor(target.y - anchor.y), z: Math.floor(target.z - anchor.z) };
    const pieces = (start.pieces ?? []).map(piece => ({
      ...piece,
      boundingBox: piece.boundingBox ? this.translateBox(piece.boundingBox, delta) : piece.boundingBox,
      position: piece.position ? { x: piece.position.x + delta.x, y: piece.position.y + delta.y, z: piece.position.z + delta.z } : piece.position
    }));
    return { ...start, anchor: { ...target }, bounds: start.bounds ? this.translateBox(start.bounds, delta) : start.bounds, pieces, relocation: delta };
  }

  translateBox(box, d) {
    return {
      min: { x: box.min.x + d.x, y: box.min.y + d.y, z: box.min.z + d.z },
      max: { x: box.max.x + d.x, y: box.max.y + d.y, z: box.max.z + d.z }
    };
  }

  center(box) {
    return { x: Math.floor((box.min.x + box.max.x) / 2), y: Math.floor((box.min.y + box.max.y) / 2), z: Math.floor((box.min.z + box.max.z) / 2) };
  }

  validate(start, evaluator) {
    if (!start?.pieces?.length) return { valid: false, reason: "empty_structure_start" };
    for (const piece of start.pieces) {
      const result = evaluator?.(piece);
      if (result && !result.valid) return { valid: false, reason: result.reason ?? "piece_validation_failed", piece };
    }
    return { valid: true };
  }
}
