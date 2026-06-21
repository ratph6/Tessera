// event-test — fire-and-watch harness for every WIRED Tessera event.
//
// Goal: confirm each event actually fires, WITHOUT spamming chat.
//   • every callback only bumps a Store counter (cheap, safe for tick/render/packet floods)
//   • the FIRST time an event fires it logs ONE line to the Tessera console (/te console) — never chat
//   • a HUD overlay lists every wired event: §agreen + count once seen, §7gray while still missing
//   • /eventtest        dump the full count table to the console
//   • /eventtestreset   zero all counters (re-arms the first-fire log + HUD colours)
//   • /eventtesthud     toggle the on-screen overlay
//
// How to drive it: load, then walk around / break a block / open inventory / take damage / hear a
// sound / send a chat line — watch entries flip green on screen. Anything still gray after you've
// tried to trigger it is a hook that didn't fire.
//
// Notes baked in from the other examples / the swc4j bytecode backend:
//   • state lives in Store, not module-level `let`s (swc4j boxes shared captures → VerifyError)
//   • a user callback can't call another user function yet, so the count/first-fire logic is inlined
//   • entity params MUST be typed EntityWrapper (untyped → Object, which swc4j can't pass through)
//   • no ternary / no `===` / no template strings — the bytecode backend is proven only on if/else,
//     `+` concat and `==`-free comparisons (>, >=, <), so subcommands are split like Cubed does
//   • we never call ChatLib.chat / never cancel anything → no feedback loops, no spam

import { Tessera, Event, ChatLib, Renderer, Store, EntityWrapper } from 'ratph6.tessera.api';

Store.setBool("hud", true); // HUD on by default; /eventtesthud flips it

ChatLib.chat("§b[event-test]§r loaded — open §e/te console§r, run §e/eventtest§r. HUD shows live hits.");

// ---- counters ----------------------------------------------------------------------------------
// Each block: bump count; on first fire log one console line and mark seen. Key scheme: n_<id> count,
// s_<id> seen-flag. Inlined everywhere because callbacks can't share a helper yet.

Tessera.register(Event.CHAT, (msg: string) => {
  Store.setNum("n_chat", Store.getNum("n_chat", 0) + 1);
  if (!Store.getBool("s_chat", false)) { Store.setBool("s_chat", true); Tessera.log("§a✓ first fire: chat"); }
}); // no reply → no loop

Tessera.register(Event.MESSAGE_SENT, (msg: string) => {
  Store.setNum("n_messageSent", Store.getNum("n_messageSent", 0) + 1);
  if (!Store.getBool("s_messageSent", false)) { Store.setBool("s_messageSent", true); Tessera.log("§a✓ first fire: messageSent"); }
}).setCancelable(false);

Tessera.register(Event.ACTION_BAR, (text: string) => {
  Store.setNum("n_actionBar", Store.getNum("n_actionBar", 0) + 1);
  if (!Store.getBool("s_actionBar", false)) { Store.setBool("s_actionBar", true); Tessera.log("§a✓ first fire: actionBar"); }
}).setCancelable(false);

Tessera.register(Event.TICK, (t: number) => {
  Store.setNum("n_tick", Store.getNum("n_tick", 0) + 1);
  if (!Store.getBool("s_tick", false)) { Store.setBool("s_tick", true); Tessera.log("§a✓ first fire: tick"); }
});

Tessera.register(Event.GAME_TICK, (t: number) => {
  Store.setNum("n_gameTick", Store.getNum("n_gameTick", 0) + 1);
  if (!Store.getBool("s_gameTick", false)) { Store.setBool("s_gameTick", true); Tessera.log("§a✓ first fire: gameTick"); }
});

Tessera.register(Event.STEP, (dt: number) => {
  Store.setNum("n_step", Store.getNum("n_step", 0) + 1);
  if (!Store.getBool("s_step", false)) { Store.setBool("s_step", true); Tessera.log("§a✓ first fire: step"); }
}).setFps(20);

Tessera.register(Event.GAME_LOAD, () => {
  Store.setNum("n_gameLoad", Store.getNum("n_gameLoad", 0) + 1);
  if (!Store.getBool("s_gameLoad", false)) { Store.setBool("s_gameLoad", true); Tessera.log("§a✓ first fire: gameLoad"); }
});

