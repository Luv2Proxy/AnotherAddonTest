package org.sathrek.sky_archipelago.worldgen.structure;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;

public final class StructureWhitelist {
   private final Set<ResourceLocation> allowedStructures;

   private StructureWhitelist(Set<ResourceLocation> allowedStructures) {
      this.allowedStructures = Set.copyOf(allowedStructures);
   }

   public static StructureWhitelist fromStrings(Collection<? extends String> entries) {
      return new StructureWhitelist(entries.stream().<ResourceLocation>map(ResourceLocation::tryParse).filter(Objects::nonNull).collect(Collectors.toSet()));
   }

   public static boolean isValidEntry(String entry) {
      return ResourceLocation.tryParse(entry) != null;
   }

   public boolean isWhitelisted(ResourceLocation structureId) {
      return structureId != null && this.allowedStructures.contains(structureId);
   }

   public int size() {
      return this.allowedStructures.size();
   }

   public Set<ResourceLocation> entries() {
      return this.allowedStructures;
   }
}
