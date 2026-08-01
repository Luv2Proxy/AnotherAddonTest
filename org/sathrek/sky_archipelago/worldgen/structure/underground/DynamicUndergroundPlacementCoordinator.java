package org.sathrek.sky_archipelago.worldgen.structure.underground;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import org.sathrek.sky_archipelago.worldgen.structure.AnchorResolverStrategy;
import org.sathrek.sky_archipelago.worldgen.structure.StructureSupportContext;
import org.sathrek.sky_archipelago.worldgen.structure.mineshafts.MineshaftPlacementDecision;
import org.sathrek.sky_archipelago.worldgen.structure.sky.SkyStructurePlacementTarget;

public final class DynamicUndergroundPlacementCoordinator {
   private static final ResourceLocation MINESHAFT_ID = ResourceLocation.withDefaultNamespace("mineshaft");
   private static final ResourceLocation TRIAL_CHAMBERS_ID = ResourceLocation.withDefaultNamespace("trial_chambers");
   private static final int DEFAULT_MAX_CANDIDATES = 2400;
   private static final int MAX_DYNAMIC_SAMPLES_EVALUATED = 60000;
   private static final int MAX_CONSECUTIVE_REJECTED_CANDIDATES = 250;
   private static final int SAMPLE_RADIUS_BLOCKS = 6;
   private static final int SAMPLE_STEP_BLOCKS = 3;
   private final AnchorResolverStrategy mineshaftAnchorStrategy;
   private final AnchorResolverStrategy jigsawAnchorStrategy;

   public DynamicUndergroundPlacementCoordinator(AnchorResolverStrategy mineshaftAnchorStrategy, AnchorResolverStrategy jigsawAnchorStrategy) {
      this.mineshaftAnchorStrategy = mineshaftAnchorStrategy;
      this.jigsawAnchorStrategy = jigsawAnchorStrategy;
   }

