package org.sathrek.sky_archipelago.worldgen.structure.underground;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import org.sathrek.sky_archipelago.worldgen.structure.ResolvedStructureSupportPlane;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.StructureSupportContext;
import org.sathrek.sky_archipelago.worldgen.structure.StructureSupportPlaneResolver;
import org.sathrek.sky_archipelago.worldgen.structure.sky.SkyStructurePlacementTarget;

public final class UndergroundPlacementCoordinator {
   private static final ResourceLocation TRIAL_CHAMBERS_ID = ResourceLocation.withDefaultNamespace("trial_chambers");
   private static final ResourceLocation ANCIENT_CITY_ID = ResourceLocation.withDefaultNamespace("ancient_city");
   private static final ResourceLocation STRONGHOLD_ID = ResourceLocation.withDefaultNamespace("stronghold");
   private static final int DEFAULT_MAX_CANDIDATES = 1400;
   private static final int MAX_HOST_BUDGET_MULTIPLIER = 4;
   private final StructureSupportPlaneResolver supportPlaneResolver;

   public UndergroundPlacementCoordinator(StructureSupportPlaneResolver supportPlaneResolver) {
      this.supportPlaneResolver = supportPlaneResolver;
   }

   public UndergroundPlacementDecision decide(
      StructureSupportContext context, StructureStart structureStart, SkyStructurePlacementTarget islandAwareTarget, int worldMinY, int maxIslandSpawnY
   ) {
      ResolvedStructureSupportPlane supportPlane = this.supportPlaneResolver
         .resolve(context.structureId(), structureStart, context.settings().advanced().structurePlacementPolicy().footprintInsetRatioFor(context.structureId()))
         .orElse(null);
      StructureFootprint raw = supportPlane != null
         ? supportPlane.rawFootprint()
         : new StructureFootprint(
            structureStart.getBoundingBox().minX(),
            structureStart.getBoundingBox().maxX(),
            structureStart.getBoundingBox().minZ(),
            structureStart.getBoundingBox().maxZ()
         );
      StructureFootprint effective = supportPlane != null ? supportPlane.effectiveFootprint() : raw;
      int preferredX = islandAwareTarget != null ? islandAwareTarget.x() : raw.centerX();
      int preferredZ = islandAwareTarget != null ? islandAwareTarget.z() : raw.centerZ();
      int clampedMinY = Math.min(worldMinY, maxIslandSpawnY);
      int clampedMaxY = Math.max(worldMinY, maxIslandSpawnY);
      List<IslandField.IslandPreview> hosts = UndergroundHostSelection.selectHosts(
         context.structureId(), context.settings(), context.islandField(), preferredX, preferredZ
      );
      int anchorBaseY = supportPlane != null ? supportPlane.baseY() : structureStart.getBoundingBox().minY();
      long anchorSeedKey = mixAnchorSeed(context.structureId(), preferredX, preferredZ, anchorBaseY);
      if (hosts.isEmpty()) {
         return UndergroundPlacementDecision.rejected(
            "rejected_underground_no_host",
            raw,
            effective,
            supportPlane,
            anchorSeedKey,
            preferredX,
            preferredZ,
            0,
            0,
            0,
            clampedMinY,
            clampedMaxY,
            0,
            0,
            0,
            0,
            0,
            0,
            "none",
            0,
            0,
            0,
            0
         );
      }

      UndergroundPlacementCoordinator.UndergroundProfile profile = undergroundProfileFor(context.structureId());
      int perHostLatticeSize = latticeSize(profile);
      int effectiveBudget = effectiveBudget(profile, hosts.size(), perHostLatticeSize);
      List<UndergroundPlacementCoordinator.Candidate> evaluated = new ArrayList<>();
      int generated = 0;
      int pruned = 0;
      int samplesEvaluated = 0;
      int prunedNoColumnAtY = 0;
      int prunedOutsideHostDominance = 0;
      int prunedInsufficientOverburden = 0;
      int prunedInsufficientDepth = 0;
      int prunedInsufficientSupport = 0;
      int prunedInsufficientStone = 0;
      IslandField.IslandPreview selectedHost = hosts.get(0);
      Map<Long, TerrainColumn> columnCache = new HashMap<>();

      label85:
      for (UndergroundPlacementCoordinator.Offset3D offset : orderedOffsets(profile, anchorSeedKey)) {
         for (IslandField.IslandPreview host : hosts) {
            if (generated >= effectiveBudget) {
               break label85;
            }

            generated++;
            selectedHost = host;
            int candidateX = preferredX + offset.dx();
            int candidateZ = preferredZ + offset.dz();
            int baseY = anchorBaseY + offset.dy();
            if (baseY >= clampedMinY && baseY <= clampedMaxY) {
               UndergroundPlacementCoordinator.HardEval hard = evaluateHardConstraints(context, raw, host, candidateX, candidateZ, baseY, profile, columnCache);
               samplesEvaluated += hard.sampleCount;
               if (!hard.valid) {
                  pruned++;
                  switch (hard.pruneCause) {
                     case NONE:
                     default:
                        break;
                     case NO_COLUMN_AT_Y:
                        prunedNoColumnAtY++;
                        break;
                     case OUTSIDE_HOST_DOMINANCE:
                        prunedOutsideHostDominance++;
                        break;
                     case INSUFFICIENT_OVERBURDEN:
                        prunedInsufficientOverburden++;
                        break;
                     case INSUFFICIENT_DEPTH:
                        prunedInsufficientDepth++;
                        break;
                     case INSUFFICIENT_SUPPORT:
                        prunedInsufficientSupport++;
                        break;
                     case INSUFFICIENT_STONE:
                        prunedInsufficientStone++;
                  }
               } else {
                  int movement = Math.abs(offset.dx()) + Math.abs(offset.dz()) + Math.abs(offset.dy());
                  double score = scoreCandidate(hard, movement, profile);
                  evaluated.add(
                     new UndergroundPlacementCoordinator.Candidate(
                        host, offset.dx(), offset.dz(), offset.dy(), candidateX, candidateZ, baseY, movement, hard, score
                     )
                  );
               }
            } else {
               pruned++;
               prunedNoColumnAtY++;
            }
         }
      }

      if (evaluated.isEmpty()) {
         String reason = rejectionReasonFor(
            prunedNoColumnAtY,
            prunedOutsideHostDominance,
            prunedInsufficientOverburden,
            prunedInsufficientDepth,
            prunedInsufficientSupport,
            prunedInsufficientStone
         );
         return UndergroundPlacementDecision.rejected(
            reason,
            raw,
            effective,
            supportPlane,
            anchorSeedKey,
            preferredX,
            preferredZ,
            generated,
            pruned,
            0,
            clampedMinY,
            clampedMaxY,
            prunedNoColumnAtY,
            prunedOutsideHostDominance,
            prunedInsufficientOverburden,
            prunedInsufficientDepth,
            prunedInsufficientSupport,
            prunedInsufficientStone,
            selectedHost.family().name(),
            selectedHost.x(),
            selectedHost.y(),
            selectedHost.z(),
            selectedHost.radius()
         );
      } else {
         evaluated.sort(
            Comparator.<UndergroundPlacementCoordinator.Candidate, Boolean>comparing(c -> true)
               .reversed()
               .thenComparingDouble(c -> c.score)
               .reversed()
               .thenComparingInt(c -> c.movement)
               .thenComparingInt(c -> c.dx)
               .thenComparingInt(c -> c.dz)
               .thenComparingInt(c -> c.dy)
               .thenComparingInt(c -> context.structureId() == null ? 0 : context.structureId().hashCode())
         );
         UndergroundPlacementCoordinator.Candidate best = evaluated.get(0);
         SkyStructurePlacementTarget target = new SkyStructurePlacementTarget(
            best.x,
            best.baseY,
            best.z,
            anchorBaseY,
            best.dy,
            best.dx,
            best.dz,
            best.hard.supportCount,
            (int)Math.round(best.hard.stoneRatio * 100.0),
            best.hard.stoneRatio,
            profile.searchRadiusX(),
            best.host.family(),
            best.host.heightBand()
         );
         boolean moved = best.dx != 0 || best.dz != 0 || best.dy != 0;
         return new UndergroundPlacementDecision(
            true,
            null,
            true,
            moved,
            generated,
            pruned,
            evaluated.size(),
            samplesEvaluated,
            clampedMinY,
            clampedMaxY,
            best.hard.minOverburden,
            best.hard.minDepth,
            best.hard.exposureSamples,
            best.hard.supportCount,
            best.hard.stoneRatio,
            best.dy,
            prunedNoColumnAtY,
            prunedOutsideHostDominance,
            prunedInsufficientOverburden,
            prunedInsufficientDepth,
            prunedInsufficientSupport,
            prunedInsufficientStone,
            target,
            raw.translate(best.x - raw.centerX(), best.z - raw.centerZ()),
            effective.translate(best.x - raw.centerX(), best.z - raw.centerZ()),
            supportPlane,
            anchorSeedKey,
            preferredX,
            preferredZ,
            best.host.family().name(),
            best.host.x(),
            best.host.y(),
            best.host.z(),
            best.host.radius(),
            "burial="
               + format(best.hard.minOverburden)
               + ",depth="
               + format(best.hard.minDepth)
               + ",support="
               + best.hard.supportCount
               + ",stone="
               + format(best.hard.stoneRatio)
               + ",movement="
               + best.movement,
            "valid>score>move>spatial>structure"
         );
      }
   }

