const DEFAULTS = {
  spacing: 96,
  minIslandY: 32,
  maxIslandY: 220,
  minIslandRadius: 18,
  maxIslandRadius: 72,
  maxIslandThickness: 150,
  lowWeight: 1,
  midHighWeight: 3,
  veryHighWeight: 1,
  lowOffset: 0,
  veryHighOffset: 0,
  terrainReliefScale: 1,
  channelCarveScale: 1,
  basinCarveScale: 1,
  oceanEnabled: false,
  oceanLevelY: 0,
  oceanFloorNoiseEnabled: false,
  deepslateStartY: 0,
  terrainOverlapMode: "crater",
  classicWeight: 1,
  bowlCraterWeight: 1,
  crescentWeight: 1,
  terraceWeight: 1,
  surfaceEnabled: true,
  vegetationEnabled: true
};

const PROPERTY = "sky_archipelago:settings_v1";

export class SkyIslandConfig {
  constructor() { this.values = { ...DEFAULTS }; }
  load() {
    const raw = world.getDynamicProperty(PROPERTY);
    try { if (typeof raw === "string") this.values = { ...DEFAULTS, ...JSON.parse(raw) }; } catch {}
    return this.values;
  }
  save() { world.setDynamicProperty(PROPERTY, JSON.stringify(this.values)); }
  reset() { this.values = { ...DEFAULTS }; this.save(); }
  get(key) { return this.values[key]; }
  set(key, value) { if (!(key in DEFAULTS)) throw new Error(`Unknown setting: ${key}`); this.values[key] = value; }
  snapshot() { return { ...this.values }; }
}

export { DEFAULTS };
