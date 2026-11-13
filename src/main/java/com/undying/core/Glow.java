package com.undying.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Outline every nearby creature so it can be seen through walls.
 *
 * <h3>Why the vanilla glowing effect and not a render hook</h3>
 * The outline vanilla draws for {@code GLOWING} already handles occlusion, distance, team colours
 * and every renderer a mod might replace. Drawing our own boxes in a render event would fight
 * Sodium, look different depending on the shader pack, and stop working the moment Mojang changes
 * the outline renderer. One effect instance per entity is a few bytes and inherits all of that
 * for free.
 *
 * <h3>Why what we added is remembered</h3>
 * A spectral arrow also grants {@code GLOWING}. Clearing every glowing entity when this feature
 * is switched off would silently cancel somebody else's effect, so only the entities in
 * {@code PlayerState.glowTargets} — the ones this mod put the effect on — are cleared.
 *
 * <h3>Why it refreshes once a second</h3>
 * The effect is applied for three seconds at a time and renewed while the feature is on. A
 * single long duration would keep working for the whole session after the player closed the
 * menu, which reads as "the switch does nothing".
 */
public final class Glow {

    private Glow() {}

    /** Effect duration per refresh, in ticks. Long enough to survive a dropped tick. */
    private static final int REFRESH_TICKS = 60;

    /** Starts (or restarts) the outline. {@code seconds <= 0} means "until switched off". */
    public static void start(ServerPlayer p, int seconds) {
        try {
            if (p == null) return;
            if (!ModConfig.GLOW.get()) return;
            if (!Environment.isAllowed(p)) return;

            PlayerState st = PlayerState.of(p);
            st.glowActive = true;
            st.glowUntilTick = seconds > 0 ? p.tickCount + seconds * 20 : 0;
            if (st.glowTargets == null) st.glowTargets = new HashSet<>();

            int marked = apply(p, st);

            // Say how many were marked. Without the number, "on" and "on but nothing matched"
            // look identical, and the difference is exactly what a player needs to know:
            // zero means the target/radius setting is wrong, non-zero means the request worked
            // and the outline itself is what is not showing.
            if (marked == 0) {
                Msg.warn(p, UndyingMod.TAG)
                        .bi("透视：已开启，但半径内没有符合条件的生物（检查目标与半径）",
                                "Outline: on, but nothing in range matched (check target and radius)")
                        .send();
                return;
            }

            Msg.ok(p, UndyingMod.TAG)
                    .bi(seconds > 0 ? "透视：开启 " + seconds + " 秒，标记 " + marked + " 个"
                                    : "透视：开启（持续），标记 " + marked + " 个",
                            seconds > 0 ? "Outline: on for " + seconds + "s, " + marked + " marked"
                                    : "Outline: on, " + marked + " marked")
                    .send();
        } catch (Throwable t) {
            ModLog.swallowed("Glow.start", t);
        }
    }

    /** Switches the outline off and clears only the effects this mod applied. */
    public static void stop(ServerPlayer p, String zhWhy, String enWhy) {
        try {
            if (p == null) return;
            PlayerState st = PlayerState.of(p);
            if (!st.glowActive) return;

            st.glowActive = false;
            clear(p, st);

            Msg.plain(p, UndyingMod.TAG)
                    .bi("透视：已关闭" + (zhWhy.isEmpty() ? "" : "（" + zhWhy + "）"),
                            "Outline: off" + (enWhy.isEmpty() ? "" : " (" + enWhy + ")"))
                    .send();
        } catch (Throwable t) {
            ModLog.swallowed("Glow.stop", t);
        }
    }

    public static void tick(ServerPlayer p) {
        try {
            if (p == null) return;
            // Cheapest test first: 19 out of 20 ticks return without touching player state.
            if (p.tickCount % Const.TICKS_PER_SECOND != 0) return;

            PlayerState st = PlayerState.of(p);
            if (!st.glowActive) return;

            if (!ModConfig.GLOW.get() || !Environment.isAllowed(p)) {
                stop(p, "开关关闭或环境不允许", "switch off or gate closed");
                return;
            }
            if (st.glowUntilTick > 0 && p.tickCount > st.glowUntilTick) {
                stop(p, "时间到", "time up");
                return;
            }

            apply(p, st);
        } catch (Throwable t) {
            ModLog.swallowed("Glow.tick", t);
        }
    }