   private static double scoreCandidate(UndergroundPlacementCoordinator.HardEval hard, int movement, UndergroundPlacementCoordinator.UndergroundProfile profile) {
      double burialQuality = Math.min(1.0, (double)hard.minOverburden / Math.max(1, profile.minOverburden() * 2));
      double depthQuality = Math.min(1.0, (double)hard.minDepth / Math.max(1, profile.minDepthBelowLocalTop() * 2));
      double exposureQuality = 1.0 - (double)hard.exposureSamples / Math.max(1, profile.maxExposureSamples());
      double supportQuality = Math.min(1.0, (double)hard.supportCount / Math.max(1, hard.coreSamples));
      double stoneQuality = hard.stoneRatio;
      double movementPenalty = movement / 128.0;
      return burialQuality * 0.3 + depthQuality * 0.25 + exposureQuality * 0.15 + supportQuality * 0.15 + stoneQuality * 0.15 - movementPenalty;
   }

   private static UndergroundPlacementCoordinator.HardEval evaluateHardConstraints(
      StructureSupportContext context,
      StructureFootprint raw,
      IslandField.IslandPreview host,
      int centerX,
      int centerZ,
      int baseY,
      UndergroundPlacementCoordinator.UndergroundProfile profile,
      Map<Long, TerrainColumn> columnCache
   ) {
      StructureFootprint shiftedRaw = raw.translate(centerX - raw.centerX(), centerZ - raw.centerZ());
      StructureFootprint shiftedCore = shiftedRaw.insetByRatio(0.22);
      int sampleGrid = Math.max(4, context.settings().structureSupport().supportSampleGridSize());
      int coreGrid = Math.max(3, context.settings().structureSupport().supportSampleGridSize() - 1);
      int minOverburden = Integer.MAX_VALUE;
      int minDepth = Integer.MAX_VALUE;
      int exposure = 0;
      int support = 0;
      int coreSamples = 0;
      int stoneLike = 0;
      int rawSamples = 0;
      int sampleCount = 0;

      for (StructureFootprint.GridPoint point : shiftedRaw.sampleGrid(sampleGrid)) {
         rawSamples++;
         TerrainColumn column = sampleColumn(context, point.x(), point.z(), columnCache);
         sampleCount++;
         if (!column.exists() || !column.contains(baseY)) {
            return UndergroundPlacementCoordinator.HardEval.invalid(
               sampleCount,
               minOverburden,
               minDepth,
               exposure,
               support,
               coreSamples,
               stoneLike,
               rawSamples,
               UndergroundPlacementCoordinator.PruneCause.NO_COLUMN_AT_Y
            );
         }

         if (column.thickness() >= 8) {
            stoneLike++;
         }
      }

      for (StructureFootprint.GridPoint point : shiftedCore.sampleGrid(coreGrid)) {
         coreSamples++;
         TerrainColumn column = sampleColumn(context, point.x(), point.z(), columnCache);
         sampleCount++;
         if (!column.exists()) {
            return UndergroundPlacementCoordinator.HardEval.invalid(
               sampleCount,
               minOverburden,
               minDepth,
               exposure,
               support,
               coreSamples,
               stoneLike,
               rawSamples,
               UndergroundPlacementCoordinator.PruneCause.NO_COLUMN_AT_Y
            );
         }

         IslandField.IslandDescriptor dominant = context.islandField().sampleDominantIslandDescriptor(point.x(), point.z(), context.settings());
         if (dominant == null || dominant.centerX() != host.x() || dominant.centerZ() != host.z() || dominant.family() != host.family()) {
            return UndergroundPlacementCoordinator.HardEval.invalid(
               sampleCount,
               minOverburden,
               minDepth,
               exposure,
               support,
               coreSamples,
               stoneLike,
               rawSamples,
               UndergroundPlacementCoordinator.PruneCause.OUTSIDE_HOST_DOMINANCE
            );
         }

         int overburden = column.topY() - baseY;
         minOverburden = Math.min(minOverburden, overburden);
         minDepth = Math.min(minDepth, column.topY() - baseY);
         if (overburden < profile.minOverburden()) {
            if (++exposure > profile.maxExposureSamples()) {
               return UndergroundPlacementCoordinator.HardEval.invalid(
                  sampleCount,
                  minOverburden,
                  minDepth,
                  exposure,
                  support,
                  coreSamples,
                  stoneLike,
                  rawSamples,
                  UndergroundPlacementCoordinator.PruneCause.INSUFFICIENT_OVERBURDEN
               );
            }
         }

         if (context.islandField().hasSupportBelow(point.x(), point.z(), baseY, context.settings().structureSupport().supportCheckDepth(), context.settings())) {
            support++;
         }
      }

      double stoneRatio = rawSamples == 0 ? 0.0 : (double)stoneLike / rawSamples;
      if (minOverburden < profile.minOverburden()) {
         return UndergroundPlacementCoordinator.HardEval.invalid(
            sampleCount,
            minOverburden,
            minDepth,
            exposure,
            support,
            coreSamples,
            stoneLike,
            rawSamples,
            UndergroundPlacementCoordinator.PruneCause.INSUFFICIENT_OVERBURDEN
         );
      } else if (minDepth < profile.minDepthBelowLocalTop()) {
         return UndergroundPlacementCoordinator.HardEval.invalid(
            sampleCount,
            minOverburden,
            minDepth,
            exposure,
            support,
            coreSamples,
            stoneLike,
            rawSamples,
            UndergroundPlacementCoordinator.PruneCause.INSUFFICIENT_DEPTH
         );
      } else if (support < profile.minSupportSamples()) {
         return UndergroundPlacementCoordinator.HardEval.invalid(
            sampleCount,
            minOverburden,
            minDepth,
            exposure,
            support,
            coreSamples,
            stoneLike,
            rawSamples,
            UndergroundPlacementCoordinator.PruneCause.INSUFFICIENT_SUPPORT
         );
      } else {
         return stoneRatio < profile.minStoneRatio()
            ? UndergroundPlacementCoordinator.HardEval.invalid(
               sampleCount,
               minOverburden,
               minDepth,
               exposure,
               support,
               coreSamples,
               stoneLike,
               rawSamples,
               UndergroundPlacementCoordinator.PruneCause.INSUFFICIENT_STONE
            )
            : new UndergroundPlacementCoordinator.HardEval(
               true, sampleCount, minOverburden, minDepth, exposure, support, coreSamples, stoneRatio, UndergroundPlacementCoordinator.PruneCause.NONE
            );
      }
   }

