// ConfigSystem — a chat "GUI" driven by clickable text + commands.
//
// Register settings once, get a live clickable menu in chat (/<command>) and a persistent JSON file
// on disk. Clicking a line toggles / cycles / steps it; everything is also reachable by typing the
// command, so it works even if chat clicks are disabled.
//
// Quick start:
//   const cfg = new Config("mymod", "cfg");
//   cfg.category("Combat");
//   cfg.toggle("aura",  "Kill Aura", false);
//   cfg.number("range", "Reach",     3, { min: 1, max: 6, step: 0.5 });
//   cfg.mode  ("sort",  "Target Sort", ["closest", "health", "angle"], "closest");
//   cfg.action("reset", "Reset All", () => cfg.resetAll());
//   cfg.build();                         // registers /cfg + loads saved values
//   ...
//   if (cfg.get("aura")) { const r = cfg.get("range") as number; ... }
//   cfg.onChange("aura", (v) => ChatLib.chat("aura -> " + v));

import { Tessera, Event, ChatLib } from "ratph6.tessera.api";

// --- Minecraft chat-component interop (clickable + hover text) ------------------------------------
const Component = Java.type("net.minecraft.network.chat.Component");
const RunCommand = Java.type("net.minecraft.network.chat.ClickEvent$RunCommand");
const ShowText = Java.type("net.minecraft.network.chat.HoverEvent$ShowText");

type SettingType = "toggle" | "number" | "mode" | "action";

interface NumberOpts {
  min?: number;
  max?: number;
  step?: number;
}

interface Setting {
  key: string;
  label: string;
  type: SettingType;
  category: string;
  value: any;
  def: any;
  opts: NumberOpts;
  modes: string[];
  action?: () => void;
  listeners: ((v: any) => void)[];
}

export class Config {
  private settings = new Map<string, Setting>();
  private order: string[] = [];
  private currentCategory = "General";

  // name = persistence file (tessera/config/<name>.json); command = the /command that opens the menu
  constructor(private name: string, private command: string) {}

  // --- registration ------------------------------------------------------------------------------
  category(name: string): this {
    this.currentCategory = name;
    return this;
  }

  toggle(key: string, label: string, def = false): this {
    return this.add(key, label, "toggle", def, {}, []);
  }

  number(key: string, label: string, def: number, opts: NumberOpts = {}): this {
    return this.add(key, label, "number", def, opts, []);
  }

  mode(key: string, label: string, modes: string[], def?: string): this {
    return this.add(key, label, "mode", def ?? modes[0], {}, modes);
  }

  action(key: string, label: string, fn: () => void): this {
    const s = this.add(key, label, "action", null, {}, []);
    this.settings.get(key)!.action = fn;
    return s;
  }

  private add(key: string, label: string, type: SettingType, def: any, opts: NumberOpts, modes: string[]): this {
    if (this.settings.has(key)) {
      ChatLib.chat(`§c[config] duplicate setting key: ${key}`);
      return this;
    }
    this.settings.set(key, {
      key, label, type, category: this.currentCategory,
      value: def, def, opts, modes, listeners: [],
    });
    this.order.push(key);
    return this;
  }

  // --- access ------------------------------------------------------------------------------------
  get(key: string): any {
    return this.settings.get(key)?.value;
  }

  set(key: string, value: any) {
    const s = this.settings.get(key);
    if (!s || s.type === "action") return;
    s.value = this.coerce(s, value);
    this.save();
    for (const l of s.listeners) {
      try { l(s.value); } catch (e) { ChatLib.chat("§c[config] listener error: " + e); }
    }
  }

  onChange(key: string, fn: (v: any) => void): this {
    this.settings.get(key)?.listeners.push(fn);
    return this;
  }

  resetAll() {
    for (const s of this.settings.values()) {
      if (s.type !== "action") this.set(s.key, s.def);
    }
    ChatLib.chat(`§7[${this.name}] §freset to defaults`);
  }

  // --- wiring ------------------------------------------------------------------------------------
  build(): this {
    this.load();
    Tessera.register(Event.COMMAND, (args: string[]) => this.handle(args)).setName(this.command);
    return this;
  }

  private handle(args: string[]) {
    if (!args || args.length === 0) { this.render(); return; }

    const key = args[0];
    const s = this.settings.get(key);
    if (!s) { ChatLib.chat(`§c[${this.name}] unknown setting: ${key}`); return; }

    // one-arg = the natural action for the type (toggle / cycle / run / step for numbers via + or -)
    if (args.length === 1) {
      if (s.type === "toggle") this.set(key, !s.value);
      else if (s.type === "mode") this.set(key, this.nextMode(s));
      else if (s.type === "action") { try { s.action!(); } catch (e) { ChatLib.chat("§c[config] " + e); } }
      else if (s.type === "number") ChatLib.chat(`§7${s.label}: §f${s.value}`);
      this.render();
      return;
    }

    // two-arg = explicit set. For numbers "+"/"-" step by opts.step.
    const raw = args[1];
    if (s.type === "number" && (raw === "+" || raw === "-")) {
      const step = s.opts.step ?? 1;
      this.set(key, s.value + (raw === "+" ? step : -step));
    } else {
      this.set(key, raw);
    }
    this.render();
  }