    // ═══════════ Applying ═══════════

    /**
     * Outline everything that matches, nearest first, up to the configured cap.
     *
     * <h3>Why there is a cap, and why "nearest"</h3>
     * Vanilla draws a glowing outline by submitting the entity a second time into an outline
     * buffer. That is cheap for one creature and ruinous for a hundred: with "all" targets and
     * a 48 block radius on a mob-heavy world, the first version measured a frame rate that
     * fell from well above 100 to below it, while the outlines themselves were barely visible.
     * The cost was ours; the benefit was not.
     *
     * <p>So the list is sorted by distance and cut at {@code GLOW_MAX}. The creatures that
     * matter are the ones near the player, and "the fiftieth nearest mob glows" is not a
     * feature anyone asked for.
     *
     * @return how many creatures are currently marked
     */
    private static int apply(ServerPlayer p, PlayerState st) {
        if (p.serverLevel() == null) return 0;

        double range = ModConfig.GLOW_RANGE.get();
        AABB box = p.getBoundingBox().inflate(range);

        List<LivingEntity> found = p.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != p && e.isAlive());

        GlowFilter.Target target = GlowFilter.Target.parse(ModConfig.GLOW_TARGET.get());
        // Read the lists once. They used to be copied per entity inside matches(), which
        // allocated two lists per creature per second for no reason.
        List<String> cats = strings(ModConfig.GLOW_CATEGORIES.get());
        List<String> ids = strings(ModConfig.GLOW_ENTITIES.get());

        List<LivingEntity> hits = new java.util.ArrayList<>(found.size());
        for (LivingEntity e : found) {
            if (matches(e, target, cats, ids)) hits.add(e);
        }

        int cap = ModConfig.GLOW_MAX.get();
        if (hits.size() > cap) {
            hits.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p)));
            hits = hits.subList(0, cap);
        }

        Set<UUID> now = new HashSet<>();
        for (LivingEntity e : hits) {
            now.add(e.getUUID());
            // Short duration, refreshed every second: switching the feature off takes effect
            // within a few seconds even if a refresh is missed.
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, REFRESH_TICKS, 0,
                    false, false, false));
        }

        // Entities that left the radius, died, or stopped matching lose the outline — but only
        // if this mod is the one that gave it to them.
        if (st.glowTargets != null) {
            for (UUID id : st.glowTargets) {
                if (now.contains(id)) continue;
                var entity = p.serverLevel().getEntity(id);
                if (entity instanceof LivingEntity le) le.removeEffect(MobEffects.GLOWING);
            }
        }
        st.glowTargets = now;
        return now.size();
    }

    private static List<String> strings(List<? extends String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<String> out = new java.util.ArrayList<>(raw.size());
        for (Object o : raw) out.add(String.valueOf(o));
        return out;
    }

    private static boolean matches(LivingEntity e, GlowFilter.Target target,
                                   List<String> cats, List<String> ids) {
        MobCategory cat = e.getType().getCategory();
        boolean monster = cat == MobCategory.MONSTER;
        boolean enemy = e instanceof Enemy;
        String catName = cat.getName();
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        String id = key == null ? "" : key.toString();

        return GlowFilter.shouldGlow(target, monster, enemy, catName, id, cats, ids);
    }

    private static void clear(ServerPlayer p, PlayerState st) {
        try {
            if (st.glowTargets == null || p.serverLevel() == null) return;
            for (UUID id : st.glowTargets) {
                var entity = p.serverLevel().getEntity(id);
                if (entity instanceof LivingEntity le) le.removeEffect(MobEffects.GLOWING);
            }
            st.glowTargets.clear();
        } catch (Throwable t) {
            ModLog.swallowed("Glow.clear", t);
        }
    }
}
