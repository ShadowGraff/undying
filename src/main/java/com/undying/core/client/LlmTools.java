package com.undying.core.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The facts the model is allowed to ask for.
 *
 * <h3>Why every tool is read-only</h3>
 * A model that can run commands is a model that can empty an inventory, teleport a player into
 * the void, or kill everything nearby — and it would be doing so on the strength of a sentence
 * somebody typed. The useful half of the feature (what time is it, what is that mob, how far to
 * the nearest village) needs no write access at all, so there is none. If command execution is
 * ever added it has to be a separate, explicitly confirmed path, not a tool.
 *
 * <h3>Why the work hops to the client thread</h3>
 * The request runs on a background thread, but the world may only be read from the render
 * thread. Each tool therefore posts itself to the client and waits for the answer. A three-second
 * cap keeps a hung tool from turning into a hung conversation.
 */
public final class LlmTools {

    private LlmTools() {}

    private static final String SCHEMA_JSON = """
            [
              {"type":"function","function":{
                "name":"get_player_state",
                "description":"Where the player is, health, hunger, game mode, time of day and weather.",
                "parameters":{"type":"object","properties":{},"required":[]}}},
              {"type":"function","function":{
                "name":"list_nearby_entities",
                "description":"Entities near the player, with their registry id (which includes the mod id) and distance.",
                "parameters":{"type":"object","properties":{
                  "radius":{"type":"number","description":"Blocks, default 32, max 64"}},"required":[]}}},
              {"type":"function","function":{
                "name":"get_inventory_summary",
                "description":"What the player is carrying, aggregated by item id.",
                "parameters":{"type":"object","properties":{},"required":[]}}},
              {"type":"function","function":{
                "name":"list_mods",
                "description":"Loaded mods and how many there are.",
                "parameters":{"type":"object","properties":{},"required":[]}}}
            ]
            """;

    public static JsonArray schemas() {
        return JsonParser.parseString(SCHEMA_JSON).getAsJsonArray();
    }

    /** @return plain text to hand back to the model; never throws */
    public static String run(String name, JsonObject args) {
        if (name == null) return "未知工具";
        try {
            return switch (name) {
                case "get_player_state" -> onClient(LlmTools::playerState);
                case "list_nearby_entities" -> onClient(() -> nearbyEntities(args));
                case "get_inventory_summary" -> onClient(LlmTools::inventory);
                case "list_mods" -> onClient(LlmTools::mods);
                default -> "未知工具: " + name;
            };
        } catch (Throwable t) {
            return "工具执行失败: " + t;
        }
    }

    // ═══════════ Implementations (client thread) ═══════════

    private static String playerState() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return "玩家不在世界中";

        var p = mc.player;
        long dayTime = mc.level.getDayTime() % 24000L;
        String mode = p.isCreative() ? "创造" : p.isSpectator() ? "旁观" : "生存";

        return String.join("\n",
                "维度: " + mc.level.dimension().location(),
                String.format("坐标: %.1f %.1f %.1f", p.getX(), p.getY(), p.getZ()),
                String.format("生命: %.1f / %.1f", p.getHealth(), p.getMaxHealth()),
                "饥饿: " + p.getFoodData().getFoodLevel() + "/20",
                "模式: " + mode,
                "时间: " + dayTime + " tick（0=日出，6000=正午，13000=夜晚，18000=午夜）",
                "天气: " + (mc.level.isThundering() ? "雷暴" : mc.level.isRaining() ? "下雨" : "晴"),
                "经验等级: " + p.experienceLevel);
    }

    private static String nearbyEntities(JsonObject args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return "玩家不在世界中";

        double radius = 32;
        if (args != null && args.has("radius")) {
            try {
                radius = Math.max(1, Math.min(64, args.get("radius").getAsDouble()));
            } catch (Throwable ignored) {
                // keep the default
            }
        }

        var p = mc.player;
        AABB box = p.getBoundingBox().inflate(radius);
        List<Entity> found = mc.level.getEntities(p, box, e -> e != p && e.isAlive());

        if (found.isEmpty()) return radius + " 格内没有其它实体";

        List<Entity> sorted = new ArrayList<>(found);
        sorted.sort(Comparator.comparingDouble(e -> e.distanceTo(p)));

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Entity e : sorted) {
            counts.merge(idOf(e), 1, Integer::sum);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(radius).append(" 格内共 ").append(found.size()).append(" 个实体：\n");
        int shown = 0;
        for (Entity e : sorted) {
            if (shown++ >= 12) break;
            sb.append("- ").append(idOf(e))
              .append(String.format("  距离 %.1f 格", e.distanceTo(p)));
            Component custom = e.getCustomName();
            if (custom != null) sb.append("  名称: ").append(custom.getString());
            sb.append('\n');
        }
        if (found.size() > 12) sb.append("（还有 ").append(found.size() - 12).append(" 个未列出）\n");

        sb.append("按类型统计: ").append(counts);
        return sb.toString();
    }

    private static String inventory() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "玩家不在世界中";

        var inv = mc.player.getInventory();
        Map<String, Integer> counts = new LinkedHashMap<>();
        int used = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            used++;
            counts.merge(BuiltInRegistries.ITEM.getKey(st.getItem()).toString(), st.getCount(), Integer::sum);
        }
        if (counts.isEmpty()) return "背包是空的";
        return "占用 " + used + " 个槽位：\n" + counts;
    }

    private static String mods() {
        var mods = ModList.get().getMods();
        StringBuilder sb = new StringBuilder("共 " + mods.size() + " 个模组：\n");
        int shown = 0;
        for (var info : mods) {
            if (shown++ >= 40) {
                sb.append("…（还有 ").append(mods.size() - 40).append(" 个）");
                break;
            }
            sb.append("- ").append(info.getModId()).append(" (").append(info.getDisplayName()).append(")\n");
        }
        return sb.toString();
    }

    // ═══════════ Plumbing ═══════════

    private static String idOf(Entity e) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        return key == null ? e.getType().toString() : key.toString();
    }

    /**
     * Runs a world read on the client thread and waits for it.
     *
     * <p>Called from the request thread. The three-second cap is not about slow hardware: every
     * read here is a loop over a few dozen objects. It exists so that a bug in a tool cannot
     * turn into a conversation that never returns.
     */
    private static String onClient(Supplier<String> body) {
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture<String> f = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                f.complete(body.get());
            } catch (Throwable t) {
                f.complete("工具出错: " + t);
            }
        });
        try {
            return f.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "工具超时（3 秒）";
        }
    }
}
