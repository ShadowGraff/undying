package com.undying.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;

/**
 * Time, weather and game mode, driven from the config screen.
 *
 * <h3>Why these are frozen together with being set</h3>
 * "Make it day" that lasts forty seconds is a worse feature than no feature: the player sets
 * the light they want to build in and it drifts away while they build. Every time and weather
 * action therefore also stops the vanilla cycle that would undo it, and each group has an
 * explicit "resume" action so the state is never something the player cannot leave.
 *
 * <p>Weather is not a one-off call either: {@code setWeatherParameters} starts a countdown
 * rather than a permanent state, so a lock is kept alive from the tick and re-applied while it
 * is on. {@link #tick} is the only thing that makes "lock" mean lock.
 *
 * <h3>What is not here</h3>
 * No difficulty, no gamerules beyond the daylight cycle, no per-dimension weather. Each of
 * those is a decision with consequences the player cannot see from a button, and this is meant
 * to be the panel you can press without reading documentation first.
 */
public final class QuickActions {

    private QuickActions() {}

    // Time of day in ticks. 0 is sunrise, 6000 noon, 12000 sunset, 18000 midnight.
    private static final long TIME_DAY = 1000L;
    private static final long TIME_NOON = 6000L;
    private static final long TIME_NIGHT = 13000L;
    private static final long TIME_MIDNIGHT = 18000L;

    /** How long a re-applied weather window lasts, in ticks. */
    private static final int WEATHER_WINDOW = 1200;

    /**
     * Set while this mod is performing a game-mode change at the player's own request.
     *
     * <p>The gamemode lock exists to stop <i>other mods</i> changing the mode behind the
     * player's back. It cannot tell "a mod did this" from "the player pressed a button in our
     * own panel", so the panel has to say which it is. Without this, the quick-control button
     * is blocked by our own anti-tamper — which is exactly what happened the first time it was
     * tried, and it looked like a random failure because the lock also lets OP players through.
     */
    private static final ThreadLocal<Boolean> PLAYER_REQUEST = ThreadLocal.withInitial(() -> false);

    public static boolean isPlayerRequest() {
        return Boolean.TRUE.equals(PLAYER_REQUEST.get());
    }

    private static boolean weatherLocked;
    private static boolean lockedRaining;
    private static boolean lockedThundering;

    public static void apply(ServerPlayer p, String action) {
        try {
            if (p == null || action == null) return;
            if (!ModConfig.QUICK_CONTROLS.get()) return;
            if (!Environment.isAllowed(p)) return;   // single-player only, like everything else

            ServerLevel ow = p.server.overworld();

            switch (action) {
                case "time_day" -> freezeTime(p, ow, TIME_DAY, "白天", "day");
                case "time_noon" -> freezeTime(p, ow, TIME_NOON, "正午", "noon");
                case "time_night" -> freezeTime(p, ow, TIME_NIGHT, "夜晚", "night");
                case "time_midnight" -> freezeTime(p, ow, TIME_MIDNIGHT, "午夜", "midnight");
                case "time_resume" -> {
                    p.server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, p.server);
                    Msg.ok(p, UndyingMod.TAG).bi("时间恢复流动", "Time flows again").send();
                }

                case "weather_clear" -> lockWeather(p, ow, false, false, "晴天", "clear");
                case "weather_rain" -> lockWeather(p, ow, true, false, "下雨", "rain");
                case "weather_thunder" -> lockWeather(p, ow, true, true, "雷暴", "thunder");
                case "weather_resume" -> {
                    weatherLocked = false;
                    Msg.ok(p, UndyingMod.TAG)
                            .bi("天气恢复变化", "Weather changes again").send();
                }

                case "mode_survival" -> mode(p, GameType.SURVIVAL, "生存", "survival");
                case "mode_creative" -> mode(p, GameType.CREATIVE, "创造", "creative");
                case "mode_adventure" -> mode(p, GameType.ADVENTURE, "冒险", "adventure");
                case "mode_spectator" -> mode(p, GameType.SPECTATOR, "旁观", "spectator");

                default -> IntegrityGuard.LOGGER.warn("[quick] unknown action: {}", action);
            }
        } catch (Throwable t) {
            ModLog.swallowed("QuickActions.apply", t);
        }
    }

    private static void freezeTime(ServerPlayer p, ServerLevel ow, long time,
                                   String zh, String en) {
        ow.setDayTime(time);
        p.server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, p.server);
        Msg.ok(p, UndyingMod.TAG)
                .bi("时间：" + zh + "（已冻结）", "Time: " + en + " (frozen)").send();
    }

    private static void lockWeather(ServerPlayer p, ServerLevel ow,
                                    boolean raining, boolean thundering,
                                    String zh, String en) {
        weatherLocked = true;
        lockedRaining = raining;
        lockedThundering = thundering;
        ow.setWeatherParameters(0, WEATHER_WINDOW, raining, thundering);
        Msg.ok(p, UndyingMod.TAG)
                .bi("天气：" + zh + "（已锁定）", "Weather: " + en + " (locked)").send();
    }

    private static void mode(ServerPlayer p, GameType type, String zh, String en) {
        // Marked as a player request so our own gamemode lock lets it through, and only it:
        // a mod calling setGameMode at any other moment is still blocked.
        boolean ok;
        PLAYER_REQUEST.set(true);
        try {
            ok = p.setGameMode(type);
        } finally {
            PLAYER_REQUEST.set(false);
        }

        // Not silent on refusal: the gamemode lock may be doing its job, and "I clicked and
        // nothing happened" is the failure this project keeps having to apologise for.
        if (!ok) {
            Msg.warn(p, UndyingMod.TAG)
                    .bi("切换失败：被模式锁拦住了", "Blocked by the game mode lock").send();
            return;
        }
        Msg.ok(p, UndyingMod.TAG).bi("模式：" + zh, "Mode: " + en).send();
    }

    /**
     * Keeps a locked weather window from expiring.
     *
     * <p>Once a second is enough: the window is 1200 ticks, so there is no moment where the
     * weather has drifted and the player could notice before it is re-applied.
     */
    public static void tick(ServerPlayer p) {
        if (!weatherLocked) return;
        try {
            if (!ModConfig.QUICK_CONTROLS.get() || !Environment.isAllowed(p)) return;
            if (p.tickCount % Const.TICKS_PER_SECOND != 0) return;
            p.server.overworld().setWeatherParameters(0, WEATHER_WINDOW,
                    lockedRaining, lockedThundering);
        } catch (Throwable t) {
            ModLog.swallowed("QuickActions.tick", t);
        }
    }

    /** Whether the weather is currently being held in place. Shown in the panel. */
    public static boolean weatherLocked() {
        return weatherLocked;
    }
}
