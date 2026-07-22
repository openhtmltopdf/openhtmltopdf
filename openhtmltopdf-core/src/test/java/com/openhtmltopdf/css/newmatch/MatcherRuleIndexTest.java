package com.openhtmltopdf.css.newmatch;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.extend.AttributeResolver;
import com.openhtmltopdf.css.extend.TreeResolver;
import com.openhtmltopdf.css.extend.lib.DOMTreeResolver;
import com.openhtmltopdf.css.parser.CSSParser;
import com.openhtmltopdf.css.sheet.PropertyDeclaration;
import com.openhtmltopdf.css.sheet.Ruleset;
import com.openhtmltopdf.css.sheet.Stylesheet;
import com.openhtmltopdf.css.sheet.StylesheetInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Element-level tests for {@link Matcher} with the rightmost-simple-selector
 * rule index: bucket routing, cascade order across buckets, chained-selector
 * splicing, and a seeded differential test against a naive reference matcher.
 */
public class MatcherRuleIndexTest {

    private static final AttributeResolver ATT_RES = new DomAttributeResolver();
    private static final TreeResolver TREE_RES = new DOMTreeResolver();

    // ---------------------------------------------------------------- harness

    private static Stylesheet parse(String css) {
        try {
            return new CSSParser((uri, message) -> fail("CSS parse error: " + message))
                    .parseStylesheet("test://css", StylesheetInfo.AUTHOR, new StringReader(css));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Matcher matcher(Stylesheet sheet) {
        return new Matcher(TREE_RES, ATT_RES, null, Collections.singletonList(sheet), "print");
    }

    private static Matcher matcher(String css) {
        return matcher(parse(css));
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

    /** The cascaded letter-spacing value for the element, or null when no rule matched. */
    private static String spacing(Matcher m, Element e) {
        PropertyDeclaration pd = m.getCascadedStyle(e, false).propertyByName(CSSName.LETTER_SPACING);
        return pd == null ? null : pd.getValue().getCssText();
    }

    // --------------------------------------------------------- targeted tests

    @Test
    public void bucketsRouteIdClassNameAndUniversal() {
        Matcher m = matcher(
                "#i { letter-spacing: 1px }\n" +
                ".c { letter-spacing: 2px }\n" +
                "p { letter-spacing: 3px }\n" +
                "[data-k] { letter-spacing: 4px }\n");
        Document doc = dom("<r>" +
                "<div id='i'/><div id='cls' class='c'/><p id='p1'/>" +
                "<span id='attr' data-k='v'/><span id='none'/></r>");

        assertEquals("1px", spacing(m, byId(doc, "i")));
        assertEquals("2px", spacing(m, byId(doc, "cls")));
        assertEquals("3px", spacing(m, byId(doc, "p1")));
        assertEquals("4px", spacing(m, byId(doc, "attr")));
        assertNull(spacing(m, byId(doc, "none")));
    }

    @Test
    public void laterRuleWinsAcrossClassAndUniversalBuckets() {
        // .a and [class~=a] have the same specificity but land in different buckets.
        Document doc = dom("<r><div id='d' class='a'/></r>");
        assertEquals("2px", spacing(
                matcher(".a { letter-spacing: 1px }\n[class~=a] { letter-spacing: 2px }"), byId(doc, "d")));
        assertEquals("1px", spacing(
                matcher("[class~=a] { letter-spacing: 2px }\n.a { letter-spacing: 1px }"), byId(doc, "d")));
    }

    @Test
    public void laterRuleWinsAcrossTwoClassBuckets() {
        Document doc = dom("<r><div id='d' class='a b'/></r>");
        assertEquals("2px", spacing(
                matcher(".a { letter-spacing: 1px }\n.b { letter-spacing: 2px }"), byId(doc, "d")));
        assertEquals("1px", spacing(
                matcher(".b { letter-spacing: 2px }\n.a { letter-spacing: 1px }"), byId(doc, "d")));
    }

    @Test
    public void multiClassSelectorAppliesOnce() {
        Matcher m = matcher(".a.b { letter-spacing: 1px }");
        Document doc = dom("<r><i id='ab' class='b a'/><i id='a' class='a'/><i id='b' class='b'/></r>");

        assertEquals("1px", spacing(m, byId(doc, "ab")));
        assertNull(spacing(m, byId(doc, "a")));
        assertNull(spacing(m, byId(doc, "b")));
    }

    @Test
    public void descendantChainMatchesAtAnyDepth() {
        Matcher m = matcher("div .x { letter-spacing: 1px }");
        Document doc = dom("<r><div><p class='x' id='child'/><p><span class='x' id='grand'/></p></div>" +
                "<p class='x' id='outside'/></r>");

        assertEquals("1px", spacing(m, byId(doc, "child")));
        assertEquals("1px", spacing(m, byId(doc, "grand")));
        assertNull(spacing(m, byId(doc, "outside")));
    }

    @Test
    public void childChainMatchesImmediateChildrenOnly() {
        Matcher m = matcher("div > .x { letter-spacing: 1px }");
        Document doc = dom("<r><div><p class='x' id='child'/><p><span class='x' id='grand'/></p></div></r>");

        assertEquals("1px", spacing(m, byId(doc, "child")));
        assertNull(spacing(m, byId(doc, "grand")));
    }

    @Test
    public void chainSpliceOrderFollowsDeclarationOrder() {
        // Both chains reach the span with equal specificity via different producers,
        // so the merged child axes order decides the cascade: later-declared wins.
        Document doc = dom("<r><body><div><span id='s'/></div></body></r>");
        assertEquals("2px", spacing(
                matcher("body span { letter-spacing: 1px }\ndiv span { letter-spacing: 2px }"), byId(doc, "s")));
        assertEquals("1px", spacing(
                matcher("div span { letter-spacing: 2px }\nbody span { letter-spacing: 1px }"), byId(doc, "s")));
    }

    @Test
    public void siblingSelector() {
        Matcher m = matcher(".a + .b { letter-spacing: 1px }");
        Document doc = dom("<r><i class='a'/><i class='b' id='second'/><i class='b' id='third'/></r>");

        assertEquals("1px", spacing(m, byId(doc, "second")));
        assertNull(spacing(m, byId(doc, "third")));
    }

    @Test
    public void dynamicConditionInsideClassBucket() {
        Matcher m = matcher(".a:first-child { letter-spacing: 1px }");
        Document doc = dom("<r><i class='a' id='first'/><i class='a' id='second'/></r>");

        assertEquals("1px", spacing(m, byId(doc, "first")));
        assertNull(spacing(m, byId(doc, "second")));
    }

    @Test
    public void pseudoElementSelectorsGoToPseudoStyles() {
        Matcher m = matcher(".a:before { content: \"x\" }");
        Document doc = dom("<r><i class='a' id='e'/></r>");
        Element e = byId(doc, "e");

        assertEquals(0, m.getCascadedStyle(e, false).countAssigned());
        CascadedStyle pe = m.getPECascadedStyle(e, "before");
        assertNotNull(pe);
        assertTrue(pe.hasProperty(CSSName.CONTENT));
    }

    @Test
    public void duplicateClassTokens() {
        Matcher m = matcher(".a { letter-spacing: 1px }");
        Document doc = dom("<r><i id='e' class='a a'/></r>");

        assertEquals("1px", spacing(m, byId(doc, "e")));
    }

    @Test
    public void getCSSForAllDescendantsAfterChainActivation() {
        // Exercises the deferred axes materialization (Mapper.axes()) of a Mapper
        // whose element activated a chained selector.
        Matcher m = matcher("div p span { color: red }");
        Document doc = dom("<r><div><p id='p'><span/></p></div></r>");

        m.getCascadedStyle(byId(doc, "p"), false);
        String css = m.getCSSForAllDescendants(byId(doc, "p"));
        assertTrue(css, css.contains("span"));
        assertTrue(css, css.contains("color"));
    }

    @Test
    public void manyChainsFromOneElementAndInternedMapperReuse() {
        // One element activating more than 8 chains exercises the growth of
        // mapChild's chainedPositions array (initial size 8). The second,
        // identically matched sibling then reuses the same interned child
        // Mapper, whose deferred axes were already materialized.
        StringBuilder css = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            css.append(".a s").append(i).append(" { letter-spacing: ").append(i + 1).append("px }\n");
        }
        StringBuilder xml = new StringBuilder("<r>");
        for (String prefix : new String[] {"c", "d"}) {
            xml.append("<div class='a'>");
            for (int i = 0; i < 10; i++) {
                xml.append("<s").append(i).append(" id='").append(prefix).append(i).append("'/>");
            }
            xml.append("</div>");
        }
        xml.append("</r>");

        Matcher m = matcher(css.toString());
        Document doc = dom(xml.toString());
        for (String prefix : new String[] {"c", "d"}) {
            for (int i = 0; i < 10; i++) {
                assertEquals((i + 1) + "px", spacing(m, byId(doc, prefix + i)));
            }
        }
    }

    @Test
    public void mergedAxesPreserveCarriedBeforeChainOnEqualPosition() {
        // Pins mergeChained at list level: on equal position the carried
        // descendant producer comes before the chain it produced (the `<=`
        // tie-break), with other descendant selectors around them by position.
        Stylesheet sheet = parse(
                "div div { letter-spacing: 1px }\n" +
                "span { letter-spacing: 2px }\n");
        Selector divTop = ((Ruleset) sheet.getContents().get(0)).getFSSelectors().get(0);
        Selector divChain = divTop.getChainedSelector();
        Selector spanTop = ((Ruleset) sheet.getContents().get(1)).getFSSelectors().get(0);

        Matcher m = matcher(sheet);
        Document doc = dom("<r><div id='outer'><div id='inner'/></div></r>");

        // The sorted base list is [span, div] (specificity 1 vs 2), and the
        // outer div activated the `div div` chain at the producer's position.
        Matcher.Mapper outerMapper = m.matchElement(byId(doc, "outer"));
        List<Selector> axes = outerMapper.axes();
        assertEquals(3, axes.size());
        assertSame(spanTop, axes.get(0));
        assertSame(divTop, axes.get(1));   // carried producer first...
        assertSame(divChain, axes.get(2)); // ...then the chain it produced

        assertEquals("1px", spacing(m, byId(doc, "inner")));
    }

    // ------------------------------------------------------- differential test

    private static final String[] TAGS = {"div", "p", "span", "i", "b"};
    private static final String[] CLASSES = {"a", "b", "c"};
    private static final String[] IDS = {"x", "y", "z"};

    /**
     * Renders many random small trees and compares, for every element, the
     * cascaded letter-spacing winner between the indexed Matcher and a naive
     * reference that evaluates each selector chain by walking the DOM.
     */
    @Test
    public void differentialAgainstNaiveReference() throws Exception {
        Stylesheet sheet = parse(
                "* { letter-spacing: 1px }\n" +
                "div { letter-spacing: 2px }\n" +
                "p { letter-spacing: 3px }\n" +
                "span { letter-spacing: 4px }\n" +
                ".a { letter-spacing: 5px }\n" +
                ".b { letter-spacing: 6px }\n" +
                ".c { letter-spacing: 7px }\n" +
                ".a.b { letter-spacing: 8px }\n" +
                "#x { letter-spacing: 9px }\n" +
                "#y { letter-spacing: 10px }\n" +
                "div .a { letter-spacing: 11px }\n" +
                "div > .b { letter-spacing: 12px }\n" +
                "p span { letter-spacing: 13px }\n" +
                "div p .a { letter-spacing: 14px }\n" +
                ".a .b { letter-spacing: 15px }\n" +
                ".c > span { letter-spacing: 16px }\n" +
                "div + p { letter-spacing: 17px }\n" +
                ".a + .b { letter-spacing: 18px }\n" +
                "span:first-child { letter-spacing: 19px }\n" +
                "div [data-k] { letter-spacing: 20px }\n" +
                "div .a > span { letter-spacing: 21px }\n" +
                "[data-k=v] { letter-spacing: 22px }\n" +
                "i b { letter-spacing: 23px }\n" +
                "p > .c .a { letter-spacing: 24px }\n");

        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Random rnd = new Random(20260720L);
        int matched = 0;

        for (int tree = 0; tree < 40; tree++) {
            Matcher m = matcher(sheet);

            // Same order as Matcher.createDocumentMapper; positions were just set by the ctor.
            TreeMap<String, Selector> sorter = new TreeMap<>();
            for (Object content : sheet.getContents()) {
                for (Selector sel : ((Ruleset) content).getFSSelectors()) {
                    sorter.put(sel.getOrder(), sel);
                }
            }

            Document doc = builder.newDocument();
            Element root = doc.createElement("div");
            doc.appendChild(root);
            grow(doc, root, rnd, 0);

            NodeList all = doc.getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); i++) {
                Element e = (Element) all.item(i);

                String expected = null;
                for (Selector top : sorter.values()) {
                    if (top.getPseudoElement() == null && chainMatches(top, e)) {
                        expected = top.getRuleset().getPropertyDeclarations().get(0).getValue().getCssText();
                    }
                }

                assertEquals(describe(e), expected, spacing(m, e));
                if (expected != null) {
                    matched++;
                }
            }
        }

        assertTrue("differential test never matched anything", matched > 200);
    }