   private static TerrainColumn sampleColumn(StructureSupportContext context, int x, int z, Map<Long, TerrainColumn> cache) {
      long key = (long)x << 32 ^ z & 4294967295L;
      return cache.computeIfAbsent(key, ignored -> context.islandField().sampleColumn(x, z, context.settings()));
   }

   private static String rejectionReasonFor(
      int prunedNoColumnAtY,
      int prunedOutsideHostDominance,
      int prunedInsufficientOverburden,
      int prunedInsufficientDepth,
      int prunedInsufficientSupport,
      int prunedInsufficientStone
   ) {
      if (prunedNoColumnAtY > 0
         && prunedNoColumnAtY >= prunedOutsideHostDominance
         && prunedNoColumnAtY >= prunedInsufficientOverburden
         && prunedNoColumnAtY >= prunedInsufficientDepth
         && prunedNoColumnAtY >= prunedInsufficientSupport
         && prunedNoColumnAtY >= prunedInsufficientStone) {
         return "rejected_underground_no_column_at_y";
      } else if (prunedOutsideHostDominance >= prunedInsufficientOverburden
         && prunedOutsideHostDominance >= prunedInsufficientDepth
         && prunedOutsideHostDominance >= prunedInsufficientSupport
         && prunedOutsideHostDominance >= prunedInsufficientStone) {
         return "rejected_underground_outside_host_dominance";
      } else if (prunedInsufficientOverburden >= prunedInsufficientDepth
         && prunedInsufficientOverburden >= prunedInsufficientSupport
         && prunedInsufficientOverburden >= prunedInsufficientStone) {
         return "rejected_underground_insufficient_overburden";
      } else if (prunedInsufficientDepth >= prunedInsufficientSupport && prunedInsufficientDepth >= prunedInsufficientStone) {
         return "rejected_underground_insufficient_depth";
      } else {
         return prunedInsufficientSupport >= prunedInsufficientStone ? "rejected_underground_insufficient_support" : "rejected_underground_insufficient_stone";
      }
   }

