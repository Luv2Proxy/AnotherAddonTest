// Maps copied vanilla .mcstructure hierarchies to logical piece roles.
export const ASSET_ROLES=Object.freeze({
"village/":{kind:"jigsaw",root:"village/"},"pillageroutpost/":{kind:"template",roots:["pillageroutpost/"]},"ruined_portal/":{kind:"template",roots:["ruined_portal/"]},"shipwreck/":{kind:"template",roots:["shipwreck/"]},"ruin/":{kind:"template",roots:["ruin/"]},"underwater_ruin/":{kind:"template",roots:["underwater_ruin/"]},"igloo/":{kind:"template",roots:["igloo/"]},"trail_ruins/":{kind:"jigsaw",root:"trail_ruins/"},
"ancient_city/":{kind:"composite",roles:{entrance:"ancient_city/city/entrance/",center:"ancient_city/city_center/",structure:"ancient_city/structures/",wall:"ancient_city/walls/"}},
"bastion/":{kind:"composite",roles:{bridge:"bastion/bridge/",hoglin_stable:"bastion/hoglin_stable/",treasure:"bastion/treasure/",units:"bastion/units/"}},
"endcity/":{kind:"jigsaw",root:"endcity/"},"mansion/":{kind:"template",roots:["mansion/"]},"coralcrust/":{kind:"template",roots:["coralcrust/"]},"fossils/":{kind:"template",roots:["fossils/"]},"nether_fossils/":{kind:"template",roots:["nether_fossils/"]},"trial_chambers/":{kind:"composite",roles:{room:"trial_chambers/"}}
});
function norm(s){return String(s??"").replace(/\\/g,"/").replace(/^.*?:/,"").replace(/\.mcstructure$/i,"");}
export function assetFamily(id){const n=norm(id);return Object.keys(ASSET_ROLES).find(f=>n.startsWith(f))??null;}
export function assetRole(id){const n=norm(id),f=assetFamily(n),spec=f?ASSET_ROLES[f]:null;if(!spec)return null;if(spec.roles)for(const[k,p]of Object.entries(spec.roles))if(n.startsWith(p))return{role:k,family:f,prefix:p};return{kind:spec.kind,family:f,prefix:f};}
export function assetCandidates(entries,family,role=null){const spec=ASSET_ROLES[family];if(!spec)return[];const roots=role&&spec.roles?.[role]?[spec.roles[role]]:(spec.roots??[spec.root??family]);return entries.filter(e=>{const n=norm(e.id);return roots.some(r=>n.startsWith(r))&&!n.endsWith("/index")&&!n.includes("/index/");});}
export function chooseAsset(entries,family,seed,role=null){const c=assetCandidates(entries,family,role);return c.length?c[Math.abs(Number(seed)%c.length)]:null;}
export function splitComposite(entries,family){const spec=ASSET_ROLES[family];if(!spec?.roles)return{all:assetCandidates(entries,family)};const out={};for(const role of Object.keys(spec.roles))out[role]=assetCandidates(entries,family,role);return out;}
export function validateAssetMapping(entries){return Object.keys(ASSET_ROLES).map(family=>{const spec=ASSET_ROLES[family],count=assetCandidates(entries,family).length;return{family,kind:spec.kind,count,empty:count===0,roles:Object.fromEntries(Object.keys(spec.roles??{}).map(r=>[r,assetCandidates(entries,family,r).length]))};});}
