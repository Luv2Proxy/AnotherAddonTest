package org.sathrek.sky_archipelago.worldgen.generator.surface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.WorldgenPerformanceMetrics;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.village.VillagePieceClassifier;
import org.sathrek.sky_archipelago.worldgen.generator.terrain.SkyIslandChunkTerrainSnapshot;
import org.sathrek.sky_archipelago.worldgen.generator.terrain.SkyIslandColumnMaterialPlan;
import org.sathrek.sky_archipelago.worldgen.structure.PieceAwareSupportPlaneResolver;
import org.sathrek.sky_archipelago.worldgen.structure.ResolvedStructureSupportPlane;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

public final class SkyIslandSurfacePass {
   private static final ResourceLocation OCEAN_MONUMENT_ID = ResourceLocation.parse("minecraft:monument");
   private static final PieceAwareSupportPlaneResolver SUPPORT_PLANE_RESOLVER = new PieceAwareSupportPlaneResolver();
   private static final int MAX_GROUNDED_BLEND_GAP = 2;
   private static final int MAX_HAMLET_BLEND_GAP = 4;
   private static final int MAX_VILLAGE_BLEND_GAP = 10;
   private static final int MAX_VILLAGE_CUT_DEPTH = 3;
   private static final int MAX_MONUMENT_INNER_BLEND_GAP = 4;
   private static final int MAX_MONUMENT_OUTER_BLEND_GAP = 7;
   private static final int MAX_MONUMENT_CUT_DEPTH = 3;
   private static final int MONUMENT_OUTER_MARGIN_BLOCKS = 6;
   private static final int LAND_INNER_BLEND_GAP = 3;
   private static final int LAND_OUTER_BLEND_GAP = 5;
   private static final VillagePieceClassifier VILLAGE_PIECE_CLASSIFIER = new VillagePieceClassifier();

   private SkyIslandSurfacePass() {
   }

   public static void buildSurface(
      WorldGenRegion level, StructureManager structureManager, ChunkAccess chunk, SkyIslandSettings settings, IslandField islandField
   ) {
      long passStartNanos = System.nanoTime();
      ChunkPos chunkPos = chunk.getPos();
      long layoutSeed = islandField.layoutSeed();
      MutableBlockPos mutablePos = new MutableBlockPos();
      long snapshotStartNanos = System.nanoTime();
      SkyIslandChunkTerrainSnapshot snapshot = islandField.sampleChunkTerrainSnapshot(chunkPos, settings);
      long snapshotNanos = System.nanoTime() - snapshotStartNanos;
      int profileColumns = 0;
      long skinWrites = 0L;

      for (int localX = 0; localX < 16; localX++) {
         for (int localZ = 0; localZ < 16; localZ++) {
            int worldX = chunkPos.getMinBlockX() + localX;
            int worldZ = chunkPos.getMinBlockZ() + localZ;
            paintOceanFloorSkin(chunk, settings, snapshot.segmentsAt(worldX, worldZ), layoutSeed, worldX, worldZ);
            IslandField.ColumnProfile profile = snapshot.profileAt(worldX, worldZ);
            if (profile.exists()) {
               profileColumns++;
               int surfaceY = profile.topY();
               mutablePos.set(worldX, surfaceY, worldZ);
               Holder<Biome> biome = chunk.getNoiseBiome(QuartPos.fromBlock(worldX), QuartPos.fromBlock(surfaceY), QuartPos.fromBlock(worldZ));
               SurfaceBlockSelector.SurfaceLayer layer = SurfaceBlockSelector.forBiome(biome, mutablePos);
               boolean steepSurface = snapshot.isSteepAt(worldX, worldZ, surfaceY);
               boolean capLikeSurface = profile.topCap() && !steepSurface || hasNearbyTopSupport(snapshot, profile, worldX, worldZ, surfaceY);
               boolean exposedCliff = steepSurface || profile.cliffBand() && !capLikeSurface;
               int soilDepth = capLikeSurface
                  ? Math.max(3, Math.max(layer.soilDepth(), profile.family() == IslandField.IslandFamily.ANCHOR_PLATEAU ? 5 : 4))
                  : (!exposedCliff && profile.family() != IslandField.IslandFamily.SPIRE ? Math.max(3, layer.soilDepth() - 1) : 2);
               int skinDepth = Math.max(3, soilDepth);

               for (int depth = 0; depth < skinDepth && surfaceY - depth >= chunk.getMinBuildHeight(); depth++) {
                  mutablePos.setY(surfaceY - depth);
                  BlockState surfaceState = layer.requiredSurfaceStateAtDepth(depth);
                  chunk.setBlockState(mutablePos, surfaceState, false);
                  skinWrites++;
               }

               rewriteExposedStoneSkin(level, chunk, worldX, worldZ, surfaceY, layer);
               if (surfaceY + 1 < chunk.getMaxBuildHeight()) {
                  mutablePos.set(worldX, surfaceY + 1, worldZ);
                  if (((Biome)biome.value()).coldEnoughToSnow(mutablePos)) {
                     chunk.setBlockState(mutablePos, Blocks.SNOW.defaultBlockState(), false);
                  }
               }
            }
         }
      }

      blendGroundedStructureBases(level, structureManager, chunk, settings);
      MineshaftChainSupportPass.apply(level, structureManager, chunk);
      WorldgenPerformanceMetrics.recordSurfacePass(System.nanoTime() - passStartNanos, snapshotNanos, profileColumns, skinWrites);
   }