   private static UndergroundPlacementCoordinator.UndergroundProfile undergroundProfileFor(ResourceLocation structureId) {
      if (TRIAL_CHAMBERS_ID.equals(structureId)) {
         return new UndergroundPlacementCoordinator.UndergroundProfile(14, 12, 1, 8, 0.92, 40, 40, 28, 12, 4, 1400);
      } else {
         return !ANCIENT_CITY_ID.equals(structureId) && !STRONGHOLD_ID.equals(structureId)
            ? new UndergroundPlacementCoordinator.UndergroundProfile(12, 10, 1, 7, 0.9, 32, 32, 24, 12, 4, 1400)
            : new UndergroundPlacementCoordinator.UndergroundProfile(16, 12, 1, 10, 0.94, 48, 48, 32, 16, 4, 1400);
      }
   }

   private static List<UndergroundPlacementCoordinator.Offset3D> orderedOffsets(UndergroundPlacementCoordinator.UndergroundProfile profile, long anchorSeedKey) {
      int step = Math.max(1, profile.searchStepBlocks());
      List<UndergroundPlacementCoordinator.Offset3D> offsets = new ArrayList<>(latticeSize(profile));

      for (int dx = -profile.searchRadiusX(); dx <= profile.searchRadiusX(); dx += step) {
         for (int dz = -profile.searchRadiusZ(); dz <= profile.searchRadiusZ(); dz += step) {
            for (int dy = -profile.searchRadiusYDown(); dy <= profile.searchRadiusYUp(); dy += step) {
               int movement = Math.abs(dx) + Math.abs(dz) + Math.abs(dy);
               long tieBreak = mixedOffsetKey(anchorSeedKey, dx, dz, dy);
               offsets.add(new UndergroundPlacementCoordinator.Offset3D(dx, dz, dy, movement, tieBreak));
            }
         }
      }

      offsets.sort(
         Comparator.comparingInt(UndergroundPlacementCoordinator.Offset3D::movement).thenComparingLong(UndergroundPlacementCoordinator.Offset3D::tieBreak)
      );
      return offsets;
   }

