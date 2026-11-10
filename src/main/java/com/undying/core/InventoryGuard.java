package com.undying.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Inventory guard: notices when the inventory changes in a way the player did not cause,
 * and offers to roll it back.
 *
 * <h3>Why this is a watch, not a wall</h3>
 * Hard-blocking inventory writes was considered and rejected. An inventory changes through
 * dozens of legitimate paths — containers, crafting, picking items up, shift-clicking,
 * furnace output, modded machines — and there is no reliable way to tell those apart from a
 * hostile mod's write at the moment it happens. A wall would either block the game or be so
 * permissive it blocked nothing.
 *
 * <p>So the rule is: let everything through, and watch the <i>result</i>. If a large change
 * appears with no plausible cause, ask. That is also what makes the feature safe on a
 * false positive — the worst case is a question the player ignores.
 *
 * <h3>What counts as a plausible cause</h3>
 * A container screen being open, or an open/drop/pickup in the last two seconds. Those cover
 * every ordinary way an inventory changes in bulk. Anything else that moves five or more
 * slots at once is worth a question.
 */
public final class InventoryGuard {

    private InventoryGuard() {}

    /** How long an ordinary action counts as "the cause" of a change. */
    private static final int LEGAL_WINDOW_TICKS = 40; // 2 seconds

    /** How long the question stays on screen before it quietly gives up. */
    private static final int ASK_TTL_TICKS = 20 * 15;

    /** Per-player runtime state. Lives on {@link PlayerState} so cleanup stays in one place. */
    public static final class State {
        /** Per-slot fingerprint of the last state we considered normal. */
        public int[] lastSlots;
        /** Contents of the last normal state, used as the rollback target. */
        public ItemStack[] lastStacks;

        public int lastContainerTick = Integer.MIN_VALUE / 2;
        public int lastDropTick = Integer.MIN_VALUE / 2;
        public int lastPickupTick = Integer.MIN_VALUE / 2;

        /** Non-zero while a question is pending. Volatile: the client HUD reads it. */
        public volatile long askId;
        /** Contents to restore if the player confirms. */
        public ItemStack[] rollbackTo;
        /** Server tick at which the question expires. */
        public int askExpiresTick;
    }

    private static State state(ServerPlayer p) {
        PlayerState s = PlayerState.of(p);
        if (s.inventoryGuard == null) s.inventoryGuard = new State();
        return s.inventoryGuard;
    }

    // ═══════════ Cause tracking (called from events) ═══════════

    public static void onContainerOpen(ServerPlayer p) {
        if (p == null) return;
        state(p).lastContainerTick = p.tickCount;
    }

    public static void onDrop(ServerPlayer p) {
        if (p == null) return;
        state(p).lastDropTick = p.tickCount;
    }

    public static void onPickup(ServerPlayer p) {
        if (p == null) return;
        state(p).lastPickupTick = p.tickCount;
    }

    // ═══════════ Per-tick watch ═══════════

    public static void tick(ServerPlayer p) {
        try {
            if (!ModConfig.INVENTORY_GUARD.get()) return;
            if (p == null) return;

            State s = state(p);
            Inventory inv = p.getInventory();
            int[] now = fingerprint(inv);

            // First tick: establish the baseline, never ask about the first reading.
            if (s.lastSlots == null) {
                s.lastSlots = now;
                s.lastStacks = copy(inv);
                return;
            }

            // An expired question is dropped silently: unanswered means "do nothing".
            if (s.askId != 0 && p.tickCount > s.askExpiresTick) {
                s.askId = 0;
                s.rollbackTo = null;
            }

            int changed = InventoryDiff.countDiffering(now, s.lastSlots);

            // A pending question must not be replaced by a newer one; the rollback target is
            // the state from before the suspicious change, and overwriting it would make the
            // question meaningless.
            if (changed > 0 && s.askId == 0) {
                boolean explained = hasLegalCause(p, s);
                if (InventoryDiff.deservesQuestion(changed, explained)) {
                    s.askId = System.nanoTime();
                    s.rollbackTo = s.lastStacks == null ? copy(inv) : s.lastStacks.clone();
                    s.askExpiresTick = p.tickCount + ASK_TTL_TICKS;
                    // Not ModLog.swallowed: this is the feature working, not an exception being
                    // eaten. Recording it there would make the self-check report "swallowed N
                    // internal exception(s)" every time the guard does its job — a false alarm
                    // that trains the player to ignore the one counter that must stay at zero.
                    IntegrityGuard.LOGGER.warn(
                            "[inventory] {} slot(s) changed with no cause ({})",
                            changed, p.getName().getString());
                    ReportLog.writeBlock("[inventory] " + p.getName().getString()
                            + ": " + changed + " slot(s) changed at tick " + p.tickCount);
                }
                // Baseline advances either way; otherwise the same difference would be
                // reported every tick until the player answered.
                s.lastSlots = now;
                s.lastStacks = copy(inv);
            }
        } catch (Throwable t) {
            ModLog.swallowed("InventoryGuard.tick", t);
        }
    }

    private static boolean hasLegalCause(ServerPlayer p, State s) {
        if (p.containerMenu != p.inventoryMenu) return true;
        return p.tickCount - s.lastContainerTick < LEGAL_WINDOW_TICKS
                || p.tickCount - s.lastDropTick < LEGAL_WINDOW_TICKS
                || p.tickCount - s.lastPickupTick < LEGAL_WINDOW_TICKS;
    }

    // ═══════════ The answer ═══════════

    /** Restore the inventory from the pending rollback target. */
    public static boolean confirm(ServerPlayer p) {
        if (p == null) return false;
        State s = state(p);
        if (s.askId == 0 || s.rollbackTo == null) return false;

        try {
            Inventory inv = p.getInventory();
            inv.clearContent();
            int n = Math.min(inv.getContainerSize(), s.rollbackTo.length);
            for (int i = 0; i < n; i++) {
                ItemStack stack = s.rollbackTo[i];
                inv.setItem(i, stack == null ? ItemStack.EMPTY : stack.copy());
            }
            // The restored state is the new baseline, so the very same difference is not
            // reported again a tick later.
            s.lastSlots = fingerprint(inv);
            s.lastStacks = copy(inv);

            s.askId = 0;
            s.rollbackTo = null;
            Msg.ok(p, "inventory").text("物品栏已回滚 / Inventory rolled back").send();
            return true;
        } catch (Throwable t) {
            ModLog.swallowed("InventoryGuard.confirm", t);
            return false;
        }
    }

    /** Drop the question without touching anything. */
    public static void decline(ServerPlayer p) {
        if (p == null) return;
        State s = state(p);
        s.askId = 0;
        s.rollbackTo = null;
    }

    /** Whether a question is pending, for the client HUD. */
    public static long pendingAsk(ServerPlayer p) {
        State s = state(p);
        return s.askId;
    }

    // ═══════════ Fingerprint ═══════════

    private static int[] fingerprint(Inventory inv) {        int size = inv.getContainerSize();
        int[] out = new int[size];
        for (int i = 0; i < size; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                out[i] = 0;
                continue;
            }
            int h = Item.getId(stack.getItem());
            h = 31 * h + stack.getCount();
            h = 31 * h + stack.getDamageValue();
            h = 31 * h + (stack.hasTag() ? stack.getTag().hashCode() : 0);
            out[i] = h;
        }
        return out;
    }

    private static ItemStack[] copy(Inventory inv) {
        int size = inv.getContainerSize();
        ItemStack[] out = new ItemStack[size];
        for (int i = 0; i < size; i++) out[i] = inv.getItem(i).copy();
        return out;
    }
}
