package com.undying.core;

/**
 * Caps a single hit so it can never take the player below the death-lock floor.
 *
 * <h3>Why not "health higher than the damage"</h3>
 * That was the first idea, and it cannot work. Vanilla {@code LivingEntity.kill()} is:
 *
 * <pre>
 *   hurt(this.damageSources().genericKill(), 3.4028235E38F)
 * </pre>
 *
 * — that constant is {@link Float#MAX_VALUE}, the largest value a {@code float} can hold, and
 * health is stored as a {@code float} too ({@code setHealth(float)} clamps against a
 * {@code float} max health). So "health greater than the damage" has no representation:
 * the only bigger value is {@code Infinity}, which breaks the health bar, every comparison and
 * the NBT round-trip (a stored {@code Infinity} can come back as {@code NaN}).
 *
 * <p>Capping the <b>damage</b> reaches the same place from the other side: after any hit,
 * health is still above the floor, whatever number the attacker sent — including
 * {@code Float.MAX_VALUE} and including a command-hidden {@code /kill}. Health stays normal, so
 * the HUD, the attribute and the save file are untouched.
 *
 * <h3>Why this is a separate layer from the "refuse the hit" gate</h3>
 * {@code MixinLivingEntity.undying$guardLethalDamage} refuses a hit outright once health is at
 * the floor. That is the right behaviour at the bottom, but it is all-or-nothing: a player at
 * 20 health never sees the bar move. This layer lets the bar fall honestly and stops it at the
 * floor, which is both easier to trust and easier to debug ("I took damage and survived")
 * than "the hit vanished".
 */
public final class DamageClamp {

    private DamageClamp() {}

    /**
     * @param amount incoming damage, may be negative, zero, {@code NaN} or infinite
     * @param health current health
     * @param floor  health that must not be crossed
     * @return the damage that may actually be applied
     */
    public static float clamp(float amount, float health, float floor) {
        // A NaN would survive every arithmetic step and poison health permanently
        // (health - NaN = NaN, and setHealth's clamp passes NaN through). Turning it into
        // "no damage" is the only outcome that leaves the player in a usable state.
        if (Float.isNaN(amount)) return 0.0F;

        // Healing, zero and negative damage are not this layer's business.
        if (amount <= 0.0F) return amount;

        // Also catches an infinite health: room is then infinite and the min() below is exact.
        float room = health - floor;

        // Already at (or below) the floor: the ordinary gate refuses the whole hit, and
        // returning the amount unchanged keeps that behaviour in one place instead of two.
        if (room <= 0.0F) return amount;

        return Math.min(amount, room);
    }
}