   private static int latticeSize(UndergroundPlacementCoordinator.UndergroundProfile profile) {
      int step = Math.max(1, profile.searchStepBlocks());
      int xCount = axisCount(profile.searchRadiusX(), profile.searchRadiusX(), step);
      int zCount = axisCount(profile.searchRadiusZ(), profile.searchRadiusZ(), step);
      int yCount = axisCount(profile.searchRadiusYDown(), profile.searchRadiusYUp(), step);
      return Math.max(1, xCount * zCount * yCount);
   }

   private static int axisCount(int negativeRadius, int positiveRadius, int step) {
      int span = negativeRadius + positiveRadius;
      return span / step + 1;
   }

   private static int effectiveBudget(UndergroundPlacementCoordinator.UndergroundProfile profile, int hostCount, int perHostLatticeSize) {
      int clampedHostCount = Math.max(1, hostCount);
      int desiredHostMultiplier = Math.min(clampedHostCount, 4);
      int atLeastOneHost = Math.max(Math.max(1, profile.maxCandidates()), perHostLatticeSize);
      int fairHostBudget = perHostLatticeSize * desiredHostMultiplier;
      return Math.max(atLeastOneHost, fairHostBudget);
   }

   private static String format(double value) {
      return String.format(Locale.ROOT, "%.2f", value);
   }

