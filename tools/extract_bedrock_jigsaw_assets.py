#!/usr/bin/env python3
"""Extract Bedrock .mcstructure jigsaws plus worldgen template pools.

Usage:
  py tools/extract_bedrock_jigsaw_assets.py --structures bedrock-addon/structures --out bedrock-addon/scripts/worldgen/generated/jigsaw-data.json
  py tools/extract_bedrock_jigsaw_assets.py --auto --out ...

The --auto mode searches common Minecraft for Windows installation locations and
all user development behavior packs. It never invents missing pool data.
"""
from __future__ import annotations
import argparse, json, os, struct
from pathlib import Path

T_END,T_BYTE,T_SHORT,T_INT,T_LONG,T_FLOAT,T_DOUBLE,T_BA,T_STRING,T_LIST,T_COMPOUND,T_IA,T_LA=range(13)
FACING={0:'down',1:'up',2:'north',3:'south',4:'west',5:'east'}
VEC={'down':(0,-1,0),'up':(0,1,0),'north':(0,0,-1),'south':(0,0,1),'west':(-1,0,0),'east':(1,0,0)}
OPP={'down':'up','up':'down','north':'south','south':'north','west':'east','east':'west'}

class NBT:
  def __init__(self,b): self.b=b; self.p=0
  def need(self,n):
    if self.p+n>len(self.b): raise ValueError('truncated NBT')
  def u8(self): self.need(1); x=self.b[self.p]; self.p+=1; return x
  def u16(self): self.need(2); x=struct.unpack_from('<H',self.b,self.p)[0]; self.p+=2; return x
  def i16(self): self.need(2); x=struct.unpack_from('<h',self.b,self.p)[0]; self.p+=2; return x
  def i32(self): self.need(4); x=struct.unpack_from('<i',self.b,self.p)[0]; self.p+=4; return x
  def i64(self): self.need(8); x=struct.unpack_from('<q',self.b,self.p)[0]; self.p+=8; return x
  def f32(self): self.need(4); x=struct.unpack_from('<f',self.b,self.p)[0]; self.p+=4; return x
  def f64(self): self.need(8); x=struct.unpack_from('<d',self.b,self.p)[0]; self.p+=8; return x
  def s(self):
    n=self.u16(); self.need(n); x=self.b[self.p:self.p+n].decode('utf8','replace'); self.p+=n; return x
  def v(self,t):
    if t==T_BYTE:return self.u8()
    if t==T_SHORT:return self.i16()
    if t==T_INT:return self.i32()
    if t==T_LONG:return self.i64()
    if t==T_FLOAT:return self.f32()
    if t==T_DOUBLE:return self.f64()
    if t==T_BA:
      n=self.i32(); self.need(n); x=list(self.b[self.p:self.p+n]); self.p+=n; return x
    if t==T_STRING:return self.s()
    if t==T_LIST:
      et=self.u8(); n=self.i32(); return [self.v(et) for _ in range(n)]
    if t==T_COMPOUND:
      d={}
      while True:
        ct=self.u8()
        if ct==T_END:return d
        d[self.s()]=self.v(ct)
    if t==T_IA:
      n=self.i32(); return [self.i32() for _ in range(n)]
    if t==T_LA:
      n=self.i32(); return [self.i64() for _ in range(n)]
    raise ValueError(f'unsupported NBT tag {t}')
  def root(self):
    if self.u8()!=T_COMPOUND: raise ValueError('root is not compound')
    self.s(); return self.v(T_COMPOUND)

def xyz(a): return {'x':int(a[0]),'y':int(a[1]),'z':int(a[2])} if isinstance(a,list) and len(a)>=3 else None

def idx_xyz(i,size):
  x=i%size[0]; q=i//size[0]; y=q%size[1]; z=q//size[1]
  return {'x':x,'y':y,'z':z}

def structure_id(root,p):
  r=p.relative_to(root).as_posix(); parts=r.split('/'); parts[-1]=Path(parts[-1]).stem
  return (parts[0]+':'+('/'.join(parts[1:]) if len(parts)>1 else parts[0])) if len(parts)>1 else 'unknown:'+parts[0]

