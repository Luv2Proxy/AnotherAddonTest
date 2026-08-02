# Generated jigsaw metadata

These files are build outputs from the vanilla jigsaw import pipeline.

## Source inputs

The source inputs are kept outside the runtime path, primarily under:

```text
bedrock-addon/structures/json/
```

and the converted structure-template library under:

```text
bedrock-addon/structures/
```

The addon runtime does **not** read those source JSON files directly.

## Generate the normalized data

From the repository root on Windows 11:

```powershell
py tools/import_vanilla_jigsaw_data_fixed.py `
  --json bedrock-addon/structures/json `
  --structures bedrock-addon/structures `
  --out bedrock-addon/scripts/worldgen/generated
```

This produces the human-readable generated artifacts:

- `pools.json`
- `structures.json`
- `processors.json`
- `structure-sets.json`
- `resolved-pieces.json`
- `jigsaw-connectors.json`
- `jigsaw-data.json`
- `import-manifest.json`
- `import-validation.json`

Then emit the JavaScript runtime module:

```powershell
py tools/emit_jigsaw_runtime_data.py `
  --input bedrock-addon/scripts/worldgen/generated/jigsaw-data.json `
  --output bedrock-addon/scripts/worldgen/generated/jigsaw-data.js
```

`jigsaw-data.js` is the file imported by the Bedrock Script runtime through `JigsawDataLoader.js`.

## Runtime architecture

```text
source JSON + structure templates
             |
             v
import_vanilla_jigsaw_data_fixed.py
             |
             +--> pools.json
             +--> structures.json
             +--> processors.json
             +--> structure-sets.json
             +--> resolved-pieces.json
             +--> jigsaw-connectors.json
             +--> jigsaw-data.json
             |
             v
emit_jigsaw_runtime_data.py
             |
             v
      jigsaw-data.js
             |
             v
     JigsawDataLoader.js
             |
             v
       JigsawRegistry
             |
       +-----+-----+
       |           |
       v           v
JigsawGenerator  StructureRegistry
       |
       v
NativeStructureAdapter
       |
       +--> native Bedrock jigsaw assembler when available
       |
       +--> generated metadata for validation/planning
```

The generated registry is used for pool graphs, weighted candidates, fallback pools, processors, structure definitions, structure sets, connector metadata, template resolution and validation.

When the active Bedrock behavior pack exposes a native jigsaw structure, final assembly should continue to use `StructureManager.placeJigsawStructure` or `StructureManager.placeJigsaw`. These APIs are the authoritative engine implementation for recursive jigsaw assembly. The generated data is therefore primarily the source for validation and custom planning, rather than a duplicate implementation of all engine semantics.

Microsoft's current documentation describes template pools as weighted collections of structure elements, with optional processors, projections and fallback pools; jigsaw structures reference a starting pool and recursively connect templates through jigsaw blocks. The Script API exposes `placeJigsaw` and `placeJigsawStructure`, returning the generated bounding box. citeturn0search0turn0search14
