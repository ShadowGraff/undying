package com.undying.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Execution chain and forced termination.
 *
 * <p>4 layers of escalation: damage → health to zero → {@code Entity.kill()} →
 * fallback cleanup. After each layer the target is examined to confirm it really
 * entered the death process before stopping.
 *
 * <p><b>Why the vanilla death process must be "advanced" instead of removing the
 * entity outright</b>: calling {@code remove()} directly skips the vanilla lethal
 * check and pushes the server into a self-contradictory state ({@code isDeadOrDying}
 * says alive while {@code isRemoved} says dead), so the client display disagrees
 * with reality.
 *
 *
 * <p><b>Layer 1 must come before health is set</b>: the first statement of vanilla
 * {@code hurt()} is {@code if (getHealth() <= 0) return false;} —— once health has
 * hit zero, further calls to {@code hurt()} are bounced straight back and
 * {@code die()} is never invoked.
 */
public final class Execution {

    private Execution() {}

    /** Progress of a single execution */
    public static final class State {
        final ServerPlayer player;
        Entity target;
        int layer;
        int cooldown;

        State(ServerPlayer p, Entity t) {
            this.player = p;
            this.target = t;
        }
    }

    /** Whether this player currently has an execution in progress (other per-tick handling for the same player is skipped during an execution) */
    public static boolean isRunning(UUID id) {
        PlayerState s = PlayerState.find(id);
        return s != null && s.execution != null;
    }

    /** Start an execution; returns false when one is already running */
    public static boolean start(ServerPlayer p, Entity target) {
        PlayerState s = PlayerState.of(p);
        if (s.execution != null) return false;
        s.execution = new State(p, target);
        return true;
    }

    /** Advance one step per tick; wraps up on its own once all layers are done */
    public static void tick(ServerPlayer p) {
        PlayerState ps = PlayerState.of(p);
        State s = ps.execution;
        if (s == null) return;
        advance(s);
        if (s.layer >= Const.EXECUTION_LAYERS) ps.execution = null;
    }

    private static void advance(State s) {
        s.cooldown--;
        if (s.cooldown > 0) return;
        s.cooldown = 2; // leave a 1-tick gap between layers

        ServerPlayer p = s.player;
        Entity t = s.target;

        // Target is already gone = success
        if (t == null || !t.isAlive() || t.isRemoved()) {
            Msg.ok(p, "exec").text("layer ").num(s.layer + 1).text(": target cleared").send();
            s.layer = Const.EXECUTION_LAYERS;
            return;
        }

        switch (s.layer) {
            case 0 -> {
                // The actual damage is dealt by the void sword in onDamage (through the vanilla damage path)
                Msg.info(p, "exec").text("layer 1: damage applied").send();
            }
            case 1 -> {
                Msg.info(p, "exec").text("layer 2: forced removal").send();
                ForceKill.kill(t);
            }
            case 2 -> {
                Msg.warn(p, "exec").text("layer 3: data cleared").send();
                ForceKill.clearPersistentData(t);
                if (t instanceof LivingEntity le) {
                    new ArrayList<>(le.getActiveEffects())
                            .forEach(e -> le.removeEffect(e.getEffect()));
                }
                if (!ForceKill.deadNow(t)) {
                    try {
                        t.kill();
                    } catch (Throwable ex) {
                        ModLog.swallowed("Execution.advance.kill", ex);
                    }
                }
            }
            case 3 -> {
                Msg.warn(p, "exec").text("layer 4: peaceful difficulty").send();
                p.server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
            }
            default -> { }
        }

        s.layer++;
        if (s.layer >= Const.EXECUTION_LAYERS) {
            Msg.ok(p, "exec").text("execution chain complete").send();
        }
    }

}
