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
let startupSelfTest = null;
let structureProcessBusy = false;
let placementProcessBusy = false;

function archipelago() { return world.getDimension(DIMENSION_ID); }
function registry() { return new JigsawRegistry(getGeneratedJigsawData()); }
function buildRuntime() {
  const data = getGeneratedJigsawData();
  const jigsawRegistry = new JigsawRegistry(data);
  const densityField = new StructureDensityField();
  placementCoordinator = new StructurePlacementCoordinator(generator, { registry: jigsawRegistry, terrainOptions: { minY: -64, maxY: 320 }, densityField });
  placementQueue = new StructurePlacementQueue({ maxPerTick: 1, maxRetries: 3, retryDelay: 20 });
  // Keep the random-spread planning window deliberately small. A 512-block
  // synchronous search across every structure set is too expensive for Bedrock's
  // script tick. The runtime revisits cells as the player moves.
  structureSets = new StructureSetRuntime(generator, { data, registry: jigsawRegistry, dimensionId: DIMENSION_ID, radius: 96, maxPlansPerTick: 1, maxPlacementsPerTick: 1, placementCoordinator, placementQueue, densityField });
  worldgen = new WorldgenJigsawRuntime(archipelago(), { data, registry: jigsawRegistry, generator, structureSets, densityField, placementCoordinator, placementQueue });
  structureSets.refresh(jigsawRegistry);
}

system.beforeEvents.startup.subscribe((event) => {
  event.dimensionRegistry.registerCustomDimension(DIMENSION_ID);
});

function runStartupSelfTest() {
  try {
    const test = runWorldgenSelfTest({ data: getGeneratedJigsawData(), registry: worldgen?.registry, seed: generator.layoutSeed, deep: false });
    startupSelfTest = test;
    if (!test.ok) console.warn(`[Sky Archipelago] worldgen self-test errors: ${test.errors.join(" | ")}`);
    if (test.warnings.length) console.warn(`[Sky Archipelago] worldgen self-test warnings: ${test.warnings.slice(0, 12).join(" | ")}`);
  } catch (error) {
    startupSelfTest = { ok: false, errors: [String(error)], warnings: [], snapshot: worldgen?.registry?.snapshot?.() ?? {} };
    console.warn(`[Sky Archipelago] worldgen self-test failed: ${error}`);
  }
}

world.afterEvents.worldLoad.subscribe(() => {
  generator.load();
  generator.dimension = archipelago();
  buildRuntime();
  system.run(runStartupSelfTest);
  system.run(() => {
    const test = startupSelfTest;
    const snapshot = test?.snapshot ?? worldgen?.registry?.snapshot?.() ?? {};
    world.sendMessage(`§aSky Archipelago loaded. §7Worldgen: ${snapshot.pieces ?? 0} pieces, ${snapshot.pools ?? 0} pools, ${snapshot.structures ?? 0} structures, ${snapshot.structureSets ?? 0} sets.`);
  });
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

  // Never allow two long async processing passes to overlap. Overlapping passes
  // multiply native getBlock/setBlock work and were a major contributor to the
  // watchdog interruptions.
  if (!structureProcessBusy) {
    structureProcessBusy = true;
    structureSets.process()
      .then(stats => { lastTickStats = stats; })
      .catch(error => console.warn(`[Sky Archipelago] structure-set runtime error: ${error}`))
      .finally(() => { structureProcessBusy = false; });
  }

  if (placementQueue && !placementProcessBusy) {
    placementProcessBusy = true;
    placementQueue.process(placementCoordinator)
      .catch(error => console.warn(`[Sky Archipelago] placement queue error: ${error}`))
      .finally(() => { placementProcessBusy = false; });
  }
}, 1);

system.afterEvents.scriptEventReceive.subscribe((event) => {
  const player = event.sourceEntity;
  if (!player || player.typeId !== "minecraft:player") return;
  if (event.id === "sky_archipelago:enter") { player.teleport({ x: 0.5, y: 120, z: 0.5 }, { dimension: archipelago() }); player.sendMessage("§bWelcome to Sky Archipelago."); return; }
  if (event.id === "sky_archipelago:lobby") { player.teleport({ x: 0.5, y: 100, z: 0.5 }, { dimension: world.getDimension("minecraft:overworld") }); player.sendMessage("§eReturned to the Overworld lobby."); return; }
  if (event.id === "sky_archipelago:reset") { generator.reset(); buildRuntime(); player.sendMessage("§eSky Archipelago generation state reset. Existing blocks are not erased."); return; }
  if (event.id === "sky_archipelago:status") {
    const snapshot = worldgen?.snapshot?.() ?? {}, native = generator.native?.snapshot?.();
    player.sendMessage(`§bSky Archipelago §7| generated=${generator.generated?.size ?? 0} queued=${generator.queue?.length ?? 0} structureJobs=${generator.structureJobs?.length ?? 0}`);
    player.sendMessage(`§7Sets=${snapshot.structureSets ?? 0} | densityBoxes=${snapshot.densityBoxes ?? 0} | junctions=${snapshot.densityJunctions ?? 0} | pendingPlans=${structureSets?.pending?.length ?? 0} | pendingPlacements=${placementQueue?.size?.() ?? 0}`);
    player.sendMessage(`§7Last tick: ${JSON.stringify(lastTickStats ?? {})} | Native overlap records: ${native?.overlap?.length ?? 0}`); return;
  }
  if (event.id === "sky_archipelago:test_worldgen") {
    try {
      const test = runWorldgenSelfTest({ data: getGeneratedJigsawData(), registry: worldgen?.registry, seed: generator.layoutSeed, deep: true });
      player.sendMessage(`§bWorldgen self-test: ${test.ok ? "§aPASS" : "§cFAIL"}`);
      player.sendMessage(`§7Pieces=${test.snapshot.pieces} Pools=${test.snapshot.pools} Structures=${test.snapshot.structures} Sets=${test.snapshot.structureSets}`);
      if (test.warnings.length) player.sendMessage(`§eWarnings: ${test.warnings.slice(0, 3).join(" | ")}`);
      if (test.errors.length) player.sendMessage(`§cErrors: ${test.errors.slice(0, 3).join(" | ")}`);
    } catch (e) { player.sendMessage(`§cWorldgen self-test exception: ${e}`); }
    return;
  }
  if (event.id === "sky_archipelago:refresh_structures") {
    try { const r = registry(); generator.registry?.refresh?.(); structureSets?.refresh?.(r); placementCoordinator?.refresh?.(r); worldgen?.refresh?.(); player.sendMessage("§aStructure registries refreshed from generated jigsaw data."); }
    catch (e) { player.sendMessage(`§cStructure registry refresh failed: ${e}`); }
  }
});