Tessera.register(Event.WORLD_LOAD, () => {
  Store.setNum("n_worldLoad", Store.getNum("n_worldLoad", 0) + 1);
  if (!Store.getBool("s_worldLoad", false)) { Store.setBool("s_worldLoad", true); Tessera.log("§a✓ first fire: worldLoad"); }
});

Tessera.register(Event.WORLD_UNLOAD, () => {
  Store.setNum("n_worldUnload", Store.getNum("n_worldUnload", 0) + 1);
  if (!Store.getBool("s_worldUnload", false)) { Store.setBool("s_worldUnload", true); Tessera.log("§a✓ first fire: worldUnload"); }
});

Tessera.register(Event.SERVER_CONNECT, (ip: string) => {
  Store.setNum("n_serverConnect", Store.getNum("n_serverConnect", 0) + 1);
  if (!Store.getBool("s_serverConnect", false)) { Store.setBool("s_serverConnect", true); Tessera.log("§a✓ first fire: serverConnect → " + ip); }
});

Tessera.register(Event.SERVER_DISCONNECT, (reason: string) => {
  Store.setNum("n_serverDisconnect", Store.getNum("n_serverDisconnect", 0) + 1);
  if (!Store.getBool("s_serverDisconnect", false)) { Store.setBool("s_serverDisconnect", true); Tessera.log("§a✓ first fire: serverDisconnect"); }
});

Tessera.register(Event.BLOCK_BREAK, (block) => {
  Store.setNum("n_blockBreak", Store.getNum("n_blockBreak", 0) + 1);
  if (!Store.getBool("s_blockBreak", false)) { Store.setBool("s_blockBreak", true); Tessera.log("§a✓ first fire: blockBreak"); }
}).setCancelable(false);

Tessera.register(Event.SPAWN_PARTICLE, (name: string) => {
  Store.setNum("n_spawnParticle", Store.getNum("n_spawnParticle", 0) + 1);
  if (!Store.getBool("s_spawnParticle", false)) { Store.setBool("s_spawnParticle", true); Tessera.log("§a✓ first fire: spawnParticle"); }
}).setCancelable(false);

Tessera.register(Event.SOUND_PLAY, (name: string) => {
  Store.setNum("n_soundPlay", Store.getNum("n_soundPlay", 0) + 1);
  if (!Store.getBool("s_soundPlay", false)) { Store.setBool("s_soundPlay", true); Tessera.log("§a✓ first fire: soundPlay → " + name); }
}).setCancelable(false);

Tessera.register(Event.ENTITY_DEATH, (entity: EntityWrapper) => {
  Store.setNum("n_entityDeath", Store.getNum("n_entityDeath", 0) + 1);
  if (!Store.getBool("s_entityDeath", false)) { Store.setBool("s_entityDeath", true); Tessera.log("§a✓ first fire: entityDeath"); }
});

Tessera.register(Event.RENDER_ENTITY, (entity: EntityWrapper) => {
  Store.setNum("n_renderEntity", Store.getNum("n_renderEntity", 0) + 1);
  if (!Store.getBool("s_renderEntity", false)) { Store.setBool("s_renderEntity", true); Tessera.log("§a✓ first fire: renderEntity"); }
}).setCancelable(false);

Tessera.register(Event.POST_RENDER_ENTITY, (entity: EntityWrapper) => {
  Store.setNum("n_postRenderEntity", Store.getNum("n_postRenderEntity", 0) + 1);
  if (!Store.getBool("s_postRenderEntity", false)) { Store.setBool("s_postRenderEntity", true); Tessera.log("§a✓ first fire: postRenderEntity"); }
}).setCancelable(false);

Tessera.register(Event.PACKET_RECEIVED, (packet, name: string) => {
  Store.setNum("n_packetReceived", Store.getNum("n_packetReceived", 0) + 1);
  if (!Store.getBool("s_packetReceived", false)) { Store.setBool("s_packetReceived", true); Tessera.log("§a✓ first fire: packetReceived"); }
}).setCancelable(false);

Tessera.register(Event.PACKET_SENT, (packet, name: string) => {
  Store.setNum("n_packetSent", Store.getNum("n_packetSent", 0) + 1);
  if (!Store.getBool("s_packetSent", false)) { Store.setBool("s_packetSent", true); Tessera.log("§a✓ first fire: packetSent"); }
}).setCancelable(false);

