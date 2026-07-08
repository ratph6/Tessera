// MovementUtils — press/hold/release movement keys programmatically, with auto-release after N ticks.
//
// Keys are driven through KeyMapping.setDown(), the same switch the game reads for movement, so this
// works while moving normally and respects the game's own input the moment you release.
//
//   movement.press("forward");            // hold W until you release it
//   movement.release("forward");          // let go
//   movement.tap("jump", 1);              // press for 1 tick, auto-release (a single jump)
//   movement.sneak(20);                   // sneak for 20 ticks (~1s), then release
//   movement.jump();                      // one jump
//   movement.releaseAll();                // let go of everything MovementUtils is holding
//
// Key names: forward, back, left, right, jump, sneak, sprint, attack, use.

import { Tessera } from "ratph6.tessera.api";

const mc = Minecraft.getInstance();

type KeyName =
  | "forward" | "back" | "left" | "right"
  | "jump" | "sneak" | "sprint" | "attack" | "use";

class MovementUtils {
  // keys this util is currently holding, plus the scheduled-release task id (0 = held indefinitely)
  private held = new Map<KeyName, number>();

  private mapping(key: KeyName): any {
    const o = mc.options;
    switch (key) {
      case "forward": return o.keyUp;
      case "back": return o.keyDown;
      case "left": return o.keyLeft;
      case "right": return o.keyRight;
      case "jump": return o.keyJump;
      case "sneak": return o.keyShift;
      case "sprint": return o.keySprint;
      case "attack": return o.keyAttack;
      case "use": return o.keyUse;
    }
  }

  // hold a key down until release() (or forever). Safe to call repeatedly.
  press(key: KeyName) {
    const m = this.mapping(key);
    if (!m) return;
    // if a timed hold was pending, cancel it — press() means "hold, no auto-release"
    this.cancelTask(key);
    m.setDown(true);
    this.held.set(key, 0);
  }

  release(key: KeyName) {
    const m = this.mapping(key);
    if (!m) return;
    this.cancelTask(key);
    m.setDown(false);
    this.held.delete(key);
  }

  // press for `ticks` client ticks, then auto-release (20 ticks ~= 1 second)
  tap(key: KeyName, ticks = 1) {
    const m = this.mapping(key);
    if (!m) return;
    this.cancelTask(key);
    m.setDown(true);
    const id = Tessera.scheduleTask(() => {
      m.setDown(false);
      this.held.delete(key);
    }, Math.max(1, ticks));
    this.held.set(key, id);
  }

  isHeld(key: KeyName): boolean {
    return this.held.has(key);
  }

  releaseAll() {
    for (const key of Array.from(this.held.keys())) this.release(key);
  }

  // --- convenience --------------------------------------------------------------------------------
  jump(ticks = 1) { this.tap("jump", ticks); }
  sneak(ticks?: number) { ticks ? this.tap("sneak", ticks) : this.press("sneak"); }
  stopSneak() { this.release("sneak"); }
  sprint(on = true) { on ? this.press("sprint") : this.release("sprint"); }
  forward(ticks?: number) { ticks ? this.tap("forward", ticks) : this.press("forward"); }
  back(ticks?: number) { ticks ? this.tap("back", ticks) : this.press("back"); }

  // left click / right click as held actions (attack/use keys)
  leftClick(ticks = 1) { this.tap("attack", ticks); }
  rightClick(ticks = 1) { this.tap("use", ticks); }

  private cancelTask(key: KeyName) {
    const id = this.held.get(key);
    if (id && id > 0) Tessera.clearTimer(id);
  }
}

export const movement = new MovementUtils();
