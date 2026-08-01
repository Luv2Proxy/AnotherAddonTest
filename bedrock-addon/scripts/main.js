import { world, system } from "@minecraft/server";
import { IslandGenerator } from "./worldgen/IslandGenerator.js";

const DIMENSION_ID = "sky_archipelago:archipelago";
const generator = new IslandGenerator();

system.beforeEvents.startup.subscribe((event) => {
  event.dimensionRegistry.registerCustomDimension(DIMENSION_ID);
});

world.afterEvents.worldLoad.subscribe(() => {
  generator.load();
  world.sendMessage("§aSky Archipelago loaded. Run §e/scriptevent sky_archipelago:enter§a to enter.");
});

system.runInterval(() => {
  for (const player of world.getAllPlayers()) {
    if (player.dimension.id === DIMENSION_ID) generator.requestAround(player);
  }
  generator.tick();
}, 1);

world.afterEvents.scriptEventReceive.subscribe((event) => {
  if (event.id === "sky_archipelago:enter") {
    const player = event.sourceEntity;
    if (!player || player.typeId !== "minecraft:player") return;
    player.teleport({ x: 0.5, y: 120, z: 0.5 }, { dimension: world.getDimension(DIMENSION_ID) });
  }
  if (event.id === "sky_archipelago:reset") {
    generator.reset();
    world.sendMessage("§eSky Archipelago generated-chunk database reset. Existing blocks are not erased.");
  }
});
