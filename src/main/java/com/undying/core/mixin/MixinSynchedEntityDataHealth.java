package com.undying.core.mixin;

import com.undying.core.Const;
import com.undying.core.DeathLock;
import com.undying.core.Environment;
import com.undying.core.ModConfig;
import com.undying.core.ModLog;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The <i>last gate</i> on health data.
 *
 * ═══════════════════════════════════════════════════════════
 *  Why it is needed (established empirically on 2026-10-05)
 * ═══════════════════════════════════════════════════════════
 *
 * I once implemented "complete damage immunity while health is low" inside
 * LivingEntity.hurt(), assuming health could then never drop to 0.
 * **Testing disproved it**: log lines containing the literal marker
 * `[snapshot] 检测到状态被毁…` kept appearing, proving health did in fact still
 * reach 0. That Chinese marker in the logs predates this translation; it is quoted
 * here verbatim so that historical logs remain searchable.
 *
 * The reason: health does not change only through hurt(). Any code can write
 *   entity.getEntityData().set(DATA_HEALTH_ID, 0.0F)
 * or LivingEntity.setHealth(0.0F) directly —— those paths do not go through hurt().
 *
 * ⇒ It has to be blocked at a <i>lower level</i>: health data has exactly one write
 *   channel —— SynchedEntityData.set(). The only real source of truth for health is
 *   this synched data (getHealth() reads from it).
 *
 * Only "the write that would be lethal" (value ≤ the floor health) is intercepted;
 * normal damage (e.g. 20→15) is not touched at all.
 *
 * ⚠️ Known imprecision: there is no way to tell here which key is being written, so
 *    the decision is based on "the value is a float and ≤ the floor health". In a
 *    player's synched data the only floats are health and absorption, so absorption
 *    ≤ 1.0 also gets raised to 1.0. The impact is negligible, but it is real ——
 *    recorded here so that nobody is left puzzled by the behaviour later.
 */
@Mixin(SynchedEntityData.class)
public abstract class MixinSynchedEntityDataHealth {

    /** Rewrite logic: replace a "lethal health write" with the floor health */
    private Object undying$clamp(Object value) {
        try {
            if (!(value instanceof Float incoming)) return value;

            SynchedEntityData self = (SynchedEntityData) (Object) this;
            Entity owner = ((SynchedEntityDataOwnerAccess) self).undying$getOwner();
            if (!(owner instanceof ServerPlayer p)) return value;

            if (!ModConfig.MASTER_TOGGLE.get() || !ModConfig.DEATH_LOCK.get()) return value;
            if (!Environment.isAllowed(p)) return value;   // single-player only
            if (incoming > Const.DEATH_LOCK_HEALTH) return value;

            if (DeathLock.isSuicidePending(p.getUUID())) return value;
            if (DeathLock.isGranted(p)) return value;

            return (Object) Const.DEATH_LOCK_HEALTH;
        } catch (Throwable t) {
            // Failing <i>open</i> is deliberate: better to let one health write through
            // than to break normal game logic with a bug of our own. It must still be
            // logged, though, otherwise it is a silent failure.
            ModLog.swallowed("MixinSynchedEntityDataHealth.clamp", t);
            return value;
        }
    }

    /** Entry point of the public set */
    @ModifyVariable(method = "m_135381_", at = @At("HEAD"), argsOnly = true, index = 2, remap = false)
    private Object undying$clampPublic(Object value) {
        return undying$clamp(value);
    }

    /**
     * Belt and braces: the real internal write method is m_276349_ (the force-taking
     * overload). The public set ultimately reaches it, so catch it there as well.
     */
    @ModifyVariable(method = "m_276349_", at = @At("HEAD"), argsOnly = true, index = 2, remap = false)
    private Object undying$clampInternal(Object value) {
        return undying$clamp(value);
    }
}
