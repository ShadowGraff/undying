package com.undying.core;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * MixinConfigPlugin (second layer of defence).
 *
 * Responsibility: ensure this mod's mixins are definitely applied, preventing an
 * external party from interfering with config loading and thereby silently disabling
 * our protection logic (death lock / ward shield / anti-tamper, etc.).
 *
 * Note the boundary: IMixinConfigPlugin only affects <i>this mod's own</i> mixin
 * config; it cannot interfere with other mods' mixins —— that is a limitation of the
 * Mixin framework.
 *
 * ⚠️ Strict constraint: this class is called extremely early in Mixin loading (before
 *    the Forge registries are ready), so it <i>must never reference the UndyingMod main
 *    class</i> —— doing so would trigger static initialization of the main class (which
 *    contains DeferredRegister registration) and cause a "Not bootstrapped" crash.
 *    Logging must use this class's own LOGGER.
 */
public class UndyingMixinPlugin implements IMixinConfigPlugin {

    /** Dedicated logger: UndyingMod.LOGGER must not be used (it would trigger main-class static initialization) */
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean loaded = false;

    @Override
    public void onLoad(String mixinPackage) {
        loaded = true;
        LOGGER.info("[Undying] MixinConfigPlugin loaded, package: {}", mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return null; // use the default refmap strategy
    }

    /**
     * Force all of this mod's mixins to take effect.
     * Returning true means the mixin should be applied —— we always return true so
     * that the protection logic can never be skipped because of an environment check.
     */
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // No extra handling: this mod's mixin targets are vanilla classes, so no coordination is needed
    }

    @Override
    public List<String> getMixins() {
        return null; // no dynamically appended mixins
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
        // Pre-injection hook: could be used to validate target-class integrity (future extension point)
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
        LOGGER.debug("[Undying] mixin applied: {} -> {}", mixinClassName, targetClassName);
    }

    public static boolean isLoaded() { return loaded; }
}
