package com.undying.core.mixin;

import com.undying.core.Const;
import com.undying.core.ModConfig;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;

/**
 * Anti-tamper: experience protection.
 * Intercepts <b>anomalous</b> negative deductions in ServerPlayer.giveExperiencePoints(int).
 *
 * Decision rules ("only block negative XP of unknown origin"):
 *   1. Not a negative value                       → allow
 *   2. Small single deduction (within threshold)  → allow (a legitimate cost never takes a
 *                                                   huge chunk at once)
 *   3. The call stack contains a whitelisted vanilla/Forge/loader frame → allow (the
 *      player's own action)
 *   4. Everything else (large amount + unknown origin) → block and record
 *
 * ═══════════ 2026-10-05 fix (important) ═══════════
 * The original whitelist contained `cn.startsWith("java.")` and `"jdk."`.
 * But frame 0 of `Thread.getStackTrace()` is <b>always `java.lang.Thread`</b> —
 * so the very first loop iteration matched the `java.` prefix and hit `return true`.
 *
 * Measured evidence (reproduced on JDK 17):
 *   frame[0] class = java.lang.Thread
 *   undying$isFromTrustedSource() called from a NON-whitelisted mod = true
 *
 * ⇒ The original whitelist was <b>always true</b>, branch 4 was unreachable, and this
 *   protection was effectively fail-open — it could not block a single large XP drain.
 *   That is far worse than the "bypassable via reflection" admitted in the comments:
 *   not bypassable, simply never in effect.
 *
 * Fix essentials:
 *   a) Remove the `java.` / `jdk.` prefixes — JDK frames are a normal part of any stack and
 *      are not evidence of trust
 *   b) Switch to StackWalker: no full array materialization, better performance
 *   c) Explicitly reject `java.lang.reflect.` / `java.lang.invoke.` frames (borrowed from
 *      the external implementation's TrustChecker)
 *   d) Switch to a <b>positive whitelist</b>: only explicitly known vanilla/loader package
 *      prefixes are trusted; if it cannot be decided, return false (= block), staying
 *      fail-closed
 *
 * ⚠️ Honest limits (still present after the fix; do not treat as absolute safety):
 *   The whitelist is decided by <b>package prefix</b>, not "exclude every external mod".
 *   A malicious mod whose package starts with `net.minecraft.` can still slip into the
 *   trusted zone. Closing that completely would need a whitelist of vanilla call sites,
 *   and the cost/benefit does not justify it.
 *   The goal of this mechanism is to stop common, direct, crude XP drains and leave a trace.
 */
@Mixin(ServerPlayer.class)
public abstract class MixinAntiTamperXp {

    private static final StackWalker WALKER =
            StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE);

    /**
     * Trusted package prefixes — they <b>must end with a dot</b>, to avoid prefix confusion
     * with things like `net.minecraftevil.`.
     *
     * Note that java. / jdk. are <b>deliberately absent</b> here: any call stack necessarily
     * contains many JDK frames, and counting them as "trusted" makes the whitelist always true.
     */
    private static final String[] TRUSTED_PACKAGES = {
            "net.minecraft.",
            "net.minecraftforge.",
            "com.mojang.",
            "cpw.mods.",
            "org.spongepowered.",
            "com.undying.core."
    };

    @Inject(method = "m_6756_", at = @At("HEAD"), cancellable = true, remap = false)
    private void undying$guardExperienceDrain(int amount, CallbackInfo ci) {
        if (!ModConfig.MASTER_TOGGLE.get() || !ModConfig.ANTI_TAMPER.get()) return;
        if (!((Object) this instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        if (!com.undying.core.Environment.isAllowed(sp)) return;   // single-player only
        if (amount >= 0) return;                                       // not a deduction
        if (-amount <= Const.XP_SUSPICIOUS_DRAIN) return;               // small: normal cost
        if (undying$isFromTrustedSource()) return;                              // whitelisted source: allow

        // Large amount + unknown origin → block and leave a trace
        ci.cancel();
        com.undying.core.IntegrityGuard.LOGGER.warn(
                "[anti-tamper] blocked suspicious XP drain: {} (caller not whitelisted)", -amount);
    }

    /**
     * Decides from the call stack whether this deduction is a "normal action by the player".
     *
     * Approach: skip JDK / Mixin / this mod's framework frames and take the <b>first "real
     * caller"</b> to decide whether it belongs to a trusted package.
     *
     * Why not "any trusted frame on the stack allows it": that loose form would treat every
     * call passing through the vanilla tick loop as trusted, and a malicious mod could bypass
     * it by calling directly from its own tick. Only looking at the direct caller is a
     * meaningful test.
     */
    private static boolean undying$isFromTrustedSource() {
        try {
            // list() stops walking as soon as it finds one; it does not traverse the whole stack
            StackFrame caller = WALKER.walk(frames -> frames
                    .filter(f -> !undying$isFrameworkFrame(f.getClassName()))
                    .limit(1)
                    .findFirst()
                    .orElse(null));

            if (caller == null) return false;      // undecidable → do not allow

            String cn = caller.getClassName();

            // Reflection / method handles = untrusted (borrowed from the external
            // implementation's TrustChecker)
            if (cn.startsWith("java.lang.reflect.") || cn.startsWith("java.lang.invoke.")) {
                return false;
            }

            for (String pkg : TRUSTED_PACKAGES) {
                if (cn.startsWith(pkg)) return true;
            }
            return false;
        } catch (Throwable t) {
            // Decision failed → block (fail-closed), rather than silently allowing through
            return false;
        }
    }

    /**
     * Framework frames: these are the "path" of a call, not its "originator", and must be skipped.
     *
     * Uses exact prefixes instead of a loose `java.` — the latter would also count a
     * stack-bottom frame like `java.lang.Thread`, which is precisely what caused this bug.
     */
    private static boolean undying$isFrameworkFrame(String cn) {
        return cn.startsWith("java.lang.Thread")
                || cn.startsWith("java.lang.StackWalker")
                || cn.startsWith("java.util.stream.")
                || cn.startsWith("java.util.Spliterator")
                || cn.startsWith("java.util.concurrent.")
                || cn.startsWith("jdk.internal.")
                || cn.startsWith("sun.")
                || cn.startsWith("org.spongepowered.")
                || cn.startsWith("cpw.mods.modlauncher.")
                || cn.startsWith("com.undying.core.mixin.");
    }
}
