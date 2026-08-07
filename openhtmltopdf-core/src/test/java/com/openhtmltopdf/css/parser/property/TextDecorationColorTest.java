package com.openhtmltopdf.css.parser.property;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
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
 * Tests a color given to the text decoration, either with the
 * <code>text-decoration-color</code> longhand or inside the
 * <code>text-decoration</code> shorthand.
 *
 * <p>Before this was supported, a color in the shorthand
 * (eg. <code>text-decoration: underline red;</code>) made the CSS parser throw,
 * and the <em>entire</em> declaration was dropped - so no decoration at all was
 * rendered, not even an uncolored one.</p>
 *
 * @see <a href="https://github.com/openhtmltopdf/openhtmltopdf/issues/101">Issue 101</a>
 */
public class TextDecorationColorTest {
    private List<String> errors;
    private CSSParser parser;

    @Before
    public void setUp() {
        errors = new ArrayList<>();
        CSSErrorHandler errorHandler = (uri, message) -> errors.add(message);
        parser = new CSSParser(errorHandler);
    }

    private List<PropertyDeclaration> parseDeclarations(String css) throws IOException {
        Stylesheet stylesheet = parser.parseStylesheet("test", 0, new StringReader(css));
        assertEquals("Expected exactly one ruleset", 1, stylesheet.getContents().size());

        Ruleset ruleset = (Ruleset) stylesheet.getContents().get(0);
        return ruleset.getPropertyDeclarations();
    }

    /**
     * The declaration the given property ends up with, or null if the property
     * was not set at all. The last one in the rule wins, the way the cascade
     * resolves it.
     */
    private PropertyDeclaration declarationFor(List<PropertyDeclaration> declarations, CSSName cssName) {
        PropertyDeclaration result = null;
        for (PropertyDeclaration decl : declarations) {
            if (decl.getCSSName() == cssName) {
                result = decl;
            }
        }
        return result;
    }

    /** The line types asked for by a text-decoration declaration, in order. */
    private List<String> lines(PropertyDeclaration decl) {
        List<String> result = new ArrayList<>();
        for (PropertyValue value : ((PropertyValue) decl.getValue()).getValues()) {
            assertEquals("line types are idents",
                    PropertyValue.VALUE_TYPE_IDENT, value.getPropertyValueType());
            result.add(value.getStringValue());
        }
        return result;
    }

    /** The color a text-decoration-color declaration resolved to, as a hex string. */
    private String color(PropertyDeclaration decl) {
        PropertyValue value = (PropertyValue) decl.getValue();
        assertEquals("expected a color", PropertyValue.VALUE_TYPE_COLOR, value.getPropertyValueType());
        return value.getFSColor().toString();
    }

    @Test
    public void testShorthandWithNamedColorIsNotDropped() throws IOException {
        List<PropertyDeclaration> decls = parseDeclarations("p { text-decoration: underline red; }");
        assertTrue(errors.isEmpty());

        assertEquals(Collections.singletonList("underline"), lines(declarationFor(decls, CSSName.TEXT_DECORATION)));
        assertEquals("#ff0000", color(declarationFor(decls, CSSName.TEXT_DECORATION_COLOR)));
    }

    @Test
    public void testShorthandWithHexColor() throws IOException {
        List<PropertyDeclaration> decls = parseDeclarations("p { text-decoration: underline #00ff00; }");
        assertTrue(errors.isEmpty());

        assertEquals(Collections.singletonList("underline"), lines(declarationFor(decls, CSSName.TEXT_DECORATION)));
        assertEquals("#00ff00", color(declarationFor(decls, CSSName.TEXT_DECORATION_COLOR)));
    }

    @Test
    public void testShorthandWithRgbColor() throws IOException {
        List<PropertyDeclaration> decls = parseDeclarations("p { text-decoration: underline rgb(0, 0, 255); }");
        assertTrue(errors.isEmpty());

        assertEquals("#0000ff", color(declarationFor(decls, CSSName.TEXT_DECORATION_COLOR)));
    }

