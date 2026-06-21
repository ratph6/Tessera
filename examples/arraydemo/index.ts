// arraydemo — the GraalJS engine ("engine": "graal" in tessera.json).
//
// Contrast with examples/cubed (the bytecode engine), which can't do any of this: there state must
// live in `Store`, args go through `Args.get`/`Num.parse`, and every param needs an explicit type.
// Here it's just normal TypeScript running on a real JavaScript runtime.

import { Tessera, Event, ChatLib } from 'ratph6.tessera.api';

// Real module-level state: a `let` closed over by callbacks. No `Store` needed.
let hits = 0;
const recent: string[] = [];

// /sum 3 8 1 4  ->  parses with real array methods, no Args/Num helpers.
Tessera.register(Event.COMMAND, (args) => {
  const nums = [...args].map(Number).filter((n) => !Number.isNaN(n));
  const total = nums.reduce((a, b) => a + b, 0);
  ChatLib.chat(`§b[arraydemo]§r sum of [${nums.join(", ")}] = ${total}`);
}).setName("sum");

// /lastchats — keep a rolling window of the last 5 chat lines using a closure + array.
Tessera.register(Event.CHAT, (message: string) => {
  hits++;
  recent.push(message);
  if (recent.length > 5) recent.shift();
});

Tessera.register(Event.COMMAND, () => {
  ChatLib.chat(`§b[arraydemo]§r seen ${hits} chats; last ${recent.length}:`);
  recent.forEach((m, i) => ChatLib.chat(`  §7${i + 1}.§r ${m}`));
}).setName("lastchats");

ChatLib.chat("§b[arraydemo]§r loaded — try §e/sum 3 8 1 4§r and §e/lastchats§r");
