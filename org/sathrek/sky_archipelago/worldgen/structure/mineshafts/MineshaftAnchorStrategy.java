package org.sathrek.sky_archipelago.worldgen.structure.mineshafts;

import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.structure.AnchorResolverStrategy;

public final class MineshaftAnchorStrategy implements AnchorResolverStrategy {
   private final MineshaftAnchorResolver resolver;

   public MineshaftAnchorStrategy(MineshaftAnchorResolver resolver) {
      this.resolver = resolver;
   }

   @Override
   public AnchorResolverStrategy.DynamicAnchor resolve(StructureStart structureStart) {
      MineshaftAnchorResolver.Anchor anchor = this.resolver.resolve(structureStart);
      return new AnchorResolverStrategy.DynamicAnchor(anchor.x(), anchor.baseY(), anchor.z(), anchor.source());
   }
}
