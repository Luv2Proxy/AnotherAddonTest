import generatedData from "./generated/jigsaw-data.js";

const NAMESPACE_DEFAULT="minecraft";

function normalizeId(id){
  if(id==null)return null;
  const value=String(id).replace(/\\/g,"/").replace(/\.mcstructure$/i,"").replace(/\.nbt$/i,"");
  return value.includes(":")?value:`${NAMESPACE_DEFAULT}:${value}`;
}
function canonicalPath(id){return normalizeId(id)?.replace(/^[^:]+:/,"")??null;}
function mapObject(value){return value&&typeof value==="object"&&!Array.isArray(value)?value:{};}

export function normalizeGeneratedJigsawData(data=generatedData){
  const source=mapObject(data),pools=mapObject(source.template_pools??source.pools),structures=mapObject(source.structures??source.jigsaw_structures),processors=mapObject(source.processors),sets=mapObject(source.structure_sets??source.structureSets),pieces=mapObject(source.pieces),resolved=mapObject(source.resolved_templates),aliases=mapObject(source.template_aliases),connectors=mapObject(source.connectors??source.jigsaw_connectors),missing=new Set([...(source.missing_templates??source.missing??[]).map(String)]);
  const missingKeys=new Set([...missing].map(x=>normalizeId(x)).filter(Boolean));
  const generatedFiles=mapObject(source.generated_files??source.files),native=mapObject(source.native??source.native_json),metadata=mapObject(source.metadata);
  function isMissing(id){const key=normalizeId(id),path=canonicalPath(id);return !key||missing.has(String(id))||missingKeys.has(key)||Boolean(source.missing_by_reason?.[key])||(!resolved[key]&&!pieces[key]&&!generatedFiles[key]);}
  function lookup(table,id){const key=normalizeId(id);return key?(table[key]??table[String(id)]??table[canonicalPath(id)]??null):null;}
  return Object.freeze({
    schema_version:Number(source.schema_version??0),
    template_pools:pools,structures,jigsaw_structures:structures,processors,structure_sets:sets,structureSets:sets,pieces,resolved_templates:resolved,template_aliases:aliases,connectors,jigsaw_connectors:connectors,generated_files:generatedFiles,native,metadata,missing_templates:[...missing],missing_by_reason:mapObject(source.missing_by_reason),isMissingTemplate:isMissing,
    getPool:id=>lookup(pools,id),getStructure:id=>lookup(structures,id),getProcessor:id=>lookup(processors,id),getStructureSet:id=>lookup(sets,id),getPiece:id=>lookup(pieces,id),getResolved:id=>lookup(resolved,id),getAlias:id=>lookup(aliases,id)
  });
}

export const GENERATED_JIGSAW_DATA=normalizeGeneratedJigsawData();
export function getGeneratedJigsawData(){return GENERATED_JIGSAW_DATA;}
export function generatedTemplateId(id){return normalizeId(id);}
export function generatedCanonicalPath(id){return canonicalPath(id);}
export function generatedPiece(id){const key=normalizeId(id);if(!key||GENERATED_JIGSAW_DATA.isMissingTemplate(key))return null;return GENERATED_JIGSAW_DATA.getPiece(key);}
export function generatedPool(id){const v=GENERATED_JIGSAW_DATA.getPool(id);return v?.definition??v??null;}
export function generatedStructure(id){const v=GENERATED_JIGSAW_DATA.getStructure(id);return v?.definition??v??null;}
export function generatedProcessor(id){const v=GENERATED_JIGSAW_DATA.getProcessor(id);return v?.definition??v??null;}
export function generatedStructureSet(id){const v=GENERATED_JIGSAW_DATA.getStructureSet(id);return v?.definition??v??null;}
export function generatedResolvedTemplatePath(id){const key=normalizeId(id);if(!key||GENERATED_JIGSAW_DATA.isMissingTemplate(key))return null;const v=GENERATED_JIGSAW_DATA.getResolved(key);return typeof v==="string"?v:v?.source??GENERATED_JIGSAW_DATA.getAlias(key)?.source??null;}
export function generatedAlias(id){return GENERATED_JIGSAW_DATA.getAlias(id)??null;}
export function generatedFile(id){return GENERATED_JIGSAW_DATA.generated_files[normalizeId(id)]??GENERATED_JIGSAW_DATA.generated_files[String(id)]??null;}
export function generatedNativeJson(kind,id){const table=GENERATED_JIGSAW_DATA.native?.[kind];if(!table)return null;const key=normalizeId(id);return table[key]??table[String(id)]??null;}
