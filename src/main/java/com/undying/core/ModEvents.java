package com.undying.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingBreatheEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * All Forge event wiring.
 *
 * <p><b>This class only wires things up.</b> Each method's job is to decide "should this be handled
 * at all", then hand the work to the matching feature class - no business logic belongs here.
 *
 * <p>The reason: event handlers are the easiest place to "just add two lines while you are here",
 * and that is usually where a god class grows from.
 */
public final class ModEvents {

    // ═══════════ Mod bus ═══════════

    @SubscribeEvent
    public void onCommonSetup(FMLCommonSetupEvent event) {
        HiddenGear.preBuild();              // the template must be built after item registration completes
        Networking.register();           // creative inventory network channel
        ModConfig.warnBlacklistedMods(); // startup check: are isolated mods still loading
        ThreatScanner.scanOnce();        // threat scan (off by default, report only, no action)
    }

    // ═══════════ Player input ═══════════

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        if (ChatTriggers.handle(event.getPlayer(), event.getMessage().getString())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickSword(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (!Environment.isAllowed(p)) return;   // single-player only
        if (!VoidSword.isHeld(p)) return;
        event.setCanceled(true);
        VoidSword.sweep(p);
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (!Environment.isAllowed(p)) return;   // single-player only
        if (!VoidSword.isHeld(p)) return;
        VoidSword.startExecution(p, event.getTarget());
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        AntiGriefCommand.register(event.getDispatcher());
    }

    // ═══════════ Sessions ═══════════

    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide) return;
        if (e.getEntity() instanceof ServerPlayer p) {
            PlayerSession.onJoin(p);
        }
    }

    @SubscribeEvent
    public void onQuit(PlayerEvent.PlayerLoggedOutEvent e) {
        // The single cleanup entry point: all player state hangs off PlayerState, one line clears it
        PlayerState.clear(e.getEntity().getUUID());
    }

    // ═══════════ Every tick ═══════════

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer p)) return;

        // Self-measurement cost: only [this mod's own] segment is measured, and finally catches early
        // returns so the accounting cannot silently miss a newly added return.
        long t0 = System.nanoTime();
        try {
            TickTasks.run(p);
        } finally {
            TickBudget.sample(System.nanoTime() - t0, p);
        }
    }

    // ═══════════ Enchantment effects of the hidden gear ═══════════

    /**
     * Respiration (hidden helmet).
     *
     * <p>The vanilla semantics are "a chance not to consume oxygen, plus an occasional refill".
     * Forge exposes it as {@code LivingBreatheEvent}, so it can be reproduced exactly here:
     * refill allowed + no consumption = unlimited underwater breathing.
     */
    @SubscribeEvent
    public void onBreathe(LivingBreatheEvent event) {
        try {
            if (!ModConfig.MASTER_TOGGLE.get() || !ModConfig.HIDDEN_GEAR.get()) return;
            if (!(event.getEntity() instanceof ServerPlayer p)) return;

            int level = HiddenEnchants.respirationLevel(p.getUUID());
            if (level <= 0) return;

            event.setCanBreathe(true);
            event.setCanRefillAir(true);
            event.setRefillAirAmount(Math.max(1, level)); // higher level refills faster
            event.setConsumeAirAmount(0);
        } catch (Throwable t) {
            ModLog.swallowed("ModEvents.onBreathe", t);
        }
    }

    /**
     * Aqua Affinity (hidden helmet).
     *
     * <p>The vanilla effect is to <b>cancel the underwater mining speed penalty</b> (5x slower by default).
     * Here the "penalised" speed is restored - consistent with the vanilla semantics, with no extra buff.
     */
    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        try {
            if (!ModConfig.MASTER_TOGGLE.get() || !ModConfig.HIDDEN_GEAR.get()) return;
            if (!(event.getEntity() instanceof ServerPlayer p)) return;
            if (!p.isUnderWater()) return;
            if (HiddenEnchants.aquaAffinityLevel(p.getUUID()) <= 0) return;

            float boosted = event.getOriginalSpeed() * 5.0F;
            if (boosted > event.getNewSpeed()) {
                event.setNewSpeed(boosted);
            }
        } catch (Throwable t) {
            ModLog.swallowed("ModEvents.onBreakSpeed", t);
        }
    }

    // ═══════════ Damage ═══════════

    @SubscribeEvent
    public void onDamage(LivingHurtEvent event) {
        DamageHandling.onHurt(event);
    }
}
