import { PostProcessDriftDecision } from "./RelocatedPlacementContext.js";
export class PostProcessDriftPolicy {
 constructor({maxDriftY=4,strictRoles={}}={}){this.maxDriftY=maxDriftY;this.strictRoles=strictRoles;}
 evaluate(context,piece,before,after){const role=context?.pieceRole(piece)??piece?.role??"unknown",expected=piece?.expectedDy??null;if(!before||!after)return PostProcessDriftDecision.allow(role,expected,"missing_bounds");const dy=after.min.y-before.min.y;if(context?.yLockEnabled&&Math.abs(dy)>this.maxDriftY){const target=expected??0;return PostProcessDriftDecision.clamp(target-dy,role,expected,"y_lock_drift");}if(this.strictRoles[role]&&Math.abs(dy)>this.strictRoles[role])return PostProcessDriftDecision.clamp((expected??0)-dy,role,expected,"role_drift_limit");return PostProcessDriftDecision.allow(role,expected,"within_tolerance");}
}
