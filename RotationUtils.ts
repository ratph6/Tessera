// RotationUtils — smooth, GCD-legit rotations that stay smooth while moving.
//
// The old version stair-stepped because the GCD snap rounded small per-frame deltas to 0 for several
// frames, then jumped a whole quantum. This carries the rounding remainder ("residual") to the next
// frame, reads the player's REAL rotation each frame (so your own mouse / strafing self-corrects),
// runs off a monotonic clock, and lands exactly on target.
//
// Standalone: define mc locally instead of importing it. If you already have `mc` in scope (bundled
// module), delete the line below.

import { Event, Tessera } from "ratph6.tessera.api";

const mc = Minecraft.getInstance();

class RotationUtils {
  private startYaw = 0;
  private startPitch = 0;
  private targetYaw = 0;
  private targetPitch = 0;

  private startTime = 0;
  private duration = 0;
  private rotating = false;

  // GCD residual: sub-quantum motion carried to the next frame instead of being rounded away.
  // This is what removes the stair-step stutter while keeping snaps on the mouse grid.
  private residualYaw = 0;
  private residualPitch = 0;

  constructor() {
    Tessera.register(Event.RENDER_OVERLAY, () => this.update());
  }

  // instant snap to an absolute rotation
  rotate(yaw: number, pitch: number) {
    if (!mc.player) return;
    this.rotating = false;
    this.residualYaw = 0;
    this.residualPitch = 0;
    this.apply(yaw, this.normalizePitch(pitch));
  }

  smoothRotate(targetYaw: number, targetPitch: number, time: number) {
    if (!mc.player) return;

    this.startYaw = mc.player.getYRot();
    this.startPitch = mc.player.getXRot();

    // absolute target on the same continuous scale as start (shortest arc), so easing never wraps
    this.targetYaw = this.startYaw + this.normalizeYaw(targetYaw - this.startYaw);
    this.targetPitch = this.normalizePitch(targetPitch);

    this.residualYaw = 0;
    this.residualPitch = 0;
    this.startTime = Tessera.millis(); // monotonic — Date.now() is wall-clock and can hitch
    this.duration = Math.max(1, time);
    this.rotating = true;
  }

  rotationToEntity(entity: any, height: number = 0.5) {
    const pos = entity.position();
    return this.calcYawPitch(new Vec3(pos.x, pos.y + height, pos.z));
  }

  smoothRotateToEntity(entity: any, time: number, height: number = 0.5) {
    const rot = this.rotationToEntity(entity, height);
    this.smoothRotate(rot.yaw, rot.pitch, time);
  }

  stop() {
    this.rotating = false;
  }

  isRotating() {
    return this.rotating;
  }

  private update() {
    if (!this.rotating || !mc.player) return;

    const progress = Math.min((Tessera.millis() - this.startTime) / this.duration, 1);
    const eased = this.easeInOutCubic(progress);

    const yaw = this.interpolateYaw(this.startYaw, this.targetYaw, eased);
    const pitch = this.lerp(this.startPitch, this.targetPitch, eased);

    if (progress >= 1) {
      // land exactly on target, flush residual so we don't stop a fraction short
      this.residualYaw = 0;
      this.residualPitch = 0;
      this.apply(this.targetYaw, this.targetPitch);
      this.rotating = false;
      return;
    }

    this.applyGCD(yaw, pitch);
  }

  // snap the delta from the player's REAL current rotation to the mouse grid, carrying the remainder
  private applyGCD(targetYaw: number, targetPitch: number) {
    if (!mc.player) return;
    const gcd = this.getGCD();

    const curYaw = mc.player.getYRot();
    const curPitch = mc.player.getXRot();

    // reading real yaw each frame means your own mouse / strafing self-corrects instead of fighting
    const wantYaw = this.normalizeYaw(targetYaw - curYaw) + this.residualYaw;
    const wantPitch = this.normalizePitch(targetPitch) - curPitch + this.residualPitch;

    const stepYaw = Math.round(wantYaw / gcd) * gcd;
    const stepPitch = Math.round(wantPitch / gcd) * gcd;

    this.residualYaw = wantYaw - stepYaw; // keep what didn't fit — applied next frame
    this.residualPitch = wantPitch - stepPitch;

    this.apply(curYaw + stepYaw, this.normalizePitch(curPitch + stepPitch));
  }

  private apply(yaw: number, pitch: number) {
    if (!mc.player) return;
    mc.player.setYRot(yaw);
    mc.player.setXRot(pitch);
    mc.player.yHeadRot = yaw;
    mc.player.yBodyRot = yaw; // keep body synced so strafe direction follows the aim while moving
  }

  calcYawPitch(target: Vec3) {
    if (!mc.player) return { yaw: 0, pitch: 0 };
    const diff = target.subtract(mc.player.getEyePosition());
    const dist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
    return {
      yaw: (Math.atan2(-diff.x, diff.z) * 180) / Math.PI,
      pitch: (-Math.atan2(diff.y, dist) * 180) / Math.PI,
    };
  }

  private getGCD() {
    const sensitivity = mc.options.sensitivity().get();
    const f = sensitivity * 0.6 + 0.2;
    return f * f * f * 1.2 * 0.15;
  }

  normalizeYaw(yaw: number) {
    yaw %= 360;
    if (yaw >= 180) yaw -= 360;
    if (yaw < -180) yaw += 360;
    return yaw;
  }

  normalizePitch(pitch: number) {
    return Math.max(-90, Math.min(90, pitch));
  }

  interpolateYaw(from: number, to: number, progress: number) {
    let delta = to - from;
    while (delta > 180) delta -= 360;
    while (delta < -180) delta += 360;
    return from + delta * progress;
  }

  lerp(from: number, to: number, progress: number) {
    return from + (to - from) * progress;
  }

  easeInOutCubic(t: number) {
    return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
  }
}

export const rotationUtils = new RotationUtils();
