package org.sathrek.sky_archipelago.worldgen.generator.core;

import com.google.common.collect.Sets;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.GenerationStep.Carving;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;
import org.sathrek.sky_archipelago.SkyArchipelago;
import org.sathrek.sky_archipelago.config.OceanBlockType;
import org.sathrek.sky_archipelago.config.SkyIslandConfig;
import org.sathrek.sky_archipelago.config.SkyIslandGeneratorSettings;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.BiomeTerrainProfileResolver;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.structure.SkyIslandStructurePlacementPass;
import org.sathrek.sky_archipelago.worldgen.generator.surface.SkyIslandSurfacePass;
import org.sathrek.sky_archipelago.worldgen.generator.terrain.SkyIslandTerrainPass;

public final class SkyIslandChunkGenerator extends NoiseBasedChunkGenerator {
   public static final MapCodec<SkyIslandChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoiseBasedChunkGenerator::generatorSettings),
            SkyIslandGeneratorSettings.CODEC.optionalFieldOf("sky_island_settings").forGetter(SkyIslandChunkGenerator::embeddedSettings)
         )
         .apply(instance, SkyIslandChunkGenerator::new)
   );
   private static final ResourceLocation LAYOUT_RANDOM_ID = SkyArchipelago.id("sky_island_layout");
   private final Optional<SkyIslandGeneratorSettings> embeddedSettings;
   private final Map<SkyIslandChunkGenerator.IslandFieldCacheKey, IslandField> islandFieldCache = new ConcurrentHashMap<>();

   public SkyIslandChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
      this(biomeSource, settings, Optional.empty());
   }

   public SkyIslandChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings, Optional<SkyIslandGeneratorSettings> embeddedSettings) {
      super(biomeSource, settings);
      this.embeddedSettings = embeddedSettings;
   }

   @NotNull
   protected MapCodec<? extends NoiseBasedChunkGenerator> codec() {
      return CODEC;
   }

   @NotNull
   public CompletableFuture<ChunkAccess> fillFromNoise(
      @NotNull Blender blender, @NotNull RandomState randomState, @NotNull StructureManager structureManager, @NotNull ChunkAccess chunk
   ) {
      return CompletableFuture.supplyAsync(Util.wrapThreadWithTaskName("sky_island_fill_noise", () -> {
         Set<LevelChunkSection> lockedSections = Sets.newHashSet();

         for (LevelChunkSection section : chunk.getSections()) {
            section.acquire();
            lockedSections.add(section);
         }

         try {
            SkyIslandSettings settings = this.effectiveSettingsForMaxBuildHeight(chunk.getMaxBuildHeight());
            SkyIslandTerrainPass.fillChunk(chunk, settings, this.getIslandField(randomState), this.defaultStoneBlock(), this.oceanFluidState(settings));
            return chunk;
         } finally {
            for (LevelChunkSection section : lockedSections) {
               section.release();
            }
         }
      }), Util.backgroundExecutor());
   }

   public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
      SkyIslandSettings settings = this.effectiveSettingsForMaxBuildHeight(chunk.getMaxBuildHeight());
      SkyIslandSurfacePass.buildSurface(level, structureManager, chunk, settings, this.getIslandField(randomState));
   }

   public void applyCarvers(
      WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, Carving carvingStep
   ) {
      super.applyCarvers(level, seed, random, biomeManager, structureManager, chunk, carvingStep);
      SkyIslandSettings settings = this.effectiveSettingsForMaxBuildHeight(chunk.getMaxBuildHeight());
      if (settings.terrain().ocean().oceanEnabled()) {
         SkyIslandTerrainPass.enforceOceanLayer(chunk, settings, this.getIslandField(random), this.defaultStoneBlock(), this.oceanFluidState(settings));
      }
   }

   public int getSpawnHeight(LevelHeightAccessor level) {
      SkyIslandSettings settings = this.effectiveSettingsForMaxBuildHeight(level.getMaxBuildHeight());
      return Math.min(level.getMaxBuildHeight() - 1, settings.spawnSearchTopY());
   }

   public int getBaseHeight(int x, int z, Types type, LevelHeightAccessor level, RandomState randomState) {
      SkyIslandSettings settings = this.effectiveSettingsForMaxBuildHeight(level.getMaxBuildHeight());
      return SkyIslandTerrainPass.baseHeightAt(this.getIslandField(randomState), x, z, level.getMinBuildHeight(), level.getMaxBuildHeight(), settings);
   }

   public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
      SkyIslandSettings settings = this.effectiveSettingsForMaxBuildHeight(heightAccessor.getMaxBuildHeight());
      return SkyIslandTerrainPass.baseColumnAt(
         this.getIslandField(randomState),
         x,
         z,
         heightAccessor.getMinBuildHeight(),
         heightAccessor.getMaxBuildHeight(),
         settings,
         this.defaultStoneBlock(),
         this.oceanFluidState(settings)
      );
   }

   public int getSeaLevel() {
      SkyIslandSettings settings = this.resolvedSkyIslandSettings();
      return settings.terrain().ocean().oceanEnabled() ? settings.terrain().ocean().oceanLevelY() : this.getMinY();
   }

   public void createStructures(
      RegistryAccess registryAccess,
      ChunkGeneratorStructureState structureState,
      StructureManager structureManager,
      ChunkAccess chunk,
      StructureTemplateManager templateManager
   ) {
      SkyIslandSettings settings = this.effectiveSettingsForMaxBuildHeight(chunk.getMaxBuildHeight());
      SkyIslandStructurePlacementPass.createStructures(
         this,
         this.biomeSource,
         registryAccess,
         structureState,
         structureManager,
         chunk,
         templateManager,
         settings,
         this.getIslandField(structureState.randomState())
      );
   }

   public WeightedRandomList<SpawnerData> getMobsAt(Holder<Biome> biome, StructureManager structureManager, MobCategory mobCategory, BlockPos pos) {
      return super.getMobsAt(biome, structureManager, mobCategory, pos);
   }

   private IslandField getIslandField(RandomState randomState) {
      long layoutSeed = randomState.getOrCreateRandomFactory(LAYOUT_RANDOM_ID).at(0, 0, 0).nextLong();
      SkyIslandSettings settings = this.resolvedSkyIslandSettings();
      SkyIslandChunkGenerator.IslandFieldCacheKey cacheKey = new SkyIslandChunkGenerator.IslandFieldCacheKey(
         layoutSeed,
         settings.advanced().biomeProfileBlendingEnabled(),
         settings.advanced().biomeProfileBlendingRadiusBlocks(),
         settings.advanced().biomeProfileBlendingQuantizationSteps(),
         settings.advanced().biomeProfileBlendingBoundaryOnly()
      );
      return this.islandFieldCache
         .computeIfAbsent(
            cacheKey,
            ignored -> {
               BiomeTerrainProfileResolver resolver = new BiomeTerrainProfileResolver(
                  settings.advanced().biomeProfileBlendingEnabled(),
                  settings.advanced().biomeProfileBlendingRadiusBlocks(),
                  settings.advanced().biomeProfileBlendingQuantizationSteps(),
                  settings.advanced().biomeProfileBlendingBoundaryOnly(),
                  pos -> this.biomeSource
                     .getNoiseBiome(QuartPos.fromBlock(pos.x()), QuartPos.fromBlock(pos.y()), QuartPos.fromBlock(pos.z()), randomState.sampler())
               );
               return new IslandField(layoutSeed, (x, y, z) -> {
                  BiomeTerrainProfileResolver.Resolution resolution = resolver.resolve(x, y, z);
                  Holder<Biome> biome = this.biomeSource
                     .getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), randomState.sampler());
                  return new IslandField.BiomeSample(resolution.terrainProfile(), biome.is(BiomeTags.IS_OCEAN));
               });
            }
         );
   }

   public boolean isAnchorLikeSpawnCandidate(RandomState randomState, int x, int z, int maxBuildHeightExclusive) {
      SkyIslandSettings settings = this.effectiveSettingsForMaxBuildHeight(maxBuildHeightExclusive);
      IslandField.ColumnProfile profile = this.getIslandField(randomState).sampleColumnProfile(x, z, settings);
      return profile.exists() && profile.family() == IslandField.IslandFamily.ANCHOR_PLATEAU && profile.topCap();
   }

   public BlockPos resolveAnchorSpawnPos(RandomState randomState, int x, int z, int maxBuildHeightExclusive) {
      SkyIslandSettings settings = this.effectiveSettingsForMaxBuildHeight(maxBuildHeightExclusive);
      IslandField.ColumnProfile profile = this.getIslandField(randomState).sampleColumnProfile(x, z, settings);
      if (profile.exists() && profile.family() == IslandField.IslandFamily.ANCHOR_PLATEAU && profile.topCap()) {
         int feetY = profile.topY() + 1;
         return settings.terrain().ocean().oceanEnabled() && feetY <= settings.terrain().ocean().oceanLevelY() ? null : new BlockPos(x, feetY, z);
      } else {
         return null;
      }
   }

   BlockState defaultStoneBlock() {
      return ((NoiseGeneratorSettings)this.generatorSettings().value()).defaultBlock();
   }

   private BlockState oceanFluidState(SkyIslandSettings settings) {
      OceanBlockType oceanBlockType = settings.advanced().oceanBlockType();
      if (oceanBlockType == OceanBlockType.WATER) {
         BlockState defaultFluid = ((NoiseGeneratorSettings)this.generatorSettings().value()).defaultFluid();
         if (defaultFluid.getFluidState().is(Fluids.WATER)) {
            return defaultFluid;
         }
      }

      return oceanBlockType.blockState();
   }

   public Optional<SkyIslandGeneratorSettings> embeddedSettings() {
      return this.embeddedSettings;
   }

   public SkyIslandGeneratorSettings activeGeneratorSettings() {
      return this.embeddedSettings.isPresent() && !SkyIslandConfig.defaultGeneratorSettings().equals(this.embeddedSettings.get())
         ? this.embeddedSettings.get()
         : SkyIslandGeneratorSettings.fromRuntimeSettings(this.resolvedSkyIslandSettings());
   }

   public SkyIslandChunkGenerator withSkyIslandSettings(SkyIslandGeneratorSettings settings) {
      return new SkyIslandChunkGenerator(this.biomeSource, this.generatorSettings(), Optional.of(settings));
   }

   public SkyIslandSettings resolvedSkyIslandSettings() {
      return SkyIslandConfig.resolveForGenerator(this.embeddedSettings);
   }

   private SkyIslandSettings effectiveSettingsForMaxBuildHeight(int maxBuildHeightExclusive) {
      SkyIslandSettings configured = this.resolvedSkyIslandSettings();
      WorldHeightClampResolver.logLegacyHeightNoticeIfNeeded(configured);
      SkyIslandSettings clamped = WorldHeightClampResolver.clampSettingsToWorld(configured, maxBuildHeightExclusive);
      WorldHeightClampResolver.logWorldClampIfNeeded(configured, clamped, maxBuildHeightExclusive);
      return clamped;
   }

   private record IslandFieldCacheKey(
      long layoutSeed,
      boolean biomeProfileBlendingEnabled,
      int biomeProfileBlendingRadiusBlocks,
      int biomeProfileBlendingQuantizationSteps,
      boolean biomeProfileBlendingBoundaryOnly
   ) {
   }
}