Tessera.register(Event.GUI_OPENED, (screen: string) => {
  Store.setNum("n_guiOpen", Store.getNum("n_guiOpen", 0) + 1);
  if (!Store.getBool("s_guiOpen", false)) { Store.setBool("s_guiOpen", true); Tessera.log("§a✓ first fire: guiOpen → " + screen); }
});

Tessera.register(Event.GUI_CLOSED, (screen: string) => {
  Store.setNum("n_guiClose", Store.getNum("n_guiClose", 0) + 1);
  if (!Store.getBool("s_guiClose", false)) { Store.setBool("s_guiClose", true); Tessera.log("§a✓ first fire: guiClose"); }
});

Tessera.register(Event.GUI_KEY, (keyEvent) => {
  Store.setNum("n_guiKey", Store.getNum("n_guiKey", 0) + 1);
  if (!Store.getBool("s_guiKey", false)) { Store.setBool("s_guiKey", true); Tessera.log("§a✓ first fire: guiKey"); }
}).setCancelable(false);

Tessera.register(Event.GUI_MOUSE_CLICK, (mouseEvent) => {
  Store.setNum("n_guiMouseClick", Store.getNum("n_guiMouseClick", 0) + 1);
  if (!Store.getBool("s_guiMouseClick", false)) { Store.setBool("s_guiMouseClick", true); Tessera.log("§a✓ first fire: guiMouseClick"); }
}).setCancelable(false);

Tessera.register(Event.GUI_DRAW_BACKGROUND, (screen: string) => {
  Store.setNum("n_guiDrawBackground", Store.getNum("n_guiDrawBackground", 0) + 1);
  if (!Store.getBool("s_guiDrawBackground", false)) { Store.setBool("s_guiDrawBackground", true); Tessera.log("§a✓ first fire: guiDrawBackground"); }
});

Tessera.register(Event.INVENTORY_OPEN, (screen: string) => {
  Store.setNum("n_inventoryOpen", Store.getNum("n_inventoryOpen", 0) + 1);
  if (!Store.getBool("s_inventoryOpen", false)) { Store.setBool("s_inventoryOpen", true); Tessera.log("§a✓ first fire: inventoryOpen"); }
});

Tessera.register(Event.INVENTORY_CLOSE, (screen: string) => {
  Store.setNum("n_inventoryClose", Store.getNum("n_inventoryClose", 0) + 1);
  if (!Store.getBool("s_inventoryClose", false)) { Store.setBool("s_inventoryClose", true); Tessera.log("§a✓ first fire: inventoryClose"); }
});

// ---- HUD overlay -------------------------------------------------------------------------------
// Counts itself (renderOverlay is wired too) and paints the scoreboard. `p` is the colour prefix:
// §a green once a count > 0, else §7 gray. `p` and `y` are plain locals (not captured by any nested
// lambda) so no swc4j boxing risk; we avoid ternary entirely with if/else reassignment.