  private nextMode(s: Setting): string {
    const i = s.modes.indexOf(s.value);
    return s.modes[(i + 1) % s.modes.length];
  }

  private coerce(s: Setting, value: any): any {
    if (s.type === "toggle") {
      if (typeof value === "boolean") return value;
      const v = String(value).toLowerCase();
      return v === "true" || v === "on" || v === "1" || v === "yes";
    }
    if (s.type === "number") {
      let n = typeof value === "number" ? value : parseFloat(value);
      if (isNaN(n)) n = s.def;
      if (s.opts.min !== undefined) n = Math.max(s.opts.min, n);
      if (s.opts.max !== undefined) n = Math.min(s.opts.max, n);
      // snap to step so displayed values stay clean
      if (s.opts.step) n = Math.round(n / s.opts.step) * s.opts.step;
      return Math.round(n * 1000) / 1000;
    }
    if (s.type === "mode") {
      return s.modes.includes(value) ? value : s.value;
    }
    return value;
  }

  // --- rendering (clickable chat) ----------------------------------------------------------------
  private render() {
    this.plain(`§8§m                    §r §b${this.name} §8§m                    `);
    let cat = "";
    for (const key of this.order) {
      const s = this.settings.get(key)!;
      if (s.category !== cat) { cat = s.category; this.plain(`§8» §7${cat}`); }
      this.renderSetting(s);
    }
    this.plain(`§8§m                                                  `);
  }

  private renderSetting(s: Setting) {
    const cmd = `/${this.command} ${s.key}`;
    if (s.type === "toggle") {
      const on = s.value === true;
      this.clickable(
        `  §f${s.label}: ${on ? "§a[ON]" : "§c[OFF]"}`,
        cmd,
        `Click to turn ${on ? "off" : "on"}`,
      );
    } else if (s.type === "mode") {
      this.clickable(`  §f${s.label}: §e${s.value} §8[cycle]`, cmd, "Click to cycle");
    } else if (s.type === "number") {
      // three clickable pieces: [-]  value  [+]
      const minus = this.piece(" §c[-]", `${cmd} -`, "decrease");
      const label = this.piece(` §f${s.label}: §b${s.value} `, cmd, "current value");
      const plus = this.piece("§a[+]", `${cmd} +`, "increase");
      this.send(Component.literal("  ").append(minus).append(label).append(plus));
    } else if (s.type === "action") {
      this.clickable(`  §d[${s.label}]`, cmd, "Click to run");
    }
  }

  // --- component helpers -------------------------------------------------------------------------
  private clickable(text: string, command: string, hover: string) {
    this.send(this.piece(text, command, hover));
  }

  private piece(text: string, command: string, hover: string): any {
    const comp = Component.literal(text);
    try {
      let style = comp.getStyle()
        .withClickEvent(new RunCommand(command))
        .withHoverEvent(new ShowText(Component.literal("§7" + hover)));
      comp.setStyle(style);
    } catch (e) {
      // component API drifted — degrade to plain text (click just won't do anything)
    }
    return comp;
  }

  private send(component: any) {
    try {
      Minecraft.getInstance().gui.hud.chat.addClientSystemMessage(component);
    } catch (e) {
      ChatLib.chat(component.getString ? component.getString() : String(component));
    }
  }

  private plain(text: string) {
    this.send(Component.literal(text));
  }

  // --- persistence -------------------------------------------------------------------------------
  private file(): any {
    const File = Java.type("java.io.File");
    const dir = new File(Minecraft.getInstance().gameDirectory, "tessera/config");
    dir.mkdirs();
    return new File(dir, this.name + ".json");
  }

  private save() {
    try {
      const data: any = {};
      for (const s of this.settings.values()) {
        if (s.type !== "action") data[s.key] = s.value;
      }
      const Files = Java.type("java.nio.file.Files");
      Files.writeString(this.file().toPath(), JSON.stringify(data, null, 2));
    } catch (e) {
      ChatLib.chat("§c[config] save failed: " + e);
    }
  }

  private load() {
    try {
      const f = this.file();
      if (!f.exists()) return;
      const Files = Java.type("java.nio.file.Files");
      const text = Files.readString(f.toPath());
      const data = JSON.parse(text);
      for (const s of this.settings.values()) {
        if (s.type !== "action" && data[s.key] !== undefined) {
          s.value = this.coerce(s, data[s.key]);
        }
      }
    } catch (e) {
      ChatLib.chat("§c[config] load failed (using defaults): " + e);
    }
  }
}
