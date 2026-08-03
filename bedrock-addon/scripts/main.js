import { world, system } from "@minecraft/server";
import { IslandGenerator } from "./worldgen/IslandGenerator.js";
import { JigsawRegistry } from "./worldgen/JigsawRegistry.js";
import { StructureSetRuntime } from "./worldgen/StructureSetRuntime.js";
import { StructurePlacementCoordinator } from "./worldgen/StructurePlacementCoordinator.js";
import { StructurePlacementQueue } from "./worldgen/StructurePlacementQueue.js";
import { getGeneratedJigsawData } from "./worldgen/JigsawDataLoader.js";
import { StructureDensityField } from "./worldgen/StructureDensityField.js";
import { WorldgenJigsawRuntime } from "./worldgen/WorldgenJigsawRuntime.js";
import { runWorldgenSelfTest } from "./worldgen/WorldgenSelfTest.js";

const DIMENSION_ID = "sky_archipelago:archipelago";
const generator = new IslandGenerator();
let structureSets = null;
let placementCoordinator = null;
let placementQueue = null;
let worldgen = null;
let lastTickStats = null;

function archipelago() { return world.getDimension(DIMENSION_ID); }
function registry() { return new JigsawRegistry(getGeneratedJigsawData()); }
function findPlayer(name) {
  const needle = String(name ?? "").trim().toLowerCase();
  if (!needle) return null;
  return world.getAllPlayers().find(p => p.name.toLowerCase() === needle) ?? null;
}
function resolveEventPlayer(event) {
  if (event.initiator?.typeId === "minecraft:player") return event.initiator;
  if (event.sourceEntity?.typeId === "minecraft:player") return event.sourceEntity;
  const args = String(event.message ?? "").trim().split(/\s+/);
  return findPlayer(args[0]);
}
function buildRuntime() {
  const data = getGeneratedJigsawData();
  const jigsawRegistry = new JigsawRegistry(data);
  const densityField = new StructureDensityField();
  placementCoordinator = new StructurePlacementCoordinator(generator, { registry: jigsawRegistry, terrainOptions: { minY: -64, maxY: 320 }, densityField });
  placementQueue = new StructurePlacementQueue({ maxPerTick: 2, maxRetries: 3, retryDelay: 20 });
  structureSets = new StructureSetRuntime(generator, { data, registry: jigsawRegistry, dimensionId: DIMENSION_ID, radius: 512, maxPlansPerTick: 1, maxPlacementsPerTick: 2, placementCoordinator, placementQueue, densityField });
  worldgen = new WorldgenJigsawRuntime(archipelago(), { data, registry: jigsawRegistry, generator, structureSets, densityField, placementCoordinator, placementQueue });
  structureSets.refresh(jigsawRegistry);
}

system.beforeEvents.startup.subscribe((event) => { event.dimensionRegistry.registerCustomDimension(DIMENSION_ID); });

world.afterEvents.worldLoad.subscribe(() => {
  try {
    generator.load();
    generator.dimension = archipelago();
    buildRuntime();
    const test = runWorldgenSelfTest({ data: getGeneratedJigsawData(), registry: worldgen.registry, seed: generator.layoutSeed });
    if (!test.ok) console.warn(`[Sky Archipelago] worldgen self-test errors: ${test.errors.join(" | ")}`);
    if (test.warnings.length) console.warn(`[Sky Archipelago] worldgen self-test warnings: ${test.warnings.slice(0, 12).join(" | ")}`);
    world.sendMessage(`§aSky Archipelago loaded. §7Worldgen: ${test.snapshot.pieces} pieces, ${test.snapshot.pools} pools, ${test.snapshot.structures} structures, ${test.snapshot.structureSets} sets.`);
  } catch (error) {
    console.warn(`[Sky Archipelago] startup initialization failed: ${error?.stack ?? error}`);
  }
});

system.runInterval(() => {
  if (!structureSets || !worldgen) return;
  for (const player of world.getAllPlayers()) {
    if (player.dimension.id === DIMENSION_ID) {
      generator.requestAround(player);
      structureSets.enqueueAround(player.location.x, player.location.z);
    }
  }
  generator.tick();
  worldgen.tick();
  structureSets.process().then(stats => { lastTickStats = stats; }).catch(error => console.warn(`[Sky Archipelago] structure-set runtime error: ${error?.stack ?? error}`));
  placementQueue?.process(placementCoordinator).catch(error => console.warn(`[Sky Archipelago] placement queue error: ${error?.stack ?? error}`));
}, 1);