   private static void paintOceanFloorSkin(ChunkAccess chunk, SkyIslandSettings settings, List<TerrainColumn> segments, long layoutSeed, int worldX, int worldZ) {
      if (settings.terrain().ocean().oceanEnabled() && settings.terrain().ocean().oceanFloorNoiseEnabled()) {
         SkyIslandColumnMaterialPlan plan = SkyIslandColumnMaterialPlan.create(
            segments, chunk.getMinBuildHeight(), chunk.getMaxBuildHeight(), settings, worldX, worldZ, layoutSeed
         );
         int floorTopY = plan.oceanFloorTopY();
         if (floorTopY > chunk.getMinBuildHeight()) {
            if (plan.materialSlotAt(floorTopY) == SkyIslandColumnMaterialPlan.MaterialSlot.STONE
               && plan.materialSlotAt(floorTopY + 1) == SkyIslandColumnMaterialPlan.MaterialSlot.OCEAN) {
               MutableBlockPos mutablePos = new MutableBlockPos(worldX, floorTopY, worldZ);
               Holder<Biome> biome = chunk.getNoiseBiome(QuartPos.fromBlock(worldX), QuartPos.fromBlock(floorTopY), QuartPos.fromBlock(worldZ));
               SurfaceBlockSelector.SurfaceLayer layer = SurfaceBlockSelector.forUnderwaterFloor(biome, mutablePos);

               for (int depth = 0; depth <= 2; depth++) {
                  int y = floorTopY - depth;
                  if (y <= chunk.getMinBuildHeight() || plan.materialSlotAt(y) != SkyIslandColumnMaterialPlan.MaterialSlot.STONE) {
                     break;
                  }

                  mutablePos.setY(y);
                  BlockState state = depth == 0 ? layer.top() : (depth == 1 ? layer.under() : layer.deep());
                  chunk.setBlockState(mutablePos, state, false);
               }
            }
         }
      }
   }

