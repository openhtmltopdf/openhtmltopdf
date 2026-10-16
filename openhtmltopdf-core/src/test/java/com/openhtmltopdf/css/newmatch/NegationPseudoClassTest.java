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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * The Selectors Level 4 negation pseudo-class {@code :not()} with a list of complex selectors.
 */
public class NegationPseudoClassTest {

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
    public void notExcludesElementsMatchingTheArgument() {
        Matcher m = matcher("p:not(.x) { letter-spacing: 1px }");
        Document doc = dom("<r><p id='plain'/><p id='x' class='x'/><p id='xy' class='y x'/><div id='div'/></r>");

        assertEquals("1px", spacing(m, byId(doc, "plain")));
        assertNull(spacing(m, byId(doc, "x")));
        assertNull(spacing(m, byId(doc, "xy")));
        assertNull(spacing(m, byId(doc, "div")));
    }

    @Test
    public void notWithAListExcludesAnyArgument() {
        Matcher m = matcher("i:not(.a, .b, [data-k]) { letter-spacing: 1px }");
        Document doc = dom("<r><i id='a' class='a'/><i id='b' class='b'/><i id='k' data-k='1'/>" +
                "<i id='c' class='c'/><i id='none'/></r>");

        assertNull(spacing(m, byId(doc, "a")));
        assertNull(spacing(m, byId(doc, "b")));
        assertNull(spacing(m, byId(doc, "k")));
        assertEquals("1px", spacing(m, byId(doc, "c")));
        assertEquals("1px", spacing(m, byId(doc, "none")));
    }

    @Test
    public void notAcceptsStructuralPseudoClasses() {
        Matcher m = matcher(".row:not(:last-child) { letter-spacing: 1px }");
        Document doc = dom("<r><div class='row' id='r1'/><div class='row' id='r2'/><div class='row' id='r3'/></r>");

        assertEquals("1px", spacing(m, byId(doc, "r1")));
        assertEquals("1px", spacing(m, byId(doc, "r2")));
        assertNull(spacing(m, byId(doc, "r3")));
    }

    @Test
    public void notAcceptsTypeUniversalAndCompoundArguments() {
        Document doc = dom("<r><p id='p' class='a'/><span id='s' class='a'/><span id='sb' class='a b'/></r>");

        Matcher type = matcher(".a:not(p) { letter-spacing: 1px }");
        assertNull(spacing(type, byId(doc, "p")));
        assertEquals("1px", spacing(type, byId(doc, "s")));

        Matcher universal = matcher(".a:not(*) { letter-spacing: 1px }");
        assertNull(spacing(universal, byId(doc, "p")));
        assertNull(spacing(universal, byId(doc, "s")));

        Matcher compound = matcher(".a:not(span.b) { letter-spacing: 1px }");
        assertEquals("1px", spacing(compound, byId(doc, "p")));
        assertEquals("1px", spacing(compound, byId(doc, "s")));
        assertNull(spacing(compound, byId(doc, "sb")));
    }

    @Test
    public void userActionPseudoClassesAreValidButNeverMatch() {
        // A PDF has no hover, focus or activation, so :hover matches nothing and :not(:hover) everything.
        Matcher m = matcher("p:not(:hover) { letter-spacing: 1px }\np:not(.x, :focus, :active) { word-spacing: 2px }");
        Document doc = dom("<r><p id='p'/><p id='x' class='x'/></r>");

        assertEquals("1px", spacing(m, byId(doc, "p")));
        assertEquals("1px", spacing(m, byId(doc, "x")));
        assertEquals("2px", wordSpacing(m, byId(doc, "p")));
        assertNull(wordSpacing(m, byId(doc, "x")));
    }

    @Test
    public void nestedNegationCancelsOut() {
        Matcher m = matcher("i:not(:not(.a)) { letter-spacing: 1px }");
        Document doc = dom("<r><i id='a' class='a'/><i id='b' class='b'/></r>");

        assertEquals("1px", spacing(m, byId(doc, "a")));
        assertNull(spacing(m, byId(doc, "b")));
    }