// /scriptevent belongs to SystemAfterEvents, not WorldAfterEvents.
// Direct player commands have sourceType=Server and no sourceEntity, so player-facing commands accept the player name as the first argument.
// Example: /scriptevent sky_archipelago:status Armaan
system.afterEvents.scriptEventReceive.subscribe((event) => {
  try {
    const id = String(event.id ?? "");
    const message = String(event.message ?? "").trim();
    const player = resolveEventPlayer(event);
    const args = message ? message.split(/\s+/) : [];

    if (id === "sky_archipelago:debug") {
      world.sendMessage(`§b[Sky Archipelago] ScriptEvent received: id=${id} source=${event.sourceType ?? "unknown"} message=${message || "<empty>"}`);
      return;
    }
    if (id === "sky_archipelago:enter") {
      if (!player) { world.sendMessage("§cUsage: /scriptevent sky_archipelago:enter <player>"); return; }
      player.teleport({ x: 0.5, y: 120, z: 0.5 }, { dimension: archipelago() });
      player.sendMessage("§bWelcome to Sky Archipelago.");
      return;
    }
    if (id === "sky_archipelago:lobby") {
      if (!player) { world.sendMessage("§cUsage: /scriptevent sky_archipelago:lobby <player>"); return; }
      player.teleport({ x: 0.5, y: 100, z: 0.5 }, { dimension: world.getDimension("minecraft:overworld") });
      player.sendMessage("§eReturned to the Overworld lobby.");
      return;
    }
    if (id === "sky_archipelago:reset") {
      generator.reset();
      buildRuntime();
      const text = "§eSky Archipelago generation state reset. Existing blocks are not erased.";
      if (player) player.sendMessage(text); else world.sendMessage(text);
      return;
    }
    if (id === "sky_archipelago:status") {
      const snapshot = worldgen?.snapshot?.() ?? {}, native = generator.native?.snapshot?.();
      const text = `§bSky Archipelago §7| generated=${generator.generated?.size ?? 0} queued=${generator.queue?.length ?? 0} structureJobs=${generator.structureJobs?.length ?? 0}\n§7Sets=${snapshot.structureSets ?? 0} | densityBoxes=${snapshot.densityBoxes ?? 0} | junctions=${snapshot.densityJunctions ?? 0} | pendingPlans=${structureSets?.pending?.length ?? 0} | pendingPlacements=${placementQueue?.size?.() ?? 0}\n§7Last tick: ${JSON.stringify(lastTickStats ?? {})} | Native overlap records: ${native?.overlap?.length ?? 0}`;
      if (player) player.sendMessage(text); else world.sendMessage(text);
      return;
    }
    if (id === "sky_archipelago:test_worldgen") {
      const test = runWorldgenSelfTest({ data: getGeneratedJigsawData(), registry: worldgen?.registry, seed: generator.layoutSeed });
      const text = `§bWorldgen self-test: ${test.ok ? "§aPASS" : "§cFAIL"}\n§7Pieces=${test.snapshot.pieces} Pools=${test.snapshot.pools} Structures=${test.snapshot.structures} Sets=${test.snapshot.structureSets}${test.warnings.length ? `\n§eWarnings: ${test.warnings.slice(0, 3).join(" | ")}` : ""}${test.errors.length ? `\n§cErrors: ${test.errors.slice(0, 3).join(" | ")}` : ""}`;
      if (player) player.sendMessage(text); else world.sendMessage(text);
      return;
    }
    if (id === "sky_archipelago:refresh_structures") {
      try {
        const r = registry();
        generator.registry?.refresh?.();
        structureSets?.refresh?.(r);
        placementCoordinator?.refresh?.(r);
        worldgen?.refresh?.();
        const text = "§aStructure registries refreshed from generated jigsaw data.";
        if (player) player.sendMessage(text); else world.sendMessage(text);
      } catch (e) {
        const text = `§cStructure registry refresh failed: ${e?.stack ?? e}`;
        if (player) player.sendMessage(text); else world.sendMessage(text);
      }
      return;
    }
    console.warn(`[Sky Archipelago] Unknown script event: ${id} message=${message} source=${event.sourceType ?? "unknown"}`);
  } catch (error) {
    console.warn(`[Sky Archipelago] script event handler failed: ${error?.stack ?? error}`);
  }
});
