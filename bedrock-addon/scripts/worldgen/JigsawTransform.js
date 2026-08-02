const OPP = { down: 'up', up: 'down', north: 'south', south: 'north', west: 'east', east: 'west' };
const V = { down:[0,-1,0], up:[0,1,0], north:[0,0,-1], south:[0,0,1], west:[-1,0,0], east:[1,0,0] };
const F = Object.fromEntries(Object.entries(V).map(([k,v]) => [v.join(','), k]));

export function rotateY(p, q) {
  q = ((q % 4) + 4) % 4;
  if (q === 0) return { ...p };
  if (q === 1) return { x:-p.z, y:p.y, z:p.x };
  if (q === 2) return { x:-p.x, y:p.y, z:-p.z };
  return { x:p.z, y:p.y, z:-p.x };
}
export function rotateFacing(facing, q) {
  const v = V[facing]; if (!v) return 'unknown';
  const r = rotateY({x:v[0],y:v[1],z:v[2]}, q);
  return F[`${r.x},${r.y},${r.z}`] ?? 'unknown';
}
export function oppositeFacing(facing) { return OPP[facing] ?? 'unknown'; }
export function add(a,b) { return {x:a.x+b.x,y:a.y+b.y,z:a.z+b.z}; }
export function sub(a,b) { return {x:a.x-b.x,y:a.y-b.y,z:a.z-b.z}; }
export function transformPoint(p, rotation, translation) { return add(rotateY(p, rotation), translation); }
export function transformedSize(size, rotation) { return rotation % 2 ? {x:size.z,y:size.y,z:size.x} : {...size}; }
export function transformedBounds(origin,size,rotation) {
  const pts=[];
  for (const x of [0,size.x-1]) for (const y of [0,size.y-1]) for (const z of [0,size.z-1]) pts.push(transformPoint({x,y,z},rotation,origin));
  return {min:{x:Math.min(...pts.map(p=>p.x)),y:Math.min(...pts.map(p=>p.y)),z:Math.min(...pts.map(p=>p.z))},max:{x:Math.max(...pts.map(p=>p.x)),y:Math.max(...pts.map(p=>p.y)),z:Math.max(...pts.map(p=>p.z))}};
}
export function boxesOverlap(a,b,padding=0) {
  return a.min.x-padding<=b.max.x && a.max.x+padding>=b.min.x && a.min.y-padding<=b.max.y && a.max.y+padding>=b.min.y && a.min.z-padding<=b.max.z && a.max.z+padding>=b.min.z;
}
export function attachHorizontal(parentWorld, childConnector, childSize, q) {
  const childFacing = rotateFacing(childConnector.facing,q);
  if (childFacing !== oppositeFacing(parentWorld.facing)) return null;
  const rotated = rotateY(childConnector.position,q);
  return { rotation:q, origin:sub(parentWorld.position,rotated), bounds:transformedBounds(sub(parentWorld.position,rotated),childSize,q) };
}
export function enumerateConnectorTransforms(parentWorld, child, options={}) {
  const out=[];
  for (let q=0;q<4;q++) {
    const x=attachHorizontal(parentWorld,child.connector,child.size,q);
    if (x) out.push(x);
  }
  return out;
}

// Compatibility helpers used by the processor/placement integration.
export function normalizeRotation(rotation=0) {
  if (typeof rotation === 'number') return ((rotation % 4) + 4) % 4;
  const r=String(rotation).toLowerCase();
  if (r.includes('180')) return 2;
  if (r.includes('counter') || r === '270') return 3;
  if (r.includes('90')) return 1;
  return 0;
}
export function composeRotation(a=0,b=0) { return (normalizeRotation(a)+normalizeRotation(b))%4; }
export function rotatePosition(position={},rotation=0,size={x:1,y:1,z:1}) {
  const q=normalizeRotation(rotation), p={x:Number(position.x??0),y:Number(position.y??0),z:Number(position.z??0)};
  if(q===1) return {x:Number(size.z??1)-1-p.z,y:p.y,z:p.x};
  if(q===2) return {x:Number(size.x??1)-1-p.x,y:p.y,z:Number(size.z??1)-1-p.z};
  if(q===3) return {x:p.z,y:p.y,z:Number(size.x??1)-1-p.x};
  return p;
}
export function rotateDirection(direction,rotation=0) { return rotateFacing(direction,normalizeRotation(rotation)); }
export function transformConnector(connector={},rotation=0,pieceSize={x:1,y:1,z:1}) {
  return {...connector,position:rotatePosition(connector.position??connector.pos??{},rotation,pieceSize),facing:rotateDirection(connector.facing??connector.front,rotation)};
}
