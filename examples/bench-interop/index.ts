// Tessera benchmark — JS<->Java interop, mirroring the ChatTriggers comparison.
// Creates n^3 Minecraft BlockPos objects. In Tessera, `new BlockPos(...)` compiles to a direct JVM
// `new` + invokespecial (no marshalling), so interop is essentially free vs JS-engine mods.
//
// Runs once on load (30^3 = 27,000, matching the comparison's 27k row).

import { Tessera, ChatLib } from 'ratph6.tessera.api';
import { BlockPos } from 'net.minecraft.core';

const n = 30;
const x = -1674;
const y = 4;
const z = 1495;
const start = Tessera.millis();
let made = 0;
let sink = 0; // touch each BlockPos so the allocation isn't optimised away

for (let x1 = x; x1 < x + n; x1++) {
  for (let z1 = z; z1 < z + n; z1++) {
    for (let y1 = y; y1 < y + n; y1++) {
      const p = new BlockPos(x1, y1, z1);
      sink += p.getX();
      made++;
    }
  }
}

ChatLib.chat("§b[bench-interop] new BlockPos x" + made + "  ->  " + (Tessera.millis() - start) + " ms");
