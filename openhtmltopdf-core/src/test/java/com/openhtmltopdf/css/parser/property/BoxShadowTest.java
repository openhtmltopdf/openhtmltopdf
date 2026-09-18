package com.openhtmltopdf.css.parser.property;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.css.parser.CSSErrorHandler;
import com.openhtmltopdf.css.parser.CSSParser;
import com.openhtmltopdf.css.parser.PropertyValue;
import com.openhtmltopdf.css.sheet.PropertyDeclaration;
import com.openhtmltopdf.css.sheet.Ruleset;
import com.openhtmltopdf.css.sheet.Stylesheet;

/**
 * Tests parsing of the <code>box-shadow</code> property. Each shadow is
 * normalized to six values (inset flag, offset-x, offset-y, blur-radius,
 * spread-radius, color), so that missing lengths and a missing color end up
 * with their defaults.
 */
public class BoxShadowTest {
    private List<String> errors;
    private CSSParser parser;

    @Before
    public void setUp() {
        errors = new ArrayList<>();
        CSSErrorHandler errorHandler = (uri, message) -> errors.add(message);
        parser = new CSSParser(errorHandler);
    }

    /** The box-shadow declaration of a single rule, or null if it was dropped. */
    private PropertyDeclaration parse(String css) throws IOException {
        Stylesheet stylesheet = parser.parseStylesheet("test", 0, new StringReader(css));

        if (stylesheet.getContents().isEmpty()) {
            return null;
        }

        Ruleset ruleset = (Ruleset) stylesheet.getContents().get(0);
        PropertyDeclaration result = null;
        for (PropertyDeclaration decl : ruleset.getPropertyDeclarations()) {
            if (decl.getCSSName() == CSSName.BOX_SHADOW) {
                result = decl;
            }
        }
        return result;
    }

