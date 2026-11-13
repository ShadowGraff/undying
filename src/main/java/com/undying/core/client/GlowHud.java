package com.undying.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.undying.core.ModConfig;
import com.undying.core.ModLog;
import com.undying.core.UndyingMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Collision boxes for glowing creatures.
 *
 * <h3>Why it keys off the glowing effect instead of the mod's own state</h3>
 * The client is not told whether the outline feature is on — that state lives on the server.
 * What the client does know is which entities are glowing, which is exactly the set the player
 * is looking at. Reading that instead of adding a second state to keep in sync means the boxes
 * cannot disagree with the outlines: they appear, move and stop together.
 *
 * <h3>Why this is not drawn every frame for every entity</h3>
 * Only glowing entities, and only when the box switch is on. The check is one effect lookup per
 * rendered entity, which is cheaper than the outline vanilla is already drawing for the same
 * entities.
 */
@Mod.EventBusSubscriber(modid = UndyingMod.MODID, value = Dist.CLIENT)
public final class GlowHud {

    private GlowHud() {}

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        try {
            boolean show;
            try {
                show = ModConfig.GLOW_HITBOX.get();
            } catch (Throwable t) {
                return;   // config not loaded yet
            }
            if (!show) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;

            PoseStack pose = event.getPoseStack();
            Vec3 cam = event.getCamera().getPosition();
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());

            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof LivingEntity living)) continue;
                if (!living.hasEffect(MobEffects.GLOWING)) continue;

                AABB box = living.getBoundingBox().move(-cam.x, -cam.y, -cam.z);
                LevelRenderer.renderLineBox(pose, lines, box, 0.25F, 1.0F, 0.5F, 1.0F);
            }

            buffers.endBatch(RenderType.lines());
        } catch (Throwable t) {
            ModLog.swallowed("GlowHud.render", t);
        }
    }
}
