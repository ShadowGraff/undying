package com.undying.core.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The three-press rule. Getting this wrong in either direction is bad: too eager and an
 * inventory is rolled back because someone typed "y" in chat, too strict and the prompt times
 * out while the player is pressing.
 */
class AnswerKeysTest {

    @Test
    void onePressIsNotAnAnswer() {
        AnswerKeys k = new AnswerKeys();
        assertEquals(AnswerKeys.Decision.NONE, k.press(true));
        assertEquals(AnswerKeys.Decision.NONE, k.press(true));
    }

    @Test
    void threePressesConfirm() {
        AnswerKeys k = new AnswerKeys();
        k.press(true);
        k.press(true);
        assertEquals(AnswerKeys.Decision.CONFIRM, k.press(true));
    }

    @Test
    void threePressesDecline() {
        AnswerKeys k = new AnswerKeys();
        k.press(false);
        k.press(false);
        assertEquals(AnswerKeys.Decision.DECLINE, k.press(false));
    }

    @Test
    void theOtherKeyResetsTheCount() {
        AnswerKeys k = new AnswerKeys();
        k.press(true);
        k.press(true);
        k.press(false);            // changes their mind
        assertEquals(0, k.yesCount());
        assertEquals(1, k.noCount());
        assertEquals(AnswerKeys.Decision.NONE, k.press(true));   // must start over
        assertEquals(AnswerKeys.Decision.NONE, k.press(true));
        assertEquals(AnswerKeys.Decision.CONFIRM, k.press(true));
    }

    @Test
    void anAnswerResetsForTheNextQuestion() {
        AnswerKeys k = new AnswerKeys();
        for (int i = 0; i < AnswerKeys.PRESSES_REQUIRED; i++) k.press(true);
        // A second question must not be answered by one leftover press.
        assertEquals(0, k.yesCount());
        assertEquals(AnswerKeys.Decision.NONE, k.press(true));
    }

    @Test
    void resetClearsBothSides() {
        AnswerKeys k = new AnswerKeys();
        k.press(true);
        k.press(false);
        k.reset();
        assertEquals(0, k.yesCount());
        assertEquals(0, k.noCount());
    }
}