   private static long mixAnchorSeed(ResourceLocation structureId, int x, int z, int y) {
      long hash = 1469598103934665603L;
      hash ^= structureId == null ? 0L : structureId.hashCode();
      hash *= 1099511628211L;
      hash ^= x;
      hash *= 1099511628211L;
      hash ^= z;
      hash *= 1099511628211L;
      hash ^= y;
      return hash * 1099511628211L;
   }

   private static long mixedOffsetKey(long seed, int dx, int dz, int dy) {
      long mixed = seed;
      mixed ^= Integer.hashCode(dx);
      mixed *= 1099511628211L;
      mixed ^= Integer.hashCode(dz);
      mixed *= 1099511628211L;
      mixed ^= Integer.hashCode(dy);
      mixed *= 1099511628211L;
      return mixed ^ mixed >>> 32;
   }

   private record Candidate(
      IslandField.IslandPreview host,
      int dx,
      int dz,
      int dy,
      int x,
      int z,
      int baseY,
      int movement,
      UndergroundPlacementCoordinator.HardEval hard,
      double score
   ) {
   }

   private record HardEval(
      boolean valid,
      int sampleCount,
      int minOverburden,
      int minDepth,
      int exposureSamples,
      int supportCount,
      int coreSamples,
      double stoneRatio,
      UndergroundPlacementCoordinator.PruneCause pruneCause
   ) {
      static UndergroundPlacementCoordinator.HardEval invalid(
         int sampleCount,
         int minOverburden,
         int minDepth,
         int exposureSamples,
         int supportCount,
         int coreSamples,
         int stoneLike,
         int rawSamples,
         UndergroundPlacementCoordinator.PruneCause cause
      ) {
         double stoneRatio = rawSamples <= 0 ? 0.0 : (double)stoneLike / rawSamples;
         return new UndergroundPlacementCoordinator.HardEval(
            false,
            sampleCount,
            minOverburden == Integer.MAX_VALUE ? 0 : minOverburden,
            minDepth == Integer.MAX_VALUE ? 0 : minDepth,
            exposureSamples,
            supportCount,
            coreSamples,
            stoneRatio,
            cause
         );
      }
   }

   private record Offset3D(int dx, int dz, int dy, int movement, long tieBreak) {
   }

   private enum PruneCause {
      NONE,
      NO_COLUMN_AT_Y,
      OUTSIDE_HOST_DOMINANCE,
      INSUFFICIENT_OVERBURDEN,
      INSUFFICIENT_DEPTH,
      INSUFFICIENT_SUPPORT,
      INSUFFICIENT_STONE;
   }

   private record UndergroundProfile(
      int minOverburden,
      int minDepthBelowLocalTop,
      int maxExposureSamples,
      int minSupportSamples,
      double minStoneRatio,
      int searchRadiusX,
      int searchRadiusZ,
      int searchRadiusYDown,
      int searchRadiusYUp,
      int searchStepBlocks,
      int maxCandidates
   ) {
   }
}
