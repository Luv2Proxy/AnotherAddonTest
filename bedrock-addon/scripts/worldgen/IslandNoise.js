export class IslandNoise {
  constructor(layoutSeed) { this.layoutSeed = BigInt.asIntN(64, BigInt(layoutSeed)); }
  static mix(seed, x, z) {
    let h = BigInt.asIntN(64, BigInt(seed));
    h ^= BigInt.asIntN(64, BigInt(x) * -7046029254386353131n);
    h = BigInt.asIntN(64, (h << 17n) | ((h & ((1n << 64n) - 1n)) >> 47n));
    h ^= BigInt.asIntN(64, BigInt(z) * -4417276706812531889n);
    h ^= h >> 29n;
    h = BigInt.asIntN(64, h * 1609587929392839161n);
    h ^= h >> 32n;
    h = BigInt.asIntN(64, h * -7046029254386353131n);
    return BigInt.asIntN(64, h ^ (h >> 28n));
  }
  sample01(seed, salt) {
    const h = IslandNoise.mix(BigInt(seed) + BigInt(salt) * 31n, salt, 17);
    const masked = BigInt.asUintN(64, h) >> 11n;
    return Number(masked) / 9007199254740992;
  }
  sampleInt(seed, salt, min, max) { if (min === max) return min; return min + Math.floor(this.sample01(seed, salt) * (max - min + 1)); }
  sampleCell(cellX, cellZ, salt) { return this.sample01(IslandNoise.mix(this.layoutSeed + BigInt(salt), cellX, cellZ), salt); }
  sampleCellInt(cellX, cellZ, salt, min, max) { return this.sampleInt(IslandNoise.mix(this.layoutSeed + BigInt(salt), cellX, cellZ), salt, min, max); }
  sampleRange(seed, salt, min, max) { return min + (max - min) * this.sample01(seed, salt); }
  lattice2D(seed, x, z) { return this.toSigned(IslandNoise.mix(BigInt(seed), x, z)); }
  lattice3D(seed, x, y, z) {
    let h = IslandNoise.mix(BigInt(seed) ^ BigInt(y) * -7046029254386353131n, x, z);
    h ^= BigInt.asIntN(64, (BigInt(y) * -4417276706812531889n));
    h ^= h >> 29n; h = BigInt.asIntN(64, h * 1609587929392839161n); h ^= h >> 28n;
    return this.toSigned(h);
  }
  toSigned(h) { return (Number(BigInt.asUintN(64, h) >> 11n) / 9007199254740992) * 2 - 1; }
  static smooth(t) { return t * t * (3 - 2 * t); }
  value2D(seed, x, z) {
    const x0 = Math.floor(x), z0 = Math.floor(z), tx = IslandNoise.smooth(x-x0), tz = IslandNoise.smooth(z-z0);
    const a=this.lattice2D(seed,x0,z0),b=this.lattice2D(seed,x0+1,z0),c=this.lattice2D(seed,x0,z0+1),d=this.lattice2D(seed,x0+1,z0+1);
    return (a+(b-a)*tx)+((c+(d-c)*tx)-(a+(b-a)*tx))*tz;
  }
  value3D(seed,x,y,z) {
    const x0=Math.floor(x),y0=Math.floor(y),z0=Math.floor(z),x1=x0+1,y1=y0+1,z1=z0+1;
    const tx=IslandNoise.smooth(x-x0),ty=IslandNoise.smooth(y-y0),tz=IslandNoise.smooth(z-z0);
    const c000=this.lattice3D(seed,x0,y0,z0),c100=this.lattice3D(seed,x1,y0,z0),c010=this.lattice3D(seed,x0,y1,z0),c110=this.lattice3D(seed,x1,y1,z0),c001=this.lattice3D(seed,x0,y0,z1),c101=this.lattice3D(seed,x1,y0,z1),c011=this.lattice3D(seed,x0,y1,z1),c111=this.lattice3D(seed,x1,y1,z1);
    const x00=c000+(c100-c000)*tx,x10=c010+(c110-c010)*tx,x01=c001+(c101-c001)*tx,x11=c011+(c111-c011)*tx;
    return (x00+(x10-x00)*ty)+((x01+(x11-x01)*ty)-(x00+(x10-x00)*ty))*tz;
  }
  fbm2D(seed,x,z,octaves,gain) { let v=0,a=1,f=1,m=0; for(let i=0;i<octaves;i++){v+=this.value2D(BigInt(seed)+BigInt(i)*97n,x*f,z*f)*a;m+=a;a*=gain;f*=2;} return m?v/m:0; }
  ridgedFbm2D(seed,x,z,o,g){return 1-Math.abs(this.fbm2D(seed,x,z,o,g));}
  fbm3D(seed,x,y,z,o,g){let v=0,a=1,f=1,m=0;for(let i=0;i<o;i++){v+=this.value3D(BigInt(seed)+BigInt(i)*131n,x*f,y*f,z*f)*a;m+=a;a*=g;f*=2;}return m?v/m:0;}
  ridgedFbm3D(seed,x,y,z,o,g){return 1-Math.abs(this.fbm3D(seed,x,y,z,o,g));}
  static ellipseDensity(x,z,rx,rz){return 1-(x*x/(rx*rx)+z*z/(rz*rz));}
}