   private static void blendGroundedStructureBases(WorldGenRegion level, StructureManager structureManager, ChunkAccess chunk, SkyIslandSettings settings) {
      Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
      Set<StructureStart> blendedStarts = Collections.newSetFromMap(new IdentityHashMap<>());
      List<Entry<Structure, StructureStart>> starts = new ArrayList<>(chunk.getAllStarts().entrySet());
      structureRegistry.forEach(structure -> {
         ResourceLocation structureIdx = structureRegistry.getKey(structure);
         if (structureIdx != null && !settings.advanced().structureWhitelist().isWhitelisted(structureIdx)) {
            StructurePlacementCategory categoryx = settings.advanced().structurePlacementPolicy().categoryFor(structureIdx);
            if (categoryx.usesIslandAwarePlacement() || categoryx == StructurePlacementCategory.DEFAULT) {
               for (StructureStart referencedStart : safeStartsForStructure(structureManager, chunk.getPos(), candidate -> candidate.equals(structure))) {
                  starts.add(Map.entry(structure, referencedStart));
               }
            }
         }
      });

      for (Entry<Structure, StructureStart> entry : starts) {
         StructureStart structureStart = entry.getValue();
         if (structureStart != null && structureStart.isValid() && blendedStarts.add(structureStart)) {
            ResourceLocation structureId = structureRegistry.getKey(entry.getKey());
            if (structureId != null && !settings.advanced().structureWhitelist().isWhitelisted(structureId)) {
               StructurePlacementCategory category = settings.advanced().structurePlacementPolicy().categoryFor(structureId);
               if (category.usesIslandAwarePlacement() || category == StructurePlacementCategory.DEFAULT) {
                  if (category == StructurePlacementCategory.GROUND_VILLAGE) {
                     for (SkyIslandSurfacePass.VillageBlendTarget target : villageBlendTargetsFor(structureStart)) {
                        blendVillagePieceBaseFootprint(chunk, target.footprint(), target.baseY(), target.maxBlendGap(), target.maxCutDepth());
                     }
                  } else {
                     ResolvedStructureSupportPlane supportPlane = SUPPORT_PLANE_RESOLVER.resolve(
                           structureId, structureStart, settings.advanced().structurePlacementPolicy().footprintInsetRatioFor(structureId)
                        )
                        .orElse(null);
                     if (supportPlane != null) {
                        if (OCEAN_MONUMENT_ID.equals(structureId)) {
                           for (SkyIslandSurfacePass.MonumentBlendTarget target : monumentBlendTargetsFor(supportPlane.rawFootprint(), supportPlane.baseY())) {
                              blendMonumentBaseFootprint(chunk, target.footprint(), target.baseY(), target.maxBlendGap(), target.maxCutDepth());
                           }
                        } else {
                           for (SkyIslandSurfacePass.BlendTarget target : blendTargetsFor(category, supportPlane)) {
                              blendStructureBaseFootprint(chunk, target.footprint(), target.baseY(), target.maxBlendGap());
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static Iterable<StructureStart> safeStartsForStructure(StructureManager structureManager, ChunkPos chunkPos, Predicate<Structure> predicate) {
      try {
         return structureManager.startsForStructure(chunkPos, predicate);
      } catch (IllegalStateException ignored) {
         return List.of();
      }
   }

   private static void blendStructureBaseFootprint(ChunkAccess chunk, StructureFootprint footprint, int baseY, int maxBlendGap) {
      int chunkMinX = chunk.getPos().getMinBlockX();
      int chunkMinZ = chunk.getPos().getMinBlockZ();
      int chunkMaxX = chunkMinX + 15;
      int chunkMaxZ = chunkMinZ + 15;
      int minX = Math.max(chunkMinX, footprint.minX());
      int maxX = Math.min(chunkMaxX, footprint.maxX());
      int minZ = Math.max(chunkMinZ, footprint.minZ());
      int maxZ = Math.min(chunkMaxZ, footprint.maxZ());
      if (minX <= maxX && minZ <= maxZ) {
         MutableBlockPos mutablePos = new MutableBlockPos();
         int fillTopY = baseY - 1;

         for (int worldX = minX; worldX <= maxX; worldX++) {
            int localX = worldX - chunkMinX;

            for (int worldZ = minZ; worldZ <= maxZ; worldZ++) {
               int localZ = worldZ - chunkMinZ;
               int terrainTopY = chunk.getHeight(Types.WORLD_SURFACE_WG, localX, localZ) - 1;
               if (terrainTopY >= chunk.getMinBuildHeight()) {
                  int gap = fillTopY - terrainTopY;
                  if (gap > 0 && gap <= maxBlendGap) {
                     Holder<Biome> biome = chunk.getNoiseBiome(QuartPos.fromBlock(worldX), QuartPos.fromBlock(fillTopY), QuartPos.fromBlock(worldZ));
                     mutablePos.set(worldX, fillTopY, worldZ);
                     SurfaceBlockSelector.SurfaceLayer layer = SurfaceBlockSelector.forBiome(biome, mutablePos);

                     for (int y = terrainTopY + 1; y <= fillTopY; y++) {
                        mutablePos.setY(y);
                        BlockState existingState = chunk.getBlockState(mutablePos);
                        if (existingState.isAir()) {
                           int depthFromTop = fillTopY - y;
                           BlockState fillState;
                           if (depthFromTop == 0) {
                              fillState = layer.top();
                           } else if (depthFromTop <= 1) {
                              fillState = layer.under();
                           } else {
                              fillState = layer.deep();
                           }

                           chunk.setBlockState(mutablePos, fillState, false);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static List<SkyIslandSurfacePass.VillageBlendTarget> villageBlendTargetsFor(StructureStart structureStart) {
      List<SkyIslandSurfacePass.VillageBlendTarget> targets = new ArrayList<>();

      for (StructurePiece piece : structureStart.getPieces()) {
         VillagePieceClassifier.PieceKind kind = VILLAGE_PIECE_CLASSIFIER.classify(piece);
         BoundingBox bounds = piece.getBoundingBox();
         StructureFootprint footprint = villageSupportFootprint(bounds, kind);
         int fillGap = kind == VillagePieceClassifier.PieceKind.FARM ? Math.min(4, 10) : 10;
         int cutDepth = kind == VillagePieceClassifier.PieceKind.FARM ? 1 : 3;
         targets.add(new SkyIslandSurfacePass.VillageBlendTarget(footprint, bounds.minY(), fillGap, cutDepth));
      }

      return targets;
   }

   private static StructureFootprint villageSupportFootprint(BoundingBox bounds, VillagePieceClassifier.PieceKind kind) {
      int minX = bounds.minX();
      int maxX = bounds.maxX();
      int minZ = bounds.minZ();
      int maxZ = bounds.maxZ();
      if (kind == VillagePieceClassifier.PieceKind.BUILDING || kind == VillagePieceClassifier.PieceKind.OTHER) {
         int insetX = Math.max(0, (maxX - minX) / 6);
         int insetZ = Math.max(0, (maxZ - minZ) / 6);
         minX += insetX;
         maxX -= insetX;
         minZ += insetZ;
         maxZ -= insetZ;
      } else if (kind == VillagePieceClassifier.PieceKind.FARM) {
         minX -= 2;
         maxX += 2;
         minZ -= 2;
         maxZ += 2;
      }

      if (minX > maxX) {
         int center = (bounds.minX() + bounds.maxX()) / 2;
         minX = center;
         maxX = center;
      }

      if (minZ > maxZ) {
         int center = (bounds.minZ() + bounds.maxZ()) / 2;
         minZ = center;
         maxZ = center;
      }

      return new StructureFootprint(minX, maxX, minZ, maxZ);
   }

   private static void blendVillagePieceBaseFootprint(ChunkAccess chunk, StructureFootprint footprint, int baseY, int maxBlendGap, int maxCutDepth) {
      int chunkMinX = chunk.getPos().getMinBlockX();
      int chunkMinZ = chunk.getPos().getMinBlockZ();
      int chunkMaxX = chunkMinX + 15;
      int chunkMaxZ = chunkMinZ + 15;
      int minX = Math.max(chunkMinX, footprint.minX());
      int maxX = Math.min(chunkMaxX, footprint.maxX());
      int minZ = Math.max(chunkMinZ, footprint.minZ());
      int maxZ = Math.min(chunkMaxZ, footprint.maxZ());
      if (minX <= maxX && minZ <= maxZ) {
         MutableBlockPos mutablePos = new MutableBlockPos();
         int fillTopY = baseY - 1;

         for (int worldX = minX; worldX <= maxX; worldX++) {
            int localX = worldX - chunkMinX;

            for (int worldZ = minZ; worldZ <= maxZ; worldZ++) {
               int localZ = worldZ - chunkMinZ;
               int terrainTopY = chunk.getHeight(Types.WORLD_SURFACE_WG, localX, localZ) - 1;
               if (terrainTopY >= chunk.getMinBuildHeight()) {
                  int gap = fillTopY - terrainTopY;
                  if (gap > 0 && gap <= maxBlendGap) {
                     Holder<Biome> biome = chunk.getNoiseBiome(QuartPos.fromBlock(worldX), QuartPos.fromBlock(fillTopY), QuartPos.fromBlock(worldZ));
                     mutablePos.set(worldX, fillTopY, worldZ);
                     SurfaceBlockSelector.SurfaceLayer layer = SurfaceBlockSelector.forBiome(biome, mutablePos);

                     for (int y = terrainTopY + 1; y <= fillTopY; y++) {
                        mutablePos.setY(y);
                        if (chunk.getBlockState(mutablePos).isAir()) {
                           int depthFromTop = fillTopY - y;
                           BlockState fillState = depthFromTop == 0 ? layer.top() : (depthFromTop <= 1 ? layer.under() : layer.deep());
                           chunk.setBlockState(mutablePos, fillState, false);
                        }
                     }
                  } else {
                     int intrusion = terrainTopY - baseY;
                     if (intrusion >= 0 && intrusion <= maxCutDepth) {
                        for (int y = baseY; y <= terrainTopY; y++) {
                           mutablePos.set(worldX, y, worldZ);
                           chunk.setBlockState(mutablePos, Blocks.AIR.defaultBlockState(), false);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void blendMonumentBaseFootprint(ChunkAccess chunk, StructureFootprint footprint, int baseY, int maxBlendGap, int maxCutDepth) {
      int chunkMinX = chunk.getPos().getMinBlockX();
      int chunkMinZ = chunk.getPos().getMinBlockZ();
      int chunkMaxX = chunkMinX + 15;
      int chunkMaxZ = chunkMinZ + 15;
      int minX = Math.max(chunkMinX, footprint.minX());
      int maxX = Math.min(chunkMaxX, footprint.maxX());
      int minZ = Math.max(chunkMinZ, footprint.minZ());
      int maxZ = Math.min(chunkMaxZ, footprint.maxZ());
      if (minX <= maxX && minZ <= maxZ) {
         MutableBlockPos mutablePos = new MutableBlockPos();
         int fillTopY = baseY - 1;

         for (int worldX = minX; worldX <= maxX; worldX++) {
            int localX = worldX - chunkMinX;

            for (int worldZ = minZ; worldZ <= maxZ; worldZ++) {
               int localZ = worldZ - chunkMinZ;
               int terrainTopY = chunk.getHeight(Types.WORLD_SURFACE_WG, localX, localZ) - 1;
               if (terrainTopY >= chunk.getMinBuildHeight()) {
                  int gap = fillTopY - terrainTopY;
                  SkyIslandSurfacePass.MonumentBlendAction action = monumentBlendAction(gap, terrainTopY - baseY, maxBlendGap, maxCutDepth);
                  if (action == SkyIslandSurfacePass.MonumentBlendAction.FILL) {
                     Holder<Biome> biome = chunk.getNoiseBiome(QuartPos.fromBlock(worldX), QuartPos.fromBlock(fillTopY), QuartPos.fromBlock(worldZ));
                     mutablePos.set(worldX, fillTopY, worldZ);
                     SurfaceBlockSelector.SurfaceLayer layer = SurfaceBlockSelector.forBiome(biome, mutablePos);

                     for (int y = terrainTopY + 1; y <= fillTopY; y++) {
                        mutablePos.setY(y);
                        if (chunk.getBlockState(mutablePos).isAir()) {
                           int depthFromTop = fillTopY - y;
                           BlockState fillState = depthFromTop == 0 ? layer.top() : (depthFromTop <= 1 ? layer.under() : layer.deep());
                           chunk.setBlockState(mutablePos, fillState, false);
                        }
                     }
                  } else if (action == SkyIslandSurfacePass.MonumentBlendAction.CUT) {
                     for (int y = baseY; y <= terrainTopY; y++) {
                        mutablePos.set(worldX, y, worldZ);
                        chunk.setBlockState(mutablePos, Blocks.AIR.defaultBlockState(), false);
                     }
                  }
               }
            }
         }
      }
   }

   static List<SkyIslandSurfacePass.MonumentBlendTarget> monumentBlendTargetsFor(StructureFootprint baseFootprint, int baseY) {
      List<SkyIslandSurfacePass.MonumentBlendTarget> targets = new ArrayList<>();
      targets.add(new SkyIslandSurfacePass.MonumentBlendTarget(baseFootprint, baseY, 4, 3));
      targets.add(new SkyIslandSurfacePass.MonumentBlendTarget(expandFootprint(baseFootprint, 6), baseY - 1, 7, 0));
      return targets;
   }

   static SkyIslandSurfacePass.MonumentBlendAction monumentBlendAction(int gap, int intrusion, int maxBlendGap, int maxCutDepth) {
      if (gap > 0 && gap <= maxBlendGap) {
         return SkyIslandSurfacePass.MonumentBlendAction.FILL;
      } else {
         return intrusion >= 0 && intrusion <= maxCutDepth ? SkyIslandSurfacePass.MonumentBlendAction.CUT : SkyIslandSurfacePass.MonumentBlendAction.NONE;
      }
   }

   static List<SkyIslandSurfacePass.BlendTarget> blendTargetsFor(StructurePlacementCategory category, ResolvedStructureSupportPlane supportPlane) {
      List<SkyIslandSurfacePass.BlendTarget> targets = new ArrayList<>();
      StructureFootprint raw = supportPlane.rawFootprint();
      if (category == StructurePlacementCategory.DEFAULT) {
         StructureFootprint core = raw.insetByRatio(0.12);
         StructureFootprint innerMargin = raw;
         StructureFootprint outerMargin = expandFootprint(raw, 3);
         targets.add(new SkyIslandSurfacePass.BlendTarget(core, supportPlane.baseY(), 2));
         targets.add(new SkyIslandSurfacePass.BlendTarget(innerMargin, supportPlane.baseY(), 3));
         targets.add(new SkyIslandSurfacePass.BlendTarget(outerMargin, supportPlane.baseY() - 1, 5));
         return targets;
      } else {
         int maxBlendGap = category == StructurePlacementCategory.HAMLET_SKY ? 4 : 2;
         targets.add(new SkyIslandSurfacePass.BlendTarget(raw, supportPlane.baseY(), maxBlendGap));
         return targets;
      }
   }

   private static StructureFootprint expandFootprint(StructureFootprint footprint, int blocks) {
      return new StructureFootprint(footprint.minX() - blocks, footprint.maxX() + blocks, footprint.minZ() - blocks, footprint.maxZ() + blocks);
   }

   private static void rewriteExposedStoneSkin(
      WorldGenRegion level, ChunkAccess chunk, int worldX, int worldZ, int surfaceY, SurfaceBlockSelector.SurfaceLayer layer
   ) {
      MutableBlockPos currentPos = new MutableBlockPos();
      MutableBlockPos neighborPos = new MutableBlockPos();

      for (int depth = 0; depth < 3 && surfaceY - depth >= chunk.getMinBuildHeight(); depth++) {
         int y = surfaceY - depth;
         currentPos.set(worldX, y, worldZ);
         BlockState currentState = chunk.getBlockState(currentPos);
         if (currentState.is(Blocks.STONE) && isExposed(level, chunk, neighborPos, worldX, y, worldZ)) {
            chunk.setBlockState(currentPos, layer.requiredSurfaceStateAtDepth(depth), false);
         }
      }
   }

   private static boolean isExposed(WorldGenRegion level, ChunkAccess chunk, MutableBlockPos pos, int x, int y, int z) {
      ChunkPos chunkPos = chunk.getPos();
      int chunkMinX = chunkPos.getMinBlockX();
      int chunkMinZ = chunkPos.getMinBlockZ();
      int chunkMaxX = chunkMinX + 15;
      int chunkMaxZ = chunkMinZ + 15;
      pos.set(x, y + 1, z);
      if (isAir(chunk, level, pos, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ)) {
         return true;
      }

      pos.set(x + 1, y, z);
      if (isAir(chunk, level, pos, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ)) {
         return true;
      }

      pos.set(x - 1, y, z);
      if (isAir(chunk, level, pos, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ)) {
         return true;
      }

      pos.set(x, y, z + 1);
      if (isAir(chunk, level, pos, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ)) {
         return true;
      }

      pos.set(x, y, z - 1);
      return isAir(chunk, level, pos, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
   }

   private static boolean isAir(ChunkAccess chunk, WorldGenRegion level, BlockPos pos, int chunkMinX, int chunkMaxX, int chunkMinZ, int chunkMaxZ) {
      return pos.getX() >= chunkMinX && pos.getX() <= chunkMaxX && pos.getZ() >= chunkMinZ && pos.getZ() <= chunkMaxZ
         ? chunk.getBlockState(pos).isAir()
         : level.getBlockState(pos).isAir();
   }

   private static boolean hasNearbyTopSupport(SkyIslandChunkTerrainSnapshot snapshot, IslandField.ColumnProfile profile, int worldX, int worldZ, int surfaceY) {
      if (profile.family() != IslandField.IslandFamily.SPIRE && surfaceY - profile.bottomY() >= 12) {
         return true;
      }

      int supportScore = 0;
      int[][] offsets = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};

      for (int[] offset : offsets) {
         TerrainColumn neighbor = snapshot.terrainColumnAt(worldX + offset[0], worldZ + offset[1]);
         if (neighbor.exists()) {
            int heightDelta = Math.abs(neighbor.topY() - surfaceY);
            if (heightDelta <= 2) {
               supportScore += 2;
            } else if (heightDelta <= 4) {
               supportScore++;
            }

            if (supportScore >= 4) {
               return true;
            }
         }
      }

      return false;
   }

   record BlendTarget(StructureFootprint footprint, int baseY, int maxBlendGap) {
   }

   enum MonumentBlendAction {
      NONE,
      FILL,
      CUT;
   }

   record MonumentBlendTarget(StructureFootprint footprint, int baseY, int maxBlendGap, int maxCutDepth) {
   }

   record VillageBlendTarget(StructureFootprint footprint, int baseY, int maxBlendGap, int maxCutDepth) {
   }
}
