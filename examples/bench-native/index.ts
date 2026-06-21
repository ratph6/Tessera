// Tessera benchmark — native compute (isPrime), mirroring the ChatTriggers comparison.
// Tessera compiles this to JVM bytecode, so it runs at ~native JVM speed.
//
// Runs once on load (edit `n` and /te reload to change the size). The prime test is inlined
// because swc4j's bytecode compiler can't yet call one user function from another.

import { Tessera, ChatLib } from 'ratph6.tessera.api';

const n = 1000000;
const start = Tessera.millis();
let count = 0;

for (let i = 0; i < n; i++) {
  let prime = true;
  if (i < 2) {
    prime = false;
  } else if (i % 2 === 0) {
    prime = i === 2;
  } else {
    for (let j = 3; j * j <= i; j += 2) {
      if (i % j === 0) { prime = false; break; }
    }
  }
  if (prime) count++;
}

ChatLib.chat("§a[bench-native] isPrime x" + n + "  ->  " + (Tessera.millis() - start) + " ms  (" + count + " primes)");
