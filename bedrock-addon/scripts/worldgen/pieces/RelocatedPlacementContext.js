export class RelocatedPlacementContext {
  constructor({structureId,startChunk,dimension,authorityAnchorY,yLockEnabled=true,policy,adapter,telemetry=null}={}){this.structureId=structureId;this.startChunk=startChunk;this.dimension=dimension;this.authorityAnchorY=authorityAnchorY;this.yLockEnabled=!!yLockEnabled;this.policy=policy;this.adapter=adapter;this.telemetry=telemetry;}
  pieceRole(piece){return this.adapter?.pieceRole?.(piece)??piece?.role??"unknown";}
  suppressIglooSurfaceAdjustment(piece){return !!this.adapter?.suppressIglooSurfaceAdjustment?.(this,piece);}
  evaluateDrift(piece,before,after){return this.policy?.evaluate?.(this,piece,before,after)??{correctionDy:0,pieceRole:this.pieceRole(piece),expectedDy:piece?.expectedDy??null,reason:"default_allow"};}
  recordDrift(piece,before,after,decision){this.telemetry?.record?.(piece,before,after,decision,this);}
}
export class PostProcessDriftDecision { static allow(pieceRole,expectedDy,reason){return{correctionDy:0,pieceRole,expectedDy,reason};}static clamp(correctionDy,pieceRole,expectedDy,reason){return{correctionDy,pieceRole,expectedDy,reason};} }
export class PostProcessDriftTelemetry {
 constructor(enabled=true){this.enabled=enabled;this.records=[];}
 record(piece,before,after,decision,context){if(!this.enabled||!before||!after)return;const drift={dx:after.min.x-before.min.x,dy:after.min.y-before.min.y,dz:after.min.z-before.min.z};const record={structureId:context?.structureId??"unknown",startChunk:context?.startChunk??null,piece:piece?.id??"unknown",role:decision?.pieceRole??piece?.role??"unknown",drift,expectedDy:decision?.expectedDy??null,correctionDy:decision?.correctionDy??0,reason:decision?.reason??"none",flagged:Math.abs(drift.dy)>4};this.records.push(record);if(record.flagged)console.warn(`[Sky Archipelago] structure piece drift: ${JSON.stringify(record)}`);}
}
