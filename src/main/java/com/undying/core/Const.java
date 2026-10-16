package com.undying.core;

/**
 * Global constants.
 *
 * Collects the magic numbers that used to be scattered around - for example 20*30, 40, 999999,
 * 2100000000F. Changing a value only requires touching this one file.
 *
 * Naming convention: a TICKS suffix means a tick count, and such values can be written in terms of TICKS_PER_SECOND.
 */
public final class Const {

    // ═══════════ Time base ═══════════

    /** 1 second = 20 ticks. All duration constants are written as N * TICKS_PER_SECOND */
    public static final int TICKS_PER_SECOND = 20;

    // ═══════════ Check cadence ═══════════

    /** Integrity guard self-check interval: 2 seconds */
    public static final int INTEGRITY_CHECK_INTERVAL = 2 * TICKS_PER_SECOND;

    /** OP status check interval: 2 seconds */
    public static final int OP_CHECK_INTERVAL = 2 * TICKS_PER_SECOND;

    // ═══════════ Aura ═══════════

    /** Timed aura duration: 30 seconds */
    public static final int AURA_DURATION_TICKS = 30 * TICKS_PER_SECOND;

    /** Aura radius: 16 blocks */
    public static final double AURA_RANGE = 16.0;

    /** Void sword right-click clear radius: 5 blocks */
    public static final double SWORD_SWEEP_RANGE = 5.0;

    // ═══════════ Combat ═══════════

    /** Void sword / forced-termination damage: 2.1 billion */
    public static final float VOID_DAMAGE = 2_100_000_000F;

    /** Death lock fallback health */
    public static final float DEATH_LOCK_HEALTH = 1.0F;

    /**
     * Execution chain layers: 4
     * Layer 1: 2.1 billion damage -> Layer 2: forced removal -> Layer 3: data wipe -> Layer 4: peaceful mode
     *
     * Note: the former layer 5 (kick the player + delete the world save) was removed on 2026-09-30
     *    at the user's request - it is an irreversible extreme side effect and does not fit the
     *    role of a "protection mod".
     */
    public static final int EXECUTION_LAYERS = 4;

    // ═══════════ Potion effects ═══════════

    /** Near-permanent duration (for persistent buffs) */
    public static final int EFFECT_INFINITE = 999_999;

    // ═══════════ World interaction ═══════════

    /** Void rescue trigger height */
    public static final double VOID_Y_THRESHOLD = -100.0;

    /** Creative inventory: maximum count per grant */
    public static final int GIVE_ITEM_MAX_COUNT = 64;

    // ═══════════ Safety ═══════════

    /** Experience protection: only a single drain above this value can be flagged as suspicious */
    public static final int XP_SUSPICIOUS_DRAIN = 30;

    private Const() {}
}
