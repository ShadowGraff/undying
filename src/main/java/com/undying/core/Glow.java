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

            apply(p, st);

            Msg.ok(p, UndyingMod.TAG)
                    .bi(seconds > 0 ? "透视：开启 " + seconds + " 秒" : "透视：开启（持续）",
                            seconds > 0 ? "Outline: on for " + seconds + "s" : "Outline: on")
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

    private static void apply(ServerPlayer p, PlayerState st) {
        if (p.serverLevel() == null) return;

        double range = ModConfig.GLOW_RANGE.get();
        AABB box = p.getBoundingBox().inflate(range);

        List<LivingEntity> found = p.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != p && e.isAlive());

        GlowFilter.Target target = GlowFilter.Target.parse(ModConfig.GLOW_TARGET.get());
        List<? extends String> cats = ModConfig.GLOW_CATEGORIES.get();
        List<? extends String> ids = ModConfig.GLOW_ENTITIES.get();

        Set<UUID> now = new HashSet<>();
        for (LivingEntity e : found) {
            if (!matches(e, target, cats, ids)) continue;
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
    }

    private static boolean matches(LivingEntity e, GlowFilter.Target target,
                                   List<? extends String> cats, List<? extends String> ids) {
        MobCategory cat = e.getType().getCategory();
        boolean monster = cat == MobCategory.MONSTER;
        boolean enemy = e instanceof Enemy;
        String catName = cat.getName();
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        String id = key == null ? "" : key.toString();

        return GlowFilter.shouldGlow(target, monster, enemy, catName, id,
                cats == null ? List.of() : List.copyOf(cats),
                ids == null ? List.of() : List.copyOf(ids));
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