    private static void grow(Document doc, Element parent, Random rnd, int depth) {
        if (depth >= 4) {
            return;
        }
        int count = rnd.nextInt(4);
        for (int i = 0; i < count; i++) {
            Element e = doc.createElement(TAGS[rnd.nextInt(TAGS.length)]);
            double r = rnd.nextDouble();
            if (r < 0.30) {
                e.setAttribute("class", CLASSES[rnd.nextInt(CLASSES.length)]);
            } else if (r < 0.45) {
                e.setAttribute("class",
                        CLASSES[rnd.nextInt(CLASSES.length)] + " " + CLASSES[rnd.nextInt(CLASSES.length)]);
            }
            if (rnd.nextDouble() < 0.10) {
                e.setAttribute("id", IDS[rnd.nextInt(IDS.length)]);
            }
            if (rnd.nextDouble() < 0.20) {
                e.setAttribute("data-k", rnd.nextBoolean() ? "v" : "w");
            }
            parent.appendChild(e);
            grow(doc, e, rnd, depth + 1);
        }
    }

    /**
     * Naive reference: the chain (leftmost link first) matches e when its last
     * link matches e and each earlier link matches an ancestor per the axis of
     * the link to its right. Sibling selectors are handled inside matches().
     */
    private static boolean chainMatches(Selector top, Element e) {
        List<Selector> links = new ArrayList<>();
        for (Selector s = top; s != null; s = s.getChainedSelector()) {
            links.add(s);
        }
        return matchesAt(links, links.size() - 1, e);
    }