   public MineshaftPlacementDecision decide(
      StructureSupportContext context,
      StructureStart structureStart,
      SkyStructurePlacementTarget islandAwareTarget,
      int worldMinY,
      int maxIslandSpawnY,
      boolean jigsawAnchorMode
   ) {
      AnchorResolverStrategy.DynamicAnchor anchor = (jigsawAnchorMode ? this.jigsawAnchorStrategy : this.mineshaftAnchorStrategy).resolve(structureStart);
      int preferredX = islandAwareTarget != null ? islandAwareTarget.x() : anchor.x();
      int preferredZ = islandAwareTarget != null ? islandAwareTarget.z() : anchor.z();
      int anchorBaseY = anchor.baseY();
      int clampedMinY = Math.min(worldMinY, maxIslandSpawnY);
      int clampedMaxY = Math.max(worldMinY, maxIslandSpawnY);
      long anchorSeedKey = mixAnchorSeed(context.structureId(), preferredX, preferredZ, anchorBaseY);
      DynamicUndergroundPlacementCoordinator.Profile profile = profileFor(context.structureId());
      int effectiveMinY = profile.anchorWindowDownBlocks() > 0 ? clamp(anchorBaseY - profile.anchorWindowDownBlocks(), clampedMinY, clampedMaxY) : clampedMinY;
      int effectiveMaxY = profile.anchorWindowUpBlocks() > 0 ? clamp(anchorBaseY + profile.anchorWindowUpBlocks(), clampedMinY, clampedMaxY) : clampedMaxY;
      if (effectiveMinY > effectiveMaxY) {
         effectiveMinY = effectiveMaxY = clamp(anchorBaseY, clampedMinY, clampedMaxY);
      }

      List<IslandField.IslandPreview> hosts = UndergroundHostSelection.selectHosts(
         context.structureId(), context.settings(), context.islandField(), preferredX, preferredZ
      );
      if (hosts.isEmpty()) {
         return MineshaftPlacementDecision.rejected(
            "rejected_dynamic_underground_no_host",
            anchor.source(),
            anchorBaseY,
            clampedMinY,
            clampedMaxY,
            effectiveMinY,
            effectiveMaxY,
            profile.hostMultiplierCap(),
            anchorSeedKey,
            preferredX,
            preferredZ,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            "none",
            "none",
            0,
            0,
            0,
            0
         );
      }

      int perHostLattice = latticeSize(profile, effectiveMinY, effectiveMaxY);
      int effectiveBudget = Math.max(Math.max(profile.maxCandidates(), perHostLattice), perHostLattice * Math.min(profile.hostMultiplierCap(), hosts.size()));
      List<DynamicUndergroundPlacementCoordinator.Offset2D> offsets = orderedOffsets(profile, anchorSeedKey);
      List<Integer> candidateYs = orderedCandidateYs(effectiveMinY, effectiveMaxY, anchorSeedKey, anchorBaseY);
      Map<Long, TerrainColumn> columnCache = new HashMap<>();
      List<DynamicUndergroundPlacementCoordinator.Candidate> evaluated = new ArrayList<>();
      int generated = 0;
      int pruned = 0;
      int samplesEvaluated = 0;
      int prunedNoColumnAtY = 0;
      int prunedOutsideHostDominance = 0;
      int prunedInsufficientOverburden = 0;
      int prunedInsufficientSupport = 0;
      int prunedInsufficientStone = 0;
      int consecutiveRejectedCandidates = 0;
      IslandField.IslandPreview selectedHost = hosts.get(0);
      String earlyStopReason = "none";

      label130:
      for (DynamicUndergroundPlacementCoordinator.Offset2D offset : offsets) {
         for (int candidateY : candidateYs) {
            for (IslandField.IslandPreview host : hosts) {
               if (generated >= effectiveBudget) {
                  break label130;
               }

               generated++;
               selectedHost = host;
               int candidateX = preferredX + offset.dx();
               int candidateZ = preferredZ + offset.dz();
               int baseY = candidateY;
               int nearestIslandTop = nearestIslandTopY(context, host, candidateX, candidateZ, columnCache);
               if (profile.minBelowLocalTopHard() > 0 && baseY > nearestIslandTop - profile.minBelowLocalTopHard()) {
                  pruned++;
                  prunedInsufficientOverburden++;
                  if (++consecutiveRejectedCandidates >= 250) {
                     earlyStopReason = "consecutive_reject_limit";
                     break label130;
                  }
               } else {
                  double interiorMargin = interiorMargin(host, candidateX, candidateZ);
                  if (profile.hardMinimumInteriorMargin() > 0.0 && interiorMargin < profile.hardMinimumInteriorMargin()) {
                     pruned++;
                     prunedOutsideHostDominance++;
                     if (++consecutiveRejectedCandidates >= 250) {
                        earlyStopReason = "consecutive_reject_limit";
                        break label130;
                     }
                  } else {
                     DynamicUndergroundPlacementCoordinator.HardEval hard = evaluateLocalAnchor(
                        context, host, candidateX, candidateZ, baseY, profile, columnCache
                     );
                     samplesEvaluated += hard.sampleCount();
                     if (samplesEvaluated >= 60000) {
                        earlyStopReason = "sample_budget_exhausted";
                        break label130;
                     }

                     if (!hard.valid()) {
                        pruned++;
                        if (++consecutiveRejectedCandidates >= 250) {
                           earlyStopReason = "consecutive_reject_limit";
                           break label130;
                        }

                        switch (hard.pruneCause()) {
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
                           case INSUFFICIENT_SUPPORT:
                              prunedInsufficientSupport++;
                              break;
                           case INSUFFICIENT_STONE:
                              prunedInsufficientStone++;
                        }
                     } else if (profile.requireTranslatedBoundsContainment()
                        && structureStart != null
                        && structureStart.isValid()
                        && !isTranslatedBoundsContained(context, structureStart, host, offset.dx(), offset.dz())) {
                        pruned++;
                        prunedOutsideHostDominance++;
                        if (++consecutiveRejectedCandidates >= 250) {
                           earlyStopReason = "consecutive_reject_limit";
                           break label130;
                        }
                     } else {
                        int movement = Math.abs(offset.dx()) + Math.abs(offset.dz());
                        double score = scoreCandidate(host, hard, movement, baseY, nearestIslandTop, candidateX, candidateZ, profile);
                        DynamicUndergroundPlacementCoordinator.Candidate accepted = new DynamicUndergroundPlacementCoordinator.Candidate(
                           host, offset.dx(), offset.dz(), baseY, candidateX, candidateZ, movement, hard, score
                        );
                        evaluated.add(accepted);
                        consecutiveRejectedCandidates = 0;
                        if (passesEarlyStop(accepted, profile, interiorMargin)) {
                           earlyStopReason = "high_quality_candidate";
                           break label130;
                        }
                     }
                  }
               }
            }
         }
      }

      if (evaluated.isEmpty()) {
         return MineshaftPlacementDecision.rejected(
            "rejected_dynamic_underground_no_anchor_fit",
            anchor.source(),
            anchorBaseY,
            clampedMinY,
            clampedMaxY,
            effectiveMinY,
            effectiveMaxY,
            profile.hostMultiplierCap(),
            anchorSeedKey,
            preferredX,
            preferredZ,
            generated,
            pruned,
            0,
            samplesEvaluated,
            prunedNoColumnAtY,
            prunedOutsideHostDominance,
            prunedInsufficientOverburden,
            prunedInsufficientSupport,
            prunedInsufficientStone,
            earlyStopReason,
            selectedHost.family().name(),
            selectedHost.x(),
            selectedHost.y(),
            selectedHost.z(),
            selectedHost.radius()
         );
      }

      evaluated.sort(
         Comparator.<DynamicUndergroundPlacementCoordinator.Candidate, Boolean>comparing(c -> true)
            .reversed()
            .thenComparingDouble(c -> c.score())
            .reversed()
            .thenComparingInt(DynamicUndergroundPlacementCoordinator.Candidate::movement)
            .thenComparingInt(DynamicUndergroundPlacementCoordinator.Candidate::dx)
            .thenComparingInt(DynamicUndergroundPlacementCoordinator.Candidate::dz)
            .thenComparingInt(DynamicUndergroundPlacementCoordinator.Candidate::baseY)
            .thenComparingInt(c -> context.structureId() == null ? 0 : context.structureId().hashCode())
      );
      DynamicUndergroundPlacementCoordinator.Candidate best = evaluated.get(0);
      int topY = nearestIslandTopY(context, best.host(), best.x(), best.z(), columnCache);
      SkyStructurePlacementTarget target = new SkyStructurePlacementTarget(
         best.x(),
         best.baseY(),
         best.z(),
         topY,
         best.baseY() - topY,
         best.dx(),
         best.dz(),
         best.hard().supportCount(),
         (int)Math.round(best.hard().stoneRatio() * 100.0),
         best.hard().stoneRatio(),
         profile.searchRadiusX(),
         best.host().family(),
         best.host().heightBand()
      );
      boolean moved = best.dx() != 0 || best.dz() != 0 || best.baseY() != anchorBaseY;
      return new MineshaftPlacementDecision(
         true,
         null,
         true,
         moved,
         generated,
         pruned,
         evaluated.size(),
         samplesEvaluated,
         best.hard().minOverburden(),
         best.hard().supportCount(),
         best.hard().stoneRatio(),
         best.baseY() - anchorBaseY,
         anchor.source(),
         anchorBaseY,
         clampedMinY,
         clampedMaxY,
         effectiveMinY,
         effectiveMaxY,
         profile.hostMultiplierCap(),
         prunedNoColumnAtY,
         prunedOutsideHostDominance,
         prunedInsufficientOverburden,
         prunedInsufficientSupport,
         prunedInsufficientStone,
         earlyStopReason,
         target,
         anchorSeedKey,
         preferredX,
         preferredZ,
         best.host().family().name(),
         best.host().x(),
         best.host().y(),
         best.host().z(),
         best.host().radius(),
         "burial="
            + format(best.hard().minOverburden())
            + ",support="
            + best.hard().supportCount()
            + ",stone="
            + format(best.hard().stoneRatio())
            + ",movement="
            + best.movement(),
         "valid>score>move>spatial>structure"
      );
   }

