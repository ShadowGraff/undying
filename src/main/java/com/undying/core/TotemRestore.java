package com.undying.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Offhand items parked while a temporary totem is injected, plus the sweep for the cases
 * where they never get parked back.
 *
 * <p>The table lives here instead of inside the mixin so the tick task can reach it. The
 * restore hook runs at {@code checkTotemDeathProtection} RETURN, and that hook does <b>not</b>
 * run when the method throws, or when another mod cancels it at HEAD — a cancellable injector
 * returns before the original RETURN instructions are reached. Either way the player used to
 * keep a totem in the offhand indefinitely and silently lose the real item that was there.
 *
 * <p>Redirecting {@code getItemInHand} inside that method would avoid touching the real
 * inventory at all, and was rejected: a {@code @Redirect} takes exclusive ownership of the
 * call site, so it would fight any other mod redirecting the same call. A sweep changes no
 * injection semantics and cannot conflict.
 */
public final class TotemRestore {

    private TotemRestore() {}

    /** Entity -> the offhand item that has to be put back. Server thread only. */
    private static final Map<LivingEntity, ItemStack> PENDING = new HashMap<>();

    /** Park the real offhand item just before injecting the temporary totem. */
    public static void stash(LivingEntity e, ItemStack original) {
        PENDING.put(e, original);
    }

    /** Take the parked item back, or null when this entity has none pending. */
    public static ItemStack take(LivingEntity e) {
        return PENDING.remove(e);
    }

    /**
     * Put back any offhand item whose restore hook never ran.
     *
     * <p>Safe to call every tick, and cheap: stash and take both happen inside a single
     * {@code checkTotemDeathProtection} call on the server thread, so at a tick boundary the
     * table is empty unless something went wrong. A non-empty entry here is therefore always
     * a leak, never a restore that is merely "not yet" — which is what makes a blanket sweep
     * correct rather than a race.
     *
     * <p>Known limit: the table is keyed by entity instance, and respawning creates a new
     * {@code ServerPlayer}. An entry leaked by the pre-respawn instance is not reachable from
     * the new one, so it stays in the table until the JVM exits. That costs one ItemStack per
     * occurrence and needs both a skipped RETURN hook and a respawn in the same call, which is
     * not worth a UUID-keyed secondary index.
     */
    public static void recoverLeaked(ServerPlayer p) {
        ItemStack original = PENDING.remove(p);
        if (original == null) return;
        p.setItemInHand(InteractionHand.OFF_HAND, original);
        // Loud on purpose: a silent repair would hide that the RETURN hook is being skipped
        // at all, and that is the thing worth knowing about. Silent failure is the failure
        // mode this project treats as unacceptable.
        IntegrityGuard.LOGGER.warn("[totem] offhand item recovered by the leak sweep: the "
                + "RETURN hook in checkTotemDeathProtection did not run for {}",
                p.getName().getString());
    }
}