    private static boolean matchesAt(List<Selector> links, int i, Element e) {
        Selector s = links.get(i);
        if (!s.matches(e, ATT_RES, TREE_RES) || !s.matchesDynamic(e, ATT_RES, TREE_RES)) {
            return false;
        }
        if (i == 0) {
            return true;
        }
        Node p = e.getParentNode();
        if (s.getAxis() == Selector.CHILD_AXIS) {
            return p instanceof Element && matchesAt(links, i - 1, (Element) p);
        }
        // DESCENDANT_AXIS: any proper ancestor
        while (p instanceof Element) {
            if (matchesAt(links, i - 1, (Element) p)) {
                return true;
            }
            p = p.getParentNode();
        }
        return false;
    }

    private static String describe(Element e) {
        StringBuilder sb = new StringBuilder(e.getNodeName());
        if (e.hasAttribute("class")) {
            sb.append(" class=\"").append(e.getAttribute("class")).append('"');
        }
        if (e.hasAttribute("id")) {
            sb.append(" id=\"").append(e.getAttribute("id")).append('"');
        }
        Node p = e.getParentNode();
        while (p instanceof Element) {
            sb.append(" < ").append(p.getNodeName());
            p = p.getParentNode();
        }
        return sb.toString();
    }

    // -------------------------------------------------------------- resolvers

