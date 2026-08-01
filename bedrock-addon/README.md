# Sky Archipelago — Bedrock Edition Addon

This folder is a Bedrock Edition port/prototype of the island-generation concepts from the Java/NeoForge `Sky Archipelago` implementation in this repository.

## Important Bedrock limitation

Bedrock Add-Ons do not currently expose the same custom chunk-generator API used by the Java mod. In particular, a behavior pack cannot register a new `ChunkGenerator` and replace vanilla terrain generation at the chunk-generation stage.

This port therefore uses the Bedrock Script API to generate deterministic island terrain around active players. It reproduces the **island placement and shape algorithm** as closely as practical from the source configuration:

- deterministic seed-based island cluster positions
- configurable island density
- consistent or dynamic cluster spacing
- weighted small/medium/large island radii
- low/mid/high elevation bands
- classic, bowl/crater, crescent, and terrace island archetypes
- radial island masks with noisy terrain relief
- tapered floating-island thickness
- configurable ocean layer
- deepslate below a configurable Y level
- generation only near players to avoid freezing the game

## Source algorithm analysis

The original mod is fundamentally a **deterministic procedural island field** rather than a list of hand-authored islands. The important generation parameters exposed by the Java project are:

1. `island_density` controls whether a candidate cluster produces an island.
2. `cluster_spacing` (or a deterministic min/max spacing range) establishes the repeating candidate grid.
3. Each candidate gets a deterministic random offset derived from the world seed and candidate coordinates.
4. Island radius is selected either from a random min/max range or from weighted small/medium/large bands.
5. Island Y is selected from weighted low/mid/high elevation bands.
6. A radial mask defines the island footprint. The mask is modified by terrain relief noise and one of four archetypes:
   - classic
   - bowl/crater
   - crescent
   - terrace
7. For each horizontal position inside the mask, the generator computes a top surface and a tapered underside. This produces a floating landmass rather than a flat disk.
8. Material selection changes with depth; the original configuration uses a deepslate threshold for deeper island material.
9. Optional ocean generation creates a water surface and a noisy floor below it.

The Bedrock implementation in `scripts/main.js` follows this same pipeline, but performs it incrementally around players because the Script API is runtime terrain editing rather than a native chunk generator.

## Files

- `manifest.json` — Behavior Pack manifest with Script API dependency.
- `scripts/main.js` — deterministic island placement and terrain generation.

## Installing

1. Copy `bedrock-addon` into a Bedrock development behavior-pack directory, or package it as a `.mcpack`.
2. Enable the behavior pack on a test world.
3. Enable the Script API / Beta APIs required by the installed Bedrock version if the pack reports that they are required.
4. Create a new world with a flat or otherwise simple base terrain for the prototype.
5. Enter the world and move around. Islands are generated in a radius around active players.

## Configuration

Edit `CONFIG` at the top of `scripts/main.js`.

For a closer match to the Java mod's compact/default profile, start with:

- `clusterSpacing: 96`
- `islandDensity: 0.4`
- `minIslandRadius: 24`
- `maxIslandRadius: 75`
- `minIslandY: 20`
- `maxIslandY: 170`
- `maxIslandThickness: 140`
- low/mid/high weights `0.15 / 0.75 / 0.10`

The generator deliberately has a conservative `generationRadius` and per-tick island budget. Increase these only after testing performance on the target device.

## Why this is not a true world-generation replacement

The Java implementation can replace the overworld's generator through a custom `ChunkGenerator`. Bedrock behavior packs cannot currently do that through the same public Add-On APIs. A true pre-generation implementation would require engine-level support for custom terrain generation, or a native/server-side solution outside the normal Add-On API.

The runtime approach here is therefore intended as a practical prototype and algorithm port, not a 1:1 replacement of Bedrock's native terrain generator.
