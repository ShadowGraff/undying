package com.undying.core;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * Mod entry point.
 *
 * <p><b>This class is allowed to contain exactly four things: constants, item
 * registration, config registration, and event bus wiring.</b> Business logic does
 * not belong here at all —— event handling lives in {@link ModEvents}, and each
 * feature is its own class.
 *
 * <p>Why this rule is spelled out in a comment: a god class is not built in a day,
 * it usually starts with "just adding two lines to the entry point".
 *
 * <p>History: before the 2026-10-11 refactor this class was <b>980 lines</b> and
 * simultaneously managed 19 state tables, the execution chain, the aura, the command
 * tree, god gear construction, chat trigger words, chat trigger words, void rescue…
 * Now only the following remains.
 */
@Mod(UndyingMod.MODID)
public class UndyingMod {

    public static final String MODID = "undying";

    /**
     * ═══════════════════════════════════════════════════════════════════
     *  Developers
     *
     *    ShadowGraff   project founder, designer
     *    DeepSeek      implementation, log analysis, protection
     * ═══════════════════════════════════════════════════════════════════
     */
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Shared chat prefix tag (also used by the small classes split out later) */
    public static final String TAG = "Undying";

    // ── Item registration ──
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final RegistryObject<Item> VOID_SWORD = ITEMS.register("void_sword", VoidSwordItem::new);
    public static final RegistryObject<Item> WARD_SHIELD = ITEMS.register("ward_shield", WardShieldItem::new);

    public UndyingMod() {
        ModLoadingContext.get().registerConfig(Type.COMMON, ModConfig.SPEC);

        // One instance wired to two buses: the Forge event bus + the mod event bus
        ModEvents events = new ModEvents();
        MinecraftForge.EVENT_BUS.register(events);

        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());

        // The god gear templates must be built only after item registration has
        // completed (FMLCommonSetupEvent), otherwise RegistryObject.get() throws an NPE
        // because the registries are not populated yet
        FMLJavaModLoadingContext.get().getModEventBus().addListener(events::onCommonSetup);

        LOGGER.info("[Undying] loaded");
    }
}
