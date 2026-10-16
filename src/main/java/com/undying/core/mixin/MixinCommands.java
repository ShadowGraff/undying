package com.undying.core.mixin;

import com.undying.core.IntegrityGuard;
import com.undying.core.ModConfig;
import com.undying.core.Notify;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Interception of the /kill command.
 *
 * ⚠️ 2026-09-19 fix: the original implementation only intercepted the <i>bare</i>
 *    `/kill` and `/minecraft:kill`, while `/kill @e`, `/kill @s` and
 *    `/minecraft:kill @a` all slipped through ——
 *    the genuinely lethal forms were the ones that got past (only the form with
 *    no arguments, which affects the executor itself, was blocked), i.e. the
 *    protection was pointed the wrong way.
 *
 * ⚠️ 2026-10-05 hardening: cover "kill hidden inside a subcommand".
 *    Previously only the command head was inspected, so forms such as
 *    `/execute as @e run kill @s` slipped straight through.
 *    Now the command is scanned <i>segment by segment</i>: a kill segment that
 *    immediately follows `run` is intercepted as well.
 *
 * What still cannot be intercepted (honest boundary):
 *   · Mods that bypass commands and call Entity.kill() / remove() directly ——
 *     those are caught by MixinEntity
 *   · Command strings that an external mod rewrites before reaching this method
 *
 * This class only covers the "command entry point" layer; the real fallbacks live
 * in MixinEntity (sealing kill/remove)
 * and MixinLivingEntity (sealing setHealth to zero).
 */
@Mixin(Commands.class)
public abstract class MixinCommands {

    /**
     * Determines whether this command text contains a call to the kill command.
     *
     * Rule (segment-by-segment scan):
     *   segment i is kill (or minecraft:kill) and either condition holds, which
     *   makes it a kill invocation:
     *     · i == 0          —— bare `/kill ...`
     *     · segment i-1 is run —— `/execute ... run kill ...`
     *
     * Segment-by-segment scanning is used instead of "the whole string contains
     * kill" to avoid false positives: for example naming an item, or `/say kill`,
     * would otherwise be misjudged.
     */
    @Unique
    private static boolean undying$isKillInvocation(String raw) {
        if (raw == null) return false;
        String t = raw.trim();
        if (t.startsWith("/")) t = t.substring(1);
        if (t.isEmpty()) return false;

        String[] parts = t.split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            String seg = parts[i].toLowerCase();
            boolean isKill = seg.equals("kill") || seg.equals("minecraft:kill");
            if (!isKill) continue;

            if (i == 0) return true;                                   // bare /kill
            if (parts[i - 1].equalsIgnoreCase("run")) return true;      // /execute ... run kill
        }
        return false;
    }

    @Inject(method = "m_230957_", at = @At("HEAD"), cancellable = true, remap = false)
    private void undying$onCommand(CommandSourceStack src, String cmd, CallbackInfoReturnable<Integer> cir) {
        if (!ModConfig.MASTER_TOGGLE.get() || !ModConfig.KILL_BLOCKER.get()) return;
        if (!com.undying.core.Environment.isAllowed(src.getServer())) return;   // single-player only
        if (!undying$isKillInvocation(cmd)) return;

        // Intercept this command
        cir.cancel();
        cir.setReturnValue(0);

        // Leave a trace: this log line is also our only evidence of whether the
        // interception worked, so it must be kept
        IntegrityGuard.LOGGER.warn("[AntiTamper] intercepted /kill command: {}", cmd == null ? "null" : cmd.trim());

        if (src.getEntity() instanceof ServerPlayer p) {
            Notify.killBlocked(p);
        }
    }
}
