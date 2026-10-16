package com.undying.core;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Self-check probes: verify that the vanilla structures we depend on are still present.
 *
 * <p><b>Every probe only asks "does this structure exist" — none of them read or modify
 * any state</b>, so they are safe to run inside the 2-second self-check loop.
 *
 * <p>These probes are the core value of self-protection. This mod has been burned by
 * "silent failure" three times, and two of them were "a hook was never attached and
 * nobody knew":
 * <ul>
 *   <li>Launch-time protection: the service was never loaded, and the log showed
 *       "plugin = not active" for a long time</li>
 *   <li>{@code /kill} interception: the log showed "mixin applied", but it did not
 *       actually block anything</li>
 * </ul>
 * So "is it attached or not" has to become a check item you can see.
 */
public final class SelfCheck {

    private SelfCheck() {}

    /** A single self-check result */
    public static final class Probe {
        public final String name;
        public final boolean ok;
        public final String detail;

        Probe(String name, boolean ok, String detail) {
            this.name = name;
            this.ok = ok;
            this.detail = detail;
        }

        @Override
        public String toString() {
            return (ok ? "OK  " : "FAIL") + "  " + name
                    + (detail == null || detail.isEmpty() ? "" : "  (" + detail + ")");
        }
    }

    /**
     * Runs one self-check pass.
     *
     * <p>How we decide whether a mixin took effect: Mixin attaches injected methods to the
     * target class as synthetic methods.
     * <ul>
     *   <li>{@code @Unique} / injection handlers → method names of the form
     *       {@code undying$xxx} or {@code handler$xxx}</li>
     *   <li>Accessor interfaces → the target class implements that interface</li>
     * </ul>
     * These are hard evidence that can be seen through reflection.
     */
    public static List<Probe> run() {
        List<Probe> out = new ArrayList<>();

        // ── 0a. Whether MixinEntity is attached (the carrier of the death lock) ──
        try {
            boolean found = false;
            for (java.lang.reflect.Method m : net.minecraft.world.entity.Entity.class.getDeclaredMethods()) {
                if (m.getName().contains("undying$") || m.getName().startsWith("handler$")) {
                    found = true;
                    break;
                }
            }
            out.add(new Probe("MixinEntity inject (death lock)", found,
                    found ? "attached" : "NOT attached — death lock may be inactive"));
        } catch (Throwable t) {
            out.add(new Probe("MixinEntity inject (death lock)", false, t.getClass().getSimpleName()));
        }

        // ── 0b. Whether the death-timer accessor interface is implemented ──
        try {
            Class<?> acc = Class.forName("com.undying.core.mixin.LivingEntityDeathTimerAccess");
            boolean impl = acc.isAssignableFrom(net.minecraft.world.entity.LivingEntity.class);
            out.add(new Probe("death-timer accessor", impl,
                    impl ? "attached" : "NOT attached — stuck death state cannot be cleared"));
        } catch (Throwable t) {
            out.add(new Probe("death-timer accessor", false, t.getClass().getSimpleName()));
        }

        // ── 0c. Whether the health-gate accessor interface is implemented ──
        try {
            Class<?> owner = Class.forName("com.undying.core.mixin.SynchedEntityDataOwnerAccess");
            boolean impl = owner.isAssignableFrom(net.minecraft.network.syncher.SynchedEntityData.class);
            out.add(new Probe("health-gate accessor", impl,
                    impl ? "attached" : "NOT attached — health gate will fail"));
        } catch (Throwable t) {
            out.add(new Probe("health-gate accessor", false, t.getClass().getSimpleName()));
        }

        // ── 0d. Whether MixinCommands is attached (the carrier of /kill interception) ──
        try {
            boolean found = false;
            for (java.lang.reflect.Method m : net.minecraft.commands.Commands.class.getDeclaredMethods()) {
                if (m.getName().contains("undying$") || m.getName().startsWith("handler$")) {
                    found = true;
                    break;
                }
            }
            out.add(new Probe("MixinCommands inject (/kill interception)", found,
                    found ? "attached" : "NOT attached — /kill interception inactive"));
        } catch (Throwable t) {
            out.add(new Probe("MixinCommands inject (/kill interception)", false, t.getClass().getSimpleName()));
        }

        // ── 1. Death-timer field (the basis for clearing the "half alive, half dead" state) ──
        try {
            Field f = findField(net.minecraft.world.entity.LivingEntity.class, "f_20919_", "deathTime");
            out.add(new Probe("LivingEntity death-timer field", f != null,
                    f == null ? "field missing — stuck death state may persist" : f.getName()));
        } catch (Throwable t) {
            out.add(new Probe("LivingEntity death-timer field", false, t.getClass().getSimpleName()));
        }

        // ── 2. Health synched accessor (the basis for the health gate) ──
        try {
            Field f = findField(net.minecraft.world.entity.LivingEntity.class, "f_20961_", "DATA_HEALTH_ID");
            out.add(new Probe("LivingEntity health accessor field", f != null,
                    f == null ? "field missing — health gate will fail" : f.getName()));
        } catch (Throwable t) {
            out.add(new Probe("LivingEntity health accessor field", false, t.getClass().getSimpleName()));
        }

        // ── 3. The entity field held by synched data (the basis for the health gate
        //        deciding "whose health this is") ──
        try {
            Field f = findField(net.minecraft.network.syncher.SynchedEntityData.class, "f_135344_", "entity");
            out.add(new Probe("SynchedEntityData owner field", f != null,
                    f == null ? "field missing — health gate cannot resolve owner" : f.getName()));
        } catch (Throwable t) {
            out.add(new Probe("SynchedEntityData owner field", false, t.getClass().getSimpleName()));
        }

        // ── 4. Removal-reason enum (the basis for the guardRemove whitelist) ──
        try {
            Object killed = net.minecraft.world.entity.Entity.RemovalReason.valueOf("KILLED");
            out.add(new Probe("RemovalReason.KILLED enum", killed != null, "present"));
        } catch (Throwable t) {
            out.add(new Probe("RemovalReason.KILLED enum", false, t.getClass().getSimpleName()));
        }

        // ── 5. Whether the master protection toggle is actually on ──
        //
        // ⚠️ This one must take part in the verdict, not merely write its value into detail.
        //    It used to be always true, so with masterToggle=false (no hook in the mod does
        //    anything and the player is completely exposed) the report still said
        //    "all 10 protection checks passed" — the player saw "everything is fine".
        //    This is exactly the kind of mistake this project keeps paying for: silent failure.
        //
        try {
            boolean master = ModConfig.MASTER_TOGGLE.get();
            boolean deathLock = ModConfig.DEATH_LOCK.get();
            String detail;
            if (!master) {
                detail = "masterToggle=false — this mod is currently performing NO protection";
            } else if (!deathLock) {
                detail = "deathLock=false — the death lock is off";
            } else {
                detail = "masterToggle=true deathLock=true";
            }
            out.add(new Probe("protection master switch", master && deathLock, detail));
        } catch (Throwable t) {
            out.add(new Probe("protection master switch", false, t.getClass().getSimpleName()));
        }

        // ── 6. Whether the snapshot backup directory is writable ──
        try {
            boolean usable = SnapshotStore.dirUsable();
            out.add(new Probe("snapshot backup directory", usable,
                    usable ? "writable" : "NOT writable — snapshots cannot be saved"));
        } catch (Throwable t) {
            out.add(new Probe("snapshot backup directory", false, t.getClass().getSimpleName()));
        }

        return out;
    }

    /**
     * Looks up a field by reflection; returns null when it is not found.
     * Supports both SRG names and official names (development and production
     * environments may differ).
     */
    private static Field findField(Class<?> owner, String... names) {
        // Iterate once and match by name, instead of calling getDeclaredField for each
        // candidate name and swallowing the exception. The latter always throws
        // NoSuchFieldException in the normal path (a candidate name is always absent),
        // so it can only be written by swallowing exceptions — using exceptions as
        // control flow, which is exactly what this cleanup pass removes.
        java.util.Set<String> wanted = java.util.Set.of(names);
        for (Field f : owner.getDeclaredFields()) {
            if (!wanted.contains(f.getName())) continue;
            try {
                f.setAccessible(true);
            } catch (Throwable t) {
                ModLog.swallowed("SelfCheck.findField.setAccessible", t);
            }
            return f;
        }
        return null;
    }
}
