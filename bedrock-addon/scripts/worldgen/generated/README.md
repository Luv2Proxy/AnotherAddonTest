# Generated jigsaw metadata

`jigsaw-data.json` is generated locally from the addon `.mcstructure` library and, when available, the installed Bedrock vanilla behavior pack/worldgen definitions.

From the repository root on Windows 11:

```powershell
py tools/extract_bedrock_jigsaw_assets.py `
  --structures bedrock-addon/structures `
  --auto `
  --out bedrock-addon/scripts/worldgen/generated/jigsaw-data.json
```

You can explicitly provide one or more Minecraft installation/content roots with `--minecraft-root` instead of `--auto`.

The extractor is intentionally loss-aware: missing pools, processors, structure definitions, or unsupported files are reported rather than fabricated.

The runtime's authoritative final placement path is `StructureManager.placeJigsawStructure` / `StructureManager.placeJigsaw`. Bedrock's native assembler performs the recursive connector matching, weighted pool selection, fallback pools, processors, projections, terrain matching, and jigsaw cleanup. The generated metadata is used by `JigsawRegistry` for validation and tooling, while `JigsawGenerator` provides the common placement facade.
