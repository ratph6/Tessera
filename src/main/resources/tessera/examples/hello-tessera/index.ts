// hello-tessera — a starter Tessera module (auto-installed on first run).
//
// Tessera compiles this TypeScript straight to JVM bytecode. Note how clean it is:
//   • no main() — top-level code runs on load
//   • no export — just write your code
//   • arrow-function callbacks, registered against the Event enum (like ChatTriggers)
//
// Each callback takes ONE argument (the event's value: the chat message, the command args, ...).
// To cancel a cancellable event, call Tessera.cancelEvent() inside the callback.
//
// Edit this file and run /te reload in game to see changes instantly.

import { Tessera, Event, ChatLib, Player } from 'ratph6.tessera.api';

ChatLib.chat("§a[hello-tessera] loaded! Say something containing 'ping', or run /coords.");

Tessera.register(Event.CHAT, (message) => {
  // Don't echo the trigger word — your reply also goes through chat and would re-trigger this.
  ChatLib.chat("§epong!");
  // Tessera.cancelEvent();   // uncomment to hide the original message
}).setContains().setCriteria("ping");

Tessera.register(Event.COMMAND, (args) => {
  ChatLib.chat("§bXYZ: " + Player.getX() + ", " + Player.getY() + ", " + Player.getZ());
}).setName("coords");
