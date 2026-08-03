import { BlockPermutation, BlockVolume } from "@minecraft/server";

const AIR="minecraft:air";
const DEFAULT_EXACT=new Set(["minecraft:air"]);

function stateKey(states){if(!states)return"";try{return JSON.stringify(Object.keys(states).sort().map(k=>[k,states[k]]));}catch{return String(states);}}
function keyOf(block){return `${block.id}|${stateKey(block.states)}`;}
function permutation(block){try{return typeof block.permutation?.type?.id==="string"?block.permutation:BlockPermutation.resolve(block.id,block.states);}catch{return null;}}
function volume(a,b){return new BlockVolume({x:a.x,y:a.y,z:a.z},{x:b.x,y:b.y,z:b.z});}
function canBulk(block,exact){return !exact.has(block.id)&&!block.exact&&!block.blockEntity&&!block.entity&&!block.jigsaw&&!block.states?.waterlogged;}

// Converts a sparse voxel map into large cuboids. Exact/stateful blocks remain singles.
export function greedyCuboids(blocks,options={}){
 const exact=options.exactIds instanceof Set?options.exactIds:DEFAULT_EXACT, map=new Map();
 for(const b of blocks??[]){if(!b||b.x==null||b.y==null||b.z==null||!b.id)continue;const k=keyOf(b);let list=map.get(k);if(!list)map.set(k,list=[]);list.push(b);}
 const out=[];
 for(const [key,list] of map){const sample=list[0];if(!canBulk(sample,exact)){for(const b of list)out.push({kind:"single",block:b});continue;}
  const vox=new Set(list.map(b=>`${b.x},${b.y},${b.z}`));
  // Greedy x-runs, then merge equal rectangles along z, then equal rectangles along y.
  const runs=[];
  for(const b of list){const left=vox.has(`${b.x-1},${b.y},${b.z}`);if(left)continue;let x2=b.x;while(vox.has(`${x2+1},${b.y},${b.z}`))x2++;runs.push({x1:b.x,x2,y1:b.y,y2:b.y,z1:b.z,z2:b.z});}
  const byRow=new Map();for(const r of runs){const k=`${r.x1},${r.x2},${r.y1}`;let a=byRow.get(k);if(!a)byRow.set(k,a=[]);a.push(r);}
  const rects=[];for(const a of byRow.values()){a.sort((p,q)=>p.z1-q.z1);for(const r of a){const last=rects[rects.length-1];if(last&&last.x1===r.x1&&last.x2===r.x2&&last.y1===r.y1&&last.z2+1===r.z1){last.z2=r.z2;}else rects.push({...r});}}
  const byLayer=new Map();for(const r of rects){const k=`${r.x1},${r.x2},${r.z1},${r.z2}`;let a=byLayer.get(k);if(!a)byLayer.set(k,a=[]);a.push(r);}
  for(const a of byLayer.values()){a.sort((p,q)=>p.y1-q.y1);for(const r of a){const last=out[out.length-1];if(last?.kind==="fill"&&last.key===key&&last.x1===r.x1&&last.x2===r.x2&&last.z1===r.z1&&last.z2===r.z2&&last.y2+1===r.y1){last.y2=r.y2;}else out.push({kind:"fill",key,id:sample.id,states:sample.states,x1:r.x1,x2:r.x2,y1:r.y1,y2:r.y2,z1:r.z1,z2:r.z2});}}
 }
 return out;
}

export function writeBulk(dimension,ops,options={}){
 let fills=0,singles=0,failed=0;
 for(const op of ops??[]){try{if(op.kind==="fill"){const p=permutation({id:op.id,states:op.states});if(!p)throw new Error(`invalid permutation ${op.id}`);dimension.fillBlocks(volume({x:op.x1,y:op.y1,z:op.z1},{x:op.x2,y:op.y2,z:op.z2}),p,{ignoreChunkBoundErrors:true});fills++;}else{const p=permutation(op.block);if(!p)continue;dimension.setBlockPermutation({x:op.block.x,y:op.block.y,z:op.block.z},p);singles++;}}catch(e){failed++;if(options.strict)throw e;}}
 return{fills,singles,failed,total:(ops??[]).length};
}

export function writeBlocksBulk(dimension,blocks,options={}){return writeBulk(dimension,greedyCuboids(blocks,options),options);}
export function blockMapFromPlan(plan,x,z,options={}){
 const blocks=[];for(let y=plan.minY;y<plan.maxY;y++){const p=plan.materialAt(y);if(!p)continue;const id=p.type?.id??p.typeId??null;if(id)blocks.push({x,y,z,id,permutation:p,exact:options.exact?.has?.(id)});}return blocks;
}