    private static final class DomAttributeResolver implements AttributeResolver {
        @Override
        public String getAttributeValue(Object o, String attrName) {
            Element e = (Element) o;
            return e.hasAttribute(attrName) ? e.getAttribute(attrName) : null;
        }

        @Override
        public String getAttributeValue(Object o, String namespaceURI, String attrName) {
            Element e = (Element) o;
            if (namespaceURI == null || namespaceURI.isEmpty()) {
                return getAttributeValue(o, attrName);
            }
            return e.hasAttributeNS(namespaceURI, attrName) ? e.getAttributeNS(namespaceURI, attrName) : null;
        }

        @Override public String getClass(Object e) { return getAttributeValue(e, "class"); }
        @Override public String getID(Object e) { return getAttributeValue(e, "id"); }
        @Override public String getNonCssStyling(Object e) { return null; }
        @Override public String getElementStyling(Object e) { return null; }
        @Override public String getLang(Object e) { return null; }
        @Override public boolean isLink(Object e) { return false; }
        @Override public boolean isVisited(Object e) { return false; }
        @Override public boolean isHover(Object e) { return false; }
        @Override public boolean isActive(Object e) { return false; }
        @Override public boolean isFocus(Object e) { return false; }
        @Override public boolean isMarker(Object e) { return false; }
    }
}
