package com.undying.core;

import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Structural fingerprint audit of critical classes.
 *
 * <p>After entering the world, take a "structural fingerprint" of the classes that are
 * <b>already loaded</b> and compare it against the on-disk baseline, specifically to catch
 * one thing: "a critical method was stripped out".
 *
 * <p>The criterion is deliberately loose: <b>a decrease in count = anomaly, an increase =
 * normal</b> (mixin injection naturally adds methods). Catch the thing that matters most
 * with the lowest possible false-positive rate.
 *
 * <p>The baseline also records one line of <b>environment fingerprint</b> (this mod's jar
 * hash + hash of the loaded mod list): if either changes, the baseline is rebuilt quietly —
 * otherwise every mod update would cry wolf, and <b>an alarm that cries wolf every day is
 * no alarm at all</b>.
 *
 * <p>Why we do not hook the class-loading phase (both approaches were tested and neither
 * worked), why we do not compare bytecode hashes, and the full story of the 2026-10-11
 * false positive along with the remaining limitations are recorded in docs/DECISIONS.md
 * (D-008).
 */
public final class ClassAudit {

    private ClassAudit() {}

    /**
     * Classes under audit.
     *
     * Selection criterion: our core protections hang off these classes, so once their
     * methods are stripped out, our death lock / health gate / command interception stop
     * working.
     */
    private static final String[] AUDITED_CLASSES = {
            "net.minecraft.world.entity.LivingEntity",
            "net.minecraft.world.entity.Entity",
            "net.minecraft.server.level.ServerPlayer",
            "net.minecraft.network.syncher.SynchedEntityData",
            "net.minecraft.commands.Commands",
    };

    /** Structural fingerprint: class name -> [method count, field count] */
    public record Fingerprint(String className, int methods, int fields) {}

    // ═══════════════════════════════════════════════════════════
    //   Capture
    // ═══════════════════════════════════════════════════════════

    /**
     * Captures the real structure of the current runtime environment.
     *
     * Uses reflection to read <b>the classes already loaded into memory</b> — this is "what
     * is actually running", not the original bytecode on disk. That is exactly the object we
     * want to audit.
     */
    public static Map<String, Fingerprint> capture() {
        Map<String, Fingerprint> out = new LinkedHashMap<>();
        for (String cn : AUDITED_CLASSES) {
            try {
                Class<?> c = Class.forName(cn, false, ClassAudit.class.getClassLoader());
                Method[] ms = c.getDeclaredMethods();
                Field[] fs = c.getDeclaredFields();
                out.put(cn, new Fingerprint(cn, ms.length, fs.length));
            } catch (Throwable t) {
                // A class that fails to load is itself information (missing = anomaly); record 0
                out.put(cn, new Fingerprint(cn, -1, -1));
            }
        }
        return out;
    }

    /** Converts the capture result into the shape AuditBaseline expects ([method count,
     *  field count]) and persists it */
    private static void saveBaseline(Map<String, Fingerprint> fp, String env) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (Fingerprint v : fp.values()) {
            counts.put(v.className(), new int[]{v.methods(), v.fields()});
        }
        AuditBaseline.save(counts, env);
    }

    // ═══════════════════════════════════════════════════════════
    //   Comparison and reporting
    // ═══════════════════════════════════════════════════════════

    /**
     * The result of one audit.
     *
     * @param rebuilt       whether the baseline was (re)built — in that case nothing is
     *                      compared and nothing is reported
     * @param envChanged    whether the rebuild was caused by "the runtime environment
     *                      changed" (mod update / mod list change)
     * @param anomalies     class name -> explanation
     * @param comparedCount number of classes that took part in the comparison
     */
    public record Result(boolean rebuilt,
                         boolean envChanged,
                         Map<String, String> anomalies,
                         int comparedCount) {

        public boolean clean() { return anomalies.isEmpty(); }
    }

    /**
     * Runs one audit.
     *
     * @param server server instance (used only for log timing; may be null)
     *
     * Behavior:
     *   · first run / environment fingerprint changed / old baseline has no #env header
     *     → rebuild the baseline, no alert
     *   · environment matches → compare against the baseline and alert on any "decrease"
     */
    public static Result audit(MinecraftServer server) {
        Map<String, Fingerprint> now = capture();
        AuditBaseline.Snapshot base = AuditBaseline.load();
        String env = AuditBaseline.envSignature();

        boolean firstRun = base.empty();
        boolean envChanged = !firstRun && !env.equals(base.env());
        // Baselines written by older versions have no #env header: rebuild once
        // unconditionally, otherwise the first world entry after an update always false-alarms
        boolean legacyBaseline = !firstRun && base.env().isEmpty();

        if (firstRun || envChanged || legacyBaseline) {
            saveBaseline(now, env);
            return new Result(true, !firstRun, new TreeMap<>(), now.size());
        }

        Map<String, String> anomalies = new TreeMap<>();
        int compared = 0;

        for (Fingerprint cur : now.values()) {
            int[] b = base.counts().get(cur.className());
            if (b == null) continue;
            compared++;

            // A class that fails to load (-1) also counts as an anomaly: it means the class is gone
            if (cur.methods() < 0) {
                anomalies.put(cur.className(), "class could not be loaded");
                continue;
            }

            if (cur.methods() < b[0]) {
                anomalies.put(cur.className(),
                        "method count decreased " + b[0] + " → " + cur.methods());
            }
            if (cur.fields() < b[1]) {
                anomalies.put(cur.className(),
                        "field count decreased " + b[1] + " → " + cur.fields());
            }
        }

        // Classes present in the baseline but not captured now — also an anomaly
        for (String cn : base.counts().keySet()) {
            if (!now.containsKey(cn)) {
                anomalies.put(cn, "not captured this run (may no longer exist)");
            }
        }

        // When the structure <b>grows</b>, update the baseline so it follows normal
        // evolution (for example another mod adding a mixin)
        boolean grew = false;
        for (Fingerprint cur : now.values()) {
            int[] b = base.counts().get(cur.className());
            if (b != null && (cur.methods() > b[0] || cur.fields() > b[1])) grew = true;
        }
        if (grew) saveBaseline(now, env);

        return new Result(false, false, anomalies, compared);
    }

    /**
     * Condenses the result into one line for IntegrityGuard's world-entry report.
     *
     * Note: this takes a Result instead of running audit() again itself. Previously
     * summaryLine() and detailLine() each called audit(), so every world entry captured the
     * fingerprint twice; worse, the first call might write the baseline, so the second
     * compared against the baseline it had just written and the report contradicted itself.
     * Now the caller runs the audit once and both use the same result.
     */
    public static String summaryLine(Result r) {
        if (r == null) return "audit not run";
        if (r.rebuilt()) {
            return r.envChanged()
                    ? "environment changed; baseline rebuilt (" + r.comparedCount() + " key classes)"
                    : "baseline established (" + r.comparedCount() + " key classes)";
        }
        if (r.clean()) return "baseline comparison passed (" + r.comparedCount() + " key classes)";
        return "WARN: found " + r.anomalies().size() + " structure anomalie(s)";
    }

    /** Full anomaly detail (for logging) */
    public static String detailLine(Result r) {
        if (r == null || r.anomalies().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : r.anomalies().entrySet()) {
            sb.append("\n    ").append(e.getKey()).append("  ").append(e.getValue());
        }
        return sb.toString();
    }
}
