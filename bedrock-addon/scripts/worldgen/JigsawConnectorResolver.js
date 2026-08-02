function normalize(value) {
  return String(value ?? "").replace(/^minecraft:/, "").toLowerCase();
}

function rotateXZ(x, z, rotation) {
  switch (String(rotation ?? "none").toLowerCase()) {
    case "clockwise_90": case "90": return { x: -z, z: x };
    case "clockwise_180": case "180": return { x: -x, z: -z };
    case "counterclockwise_90": case "270": return { x: z, z: -x };
    default: return { x, z };
  }
}

export class JigsawConnectorResolver {
  constructor(registry, options = {}) {
    this.registry = registry;
    this.maxDistance = Number(options.maxDistance ?? 48);
  }

  connectors(pieceOrId) {
    const piece = typeof pieceOrId === "string" ? this.registry?.piece?.(pieceOrId) : pieceOrId;
    return piece?.connectors ?? piece?.jigsaws ?? piece?.jigsawBlocks ?? [];
  }

  resolveTargets(connector, poolId, targetName, options = {}) {
    const pool = this.registry?.pool?.(poolId);
    const wanted = normalize(targetName);
    const entries = pool?.elements ?? [];
    return entries.flatMap((wrapper) => {
      const element = wrapper?.element ?? wrapper;
      const id = element?.location ?? element?.name ?? element?.id;
      if (!id) return [];
      const piece = this.registry?.piece?.(id);
      const connectors = this.connectors(piece);
      const compatible = connectors.filter((c) => {
        const name = normalize(c?.name ?? c?.target_name ?? c?.targetName);
        return !wanted || name === wanted;
      });
      return compatible.length ? [{ id, piece, element, weight: Number(wrapper?.weight ?? 1), connectors: compatible }] : [];
    });
  }

  transformConnector(connector, rotation = "none") {
    const pos = connector?.pos ?? connector?.position ?? { x: 0, y: 0, z: 0 };
    const rotated = rotateXZ(Number(pos.x ?? 0), Number(pos.z ?? 0), rotation);
    return { ...connector, pos: { x: rotated.x, y: Number(pos.y ?? 0), z: rotated.z }, rotation };
  }

  attachPosition(parentConnector, childConnector, rotation = "none") {
    const parent = this.transformConnector(parentConnector, "none").pos;
    const child = this.transformConnector(childConnector, rotation).pos;
    return { x: Math.floor(parent.x - child.x), y: Math.floor(parent.y - child.y), z: Math.floor(parent.z - child.z) };
  }
}