   private static DynamicUndergroundPlacementCoordinator.HardEval evaluateLocalAnchor(
      StructureSupportContext context,
      IslandField.IslandPreview host,
      int centerX,
      int centerZ,
      int baseY,
      DynamicUndergroundPlacementCoordinator.Profile profile,
      Map<Long, TerrainColumn> cache
   ) {
      int minOverburden = Integer.MAX_VALUE;
      int support = 0;
      int stoneLike = 0;
      int sampleCount = 0;
      int total = 0;

      for (int dx = -6; dx <= 6; dx += 3) {
         int dz = -6;

         while (dz <= 6) {
            int x = centerX + dx;
            int z = centerZ + dz;
            total++;
            TerrainColumn column = sampleColumn(context, x, z, cache);
            sampleCount++;
            if (column.exists() && column.contains(baseY)) {
               IslandField.IslandDescriptor dominant = context.islandField().sampleDominantIslandDescriptor(x, z, context.settings());
               if (dominant != null && dominant.centerX() == host.x() && dominant.centerZ() == host.z() && dominant.family() == host.family()) {
                  int overburden = column.topY() - baseY;
                  minOverburden = Math.min(minOverburden, overburden);
                  if (column.thickness() >= profile.minStoneThickness()) {
                     stoneLike++;
                  }

                  if (context.islandField().hasSupportBelow(x, z, baseY, context.settings().structureSupport().supportCheckDepth(), context.settings())) {
                     support++;
                  }

                  dz += 3;
                  continue;
               }

               return DynamicUndergroundPlacementCoordinator.HardEval.invalid(
                  sampleCount, minOverburden, support, stoneLike, total, DynamicUndergroundPlacementCoordinator.PruneCause.OUTSIDE_HOST_DOMINANCE
               );
            }

            return DynamicUndergroundPlacementCoordinator.HardEval.invalid(
               sampleCount, minOverburden, support, stoneLike, total, DynamicUndergroundPlacementCoordinator.PruneCause.NO_COLUMN_AT_Y
            );
         }
      }

      double stoneRatio = total <= 0 ? 0.0 : (double)stoneLike / total;
      if (minOverburden < profile.minOverburden()) {
         return DynamicUndergroundPlacementCoordinator.HardEval.invalid(
            sampleCount, minOverburden, support, stoneLike, total, DynamicUndergroundPlacementCoordinator.PruneCause.INSUFFICIENT_OVERBURDEN
         );
      } else if (support < profile.minSupportSamples()) {
         return DynamicUndergroundPlacementCoordinator.HardEval.invalid(
            sampleCount, minOverburden, support, stoneLike, total, DynamicUndergroundPlacementCoordinator.PruneCause.INSUFFICIENT_SUPPORT
         );
      } else {
         return stoneRatio < profile.minStoneRatio()
            ? DynamicUndergroundPlacementCoordinator.HardEval.invalid(
               sampleCount, minOverburden, support, stoneLike, total, DynamicUndergroundPlacementCoordinator.PruneCause.INSUFFICIENT_STONE
            )
            : new DynamicUndergroundPlacementCoordinator.HardEval(
               true, sampleCount, minOverburden, support, stoneRatio, DynamicUndergroundPlacementCoordinator.PruneCause.NONE
            );
      }
   }

