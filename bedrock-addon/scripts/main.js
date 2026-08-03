import { world, system } from "@minecraft/server";
import { IslandGenerator } from "./worldgen/IslandGenerator.js";
import { JigsawRegistry } from "./worldgen/JigsawRegistry.js";
import { StructureSetRuntime } from "./worldgen/StructureSetRuntime.js";
import { StructurePlacementCoordinator } from "./worldgen/StructurePlacementCoordinator.js";
import { StructurePlacementQueue } from "./worldgen/StructurePlacementQueue.js";

const DIMENSION_ID = "sky_archipelago:archipelago";
const generator = new IslandGenerator();
let structureSets = null;
let placementCoordinator = null;
let placementQueue = null;

function archipelago() { return world.getDimension(DIMENSION_ID); }

system.beforeEvents.startup.subscribe((event) => {
  event.dimensionRegistry.registerCustomDimension(DIMENSION_ID);
});

world.afterEvents.worldLoad.subscribe(() => {
  generator.load();
  generator.dimension = archipelago();
  const registry = new JigsawRegistry();
  placementCoordinator = new StructurePlacementCoordinator(generator, {
    registry,
    terrainOptions: { minY: -64, maxY: 320 }
  });
  placementQueue = new StructurePlacementQueue({ maxPerTick: 2, maxRetries: 3, retryDelay: 20 });
  structureSets = new StructureSetRuntime(generator, {
    registry,
    dimensionId: DIMENSION_ID,
    radius: 512,
    maxPlansPerTick: 1,
    maxPlacementsPerTick: 2,
    placementCoordinator,
    placementQueue
  });
  structureSets.refresh();
  world.sendMessage("§aSky Archipelago loaded. §e/scriptevent sky_archipelago:enter §7to enter the archipelago.");
});

system.runInterval(() => {
  for (const player of world.getAllPlayers()) {
    if (player.dimension.id === DIMENSION_ID) {
      generator.requestAround(player);
      structureSets?.enqueueAround(player.location.x, player.location.z);
    }
  }
  generator.tick();
  structureSets?.process().catch(error => console.warn(`[Sky Archipelago] structure-set runtime error: ${error}`));
  placementQueue?.process(placementCoordinator).catch(error => console.warn(`[Sky Archipelago] placement queue error: ${error}`));
}, 1);

world.afterEvents.scriptEventReceive.subscribe((event) => {
  const player = event.sourceEntity;
  if (!player || player.typeId !== "minecraft:player") return;

  if (event.id === "sky_archipelago:enter") {
    player.teleport({ x: 0.5, y: 120, z: 0.5 }, { dimension: archipelago() });
    player.sendMessage("§bWelcome to Sky Archipelago.");
    return;
  }

  if (event.id === "sky_archipelago:lobby") {
    player.teleport({ x: 0.5, y: 100, z: 0.5 }, { dimension: world.getDimension("minecraft:overworld") });
    player.sendMessage("§eReturned to the Overworld lobby.");
    return;
  }

  if (event.id === "sky_archipelago:reset") {
    generator.reset();
    const registry = new JigsawRegistry();
    placementCoordinator = new StructurePlacementCoordinator(generator, {
      registry,
      terrainOptions: { minY: -64, maxY: 320 }
    });
    placementQueue = new StructurePlacementQueue({ maxPerTick: 2, maxRetries: 3, retryDelay: 20 });
    structureSets = new StructureSetRuntime(generator, { registry, dimensionId: DIMENSION_ID, placementCoordinator, placementQueue });
    player.sendMessage("§eSky Archipelago generation state reset. Existing blocks are not erased.");
    return;
  }

  if (event.id === "sky_archipelago:status") {
    const snapshot = generator.native?.snapshot?.() ?? {};
    const setCount = structureSets?.sets?.length ?? 0;
    const pending = structureSets?.pending?.length ?? 0;
    const placementPending = placementQueue?.size?.() ?? 0;
    player.sendMessage(`§bSky Archipelago §7| generated=${generator.generated?.size ?? 0} queued=${generator.queue?.length ?? 0} structures=${generator.structureJobs?.length ?? 0}`);
    player.sendMessage(`§7Generated structure sets: ${setCount} | pending plans: ${pending} | pending placements: ${placementPending}`);
    player.sendMessage(`§7Native overlap records: ${snapshot.overlap?.length ?? 0}`);
    return;
  }

  if (event.id === "sky_archipelago:refresh_structures") {
    try {
      const registry = new JigsawRegistry();
      generator.registry?.refresh?.();
      structureSets?.refresh?.(registry);
      placementCoordinator?.refresh?.(registry);
      player.sendMessage("§aStructure registries refreshed from generated jigsaw data and the active Bedrock pack.");
    } catch (e) {
      player.sendMessage(`§cStructure registry refresh failed: ${e}`);
    }
    return;
  }
});
