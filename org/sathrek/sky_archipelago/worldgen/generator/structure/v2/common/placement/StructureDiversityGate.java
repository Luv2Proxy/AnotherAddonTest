package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostIslandKey;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.StructurePlacementPolicy;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry.AcceptedStructurePlacementRegistry;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public final class StructureDiversityGate {
   public StructureDiversityGate.OriginResult evaluateOrigin(ResourceLocation structureId, ChunkPos originChunk, StructurePlacementPolicy policy) {
      boolean spacingBlocked = AcceptedStructurePlacementRegistry.hasSameStructureWithinSpacing(
         structureId, originChunk, policy.minSameStructureSpacingChunks()
      );
      return spacingBlocked
         ? new StructureDiversityGate.OriginResult(false, "same_structure_spacing", true, policy.minSameStructureSpacingChunks())
         : new StructureDiversityGate.OriginResult(true, "accepted", false, policy.minSameStructureSpacingChunks());
   }

   public StructureDiversityGate.HostResult evaluateHost(
      ResourceLocation structureId, StructurePlacementCategory category, HostIslandKey hostIslandKey, StructurePlacementPolicy policy
   ) {
      int totalOnHost = AcceptedStructurePlacementRegistry.countForHost(hostIslandKey, category);
      if (totalOnHost >= policy.maxPerHostIsland()) {
         return new StructureDiversityGate.HostResult(
            false,
            "host_category_limit",
            totalOnHost,
            policy.maxPerHostIsland(),
            AcceptedStructurePlacementRegistry.countSameForHost(structureId, hostIslandKey),
            policy.maxSameStructurePerHostIsland()
         );
      }

      int sameOnHost = AcceptedStructurePlacementRegistry.countSameForHost(structureId, hostIslandKey);
      return sameOnHost >= policy.maxSameStructurePerHostIsland()
         ? new StructureDiversityGate.HostResult(
            false, "host_same_structure_limit", totalOnHost, policy.maxPerHostIsland(), sameOnHost, policy.maxSameStructurePerHostIsland()
         )
         : new StructureDiversityGate.HostResult(true, "accepted", totalOnHost, policy.maxPerHostIsland(), sameOnHost, policy.maxSameStructurePerHostIsland());
   }

   public record HostResult(boolean accepted, String reason, int totalOnHost, int maxPerHost, int sameOnHost, int maxSamePerHost) {
   }

   public record OriginResult(boolean accepted, String reason, boolean spacingBlocked, int spacingChunks) {
   }
}
