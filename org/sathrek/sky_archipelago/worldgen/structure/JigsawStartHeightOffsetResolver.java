package org.sathrek.sky_archipelago.worldgen.structure;

import java.lang.reflect.Field;
import java.util.Optional;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.sathrek.sky_archipelago.mixin.worldgen.JigsawStructureAccessor;

public final class JigsawStartHeightOffsetResolver {
   private static final String CONSTANT_HEIGHT_CLASS = "net.minecraft.world.level.levelgen.heightproviders.ConstantHeight";
   private static final String ABSOLUTE_VERTICAL_ANCHOR_CLASS = "net.minecraft.world.level.levelgen.VerticalAnchor$Absolute";
   private final String constantHeightClassName;
   private final String absoluteVerticalAnchorClassName;

   public JigsawStartHeightOffsetResolver() {
      this("net.minecraft.world.level.levelgen.heightproviders.ConstantHeight", "net.minecraft.world.level.levelgen.VerticalAnchor$Absolute");
   }

   JigsawStartHeightOffsetResolver(String constantHeightClassName, String absoluteVerticalAnchorClassName) {
      this.constantHeightClassName = constantHeightClassName;
      this.absoluteVerticalAnchorClassName = absoluteVerticalAnchorClassName;
   }

   public int resolve(Structure structure) {
      if (structure instanceof JigsawStructure jigsawStructure) {
         JigsawStartHeightOffsetResolver.JigsawStartHeightData data = dataFor(jigsawStructure);
         return data == null ? 0 : this.resolve(data.startHeight(), data.projectStartToHeightmap());
      } else {
         return 0;
      }
   }

   int resolve(Object startHeight, Optional<?> projectStartToHeightmap) {
      if (startHeight != null && projectStartToHeightmap != null && !projectStartToHeightmap.isEmpty()) {
         if (!this.constantHeightClassName.equals(startHeight.getClass().getName())) {
            return 0;
         } else {
            Object verticalAnchor = fieldValueOrNull(startHeight, "value");
            if (verticalAnchor != null && this.absoluteVerticalAnchorClassName.equals(verticalAnchor.getClass().getName())) {
               return fieldValueOrNull(verticalAnchor, "y") instanceof Integer value ? value : 0;
            } else {
               return 0;
            }
         }
      } else {
         return 0;
      }
   }

   private static JigsawStartHeightOffsetResolver.JigsawStartHeightData dataFor(JigsawStructure structure) {
      return structure instanceof JigsawStructureAccessor accessor
         ? new JigsawStartHeightOffsetResolver.JigsawStartHeightData(accessor.sky_archipelago$startHeight(), accessor.sky_archipelago$projectStartToHeightmap())
         : reflectionDataFor(structure);
   }

   private static JigsawStartHeightOffsetResolver.JigsawStartHeightData reflectionDataFor(JigsawStructure structure) {
      try {
         HeightProvider startHeight = fieldValue(structure, "startHeight", HeightProvider.class);
         Optional<Types> projectStartToHeightmap = fieldValue(structure, "projectStartToHeightmap", Optional.class);
         return new JigsawStartHeightOffsetResolver.JigsawStartHeightData(startHeight, projectStartToHeightmap);
      } catch (ReflectiveOperationException | RuntimeException ignored) {
         return null;
      }
   }

   private static <T> T fieldValue(Object owner, String fieldName, Class<T> fieldType) throws ReflectiveOperationException {
      Field field = owner.getClass().getDeclaredField(fieldName);
      if (!field.canAccess(owner)) {
         field.setAccessible(true);
      }

      return fieldType.cast(field.get(owner));
   }

   private static Object fieldValueOrNull(Object owner, String fieldName) {
      try {
         Field field = owner.getClass().getDeclaredField(fieldName);
         if (!field.canAccess(owner)) {
            field.setAccessible(true);
         }

         return field.get(owner);
      } catch (ReflectiveOperationException | RuntimeException ignored) {
         return null;
      }
   }

   private record JigsawStartHeightData(HeightProvider startHeight, Optional<Types> projectStartToHeightmap) {
   }
}
