package com.undying.core.mixin;

import com.undying.core.Const;
import com.undying.core.DeathLock;
import com.undying.core.Environment;
import com.undying.core.ModConfig;
import com.undying.core.UndyingMod;
import com.undying.core.Notify;
import com.undying.core.Thorns;
import com.undying.core.TotemFx;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    /** Extra armor value from hidden armor: netherite helmet 3 + chestplate 8 + leggings 6 + boots 3 */
    @Unique
    private static final int HIDDEN_ARMOR_BONUS = 20;

    /*
     * Tracks temporary totems injected so vanilla can fire LivingUseTotemEvent normally.
     *
     * ⚠️ 2026-09-19 fix: this used to be a WeakHashMap. It would not actually fail in
     * practice (injection and restore happen within the same method call, so no GC can
     * occur in between), but the semantics were wrong — once a key is collected, the
     * restore phase loses the original offhand item. Switched to a plain HashMap, and
     * undying$restoreOffhand is guaranteed to remove the entry (see below).
     */
    @Unique
    private static final Map<LivingEntity, ItemStack> TOTEM_RESTORE = new java.util.HashMap<>();

    // Step 1: ensure a totem exists so vanilla checkTotemDeathProtection fires all events/sounds
    // Hidden second offhand: if HiddenSlots holds a totem (immortal mode initializes it on join)
    // → inject a temporary one on death
    //
    // ⚠️ 2026-10-07 added a "visual cooldown":
    //    when killed repeatedly, no totem is injected for 2 seconds — that vanilla branch is
    //    then not taken, so the particles do not replay over and over (they would smear all
    //    over the screen). The sound has been muted separately.
    //    During the cooldown the player still cannot die (death lock / health gate / snapshot
    //    restore are all active); there is simply no visual effect.
    @Inject(method = "m_21262_", at = @At("HEAD"), remap = false)
    private void undying$ensureTotem(DamageSource src, CallbackInfoReturnable<Boolean> cir) {
        if (!ModConfig.MASTER_TOGGLE.get() || !ModConfig.TOTEM_PROTECT.get()) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayer p)) return;
        if (!Environment.isAllowed(p)) return;   // single-player only
        if (!com.undying.core.HiddenSlots.hasTotem(p.getUUID())) return; // no hidden totem

        for (InteractionHand h : InteractionHand.values()) {
            if (self.getItemInHand(h).is(Items.TOTEM_OF_UNDYING)) return; // totem already present
        }

        // Visual cooldown: skip the injection (only the effect is missing; the survival
        // logic is unaffected)
        if (TotemFx.inCooldown(p, true)) return;

        // Inject a temporary totem into offhand; vanilla will consume it + fire event
        TOTEM_RESTORE.put(self, self.getOffhandItem().copy());
        self.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
    }

    // Step 2: restore offhand and force success
    @Inject(method = "m_21262_", at = @At("RETURN"), remap = false)
    private void undying$restoreOffhand(DamageSource src, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        ItemStack original = TOTEM_RESTORE.remove(self);
        if (original != null) {
            self.setItemInHand(InteractionHand.OFF_HAND, original);
            cir.setReturnValue(true);
        }
    }

    /**
     * At zero health, damage is <b>fully negated</b>. The core gate of the death lock.
     *
     * ═══════════ 2026-10-05 why this layer is mandatory ═══════════
     * A deadlock loop caught in live testing:
     *   ① A titan hits the player → health drops to 0
     *   ② The vanilla check `if (getHealth() <= 0)` inside hurt() succeeds
     *      → the death animation starts (tickDeath) and the timer begins counting up
     *   ③ Our undying$guardHealth writes health back to 1.0 — but the animation is already running
     *   ④ One second later tickDeath calls remove(KILLED)
     *   ⑤ MixinEntity blocks it
     *   ⑥ The timer is still full → the next round repeats ④
     *   ⇒ The player is stuck "half alive, half dead": the server considers them alive
     *      (health 1.0, isDeadOrDying=false), yet tries to remove them every 50 ms,
     *      so the character twitches and cannot be controlled.
     *
     * The root cause is that "let health drop to 0 first, then change it back" is
     * inherently flawed: once the death animation has started, changing health cannot
     * stop it.
     *
     * ⇒ The correct approach: when health is already low, <b>refuse the damage outright</b>
     * so it can never reach 0.
     *
     * Note: this layer is active while the death lock and immortal mode are on. Turning the
     * death lock off restores vanilla behavior.
     */
    @Inject(method = "m_6469_", at = @At("HEAD"), cancellable = true, remap = false)
    private void undying$guardLethalDamage(net.minecraft.world.damagesource.DamageSource src, float amount,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (!ModConfig.MASTER_TOGGLE.get() || !ModConfig.DEATH_LOCK.get()) return;
        if (!((Object) this instanceof ServerPlayer p)) return;
        if (!Environment.isAllowed(p)) return;   // single-player only
        // Already in the death flow: do not interfere, let the logic below handle it
        // (to avoid getting stuck in the real death teardown)
        if (p.isDeadOrDying()) return;
        // Health at or below the floor → this hit would be lethal → refuse it entirely
        if (p.getHealth() <= Const.DEATH_LOCK_HEALTH) {
            if (ModConfig.HIDDEN_GEAR.get() && com.undying.core.HiddenSlots.hasShield(p.getUUID())) {
                Thorns.apply(p, src);
            }
            p.level().broadcastEntityEvent(p, (byte) 35);
            cir.setReturnValue(false);
        }
    }

    /**
     * Health guard: while the death lock is active, the player's health is
     * <b>never allowed to be written as 0 or negative</b>.
     *
     * This is the first gate against the "drain health, then remove" combo. Without it, an
     * external implementation only had to call setHealth(0) first and isDeadOrDying() became
     * true, so our later removal interception stood aside through its own "already dead, let
     * it through" path.
     *
     * Now: health cannot be drained → the dead state can never be established → later
     * interceptions never stand aside.
     */
    @ModifyVariable(method = "m_21153_", at = @At("HEAD"), argsOnly = true, remap = false)
    private float undying$guardHealth(float health) {
        if (!ModConfig.MASTER_TOGGLE.get() || !ModConfig.DEATH_LOCK.get()) return health;
        if (!((Object) this instanceof ServerPlayer p)) return health;
        if (!Environment.isAllowed(p)) return health;   // single-player only
        if (health > 0.0F) return health;

        // Our own /antigrief die: let it through as-is and grant the death permission
        if (DeathLock.isSuicidePending(p.getUUID())) {
            DeathLock.grant(p.getUUID());
            return health;
        }
        // Already inside the death-authorization window (teardown after a real death):
        // let it through as-is
        if (DeathLock.isGranted(p)) return health;

        // Everything else is raised back to the floor health; never to zero
        p.level().broadcastEntityEvent(p, (byte) 35); // death animation
        return Const.DEATH_LOCK_HEALTH;
    }

    /**
     * The master gate of the death flow: while the death lock is on, die() never runs to
     * completion.
     *
     * <p>Cancelling it and rewinding the death flow to its starting point means the player
     * sees "that hit was blocked" rather than "I died".
     *
     * <p>The old implementation "grant the death permission, then let it through", and that
     * path had two consequences: the client would show the death screen first
     * ({@code ServerPlayer.die()} sends
     * {@code ClientboundPlayerCombatKillPacket} before calling super), and the grant would
     * let the subsequent {@code remove(KILLED)} → respawn clone through.
     *
     * <p>Normal mode is unaffected (the toggle check is the first line); a real death goes
     * through the /antigrief die branch.
     */
    @Inject(method = "m_6667_", at = @At("HEAD"), cancellable = true, remap = false)
    private void undying$onDie(DamageSource src, CallbackInfo ci) {
        if (!ModConfig.MASTER_TOGGLE.get() || !ModConfig.DEATH_LOCK.get()) return;
        if (!((Object) this instanceof ServerPlayer p)) return;

        // Our own /antigrief die: let it through and grant the death permission.
        // Checked BEFORE the environment gate so that the suicide command still works
        // as a way out of a world, even though the gate blocks everything else.
        if (DeathLock.consumeSuicide(p.getUUID())) {
            DeathLock.grant(p.getUUID());
            UndyingMod.LOGGER.info("[Undying] Suicide: {}", p.getName().getString());
            return;
        }

        if (!Environment.isAllowed(p)) return;   // single-player only

        // Everything else: rewind the death flow to its starting point and reject this death
        DeathLock.exitState(p);
        Notify.removalBlocked(p, "die()");
        ci.cancel();
    }

    // Hidden third offhand (ward shield) + spawn protection: full damage immunity
    //  - has the hidden ward shield (immortal mode) → permanently immune to everything
    //  - otherwise, inside the spawn-protection window → briefly immune to everything
    //    (the first seconds after entering the world)
    @Inject(method = "m_6469_", at = @At("HEAD"), cancellable = true, remap = false)
    private void undying$guardWardShield(net.minecraft.world.damagesource.DamageSource src, float amount,
                                CallbackInfoReturnable<Boolean> cir) {
        if (!ModConfig.MASTER_TOGGLE.get()) return;
        if (!((Object) this instanceof ServerPlayer p)) return;
        if (!Environment.isAllowed(p)) return;   // single-player only
        boolean shielded = ModConfig.HIDDEN_GEAR.get() && com.undying.core.HiddenSlots.hasShield(p.getUUID());
        boolean spawning = com.undying.core.SpawnProtection.isProtected(p);
        if (!shielded && !spawning) return;
        // Run the thorns reflect manually before full immunity: otherwise, once hurt is
        // intercepted, thorns from both real and hidden armor stop working
        if (shielded) Thorns.apply(p, src);
        cir.setReturnValue(false); // full immunity: no damage, no events, no knockback
    }

    // Hidden armor (the second god-tier set): armor value stacks a full netherite set (20);
    // vanilla armor calculation then applies automatically
    @Unique
    @Inject(method = "m_21230_", at = @At("RETURN"), cancellable = true, remap = false)
    private void undying$hiddenArmorValue(CallbackInfoReturnable<Integer> cir) {
        if (!ModConfig.MASTER_TOGGLE.get() || !ModConfig.HIDDEN_GEAR.get()) return;
        if (!((Object) this instanceof ServerPlayer p)) return;
        if (!Environment.isAllowed(p)) return;   // single-player only
        if (!com.undying.core.HiddenSlots.hasArmor(p.getUUID())) return;
        cir.setReturnValue(cir.getReturnValue() + HIDDEN_ARMOR_BONUS);
    }
}
