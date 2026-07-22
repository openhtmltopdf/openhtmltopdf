package com.openhtmltopdf.css.parser;

import org.junit.Test;

import static org.junit.Assert.*;

public class FSRGBColorAlphaTest {

    @Test
    public void defaultAlphaIsOpaque() {
        FSRGBColor color = new FSRGBColor(96, 128, 159);
        assertEquals(1f, color.getAlpha(), 0f);
        assertFalse(color.hasAlpha());
        assertEquals("#60809f", color.toString());
    }

    @Test
    public void alphaToString() {
        assertEquals("rgba(96, 128, 159, 0.5)", new FSRGBColor(96, 128, 159, 0.5f).toString());
        assertEquals("rgba(96, 128, 159, 0)", new FSRGBColor(96, 128, 159, 0f).toString());
        assertEquals("#60809f", new FSRGBColor(96, 128, 159, 1f).toString());
    }

    @Test
    public void transparentConstantIsFullyTransparent() {
        assertEquals(0f, FSRGBColor.TRANSPARENT.getAlpha(), 0f);
        assertTrue(FSRGBColor.TRANSPARENT.hasAlpha());
    }

    @Test
    public void equalsConsidersAlpha() {
        FSRGBColor opaque = new FSRGBColor(96, 128, 159);
        FSRGBColor transparent = new FSRGBColor(96, 128, 159, 0.5f);

        assertNotEquals(opaque, transparent);
        assertEquals(opaque, new FSRGBColor(96, 128, 159, 1f));
        assertEquals(transparent, new FSRGBColor(96, 128, 159, 0.5f));
        assertNotEquals(opaque.hashCode(), transparent.hashCode());
    }

    @Test
    public void withAlpha() {
        FSRGBColor color = new FSRGBColor(96, 128, 159).withAlpha(0.25f);
        assertEquals(0.25f, color.getAlpha(), 0f);
        assertEquals(96, color.getRed());
        assertEquals(128, color.getGreen());
        assertEquals(159, color.getBlue());
    }

    @Test
    public void lightenAndDarkenPreserveAlpha() {
        FSRGBColor color = new FSRGBColor(96, 128, 159, 0.5f);
        assertEquals(0.5f, ((FSRGBColor) color.lightenColor()).getAlpha(), 0f);
        assertEquals(0.5f, ((FSRGBColor) color.darkenColor()).getAlpha(), 0f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void alphaAboveOneIsRejected() {
        new FSRGBColor(96, 128, 159, 1.5f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeAlphaIsRejected() {
        new FSRGBColor(96, 128, 159, -0.5f);
    }
}
