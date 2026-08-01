package org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public final class AnchorBoundsPolicy {
   private static final Set<StructurePlacementCategory> ROOT_PIECE_TRIAL_CATEGORIES = EnumSet.of(
      StructurePlacementCategory.DEFAULT, StructurePlacementCategory.SURFACE_SKY, StructurePlacementCategory.SMALL_SKY, StructurePlacementCategory.HAMLET_SKY
   );
   private final Set<StructurePlacementCategory> rootPieceCategories;

   public AnchorBoundsPolicy(Set<StructurePlacementCategory> rootPieceCategories) {
      this.rootPieceCategories = rootPieceCategories.isEmpty() ? EnumSet.noneOf(StructurePlacementCategory.class) : EnumSet.copyOf(rootPieceCategories);
   }

   public static AnchorBoundsPolicy rootPieceTrialPolicy() {
      return new AnchorBoundsPolicy(ROOT_PIECE_TRIAL_CATEGORIES);
   }

   public static AnchorBoundsPolicy legacyOnlyPolicy() {
      return new AnchorBoundsPolicy(EnumSet.noneOf(StructurePlacementCategory.class));
   }

   public boolean useRootPieceBounds(StructurePlacementCategory category, Structure structure) {
      return category != null && this.rootPieceCategories.contains(category) ? !(structure instanceof JigsawStructure) : false;
   }
}
