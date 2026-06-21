// Cubed — Tessera port of the ChatTriggers "Cubed" module (ratph6 & Slic_e).
//
// Scales and/or spins the player model by pushing transforms onto the entity's pose stack during
// render (Event.RENDER_ENTITY). The original's Amaterasu GUI is replaced with chat commands.
//
//   /cubed                      show status + command help
//   /cubedtoggle                toggle your own size
//   /cubedsize <x> <y> <z>      set your size (1 1 1 = normal; negative y flips you upside-down)
//   /cubedglobal                toggle global sizes (other players, from the remote table)
//   /fetchcubedglobals          re-fetch the global size table
//   /cubedspin                  toggle spinning
//   /cubedaxis <a> <b> <c>      spin axis (1/0 per axis, like the original)
//   /cubedspeed <n>             spin speed (degrees per step)
//
// State lives in Store (a static API), not module-level `let`s: swc4j boxes each reassigned captured
// variable and miscomputes the stack when one callback captures many (→ VerifyError). Reading state
// through Store keeps every callback capture-free.

import { Tessera, Event, ChatLib, Tessellator, PlayerScales, Num, Args, Store, EntityWrapper } from 'ratph6.tessera.api';

const SIZES_URL = "https://raw.githubusercontent.com/ratph6/Cubed/refs/heads/main/sizes.json";

// defaults
Store.setNum("sx", 1); Store.setNum("sy", 1); Store.setNum("sz", 1);
Store.setBool("globalOn", true);
Store.setNum("axisX", 0); Store.setNum("axisY", 1); Store.setNum("axisZ", 0);
Store.setNum("speed", 6); Store.setNum("rot", 0);

ChatLib.chat("§b[Cubed]§r loaded — run §e/cubed§r for commands.");
PlayerScales.fetch(SIZES_URL);

// Advance the spin angle over time (the original used a 100fps step trigger).
Tessera.register(Event.STEP, (dt) => {
  if (!Store.getBool("spinOn", false)) return;
  let r: number = Store.getNum("rot", 0) + Store.getNum("speed", 6);
  if (r >= 360) r = r - 360;
  if (r < 0) r = r + 360;
  Store.setNum("rot", r);
}).setFps(100);

// Scale / spin the player model. The pose is already at the entity's origin, and Tessera auto-pops after
// the model renders, so we only ever push here. `entity` MUST be typed (an untyped param compiles to
// Object, which swc4j can't negate / do arithmetic on / pass to typed methods).
Tessera.register(Event.RENDER_ENTITY, (entity: EntityWrapper) => {
  if (!entity.isPlayer()) return;

  if (Store.getBool("sizeOn", false) && entity.isLocalPlayer()) {
    Tessellator.pushMatrix();
    let ay: number = Store.getNum("sy", 1);
    if (ay < 0) {
      // negative Y: flip upside-down (mirror of the original's translate+rotate dance)
      ay = -ay;
      Tessellator.rotate(180, 1, 0, 0);
      Tessellator.rotate(2 * entity.getYaw() + 180, 0, 1, 0);
    }
    Tessellator.scale(Store.getNum("sx", 1), ay, Store.getNum("sz", 1));
    if (Store.getBool("spinOn", false)) Tessellator.rotate(Store.getNum("rot", 0), Store.getNum("axisX", 0), Store.getNum("axisY", 1), Store.getNum("axisZ", 0));
    return;
  }

  if (Store.getBool("globalOn", true) && PlayerScales.has(entity.getName())) {
    const name = entity.getName();
    Tessellator.pushMatrix();
    Tessellator.scale(PlayerScales.getX(name), PlayerScales.getY(name), PlayerScales.getZ(name));
    if (Store.getBool("spinOn", false)) Tessellator.rotate(Store.getNum("rot", 0), Store.getNum("axisX", 0), Store.getNum("axisY", 1), Store.getNum("axisZ", 0));
  }
}).setCancelable(false);

// ---- commands ----------------------------------------------------------------------------------

Tessera.register(Event.COMMAND, (args) => {
  ChatLib.chat("§b[Cubed]§r size=" + Store.getBool("sizeOn", false) + " (" + Store.getNum("sx", 1) + " " + Store.getNum("sy", 1) + " " + Store.getNum("sz", 1) + ")  global=" + Store.getBool("globalOn", true) + "  spin=" + Store.getBool("spinOn", false) + " (speed " + Store.getNum("speed", 6) + ")");
  ChatLib.chat("§7/cubedtoggle  /cubedsize x y z  /cubedglobal  /fetchcubedglobals  /cubedspin  /cubedaxis a b c  /cubedspeed n");
}).setName("cubed");

Tessera.register(Event.COMMAND, (args) => {
  ChatLib.chat("§b[Cubed]§r your size is now " + Store.toggle("sizeOn"));
}).setName("cubedtoggle");

Tessera.register(Event.COMMAND, (args) => {
  if (Args.count(args) >= 3) {
    Store.setNum("sx", Num.parse(Args.get(args, 0), 1));
    Store.setNum("sy", Num.parse(Args.get(args, 1), 1));
    Store.setNum("sz", Num.parse(Args.get(args, 2), 1));
    Store.setBool("sizeOn", true);
    ChatLib.chat("§b[Cubed]§r size set to " + Store.getNum("sx", 1) + " " + Store.getNum("sy", 1) + " " + Store.getNum("sz", 1));
  } else {
    ChatLib.chat("§cusage: /cubedsize <x> <y> <z>");
  }
}).setName("cubedsize");

Tessera.register(Event.COMMAND, (args) => {
  ChatLib.chat("§b[Cubed]§r global sizes are now " + Store.toggle("globalOn"));
}).setName("cubedglobal");

Tessera.register(Event.COMMAND, (args) => {
  PlayerScales.fetch(SIZES_URL);
  ChatLib.chat("§b[Cubed]§r fetching global sizes...");
}).setName("fetchcubedglobals");

Tessera.register(Event.COMMAND, (args) => {
  ChatLib.chat("§b[Cubed]§r spin is now " + Store.toggle("spinOn"));
}).setName("cubedspin");

Tessera.register(Event.COMMAND, (args) => {
  if (Args.count(args) >= 3) {
    Store.setNum("axisX", Num.parse(Args.get(args, 0), 0));
    Store.setNum("axisY", Num.parse(Args.get(args, 1), 1));
    Store.setNum("axisZ", Num.parse(Args.get(args, 2), 0));
    ChatLib.chat("§b[Cubed]§r spin axis set to " + Store.getNum("axisX", 0) + " " + Store.getNum("axisY", 1) + " " + Store.getNum("axisZ", 0));
  } else {
    ChatLib.chat("§cusage: /cubedaxis <a> <b> <c>  (1 or 0 per axis)");
  }
}).setName("cubedaxis");

Tessera.register(Event.COMMAND, (args) => {
  if (Args.count(args) >= 1) Store.setNum("speed", Num.parse(Args.get(args, 0), 6));
  ChatLib.chat("§b[Cubed]§r spin speed set to " + Store.getNum("speed", 6));
}).setName("cubedspeed");