    @Test
    public void classesInsideNotAreNotRequiredByTheRuleIndex() {
        // The rule index files a selector under one of its required classes. A class inside
        // :not() is the opposite of required, so these rules must still reach unclassed elements.
        Matcher m = matcher(":not(.b) { letter-spacing: 1px }\n.a:not(.b) { letter-spacing: 2px }");
        Document doc = dom("<r><i id='none'/><i id='a' class='a'/><i id='ab' class='a b'/><i id='b' class='b'/></r>");

        assertEquals("1px", spacing(m, byId(doc, "none")));
        assertEquals("2px", spacing(m, byId(doc, "a")));
        assertNull(spacing(m, byId(doc, "ab")));
        assertNull(spacing(m, byId(doc, "b")));
    }

    @Test
    public void specificityIsThatOfTheMostSpecificArgument() {
        Document doc = dom("<r><i id='e' class='c'/></r>");

        // :not(#other) counts as an id selector, so it beats a later class selector.
        assertEquals("1px", spacing(
                matcher("i:not(#other) { letter-spacing: 1px }\ni.c { letter-spacing: 2px }"), byId(doc, "e")));
        // :not(.x, #other) takes the id argument's specificity, not the class argument's.
        assertEquals("1px", spacing(
                matcher("i:not(.x, #other) { letter-spacing: 1px }\ni.c { letter-spacing: 2px }"), byId(doc, "e")));
        // :not(.x) counts as one class, the same as .c, so declaration order decides.
        assertEquals("2px", spacing(
                matcher("i:not(.x) { letter-spacing: 1px }\ni.c { letter-spacing: 2px }"), byId(doc, "e")));
        assertEquals("1px", spacing(
                matcher("i.c { letter-spacing: 2px }\ni:not(.x) { letter-spacing: 1px }"), byId(doc, "e")));
    }

    @Test
    public void notAcceptsComplexSelectors() {
        Document doc = dom("<r><div><p id='child'/><span><p id='grandchild'/></span></div><p id='outside'/>" +
                "<h1/><p id='after-h1'/></r>");

        Matcher descendant = matcher("p:not(div p) { letter-spacing: 1px }");
        assertNull(spacing(descendant, byId(doc, "child")));
        assertNull(spacing(descendant, byId(doc, "grandchild")));
        assertEquals("1px", spacing(descendant, byId(doc, "outside")));

        Matcher child = matcher("p:not(div > p) { letter-spacing: 1px }");
        assertNull(spacing(child, byId(doc, "child")));
        assertEquals("1px", spacing(child, byId(doc, "grandchild")));

        Matcher sibling = matcher("p:not(h1 + p, r > div > span > p) { letter-spacing: 1px }");
        assertNull(spacing(sibling, byId(doc, "after-h1")));
        assertNull(spacing(sibling, byId(doc, "grandchild")));
        assertEquals("1px", spacing(sibling, byId(doc, "outside")));
    }

    @Test
    public void complexArgumentsCountTheirWholeSpecificity() {
        // :not(#a .x) counts (1,1,0) with the i, which beats i.c.d at (0,2,1).
        Document doc = dom("<r><i id='e' class='c d'/></r>");
        assertEquals("1px", spacing(
                matcher("i:not(#a .x) { letter-spacing: 1px }\ni.c.d { letter-spacing: 2px }"), byId(doc, "e")));
    }

    @Test
    public void invalidArgumentsDropOnlyTheirRule() {
        Document doc = dom("<r><div><p id='p'/></div></r>");
        String[] invalid = {
            "p:not(div ::before) { letter-spacing: 9px }",
            "p:not(div::before p) { letter-spacing: 9px }",
            "p:not(div >) { letter-spacing: 9px }",
            "p:not(::before) { letter-spacing: 9px }",
            "p:not(:before) { letter-spacing: 9px }",
            "p:not() { letter-spacing: 9px }",
            "p:not(.a,) { letter-spacing: 9px }",
        };
        for (String rule : invalid) {
            List<String> errors = new ArrayList<>();
            Stylesheet sheet = parse(rule + "\np { letter-spacing: 1px }", (uri, message) -> errors.add(message));
            Matcher m = new Matcher(TREE_RES, ATT_RES, null, Collections.singletonList(sheet), "print");

            assertFalse("expected a parse error for " + rule, errors.isEmpty());
            assertEquals(rule, "1px", spacing(m, byId(doc, "p")));
        }
    }
}
