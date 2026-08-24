package com.openhtmltopdf.render;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.openhtmltopdf.layout.TextSpacing;

/**
 * Tests that the deprecated letter-spacing accessors still delegate to
 * {@link InlineText#setTextSpacing(TextSpacing)} correctly.
 */
public class InlineTextSpacingTest {
    private static final float DELTA = 0.0001f;

    @Test
    @SuppressWarnings("deprecation")
    public void testDeprecatedAccessorsRoundTrip() {
        InlineText text = new InlineText();

        assertEquals("nothing set", 0f, text.getLetterSpacing(), DELTA);

        text.setLetterSpacing(3f);

        assertEquals(3f, text.getLetterSpacing(), DELTA);
        assertEquals(3f, text.getTextSpacing().getLetterSpacing(), DELTA);
    }

    /**
     * The deprecated setter knows nothing of word-spacing, so it must leave
     * whatever was already set alone rather than clearing it.
     */
    @Test
    @SuppressWarnings("deprecation")
    public void testDeprecatedSetterKeepsWordSpacing() {
        InlineText text = new InlineText();

        text.setTextSpacing(TextSpacing.of(1f, 7f));
        text.setLetterSpacing(3f);

        assertEquals(3f, text.getTextSpacing().getLetterSpacing(), DELTA);
        assertEquals(7f, text.getTextSpacing().getWordSpacing(), DELTA);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testDeprecatedGetterReadsTheNewSetter() {
        InlineText text = new InlineText();

        text.setTextSpacing(TextSpacing.of(2f, 7f));

        assertEquals(2f, text.getLetterSpacing(), DELTA);
    }
}
