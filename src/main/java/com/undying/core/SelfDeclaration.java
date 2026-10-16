package com.undying.core;

/**
 * Declaration text for self-protection.
 *
 * <p>Why it is its own class: this is <b>text shown to players</b>, and that is a
 * different concern from "how it is checked". Mixing them together would mean
 * "changing one sentence requires trawling three hundred lines of probe code".
 *
 * <p>⚠️ Think carefully before changing the wording here —— <b>this is our promise
 * to players</b>: every capability listed must genuinely exist, and anything not
 * listed must not be done behind their back.
 * Design principles
 */
public final class SelfDeclaration {

    private SelfDeclaration() {}

    /**
     * The full description of self-protection. Both the chat box and the log refer
     * directly to this one copy, so the two can never disagree.
     */
    public static final String[] LINES = {
            "[Self-check notice] This mod can protect itself from being quietly disabled by other mods.",
            "",
            "Once enabled, it will do the following:",
            "  1. Periodically verify that its own protection is still in effect (read-only checks, nothing is modified)",
            "  2. If it finds its protection has been disabled externally, it will raise a clear alert in chat and in the log",
            "  3. Record a set of diagnostic information to make troubleshooting easier",
            "",
            "It will NOT do the following:",
            "  · It will not modify the code or behaviour of other mods",
            "  · It will not stop you from turning this mod's features off manually",
            "  · It will not hide itself, and it will not stop anyone from auditing it",
            "  · It will not automatically \"fix\" anything (automatically altering vanilla structures is far too risky)",
            "",
            "This option is off by default. It is enabled only after you tick the consent box here.",
            "You can change it back at any time in config/undying-common.toml.",
    };

    /** Join the lines into one readable block of text (used for logging) */
    public static String text() {
        return String.join("\n", LINES);
    }
}
