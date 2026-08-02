import { world, system } from "@minecraft/server";
import { IslandGenerator } from "./worldgen/IslandGenerator.js";

const DIMENSION_ID = "sky_archipelago:archipelago";
const generator = new IslandGenerator();

function archipelago(){return world.getDimension(DIMENSION_ID);}

system.beforeEvents.startup.subscribe((event) => {
  event.dimensionRegistry.registerCustomDimension(DIMENSION_ID);
});

world.afterEvents.worldLoad.subscribe(() => {
  generator.load();
  world.sendMessage("§aSky Archipelago loaded. §e/scriptevent sky_archipelago:enter §7to enter the archipelago.");
});

system.runInterval(() => {
  for (const player of world.getAllPlayers()) {
    if (player.dimension.id === DIMENSION_ID) generator.requestAround(player);
  }
  generator.tick();
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
    // The addon does not own a separate lobby dimension yet; use the Overworld
    // as the safe lobby so this command is useful in every world configuration.
    player.teleport({ x: 0.5, y: 100, z: 0.5 }, { dimension: world.getDimension("minecraft:overworld") });
    player.sendMessage("§eReturned to the Overworld lobby.");
    return;
  }

  if (event.id === "sky_archipelago:reset") {
    generator.reset();
    player.sendMessage("§eSky Archipelago generation state reset. Existing blocks are not erased.");
    return;
  }

  if (event.id === "sky_archipelago:status") {
    const snapshot = generator.native?.snapshot?.() ?? {};
    player.sendMessage(`§bSky Archipelago §7| generated=${generator.generated?.size ?? 0} queued=${generator.queue?.length ?? 0} structures=${generator.structureJobs?.length ?? 0}`);
    player.sendMessage(`§7Native overlap records: ${snapshot.overlap?.length ?? 0}`);
    return;
  }

  if (event.id === "sky_archipelago:refresh_structures") {
    try {
      generator.registry?.refresh?.();
      player.sendMessage("§aStructure registry refreshed from the active Bedrock pack.");
    } catch (e) {
      player.sendMessage(`§cStructure registry refresh failed: ${e}`);
    }
    return;
  }
});
