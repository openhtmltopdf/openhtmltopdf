package com.openhtmltopdf.css.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.sheet.StylesheetInfo;

/**
 * Hex colors with an alpha digit pair (#rrggbbaa) or digit (#rgba).
 */
public class HexColorAlphaTest {
    private static FSRGBColor parse(String css) {
        CSSParser parser = new CSSParser((uri, message) -> { });
        PropertyValue value = parser.parsePropertyValue(CSSName.COLOR, StylesheetInfo.AUTHOR, css);
        return value == null ? null : (FSRGBColor) value.getFSColor();
    }

    @Test
    public void eightDigits() {
        FSRGBColor color = parse("#ff000080");
        assertEquals(new FSRGBColor(255, 0, 0, 128 / 255f), color);
    }

    @Test
    public void fourDigits() {
        FSRGBColor color = parse("#f008");
        assertEquals(new FSRGBColor(255, 0, 0, 0x88 / 255f), color);
    }

    @Test
    public void withoutAlphaStaysOpaque() {
        assertEquals(new FSRGBColor(255, 0, 0), parse("#f00"));
        assertEquals(new FSRGBColor(255, 0, 0), parse("#ff0000"));
    }

    @Test
    public void otherLengthsAreInvalid() {
        assertNull(parse("#ff00f"));
        assertNull(parse("#ff00000"));
    }
}
