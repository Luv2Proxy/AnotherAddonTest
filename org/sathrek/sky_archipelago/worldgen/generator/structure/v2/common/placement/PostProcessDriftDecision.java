package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

public record PostProcessDriftDecision(int correctionDy, String pieceRole, Integer expectedDy, String reason) {
   public static PostProcessDriftDecision allow(String pieceRole, Integer expectedDy, String reason) {
      return new PostProcessDriftDecision(0, pieceRole, expectedDy, reason);
   }

   public static PostProcessDriftDecision clamp(int correctionDy, String pieceRole, Integer expectedDy, String reason) {
      return new PostProcessDriftDecision(correctionDy, pieceRole, expectedDy, reason);
   }
}
