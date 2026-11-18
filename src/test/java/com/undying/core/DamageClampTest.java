package com.undying.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The damage ceiling.
 *
 * <p>The value that matters is {@link Float#MAX_VALUE}: that is exactly what vanilla
 * {@code LivingEntity.kill()} sends, so every test that claims "the player survives" has to be
 * written against that number rather than a big-looking one like 1000.
 */
class DamageClampTest {

    private static final float FLOOR = 1.0F;

    @Test
    void aKillCommandCannotCrossTheFloor() {
        // /kill → hurt(genericKill, Float.MAX_VALUE) at full health
        float out = DamageClamp.clamp(Float.MAX_VALUE, 20.0F, FLOOR);
        assertEquals(19.0F, out, 0.0001F);
        assertTrue(20.0F - out >= FLOOR, "health after the hit must stay at or above the floor");
    }

    @Test
    void theSameHoldsWhenAlreadyLow() {
        float out = DamageClamp.clamp(Float.MAX_VALUE, 1.5F, FLOOR);
        assertEquals(0.5F, out, 0.0001F);
    }

    @Test
    void atTheFloorTheHitIsLeftAloneSoOneGateOwnsThatCase() {
        // Health is already at the floor: the "refuse outright" gate handles it. Passing the
        // amount through unchanged keeps that decision in exactly one place.
        assertEquals(Float.MAX_VALUE, DamageClamp.clamp(Float.MAX_VALUE, FLOOR, FLOOR));
    }

    @Test
    void ordinaryDamageIsNotTouched() {
        assertEquals(3.5F, DamageClamp.clamp(3.5F, 20.0F, FLOOR), 0.0001F);
    }

    @Test
    void aHitThatWouldExactlyEmptyTheBarIsCappedToLeaveTheFloor() {
        // 20 damage against 20 health: not "anything goes", it stops one point short.
        // This is the whole promise of the layer, so it is asserted rather than assumed.
        assertEquals(19.0F, DamageClamp.clamp(20.0F, 20.0F, FLOOR), 0.0001F);
    }

    @Test
    void healingAndZeroPassThrough() {
        assertEquals(0.0F, DamageClamp.clamp(0.0F, 5.0F, FLOOR));
        assertEquals(-4.0F, DamageClamp.clamp(-4.0F, 5.0F, FLOOR), 0.0001F);
    }

    @Test
    void notANumberBecomesNoDamage() {
        // A NaN that reached health would make the player permanently unhealable and
        // unkillable at the same time, and it survives every later subtraction.
        assertEquals(0.0F, DamageClamp.clamp(Float.NaN, 20.0F, FLOOR));
        assertTrue(!Float.isNaN(20.0F - DamageClamp.clamp(Float.NaN, 20.0F, FLOOR)));
    }

    @Test
    void infinityIsBoundedByTheRoom() {
        assertEquals(19.0F, DamageClamp.clamp(Float.POSITIVE_INFINITY, 20.0F, FLOOR), 0.0001F);
    }

    @Test
    void aFullHealthHitLeavesExactlyTheFloor() {
        float health = 20.0F;
        float out = DamageClamp.clamp(Float.MAX_VALUE, health, FLOOR);
        assertEquals(FLOOR, health - out, 0.0001F);
    }
}
