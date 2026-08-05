package com.openhtmltopdf.css.parser.property;

import com.openhtmltopdf.css.parser.CSSErrorHandler;
import com.openhtmltopdf.css.parser.CSSParser;
import com.openhtmltopdf.css.parser.PropertyValue;
import com.openhtmltopdf.css.sheet.PropertyDeclaration;
import com.openhtmltopdf.css.sheet.Ruleset;
import com.openhtmltopdf.css.sheet.Stylesheet;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Regression tests for https://github.com/openhtmltopdf/openhtmltopdf/issues/101
 * <p>
 * Previously, specifying a color as part of the text-decoration shorthand
 * (eg. <code>text-decoration: underline red;</code>) caused the CSS parser to
 * throw and the *entire* declaration to be dropped, so no decoration at all
 * was rendered.
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

    private PropertyDeclaration parseSingleDeclaration(String css) throws IOException {
        Stylesheet stylesheet = parser.parseStylesheet("test", 0, new StringReader(css));
        assertEquals("Expected exactly one ruleset", 1, stylesheet.getContents().size());

        Ruleset ruleset = (Ruleset) stylesheet.getContents().get(0);
        List<PropertyDeclaration> decls = ruleset.getPropertyDeclarations();
        assertEquals("Declaration should not have been dropped", 1, decls.size());

        return decls.get(0);
    }

    @Test
    public void underline_with_named_color_is_not_dropped() throws IOException {
        PropertyDeclaration decl = parseSingleDeclaration("p { text-decoration: underline red; }");
        assertTrue(errors.isEmpty());

        PropertyValue value = (PropertyValue) decl.getValue();
        List<PropertyValue> values = value.getValues();
        assertEquals(2, values.size());

        assertEquals(PropertyValue.VALUE_TYPE_IDENT, values.get(0).getPropertyValueType());
        assertEquals("underline", values.get(0).getStringValue());

        assertEquals(PropertyValue.VALUE_TYPE_COLOR, values.get(1).getPropertyValueType());
        assertEquals("#ff0000", values.get(1).getFSColor().toString());
    }

    @Test
    public void underline_with_hex_color_is_not_dropped() throws IOException {
        PropertyDeclaration decl = parseSingleDeclaration("p { text-decoration: underline #00ff00; }");
        assertTrue(errors.isEmpty());

        PropertyValue value = (PropertyValue) decl.getValue();
        List<PropertyValue> values = value.getValues();
        assertEquals(2, values.size());
        assertEquals(PropertyValue.VALUE_TYPE_COLOR, values.get(1).getPropertyValueType());
        assertEquals("#00ff00", values.get(1).getFSColor().toString());
    }

    @Test
    public void multiple_line_types_with_color_are_not_dropped() throws IOException {
        PropertyDeclaration decl = parseSingleDeclaration(
                "p { text-decoration: underline line-through blue; }");
        assertTrue(errors.isEmpty());

        PropertyValue value = (PropertyValue) decl.getValue();
        List<PropertyValue> values = value.getValues();
        assertEquals(3, values.size());

        assertEquals(PropertyValue.VALUE_TYPE_IDENT, values.get(0).getPropertyValueType());
        assertEquals(PropertyValue.VALUE_TYPE_IDENT, values.get(1).getPropertyValueType());
        assertEquals(PropertyValue.VALUE_TYPE_COLOR, values.get(2).getPropertyValueType());
        assertEquals("#0000ff", values.get(2).getFSColor().toString());
    }

    @Test
    public void plain_underline_without_color_still_works() throws IOException {
        PropertyDeclaration decl = parseSingleDeclaration("p { text-decoration: underline; }");
        assertTrue(errors.isEmpty());

        PropertyValue value = (PropertyValue) decl.getValue();
        List<PropertyValue> values = value.getValues();
        assertEquals(1, values.size());
        assertEquals(PropertyValue.VALUE_TYPE_IDENT, values.get(0).getPropertyValueType());
    }

    @Test
    public void none_still_works() throws IOException {
        PropertyDeclaration decl = parseSingleDeclaration("p { text-decoration: none; }");
        assertTrue(errors.isEmpty());
        assertEquals("none", decl.getValue().getCssText());
    }

    @Test
    public void invalid_ident_is_still_rejected() throws IOException {
        // Regression guard: an unrecognized, non-color ident should still
        // be treated as invalid rather than silently accepted.
        Stylesheet stylesheet = parser.parseStylesheet(
                "test", 0, new StringReader("p { text-decoration: bogus; }"));

        assertFalse("Expected a CSS parse error for an invalid ident", errors.isEmpty());

        if (!stylesheet.getContents().isEmpty()) {
            Ruleset ruleset = (Ruleset) stylesheet.getContents().get(0);
            assertTrue(ruleset.getPropertyDeclarations().isEmpty());
        }
    }
}
