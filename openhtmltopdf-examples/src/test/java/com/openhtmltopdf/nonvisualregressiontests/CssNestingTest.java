package com.openhtmltopdf.nonvisualregressiontests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Element;

import com.openhtmltopdf.css.parser.FSRGBColor;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.visualtest.TestSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests CSS Nesting: style rules nested in style rules, the nesting selector {@code &}
 * and nested declarations rules.
 */
public class CssNestingTest {

    @BeforeClass
    public static void configure() {
        TestSupport.quietLogs();
    }

    private static final String RED = "rgb(255, 0, 0)";
    private static final String BLUE = "rgb(0, 0, 255)";
    private static final String BLACK = "rgb(0, 0, 0)";

    private Box rootBox;

    private void layout(String css, String body) throws IOException {
        // The document is XML, where & is written &amp; inside <style> too.
        String html = "<html><head><style>body { color: black; } " + css.replace("&", "&amp;") +
                "</style></head><body>" +
                body + "</body></html>";
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(new ByteArrayOutputStream());
        try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
            renderer.layout();
            rootBox = renderer.getRootBox();
        }
    }

    private String color(String id) {
        Box box = findById(rootBox, id);
        assertNotNull("no box for #" + id, box);
        FSRGBColor c = (FSRGBColor) box.getStyle().getColor();
        return "rgb(" + c.getRed() + ", " + c.getGreen() + ", " + c.getBlue() + ")";
    }

    private static Box findById(Box box, String id) {
        Element e = box.getElement();
        if (e != null && id.equals(e.getAttribute("id"))) {
            return box;
        }
        for (int i = 0; i < box.getChildCount(); i++) {
            Box found = findById(box.getChild(i), id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    public void testNestingSelectorMatchesDescendantsOfTheParent() throws IOException {
        layout(".a { & .b { color: red; } }",
                "<div class='a'><div id='in' class='b'>x</div></div><div id='out' class='b'>x</div>");
        assertEquals(RED, color("in"));
        assertEquals(BLACK, color("out"));
    }

    @Test
    public void testNestedSelectorWithoutNestingSelectorIsADescendant() throws IOException {
        layout(".a { .b { color: red; } }",
                "<div class='a'><div id='in' class='b'>x</div></div><div id='out' class='b'>x</div>");
        assertEquals(RED, color("in"));
        assertEquals(BLACK, color("out"));
    }

    @Test
    public void testNestingSelectorInACompound() throws IOException {
        layout(".a { &.b { color: red; } }",
                "<div id='both' class='a b'>x</div><div id='a' class='a'>x</div>");
        assertEquals(RED, color("both"));
        assertEquals(BLACK, color("a"));
    }

    @Test
    public void testRelativeSelectors() throws IOException {
        layout(".a { > .b { color: red; } + .c { color: blue; } }",
                "<div class='a'><div id='child' class='b'>x</div></div><div id='sibling' class='c'>x</div>");
        assertEquals(RED, color("child"));
        assertEquals(BLUE, color("sibling"));
    }

    @Test
    public void testChildCombinatorDoesNotMatchGrandchildren() throws IOException {
        layout(".a { > .b { color: red; } }",
                "<div class='a'><div><div id='grandchild' class='b'>x</div></div></div>");
        assertEquals(BLACK, color("grandchild"));
    }

    @Test
    public void testNestingSelectorIsLikeIsNotTextSubstitution() throws IOException {
        // ".x &" is ".x :is(.p .q)": the .x ancestor may be above or below .p.
        layout(".p .q { .x & { color: red; } }",
                "<div class='x'><div class='p'><div id='x-above' class='q'>x</div></div></div>" +
                "<div class='p'><div class='x'><div id='x-between' class='q'>x</div></div></div>" +
                "<div class='p'><div id='no-x' class='q'>x</div></div>");
        assertEquals(RED, color("x-above"));
        assertEquals(RED, color("x-between"));
        assertEquals(BLACK, color("no-x"));
    }

    @Test
    public void testNestingSelectorHasTheSpecificityOfTheMostSpecificParent() throws IOException {
        // "& .c" is ":is(.a, #b) .c": specificity (1,1,0) for both parents, which beats (0,4,0).
        layout(".a .c.d.e { color: blue; } .a, #b { & .c { color: red; } }",
                "<div class='a'><div id='target' class='c d e'>x</div></div>");
        assertEquals(RED, color("target"));
    }

    @Test
    public void testDeclarationsAfterANestedRuleComeAfterItInTheCascade() throws IOException {
        // "& { color: red }" and the trailing declaration have the same specificity; the later wins.
        layout(".a { & { color: red; } color: blue; } .b { color: red; & { color: blue; } }",
                "<div id='a' class='a'>x</div><div id='b' class='b'>x</div>");
        assertEquals(BLUE, color("a"));
        assertEquals(BLUE, color("b"));
    }

    @Test
    public void testRulesNestDeeply() throws IOException {
        layout(".a { .b { .c { color: red; } } }",
                "<div class='a'><div class='b'><div id='in' class='c'>x</div></div></div>" +
                "<div class='b'><div id='out' class='c'>x</div></div>");
        assertEquals(RED, color("in"));
        assertEquals(BLACK, color("out"));
    }

    @Test
    public void testNestingSelectorAtTheTopLevelIsTheRoot() throws IOException {
        layout("& > body { color: red; }", "<div id='div'>x</div>");
        assertEquals(RED, color("div"));
    }

    @Test
    public void testAnInvalidNestedRuleIsSkippedAlone() throws IOException {
        layout(".a { *zoom: 1; .b { color: blue } ~ .c { color: blue } color: red; } .d { color: red; }",
                "<div id='a' class='a'>x</div><div id='d' class='d'>x</div>");
        assertEquals(RED, color("a"));
        assertEquals(RED, color("d"));
    }

    @Test
    public void testANestedRuleCanStartWithATypeSelector() throws IOException {
        // Parsed as a declaration first and, when that fails, again as a nested rule (CSS Syntax 3).
        layout(".a { color: red; div { color: blue; } span:last-child { color: green; } }",
                "<div id='a' class='a'><div id='div'>x</div><span id='span'>y</span></div>");
        assertEquals(RED, color("a"));
        assertEquals(BLUE, color("div"));
        assertEquals("rgb(0, 128, 0)", color("span"));
    }

    @Test
    public void testDeclarationsStillParseAsDeclarations() throws IOException {
        // Values that look like selectors stay declarations, and the rules around them still apply.
        layout(".a { font-family: serif; color: red; div { color: blue; } }",
                "<div id='a' class='a'><div id='div'>x</div></div>");
        assertEquals(RED, color("a"));
        assertEquals(BLUE, color("div"));
    }

    @Test
    public void testAnInvalidDeclarationStillReportsItsOwnError() throws IOException {
        java.util.List<String> errors = new java.util.ArrayList<>();
        com.openhtmltopdf.css.parser.CSSParser parser = new com.openhtmltopdf.css.parser.CSSParser(
                (uri, message) -> errors.add(message));
        parser.parseStylesheet("test://css", com.openhtmltopdf.css.sheet.StylesheetInfo.AUTHOR,
                new java.io.StringReader(".a { color red; width: 10px; }"));
        assertEquals(errors.toString(), 1, errors.size());
        assertTrue(errors.get(0), errors.get(0).contains("Skipping declaration"));
    }

    @Test
    public void testNestedMediaRulesApplyForTheirMedia() throws IOException {
        layout(".a { color: black; @media print { color: red; .b { color: blue; } } " +
                "@media screen { color: green; .b { color: green; } } }",
                "<div id='a' class='a'><div id='b' class='b'>x</div></div>");
        assertEquals(RED, color("a"));
        assertEquals(BLUE, color("b"));
    }

    @Test
    public void testNestedMediaRulesCombineWithTheMediaAroundThem() throws IOException {
        layout("@media print { .a { @media screen { color: green; } @media all { & .b { color: blue; } } } } " +
                ".c { @media print { @media print, screen { color: red; } } }",
                "<div id='a' class='a'><div id='b' class='b'>x</div></div><div id='c' class='c'>x</div>");
        assertEquals(BLACK, color("a"));
        assertEquals(BLUE, color("b"));
        assertEquals(RED, color("c"));
    }

    @Test
    public void testDeclarationsAfterANestedMediaRuleComeAfterIt() throws IOException {
        layout(".a { @media print { color: red; } color: blue; }", "<div id='a' class='a'>x</div>");
        assertEquals(BLUE, color("a"));
    }

    @Test
    public void testNestingSelectorNeverMatchesAPseudoElementParent() throws IOException {
        layout(".a::before { & .b { color: red; } }",
                "<div class='a'><div id='in' class='b'>x</div></div>");
        assertEquals(BLACK, color("in"));
    }
}
