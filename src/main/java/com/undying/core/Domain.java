package com.undying.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain: mobs outside the allow-list cannot approach the player.
 *
 * <p>Deliberately different from {@link Aura}:
 * <ul>
 *   <li><b>Aura clears</b> hostile mobs — they cease to exist.</li>
 *   <li><b>Domain repels</b> everything that is not allowed — they keep existing, they just
 *       cannot get close. Nothing dies, no drops appear, no kill events fire.</li>
 * </ul>
 * They are meant to run together: the aura removes what is hostile, the domain keeps the
 * rest at arm's length. Neither replaces the other.
 *
 * <p>Bosses are not special-cased. Pushing the warden away is exactly the point of the
 * feature, and a boss that ignores the push (the ender dragon stays in the air) simply is
 * not affected — which is harmless.
 */
public final class Domain {

    private Domain() {}

    /**
     * Run every other tick.
     *
     * <p>Repelling is a comfort feature, not a safety one: a mob two ticks late is still
     * two ticks away. Halving the entity scan is worth that.
     */
    private static final int INTERVAL = 2;

    public static void tick(ServerPlayer p) {
        try {
            if (!ModConfig.DOMAIN_ENABLED.get()) return;
            if (p.tickCount % INTERVAL != 0) return;

            int range = ModConfig.DOMAIN_RANGE.get();
            if (range <= 0) return;

            List<String> categories = strings(ModConfig.DOMAIN_ALLOW_CATEGORIES.get());
            List<String> entities = strings(ModConfig.DOMAIN_ALLOW_ENTITIES.get());

            for (Mob m : p.level().getEntitiesOfClass(Mob.class,
                    p.getBoundingBox().inflate(range),
                    m -> m.isAlive() && !isAllowed(m, categories, entities))) {
                pushAway(p, m, range);
            }
        } catch (Throwable t) {
            ModLog.swallowed("Domain.tick", t);
        }
    }

    /** Whether this mob is on the allow-list. */
    public static boolean isAllowed(Mob m, List<String> categories, List<String> entities) {
        String category = m.getType().getCategory().name();
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).toString();
        return DomainFilter.isAllowed(category, id, categories, entities);
    }

    /**
     * Push one mob outwards, harder the closer it is.
     *
     * <p>{@code hurtMarked} is set so the client is told about the position change; without
     * it the mob visibly stutters, snapping forward on the next sync.
     */
    private static void pushAway(ServerPlayer p, Mob m, int range) {
        double dx = m.getX() - p.getX();
        double dz = m.getZ() - p.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist < 1.0E-4) {
            // Exactly on top of the player: pick a direction instead of dividing by zero.
            dx = 1.0;
            dz = 0.0;
            dist = 1.0;
        }

        double nx = dx / dist;
        double nz = dz / dist;
        double fade = 1.0 - Math.min(dist / range, 1.0);
        double force = 0.12 + 0.25 * fade;

        m.push(nx * force, 0.02, nz * force);
        m.hurtMarked = true;
    }

    private static List<String> strings(List<? extends String> raw) {
        List<String> out = new ArrayList<>(raw.size());
        for (Object o : raw) out.add(String.valueOf(o));
        return out;
    }
}