    /** Each parsed shadow as "[inset ]x y blur spread color", for compact asserts. */
    private List<String> shadows(PropertyDeclaration decl) {
        List<PropertyValue> values = ((PropertyValue) decl.getValue()).getValues();
        assertEquals("values come in groups per shadow",
                0, values.size() % BoxShadowPropertyBuilder.VALUES_PER_SHADOW);

        List<String> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i += BoxShadowPropertyBuilder.VALUES_PER_SHADOW) {
            String inset = values.get(i).getIdentValue() == IdentValue.INSET ? "inset " : "";
            result.add(inset +
                    values.get(i + 1).getCssText() + " " +
                    values.get(i + 2).getCssText() + " " +
                    values.get(i + 3).getCssText() + " " +
                    values.get(i + 4).getCssText() + " " +
                    values.get(i + 5).getFSColor());
        }
        return result;
    }

    private void assertRejected(String css) throws IOException {
        PropertyDeclaration decl = parse(css);
        assertFalse("Expected a CSS parse error for: " + css, errors.isEmpty());
        assertNull("Expected the declaration to be dropped for: " + css, decl);
    }

    @Test
    public void testNone() throws IOException {
        PropertyDeclaration decl = parse("div { box-shadow: none; }");
        assertTrue(errors.isEmpty());
        assertEquals(IdentValue.NONE, ((PropertyValue) decl.getValue()).getIdentValue());
    }

    @Test
    public void testInherit() throws IOException {
        PropertyDeclaration decl = parse("div { box-shadow: inherit; }");
        assertTrue(errors.isEmpty());
        assertEquals("inherit", decl.getValue().getCssText());
    }

    @Test
    public void testOffsetsOnlyDefaultsBlurSpreadAndColor() throws IOException {
        PropertyDeclaration decl = parse("div { box-shadow: 2px 3px; }");
        assertTrue(errors.isEmpty());
        assertEquals(Collections.singletonList("2px 3px 0 0 #000000"), shadows(decl));
    }

    @Test
    public void testBlurDefaultsSpread() throws IOException {
        PropertyDeclaration decl = parse("div { box-shadow: 2px 3px 4px red; }");
        assertTrue(errors.isEmpty());
        assertEquals(Collections.singletonList("2px 3px 4px 0 #ff0000"), shadows(decl));
    }

    @Test
    public void testAllFourLengths() throws IOException {
        PropertyDeclaration decl = parse("div { box-shadow: -2px -3px 4px 5px #00ff00; }");
        assertTrue(errors.isEmpty());
        assertEquals(Collections.singletonList("-2px -3px 4px 5px #00ff00"), shadows(decl));
    }

    @Test
    public void testNegativeSpreadIsAllowed() throws IOException {
        PropertyDeclaration decl = parse("div { box-shadow: 0 4px 0 -2px black; }");
        assertTrue(errors.isEmpty());
        assertEquals(Collections.singletonList("0 4px 0 -2px #000000"), shadows(decl));
    }

    /** The color may come before the lengths. */
    @Test
    public void testColorFirst() throws IOException {
        PropertyDeclaration decl = parse("div { box-shadow: blue 1px 1px; }");
        assertTrue(errors.isEmpty());
        assertEquals(Collections.singletonList("1px 1px 0 0 #0000ff"), shadows(decl));
    }

    @Test
    public void testRgbaColor() throws IOException {
        PropertyDeclaration decl = parse("div { box-shadow: -4px 4px 0 0 rgba(0, 0, 0, 0.5); }");
        assertTrue(errors.isEmpty());
        assertEquals(Collections.singletonList("-4px 4px 0 0 rgba(0, 0, 0, 0.5)"), shadows(decl));
    }

    @Test
    public void testInset() throws IOException {
        PropertyDeclaration decl = parse("div { box-shadow: inset 1px 2px red; }");
        assertTrue(errors.isEmpty());
        assertEquals(Collections.singletonList("inset 1px 2px 0 0 #ff0000"), shadows(decl));
    }

    @Test
    public void testInsetLast() throws IOException {
        PropertyDeclaration decl = parse("div { box-shadow: 1px 2px red inset; }");
        assertTrue(errors.isEmpty());
        assertEquals(Collections.singletonList("inset 1px 2px 0 0 #ff0000"), shadows(decl));
    }

    @Test
    public void testMultipleShadowsKeepTheirOrder() throws IOException {
        PropertyDeclaration decl = parse("div { box-shadow: 1px 1px red, inset 2px 2px 3px, 3px 3px 0 1px blue; }");
        assertTrue(errors.isEmpty());
        assertEquals(Arrays.asList(
                "1px 1px 0 0 #ff0000",
                "inset 2px 2px 3px 0 #000000",
                "3px 3px 0 1px #0000ff"), shadows(decl));
    }

    @Test
    public void testOneLengthIsRejected() throws IOException {
        assertRejected("div { box-shadow: 2px; }");
    }

    @Test
    public void testFiveLengthsAreRejected() throws IOException {
        assertRejected("div { box-shadow: 1px 2px 3px 4px 5px; }");
    }

    @Test
    public void testNegativeBlurIsRejected() throws IOException {
        assertRejected("div { box-shadow: 1px 2px -3px; }");
    }

    @Test
    public void testTwoColorsAreRejected() throws IOException {
        assertRejected("div { box-shadow: 1px 2px red blue; }");
    }

    @Test
    public void testInsetTwiceIsRejected() throws IOException {
        assertRejected("div { box-shadow: inset 1px 2px inset; }");
    }

    @Test
    public void testUnknownIdentIsRejected() throws IOException {
        assertRejected("div { box-shadow: 1px 2px bogus; }");
    }

    /** One bad shadow in a list drops the whole declaration, as in browsers. */
    @Test
    public void testOneInvalidShadowInAListIsRejected() throws IOException {
        assertRejected("div { box-shadow: 1px 1px red, 2px; }");
    }

    @Test
    public void testNoneInAListIsRejected() throws IOException {
        assertRejected("div { box-shadow: none, 1px 1px; }");
    }
}
