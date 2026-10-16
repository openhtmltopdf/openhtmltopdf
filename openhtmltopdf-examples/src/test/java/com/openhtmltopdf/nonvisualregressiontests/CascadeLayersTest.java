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

/**
 * Tests cascade layers ({@code @layer}, CSS Cascading Level 5): layers are compared before
 * specificity, unlayered rules win, and important declarations take the layers in reverse.
 */
public class CascadeLayersTest {

    @BeforeClass
    public static void configure() {
        TestSupport.quietLogs();
    }

    private static final String RED = "rgb(255, 0, 0)";
    private static final String BLUE = "rgb(0, 0, 255)";
    private static final String GREEN = "rgb(0, 128, 0)";

    private Box rootBox;

    private String color(String css) throws IOException {
        return color(css, "");
    }

    /** The color of a div with id x, class x, styled by the css and the style attribute. */
    private String color(String css, String styleAttribute) throws IOException {
        String html = "<html><head><style>" + css + "</style></head><body>" +
                "<div id='x' class='x' style='" + styleAttribute + "'>x</div></body></html>";
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(new ByteArrayOutputStream());
        try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
            renderer.layout();
            rootBox = renderer.getRootBox();
        }
        Box box = findById(rootBox, "x");
        assertNotNull(box);
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
    public void testUnlayeredRulesBeatLayeredRulesOfHigherSpecificity() throws IOException {
        assertEquals(BLUE, color("@layer base { #x { color: red; } } div { color: blue; }"));
    }

    @Test
    public void testALaterLayerBeatsAnEarlierOneOfHigherSpecificity() throws IOException {
        assertEquals(BLUE, color("@layer a { #x { color: red; } } @layer b { div { color: blue; } }"));
    }

    @Test
    public void testTheLayerStatementSetsTheOrder() throws IOException {
        assertEquals(RED, color("@layer b, a; @layer a { div { color: red; } } @layer b { #x { color: blue; } }"));
    }

    @Test
    public void testWithinALayerSpecificityAndOrderStillApply() throws IOException {
        assertEquals(RED, color("@layer a { #x { color: red; } div { color: blue; } }"));
        assertEquals(BLUE, color("@layer a { div { color: red; } } @layer a { div { color: blue; } }"));
    }

    @Test
    public void testImportantDeclarationsTakeTheLayersInReverse() throws IOException {
        assertEquals(RED, color("@layer a { div { color: red !important; } } " +
                "@layer b { #x { color: blue !important; } } #x.x { color: green !important; }"));
    }

    @Test
    public void testTheStyleAttributeBeatsLayers() throws IOException {
        assertEquals(BLUE, color("@layer a { #x { color: red; } }", "color: blue"));
        assertEquals(BLUE, color("@layer a { #x { color: red !important; } }", "color: blue !important"));
        assertEquals("importance comes first", RED, color("@layer a { #x { color: red !important; } }", "color: blue"));
    }

    @Test
    public void testRulesDirectlyInALayerBeatItsSublayers() throws IOException {
        assertEquals(RED, color("@layer a { div { color: red; } @layer b { #x { color: blue; } } }"));
        assertEquals(RED, color("@layer a.b { #x { color: blue; } } @layer a { div { color: red; } }"));
    }

    @Test
    public void testSublayersAreOrderedInsideTheirParent() throws IOException {
        // a.b, a.c, a, then d: d wins although a.c was declared after it.
        assertEquals(GREEN, color("@layer a.b { div { color: red; } } @layer d { div { color: green; } } " +
                "@layer a.c { #x { color: blue; } }"));
    }

    @Test
    public void testAnonymousLayersAreDistinct() throws IOException {
        assertEquals(BLUE, color("@layer { #x { color: red; } } @layer { div { color: blue; } }"));
        assertEquals(BLUE, color("@layer { #x { color: red; } } div { color: blue; }"));
    }

    @Test
    public void testALayerInAMediaRuleThatDoesNotApplyIsNotDeclared() throws IOException {
        // Rendering for print, so b is first declared after a.
        assertEquals(BLUE, color("@media screen { @layer b { div { color: green; } } } " +
                "@layer a { #x { color: red; } } @layer b { div { color: blue; } }"));
    }

    @Test
    public void testPageRulesInALayerStillApply() throws IOException {
        color("@layer base { @page { size: 300px 200px; margin: 0; } }");
        assertEquals(300, rootBox.getWidth() / 20);
    }

    @Test
    public void testAnInvalidLayerRuleIsSkipped() throws IOException {
        assertEquals(BLUE, color("@layer a b { div { color: red; } } @layer a, b { div { color: red; } } " +
                "div { color: blue; }"));
    }
}