   private static TerrainColumn sampleColumn(StructureSupportContext context, int x, int z, Map<Long, TerrainColumn> cache) {
      long key = (long)x << 32 ^ z & 4294967295L;
      return cache.computeIfAbsent(key, ignored -> context.islandField().sampleColumn(x, z, context.settings()));
   }

   private static int nearestIslandTopY(
      StructureSupportContext context, IslandField.IslandPreview host, int centerX, int centerZ, Map<Long, TerrainColumn> cache
   ) {
      TerrainColumn hostCenter = sampleColumn(context, host.x(), host.z(), cache);
      TerrainColumn local = sampleColumn(context, centerX, centerZ, cache);
      if (local.exists()) {
         return local.topY();
      } else {
         return hostCenter.exists() ? hostCenter.topY() : host.y();
      }
   }

   private static double scoreCandidate(
      IslandField.IslandPreview host,
      DynamicUndergroundPlacementCoordinator.HardEval hard,
      int movement,
      int baseY,
      int nearestIslandTopY,
      int candidateX,
      int candidateZ,
      DynamicUndergroundPlacementCoordinator.Profile profile
   ) {
      double burialQuality = Math.min(1.0, hard.minOverburden() / Math.max(1.0, profile.minOverburden() * profile.burialTargetMultiplier()));
      double supportQuality = Math.min(1.0, (double)hard.supportCount() / Math.max(1, profile.minSupportSamples() * 2));
      double stoneQuality = hard.stoneRatio();
      double movementPenalty = movement / 128.0;
      double yDistancePenalty = Math.abs(baseY - nearestIslandTopY) / 256.0 * profile.yDistancePenaltyWeight();
      double edgePenalty = edgeSoftCapPenalty(host, candidateX, candidateZ, profile);
      return burialQuality * profile.burialWeight()
         + supportQuality * profile.supportWeight()
         + stoneQuality * profile.stoneWeight()
         - movementPenalty
         - yDistancePenalty
         - edgePenalty;
   }

