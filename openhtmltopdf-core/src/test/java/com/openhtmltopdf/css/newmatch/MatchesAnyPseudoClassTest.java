package com.openhtmltopdf.css.newmatch;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.extend.AttributeResolver;
import com.openhtmltopdf.css.extend.TreeResolver;
import com.openhtmltopdf.css.extend.lib.DOMTreeResolver;
import com.openhtmltopdf.css.parser.CSSErrorHandler;
import com.openhtmltopdf.css.parser.CSSParser;
import com.openhtmltopdf.css.sheet.PropertyDeclaration;
import com.openhtmltopdf.css.sheet.Stylesheet;
import com.openhtmltopdf.css.sheet.StylesheetInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * The Selectors Level 4 matches-any pseudo-class {@code :is()} and its zero-specificity form
 * {@code :where()}, each taking a forgiving list of complex selectors.
 */
public class MatchesAnyPseudoClassTest {

    private static final AttributeResolver ATT_RES = new DomAttributeResolver();
    private static final TreeResolver TREE_RES = new DOMTreeResolver();

    // ---------------------------------------------------------------- harness

    private static Stylesheet parse(String css, CSSErrorHandler errors) {
        try {
            return new CSSParser(errors).parseStylesheet("test://css", StylesheetInfo.AUTHOR, new StringReader(css));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Matcher matcher(String css) {
        Stylesheet sheet = parse(css, (uri, message) -> fail("CSS parse error: " + message));
        return new Matcher(TREE_RES, ATT_RES, null, Collections.singletonList(sheet), "print");
    }

    private static Document dom(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Element byId(Document doc, String id) {
        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (id.equals(e.getAttribute("id"))) {
                return e;
            }
        }
        throw new AssertionError("no element with id " + id);
    }

    /** The cascaded word-spacing value for the element, or null when no rule matched. */
    private static String wordSpacing(Matcher m, Element e) {
        PropertyDeclaration pd = m.getCascadedStyle(e, false).propertyByName(CSSName.WORD_SPACING);
        return pd == null ? null : pd.getValue().getCssText();
    }

    /** The cascaded letter-spacing value for the element, or null when no rule matched. */
    private static String spacing(Matcher m, Element e) {
        PropertyDeclaration pd = m.getCascadedStyle(e, false).propertyByName(CSSName.LETTER_SPACING);
        return pd == null ? null : pd.getValue().getCssText();
    }

    // ----------------------------------------------------------------- tests

    @Test
    public void isMatchesAnyArgument() {
        Matcher m = matcher("i:is(.a, .b, [data-k]) { letter-spacing: 1px }");
        Document doc = dom("<r><i id='a' class='a'/><i id='b' class='b'/><i id='k' data-k='1'/><i id='c' class='c'/></r>");

        assertEquals("1px", spacing(m, byId(doc, "a")));
        assertEquals("1px", spacing(m, byId(doc, "b")));
        assertEquals("1px", spacing(m, byId(doc, "k")));
        assertNull(spacing(m, byId(doc, "c")));
    }

    @Test
    public void whereMatchesLikeIs() {
        Matcher m = matcher(":where(p, .a) { letter-spacing: 1px }");
        Document doc = dom("<r><p id='p'/><i id='a' class='a'/><i id='none'/></r>");

        assertEquals("1px", spacing(m, byId(doc, "p")));
        assertEquals("1px", spacing(m, byId(doc, "a")));
        assertNull(spacing(m, byId(doc, "none")));
    }

    @Test
    public void isTakesTheSpecificityOfItsMostSpecificArgument() {
        Document doc = dom("<r><i id='e' class='c'/></r>");

        // :is(.c, #nope) counts as an id selector, so it beats a later rule with two classes.
        assertEquals("1px", spacing(
                matcher("i:is(.c, #nope) { letter-spacing: 1px }\ni.c.c { letter-spacing: 2px }"), byId(doc, "e")));
        // :is(.c) counts as one class, the same as .c, so declaration order decides.
        assertEquals("2px", spacing(
                matcher("i:is(.c) { letter-spacing: 1px }\ni.c { letter-spacing: 2px }"), byId(doc, "e")));
    }

    @Test
    public void whereAddsNoSpecificity() {
        Document doc = dom("<r><i id='e' class='c'/></r>");

        // Even an id inside :where() loses to a later type selector.
        assertEquals("2px", spacing(
                matcher(":where(#e) { letter-spacing: 1px }\ni { letter-spacing: 2px }"), byId(doc, "e")));
        // ... and to an earlier one: zero specificity is lower than any type selector.
        assertEquals("2px", spacing(
                matcher("i { letter-spacing: 2px }\n:where(#e, .c) { letter-spacing: 1px }"), byId(doc, "e")));
        // i:where(.c) counts as the type selector alone, so declaration order decides against i.
        assertEquals("1px", spacing(
                matcher("i { letter-spacing: 2px }\ni:where(.c) { letter-spacing: 1px }"), byId(doc, "e")));
    }

    @Test
    public void invalidArgumentsAreSkippedNotFatal() {
        Document doc = dom("<r><i id='a' class='a'/><i id='b' class='b'/><i id='c' class='c'/></r>");
        List<String> errors = new ArrayList<>();
        Stylesheet sheet = parse("i:is(.a, div ::before, ::before, .b) { letter-spacing: 1px }",
                (uri, message) -> errors.add(message));
        Matcher m = new Matcher(TREE_RES, ATT_RES, null, Collections.singletonList(sheet), "print");

        assertEquals("one warning per invalid argument: " + errors, 2, errors.size());
        assertEquals("1px", spacing(m, byId(doc, "a")));
        assertEquals("1px", spacing(m, byId(doc, "b")));
        assertNull(spacing(m, byId(doc, "c")));
    }

    @Test
    public void userActionPseudoClassesAreValidButNeverMatch() {
        List<String> errors = new ArrayList<>();
        Stylesheet sheet = parse("i:is(:hover, .a) { letter-spacing: 1px }\ni:where(:focus) { word-spacing: 2px }",
                (uri, message) -> errors.add(message));
        Matcher m = new Matcher(TREE_RES, ATT_RES, null, Collections.singletonList(sheet), "print");
        Document doc = dom("<r><i id='a' class='a'/><i id='b' class='b'/></r>");

        assertEquals("no warnings: " + errors, 0, errors.size());
        assertEquals("1px", spacing(m, byId(doc, "a")));
        assertNull(spacing(m, byId(doc, "b")));
        assertNull(m.getCascadedStyle(byId(doc, "a"), false).propertyByName(CSSName.WORD_SPACING));
    }

    @Test
    public void listWithNoValidArgumentMatchesNothing() {
        Document doc = dom("<r><div><i id='i'/></div></r>");
        for (String rule : new String[] { "i:is(div ::before) { letter-spacing: 9px }", "i:where() { letter-spacing: 9px }" }) {
            Stylesheet sheet = parse(rule + "\ni { letter-spacing: 1px }", (uri, message) -> { });
            Matcher m = new Matcher(TREE_RES, ATT_RES, null, Collections.singletonList(sheet), "print");

            assertEquals(rule, "1px", spacing(m, byId(doc, "i")));
        }
    }

    @Test
    public void isAndWhereAcceptComplexSelectors() {
        Document doc = dom("<r><div class='card'><i id='child'/><b><i id='grandchild'/></b></div>" +
                "<h1/><i id='after-h1'/><i id='plain'/></r>");

        Matcher m = matcher("i:is(.card > i, h1 + i) { letter-spacing: 1px }\n" +
                "i:where(.card i) { word-spacing: 2px }");
        assertEquals("1px", spacing(m, byId(doc, "child")));
        assertNull(spacing(m, byId(doc, "grandchild")));
        assertEquals("1px", spacing(m, byId(doc, "after-h1")));
        assertNull(spacing(m, byId(doc, "plain")));
        assertEquals("2px", wordSpacing(m, byId(doc, "grandchild")));
        assertNull(wordSpacing(m, byId(doc, "plain")));
    }

    @Test
    public void isCountsTheWholeSpecificityOfAComplexArgument() {
        // :is(#a .x, .y) counts (1,1,0) with the i, which beats i.c.d at (0,2,1); :where() counts none.
        Document doc = dom("<r><i id='e' class='c d'/></r>");
        assertEquals("1px", spacing(
                matcher("i:is(#a .x, i) { letter-spacing: 1px }\ni.c.d { letter-spacing: 2px }"), byId(doc, "e")));
        assertEquals("2px", spacing(
                matcher("i.c.d { letter-spacing: 2px }\ni:where(#a .x, i) { letter-spacing: 1px }"), byId(doc, "e")));
    }

    @Test
    public void argumentClassesAreNotRequiredByTheRuleIndex() {
        // The arguments are alternatives: no single class may decide which bucket the rule lands in.
        Matcher m = matcher(":is(.a, .b) { letter-spacing: 1px }\n.x:where(.a, .b) { letter-spacing: 2px }");
        Document doc = dom("<r><i id='a' class='a'/><i id='b' class='b'/><i id='xb' class='x b'/><i id='none'/></r>");

        assertEquals("1px", spacing(m, byId(doc, "a")));
        assertEquals("1px", spacing(m, byId(doc, "b")));
        assertEquals("2px", spacing(m, byId(doc, "xb")));
        assertNull(spacing(m, byId(doc, "none")));
    }
}