def parse_piece(root,p):
  n=NBT(p.read_bytes()).root(); st=n.get('structure',{}); sz=xyz(n.get('size')) or {'x':0,'y':0,'z':0}
  size=(sz['x'],sz['y'],sz['z']); pal=st.get('palette',{}).get('default',{}); bp=pal.get('block_palette',[]); inds=st.get('block_indices',[]); pd=pal.get('block_position_data',{})
  primary=inds[0] if inds and isinstance(inds[0],list) else []
  js=[]
  for k,e in pd.items():
    try:i=int(k)
    except:continue
    be=e.get('block_entity_data') if isinstance(e,dict) else None
    if not isinstance(be,dict) or str(be.get('id','')).lower() not in ('jigsawblock','minecraft:jigsaw'): continue
    pos=idx_xyz(i,size) if all(size) else None; pi=primary[i] if 0<=i<len(primary) else None; perm=bp[pi] if isinstance(pi,int) and 0<=pi<len(bp) else {}
    states=perm.get('states',{}) if isinstance(perm,dict) else {}; fd=states.get('facing_direction'); facing=FACING.get(int(fd),'unknown') if isinstance(fd,(int,float)) else 'unknown'
    js.append({'position':pos,'position_index':i,'facing':facing,'facing_direction':fd if fd is not None else 'unknown','rotation':states.get('rotation','unknown'),'joint':be.get('joint','unknown'),'name':be.get('name','unknown'),'target':be.get('target','unknown'),'pool':be.get('target_pool','unknown'),'final_state':be.get('final_state','unknown'),'selection_priority':be.get('selection_priority','unknown'),'placement_priority':be.get('placement_priority','unknown')})
  return {'id':structure_id(root,p),'source':p.relative_to(root).as_posix(),'size':sz,'jigsaws':js,'entities':st.get('entities',[]),'format_version':n.get('format_version','unknown')}

def find_roots(auto):
  roots=[]
  if os.name=='nt':
    la=Path(os.environ.get('LOCALAPPDATA',''))
    roots += [la/'Packages'/'Microsoft.MinecraftUWP_8wekyb3d8bbwe'/'LocalState'/'games'/'com.mojang', la/'Packages'/'Microsoft.MinecraftWindowsBeta_8wekyb3d8bbwe'/'LocalState'/'games'/'com.mojang']
    for base in [Path(os.environ.get('ProgramFiles','C:/Program Files'))/'WindowsApps',Path('D:/XboxGames/Minecraft for Windows/Content'),Path('C:/XboxGames/Minecraft for Windows/Content')]:
      if base.exists():
        roots += list(base.glob('Minecraft*')) if base.name=='WindowsApps' else [base]
  return [r for r in roots if r.exists()]

def scan_worldgen(root):
  pools={}; structures={}; processors={}; sets={}
  for kind,folder,out in [('template_pools','template_pools',pools),('structures','structures',structures),('processors','processors',processors),('structure_sets','structure_sets',sets)]:
    for p in root.rglob('*.json'):
      if folder not in p.parts: continue
      try:d=json.loads(p.read_text(encoding='utf8-sig'))
      except:continue
      key=None; obj=d.get('minecraft:template_pool') or d.get('minecraft:jigsaw') or d.get('minecraft:processor_list') or d.get('minecraft:structure_set')
      if isinstance(obj,dict): key=(obj.get('description') or {}).get('identifier') or p.stem; out[key]={'source':str(p),'definition':obj}
  return pools,structures,processors,sets

def main():
  ap=argparse.ArgumentParser(); ap.add_argument('--structures'); ap.add_argument('--minecraft-root',action='append',default=[]); ap.add_argument('--auto',action='store_true'); ap.add_argument('--out',required=True); a=ap.parse_args()
  roots=[Path(x).resolve() for x in a.minecraft_root]
  if a.auto: roots += find_roots(True)
  pieces=[]; errors=[]
  if a.structures:
    sr=Path(a.structures).resolve()
    for p in sorted(sr.rglob('*.mcstructure')):
      try: pieces.append(parse_piece(sr,p))
      except Exception as e: errors.append({'source':str(p),'error':str(e)})
  pools={}; structures={}; processors={}; sets={}
  for r in roots:
    candidates=[r/'behavior_packs'/'vanilla',r/'data',r/'Content'/'data'/'behavior_packs'/'vanilla',r/'Content'/'data']
    for c in candidates:
      if c.exists():
        p,s,pr,ss=scan_worldgen(c); pools.update(p); structures.update(s); processors.update(pr); sets.update(ss)
  families={}
  for x in pieces: families.setdefault(x['id'].split(':',1)[0],[]).append(x)
  out={'schema_version':2,'format':{'mcstructure':'little-endian NBT','index_formula':'x + size.x * (y + size.y * z)'},'pieces':pieces,'families':families,'template_pools':pools,'jigsaw_structures':structures,'processors':processors,'structure_sets':sets,'errors':errors,'sources':{'minecraft_roots':[str(x) for x in roots]}}
  Path(a.out).parent.mkdir(parents=True,exist_ok=True); Path(a.out).write_text(json.dumps(out,indent=2,ensure_ascii=False),encoding='utf8')
  print(f'pieces={len(pieces)} pools={len(pools)} structures={len(structures)} processors={len(processors)} errors={len(errors)}')
if __name__=='__main__': main()
