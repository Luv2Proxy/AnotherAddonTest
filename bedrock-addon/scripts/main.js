import { world, system } from "@minecraft/server";
import { ActionFormData } from "@minecraft/server-ui";
import { IslandGenerator } from "./worldgen/IslandGenerator.js";
import { openSkyArchipelagoSettings } from "./ui/SkyArchipelagoSettingsMenu.js";

const DIMENSION_ID="sky_archipelago:archipelago";
const generator=new IslandGenerator();

system.beforeEvents.startup.subscribe((event)=>{event.dimensionRegistry.registerCustomDimension(DIMENSION_ID);});
world.afterEvents.worldLoad.subscribe(()=>{generator.load();world.sendMessage("§aSky Archipelago loaded. Run §e/scriptevent sky_archipelago:enter§a to enter. Use §e/scriptevent sky_archipelago:settings§a for settings.");});

system.runInterval(()=>{for(const player of world.getAllPlayers()){if(player.dimension.id===DIMENSION_ID)generator.requestAround(player);}generator.tick();},1);

world.afterEvents.scriptEventReceive.subscribe(async(event)=>{
 const player=event.sourceEntity;
 if(event.id==="sky_archipelago:enter"&&player?.typeId==="minecraft:player")player.teleport({x:.5,y:120,z:.5},{dimension:world.getDimension(DIMENSION_ID)});
 if(event.id==="sky_archipelago:settings"&&player?.typeId==="minecraft:player")await openSkyArchipelagoSettings(player);
 if(event.id==="sky_archipelago:reset"&&player?.typeId==="minecraft:player"){generator.reset();player.sendMessage("§eSky Archipelago generated-chunk database reset. Existing blocks are not erased.");}
});

world.afterEvents.itemUse.subscribe(async(event)=>{if(event.itemStack?.typeId==="minecraft:compass")await openSkyArchipelagoSettings(event.source);});

async function fallbackMenu(player){const r=await new ActionFormData().title("Sky Archipelago").body("Open generator settings?").button("Settings").show(player);if(!r.canceled)await openSkyArchipelagoSettings(player);}