   private static boolean passesEarlyStop(
      DynamicUndergroundPlacementCoordinator.Candidate candidate, DynamicUndergroundPlacementCoordinator.Profile profile, double interiorMargin
   ) {
      return !profile.earlyStopEnabled()
         ? false
         : candidate.hard().minOverburden() >= profile.earlyStopOverburden()
            && candidate.hard().supportCount() >= profile.earlyStopSupport()
            && candidate.hard().stoneRatio() >= profile.earlyStopStoneRatio()
            && candidate.movement() <= profile.earlyStopMovement()
            && interiorMargin >= profile.earlyStopMinInteriorMargin();
   }

   static double edgeSoftCapPenalty(IslandField.IslandPreview host, int candidateX, int candidateZ, DynamicUndergroundPlacementCoordinator.Profile profile) {
      return edgeSoftCapPenalty(host, candidateX, candidateZ, profile.preferredInteriorMargin(), profile.edgePenaltyWeight());
   }

   static double edgeSoftCapPenalty(IslandField.IslandPreview host, int candidateX, int candidateZ, double preferredInteriorMargin, double edgePenaltyWeight) {
      int radius = Math.max(1, host.radius());
      double interiorMargin = interiorMargin(host, candidateX, candidateZ);
      if (interiorMargin >= preferredInteriorMargin) {
         return 0.0;
      }

      double deficit = preferredInteriorMargin - interiorMargin;
      double normalizedDeficit = Math.max(0.0, deficit) / Math.max(1.0, preferredInteriorMargin);
      return normalizedDeficit * edgePenaltyWeight;
   }

   private static double interiorMargin(IslandField.IslandPreview host, int x, int z) {
      int radius = Math.max(1, host.radius());
      double dx = x - host.x();
      double dz = z - host.z();
      double radialDistance = Math.sqrt(dx * dx + dz * dz);
      return radius - radialDistance;
   }

   private static DynamicUndergroundPlacementCoordinator.Profile profileFor(ResourceLocation structureId) {
      if (MINESHAFT_ID.equals(structureId)) {
         return new DynamicUndergroundPlacementCoordinator.Profile(
            8, 8, 0.7, 32, 32, 4, 2400, 20.0, 0.35, 0, 0, 0.0, true, 16, 20, 0.95, 10.0, 4, 0.4, 0.3, 0.3, 2.0, 1.0, false
         );
      } else {
         return TRIAL_CHAMBERS_ID.equals(structureId)
            ? new DynamicUndergroundPlacementCoordinator.Profile(
               18, 8, 0.8, 40, 40, 4, 2400, 28.0, 0.7, 72, 8, 12.0, false, 24, 23, 0.95, 20.0, 2, 0.55, 0.25, 0.2, 2.4, 0.0, true
            )
            : new DynamicUndergroundPlacementCoordinator.Profile(
               10, 8, 0.75, 32, 32, 4, 2400, 28.0, 0.45, 40, 40, 0.0, true, 18, 22, 0.95, 12.0, 2, 0.4, 0.3, 0.3, 2.0, 1.0, false
            );
      }
   }

   private static List<DynamicUndergroundPlacementCoordinator.Offset2D> orderedOffsets(DynamicUndergroundPlacementCoordinator.Profile profile, long seed) {
      int step = Math.max(1, profile.searchStepBlocks());
      List<DynamicUndergroundPlacementCoordinator.Offset2D> offsets = new ArrayList<>(xyLatticeSize(profile));

      for (int dx = -profile.searchRadiusX(); dx <= profile.searchRadiusX(); dx += step) {
         for (int dz = -profile.searchRadiusZ(); dz <= profile.searchRadiusZ(); dz += step) {
            int movement = Math.abs(dx) + Math.abs(dz);
            offsets.add(new DynamicUndergroundPlacementCoordinator.Offset2D(dx, dz, movement, mixedOffsetKey(seed, dx, dz, 0)));
         }
      }

      offsets.sort(
         Comparator.comparingInt(DynamicUndergroundPlacementCoordinator.Offset2D::movement)
            .thenComparingLong(DynamicUndergroundPlacementCoordinator.Offset2D::tieBreak)
      );
      return offsets;
   }

   private static List<Integer> orderedCandidateYs(int minY, int maxY, long seed, int anchorBaseY) {
      List<Integer> ys = new ArrayList<>(Math.max(1, maxY - minY + 1));

      for (int y = minY; y <= maxY; y++) {
         ys.add(y);
      }

      ys.sort(Comparator.<Integer>comparingInt(yx -> Math.abs(yx - anchorBaseY)).thenComparingLong(yx -> mixedOffsetKey(seed, 0, 0, yx)));
      return ys;
   }