    /** The color may come before the line types, as the shorthand is unordered. */
    @Test
    public void testShorthandWithColorFirst() throws IOException {
        List<PropertyDeclaration> decls = parseDeclarations("p { text-decoration: red underline; }");
        assertTrue(errors.isEmpty());

        assertEquals(Collections.singletonList("underline"), lines(declarationFor(decls, CSSName.TEXT_DECORATION)));
        assertEquals("#ff0000", color(declarationFor(decls, CSSName.TEXT_DECORATION_COLOR)));
    }

    @Test
    public void testShorthandWithMultipleLineTypesAndColor() throws IOException {
        List<PropertyDeclaration> decls = parseDeclarations(
                "p { text-decoration: underline line-through blue; }");
        assertTrue(errors.isEmpty());

        List<String> lines = lines(declarationFor(decls, CSSName.TEXT_DECORATION));
        assertEquals(2, lines.size());
        assertEquals("underline", lines.get(0));
        assertEquals("line-through", lines.get(1));

        assertEquals("#0000ff", color(declarationFor(decls, CSSName.TEXT_DECORATION_COLOR)));
    }

    /**
     * A shorthand carrying no color of its own resets the color longhand, so
     * that a color set by an earlier rule does not leak into it.
     */
    @Test
    public void testShorthandWithoutColorResetsTheColor() throws IOException {
        List<PropertyDeclaration> decls = parseDeclarations("p { text-decoration: underline; }");
        assertTrue(errors.isEmpty());

        assertEquals(Collections.singletonList("underline"), lines(declarationFor(decls, CSSName.TEXT_DECORATION)));

        PropertyDeclaration colorDecl = declarationFor(decls, CSSName.TEXT_DECORATION_COLOR);
        assertEquals(IdentValue.CURRENT_COLOR, ((PropertyValue) colorDecl.getValue()).getIdentValue());
    }

    /** A color on its own asks for no line at all, which is valid if pointless. */
    @Test
    public void testShorthandWithOnlyAColorAsksForNoLine() throws IOException {
        List<PropertyDeclaration> decls = parseDeclarations("p { text-decoration: red; }");
        assertTrue(errors.isEmpty());

        assertTrue(lines(declarationFor(decls, CSSName.TEXT_DECORATION)).isEmpty());
        assertEquals("#ff0000", color(declarationFor(decls, CSSName.TEXT_DECORATION_COLOR)));
    }

    /**
     * The color component of the shorthand takes any color, the two color
     * keywords included. Getting this wrong costs the whole declaration, so
     * the line types go missing too, not just the color.
     */
    @Test
    public void testShorthandWithCurrentColorKeyword() throws IOException {
        List<PropertyDeclaration> decls = parseDeclarations("p { text-decoration: underline currentColor; }");
        assertTrue(errors.isEmpty());

        assertEquals(Collections.singletonList("underline"), lines(declarationFor(decls, CSSName.TEXT_DECORATION)));

        PropertyDeclaration colorDecl = declarationFor(decls, CSSName.TEXT_DECORATION_COLOR);
        assertEquals(IdentValue.CURRENT_COLOR, ((PropertyValue) colorDecl.getValue()).getIdentValue());
    }

    @Test
    public void testShorthandWithTransparentKeyword() throws IOException {
        List<PropertyDeclaration> decls = parseDeclarations("p { text-decoration: underline transparent; }");
        assertTrue(errors.isEmpty());

        assertEquals(Collections.singletonList("underline"), lines(declarationFor(decls, CSSName.TEXT_DECORATION)));

        PropertyDeclaration colorDecl = declarationFor(decls, CSSName.TEXT_DECORATION_COLOR);
        assertEquals(IdentValue.TRANSPARENT, ((PropertyValue) colorDecl.getValue()).getIdentValue());
    }

    /** A keyword may lead, as the shorthand is unordered. */
    @Test
    public void testShorthandWithColorKeywordFirst() throws IOException {
        List<PropertyDeclaration> decls = parseDeclarations("p { text-decoration: currentColor line-through; }");
        assertTrue(errors.isEmpty());

        assertEquals(Collections.singletonList("line-through"), lines(declarationFor(decls, CSSName.TEXT_DECORATION)));

        PropertyDeclaration colorDecl = declarationFor(decls, CSSName.TEXT_DECORATION_COLOR);
        assertEquals(IdentValue.CURRENT_COLOR, ((PropertyValue) colorDecl.getValue()).getIdentValue());
    }

