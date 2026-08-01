import { ActionFormData, ModalFormData } from "@minecraft/server-ui";
import { SkyIslandConfig } from "../config/SkyIslandConfig.js";

const config = new SkyIslandConfig();

export async function openSkyArchipelagoSettings(player) {
  config.load();
  const values = config.snapshot();
  const menu = new ActionFormData()
    .title("Sky Archipelago")
    .body("Configure procedural island generation. Changes affect newly generated chunks.")
    .button("World Generation")
    .button("Island Shapes")
    .button("Terrain & Materials")
    .button("Surface & Vegetation")
    .button("Reset to Defaults");
  const result = await menu.show(player);
  if (result.canceled) return;
  if (result.selection === 0) return worldGeneration(player, values);
  if (result.selection === 1) return islandShapes(player, values);
  if (result.selection === 2) return terrain(player, values);
  if (result.selection === 3) return surface(player, values);
  if (result.selection === 4) { config.reset(); player.sendMessage("§aSky Archipelago settings reset to defaults."); }
}

async function worldGeneration(player, v) {
  const f = new ModalFormData().title("World Generation")
    .slider("Cluster spacing", 48, 256, { valueStep: 8, defaultValue: v.spacing })
    .slider("Minimum island Y", -64, 180, { valueStep: 1, defaultValue: v.minIslandY })
    .slider("Maximum island Y", 64, 319, { valueStep: 1, defaultValue: v.maxIslandY })
    .slider("Minimum island radius", 8, 64, { valueStep: 1, defaultValue: v.minIslandRadius })
    .slider("Maximum island radius", 24, 128, { valueStep: 1, defaultValue: v.maxIslandRadius })
    .slider("Maximum island thickness", 32, 240, { valueStep: 1, defaultValue: v.maxIslandThickness })
    .dropdown("Overlap mode", ["crater", "overlap", "void"], { defaultValueIndex: ["crater","overlap","void"].indexOf(v.terrainOverlapMode) });
  const r = await f.show(player); if (r.canceled) return;
  const x=r.formValues; config.load(); ["spacing","minIslandY","maxIslandY","minIslandRadius","maxIslandRadius","maxIslandThickness"].forEach((k,i)=>config.set(k,Number(x[i]))); config.set("terrainOverlapMode",["crater","overlap","void"][Number(x[6])]); config.save(); player.sendMessage("§aWorld generation settings saved.");
}

async function islandShapes(player, v) {
  const f=new ModalFormData().title("Island Shapes")
    .slider("Classic weight",0,10,{valueStep:.1,defaultValue:v.classicWeight})
    .slider("Bowl crater weight",0,10,{valueStep:.1,defaultValue:v.bowlCraterWeight})
    .slider("Crescent weight",0,10,{valueStep:.1,defaultValue:v.crescentWeight})
    .slider("Terrace weight",0,10,{valueStep:.1,defaultValue:v.terraceWeight});
  const r=await f.show(player);if(r.canceled)return;const x=r.formValues;config.load();config.set("classicWeight",Number(x[0]));config.set("bowlCraterWeight",Number(x[1]));config.set("crescentWeight",Number(x[2]));config.set("terraceWeight",Number(x[3]));config.save();player.sendMessage("§aIsland shape settings saved.");
}

async function terrain(player,v){
  const f=new ModalFormData().title("Terrain & Materials")
    .slider("Terrain relief",0,3,{valueStep:.05,defaultValue:v.terrainReliefScale})
    .slider("Channel carving",0,3,{valueStep:.05,defaultValue:v.channelCarveScale})
    .slider("Basin carving",0,3,{valueStep:.05,defaultValue:v.basinCarveScale})
    .slider("Deepslate start Y",-64,200,{valueStep:1,defaultValue:v.deepslateStartY})
    .toggle("Enable ocean",{defaultValue:v.oceanEnabled})
    .slider("Ocean level Y",-64,200,{valueStep:1,defaultValue:v.oceanLevelY})
    .toggle("Noise ocean floor",{defaultValue:v.oceanFloorNoiseEnabled});
  const r=await f.show(player);if(r.canceled)return;const x=r.formValues;config.load();config.set("terrainReliefScale",Number(x[0]));config.set("channelCarveScale",Number(x[1]));config.set("basinCarveScale",Number(x[2]));config.set("deepslateStartY",Number(x[3]));config.set("oceanEnabled",Boolean(x[4]));config.set("oceanLevelY",Number(x[5]));config.set("oceanFloorNoiseEnabled",Boolean(x[6]));config.save();player.sendMessage("§aTerrain settings saved.");
}

async function surface(player,v){
  const f=new ModalFormData().title("Surface & Vegetation").toggle("Generate surface layers",{defaultValue:v.surfaceEnabled}).toggle("Generate vegetation",{defaultValue:v.vegetationEnabled});
  const r=await f.show(player);if(r.canceled)return;config.load();config.set("surfaceEnabled",Boolean(r.formValues[0]));config.set("vegetationEnabled",Boolean(r.formValues[1]));config.save();player.sendMessage("§aSurface settings saved.");
}