   private static int xyLatticeSize(DynamicUndergroundPlacementCoordinator.Profile profile) {
      int step = Math.max(1, profile.searchStepBlocks());
      int xCount = profile.searchRadiusX() * 2 / step + 1;
      int zCount = profile.searchRadiusZ() * 2 / step + 1;
      return Math.max(1, xCount * zCount);
   }

   private static int latticeSize(DynamicUndergroundPlacementCoordinator.Profile profile, int minY, int maxY) {
      return xyLatticeSize(profile) * Math.max(1, maxY - minY + 1);
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
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

   private static String format(double value) {
      return String.format(Locale.ROOT, "%.2f", value);
   }

   private static boolean isTranslatedBoundsContained(
      StructureSupportContext context, StructureStart structureStart, IslandField.IslandPreview host, int offsetX, int offsetZ
   ) {
      BoundingBox bounds = structureStart.getBoundingBox();
      int minX = bounds.minX() + offsetX;
      int maxX = bounds.maxX() + offsetX;
      int minZ = bounds.minZ() + offsetZ;
      int maxZ = bounds.maxZ() + offsetZ;
      int midX = minX + maxX >> 1;
      int midZ = minZ + maxZ >> 1;
      int[][] samples = new int[][]{
         {minX, minZ}, {minX, maxZ}, {maxX, minZ}, {maxX, maxZ}, {midX, minZ}, {midX, maxZ}, {minX, midZ}, {maxX, midZ}, {midX, midZ}
      };

      for (int[] sample : samples) {
         IslandField.IslandDescriptor dominant = context.islandField().sampleDominantIslandDescriptor(sample[0], sample[1], context.settings());
         if (dominant == null || dominant.centerX() != host.x() || dominant.centerZ() != host.z() || dominant.family() != host.family()) {
            return false;
         }
      }

      return true;
   }

   private record Candidate(
      IslandField.IslandPreview host, int dx, int dz, int baseY, int x, int z, int movement, DynamicUndergroundPlacementCoordinator.HardEval hard, double score
   ) {
   }

   private record HardEval(
      boolean valid, int sampleCount, int minOverburden, int supportCount, double stoneRatio, DynamicUndergroundPlacementCoordinator.PruneCause pruneCause
   ) {
      static DynamicUndergroundPlacementCoordinator.HardEval invalid(
         int sampleCount, int minOverburden, int supportCount, int stoneLike, int total, DynamicUndergroundPlacementCoordinator.PruneCause cause
      ) {
         double stoneRatio = total <= 0 ? 0.0 : (double)stoneLike / total;
         return new DynamicUndergroundPlacementCoordinator.HardEval(
            false, sampleCount, minOverburden == Integer.MAX_VALUE ? 0 : minOverburden, supportCount, stoneRatio, cause
         );
      }
   }

   private record Offset2D(int dx, int dz, int movement, long tieBreak) {
   }

   private record Profile(
      int minOverburden,
      int minSupportSamples,
      double minStoneRatio,
      int searchRadiusX,
      int searchRadiusZ,
      int searchStepBlocks,
      int maxCandidates,
      double preferredInteriorMargin,
      double edgePenaltyWeight,
      int anchorWindowDownBlocks,
      int anchorWindowUpBlocks,
      double hardMinimumInteriorMargin,
      boolean earlyStopEnabled,
      int earlyStopOverburden,
      int earlyStopSupport,
      double earlyStopStoneRatio,
      double earlyStopMinInteriorMargin,
      int hostMultiplierCap,
      double burialWeight,
      double supportWeight,
      double stoneWeight,
      double burialTargetMultiplier,
      double yDistancePenaltyWeight,
      boolean requireTranslatedBoundsContainment
   ) {
      int minStoneThickness() {
         return 8;
      }

      int earlyStopMovement() {
         return 16;
      }

      int minBelowLocalTopHard() {
         return this.minOverburden();
      }
   }

   private enum PruneCause {
      NONE,
      NO_COLUMN_AT_Y,
      OUTSIDE_HOST_DOMINANCE,
      INSUFFICIENT_OVERBURDEN,
      INSUFFICIENT_SUPPORT,
      INSUFFICIENT_STONE;
   }
}