    @Test
    public void testLonghand() throws IOException {
        List<PropertyDeclaration> decls = parseDeclarations(
                "p { text-decoration: underline; text-decoration-color: red; }");
        assertTrue(errors.isEmpty());

        assertEquals(Collections.singletonList("underline"), lines(declarationFor(decls, CSSName.TEXT_DECORATION)));
        assertEquals("#ff0000", color(declarationFor(decls, CSSName.TEXT_DECORATION_COLOR)));
    }

    /** The longhand wins over a color in a shorthand that came before it. */
    @Test
    public void testLonghandAfterShorthandWins() throws IOException {
        List<PropertyDeclaration> decls = parseDeclarations(
                "p { text-decoration: underline blue; text-decoration-color: red; }");
        assertTrue(errors.isEmpty());

        assertEquals("#ff0000", color(declarationFor(decls, CSSName.TEXT_DECORATION_COLOR)));
    }

    @Test
    public void testLonghandAcceptsCurrentColor() throws IOException {
        List<PropertyDeclaration> decls = parseDeclarations("p { text-decoration-color: currentColor; }");
        assertTrue(errors.isEmpty());

        PropertyDeclaration decl = declarationFor(decls, CSSName.TEXT_DECORATION_COLOR);
        assertEquals(IdentValue.CURRENT_COLOR, ((PropertyValue) decl.getValue()).getIdentValue());
    }

    /**
     * An invalid color is dropped on its own, without taking the rest of the
     * rule with it.
     */
    @Test
    public void testLonghandRejectsAnInvalidIdent() throws IOException {
        List<PropertyDeclaration> decls = parseDeclarations(
                "p { text-decoration: underline; text-decoration-color: bogus; }");

        assertFalse("Expected a CSS parse error for an invalid ident", errors.isEmpty());

        assertEquals(Collections.singletonList("underline"), lines(declarationFor(decls, CSSName.TEXT_DECORATION)));

        PropertyDeclaration colorDecl = declarationFor(decls, CSSName.TEXT_DECORATION_COLOR);
        assertEquals("the shorthand's reset to currentcolor is all that is left",
                IdentValue.CURRENT_COLOR, ((PropertyValue) colorDecl.getValue()).getIdentValue());
    }

    @Test
    public void testPlainUnderlineStillWorks() throws IOException {
        List<PropertyDeclaration> decls = parseDeclarations("p { text-decoration: underline; }");
        assertTrue(errors.isEmpty());

        assertEquals(Collections.singletonList("underline"), lines(declarationFor(decls, CSSName.TEXT_DECORATION)));
    }

    /** An inherited shorthand brings the color along with the line types. */
    @Test
    public void testInheritCarriesTheColor() throws IOException {
        List<PropertyDeclaration> decls = parseDeclarations("p { text-decoration: inherit; }");
        assertTrue(errors.isEmpty());

        assertEquals("inherit", declarationFor(decls, CSSName.TEXT_DECORATION).getValue().getCssText());
        assertEquals("inherit",
                declarationFor(decls, CSSName.TEXT_DECORATION_COLOR).getValue().getCssText());
    }

    @Test
    public void testNoneStillWorks() throws IOException {
        List<PropertyDeclaration> decls = parseDeclarations("p { text-decoration: none; }");
        assertTrue(errors.isEmpty());
        assertEquals("none", declarationFor(decls, CSSName.TEXT_DECORATION).getValue().getCssText());
    }

    /**
     * Regression guard: an unrecognized, non-color ident must still be treated
     * as invalid rather than silently accepted.
     */
    @Test
    public void testInvalidIdentIsStillRejected() throws IOException {
        Stylesheet stylesheet = parser.parseStylesheet(
                "test", 0, new StringReader("p { text-decoration: bogus; }"));

        assertFalse("Expected a CSS parse error for an invalid ident", errors.isEmpty());

        if (!stylesheet.getContents().isEmpty()) {
            Ruleset ruleset = (Ruleset) stylesheet.getContents().get(0);
            assertTrue(ruleset.getPropertyDeclarations().isEmpty());
        }
    }
}