Tessera.register(Event.RENDER_OVERLAY, () => {
  Store.setNum("n_renderOverlay", Store.getNum("n_renderOverlay", 0) + 1);
  if (!Store.getBool("s_renderOverlay", false)) { Store.setBool("s_renderOverlay", true); Tessera.log("§a✓ first fire: renderOverlay"); }
  if (!Store.getBool("hud", true)) return;

  const white = Renderer.color(255, 255, 255);
  let y: number = 4;
  let p: string = "§7";
  Renderer.drawStringWithShadow("§b[event-test] §7green=fired gray=missing", 4, y, white); y += 12;

  p = "§7"; if (Store.getNum("n_chat", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "chat " + Store.getNum("n_chat", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_messageSent", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "messageSent " + Store.getNum("n_messageSent", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_actionBar", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "actionBar " + Store.getNum("n_actionBar", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_tick", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "tick " + Store.getNum("n_tick", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_gameTick", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "gameTick " + Store.getNum("n_gameTick", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_step", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "step " + Store.getNum("n_step", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_gameLoad", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "gameLoad " + Store.getNum("n_gameLoad", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_worldLoad", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "worldLoad " + Store.getNum("n_worldLoad", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_worldUnload", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "worldUnload " + Store.getNum("n_worldUnload", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_serverConnect", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "serverConnect " + Store.getNum("n_serverConnect", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_serverDisconnect", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "serverDisconnect " + Store.getNum("n_serverDisconnect", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_blockBreak", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "blockBreak " + Store.getNum("n_blockBreak", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_spawnParticle", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "spawnParticle " + Store.getNum("n_spawnParticle", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_soundPlay", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "soundPlay " + Store.getNum("n_soundPlay", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_entityDeath", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "entityDeath " + Store.getNum("n_entityDeath", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_renderEntity", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "renderEntity " + Store.getNum("n_renderEntity", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_postRenderEntity", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "postRenderEntity " + Store.getNum("n_postRenderEntity", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_packetReceived", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "packetReceived " + Store.getNum("n_packetReceived", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_packetSent", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "packetSent " + Store.getNum("n_packetSent", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_guiOpen", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "guiOpen " + Store.getNum("n_guiOpen", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_guiClose", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "guiClose " + Store.getNum("n_guiClose", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_guiKey", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "guiKey " + Store.getNum("n_guiKey", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_guiMouseClick", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "guiMouseClick " + Store.getNum("n_guiMouseClick", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_guiDrawBackground", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "guiDrawBackground " + Store.getNum("n_guiDrawBackground", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_inventoryOpen", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "inventoryOpen " + Store.getNum("n_inventoryOpen", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_inventoryClose", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "inventoryClose " + Store.getNum("n_inventoryClose", 0), 4, y, white); y += 10;
  p = "§7"; if (Store.getNum("n_renderOverlay", 0) > 0) p = "§a";
  Renderer.drawStringWithShadow(p + "renderOverlay " + Store.getNum("n_renderOverlay", 0), 4, y, white); y += 10;
}).setCancelable(false);

// ---- commands ----------------------------------------------------------------------------------
// Split into separate /names (like Cubed) to avoid string-compare on subcommands. All output goes to
// the Tessera console via Tessera.log — never chat.

Tessera.register(Event.COMMAND, (args) => {
  Tessera.log("§b[event-test] counts —");
  Tessera.log("  chat=" + Store.getNum("n_chat", 0) + " messageSent=" + Store.getNum("n_messageSent", 0) + " actionBar=" + Store.getNum("n_actionBar", 0));
  Tessera.log("  tick=" + Store.getNum("n_tick", 0) + " gameTick=" + Store.getNum("n_gameTick", 0) + " step=" + Store.getNum("n_step", 0));
  Tessera.log("  gameLoad=" + Store.getNum("n_gameLoad", 0) + " worldLoad=" + Store.getNum("n_worldLoad", 0) + " worldUnload=" + Store.getNum("n_worldUnload", 0));
  Tessera.log("  serverConnect=" + Store.getNum("n_serverConnect", 0) + " serverDisconnect=" + Store.getNum("n_serverDisconnect", 0));
  Tessera.log("  blockBreak=" + Store.getNum("n_blockBreak", 0) + " spawnParticle=" + Store.getNum("n_spawnParticle", 0) + " soundPlay=" + Store.getNum("n_soundPlay", 0) + " entityDeath=" + Store.getNum("n_entityDeath", 0));
  Tessera.log("  renderEntity=" + Store.getNum("n_renderEntity", 0) + " postRenderEntity=" + Store.getNum("n_postRenderEntity", 0) + " renderOverlay=" + Store.getNum("n_renderOverlay", 0));
  Tessera.log("  packetReceived=" + Store.getNum("n_packetReceived", 0) + " packetSent=" + Store.getNum("n_packetSent", 0));
  Tessera.log("  guiOpen=" + Store.getNum("n_guiOpen", 0) + " guiClose=" + Store.getNum("n_guiClose", 0) + " guiKey=" + Store.getNum("n_guiKey", 0) + " guiMouseClick=" + Store.getNum("n_guiMouseClick", 0) + " guiDrawBackground=" + Store.getNum("n_guiDrawBackground", 0));
  Tessera.log("  inventoryOpen=" + Store.getNum("n_inventoryOpen", 0) + " inventoryClose=" + Store.getNum("n_inventoryClose", 0));
  Tessera.log("§7  also: /eventtestreset  /eventtesthud");
}).setName("eventtest");

Tessera.register(Event.COMMAND, (args) => {
  Store.clear();
  Store.setBool("hud", true);
  Tessera.log("§e[event-test] counters reset");
}).setName("eventtestreset");

Tessera.register(Event.COMMAND, (args) => {
  Tessera.log("§e[event-test] HUD " + Store.toggle("hud"));
}).setName("eventtesthud");
