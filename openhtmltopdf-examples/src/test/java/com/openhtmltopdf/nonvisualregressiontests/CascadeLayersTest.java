package com.openhtmltopdf.nonvisualregressiontests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Element;

import com.openhtmltopdf.css.extend.lib.DOMTreeResolver;
import com.openhtmltopdf.css.newmatch.Matcher;
import com.openhtmltopdf.css.parser.CSSParser;
import com.openhtmltopdf.css.parser.FSRGBColor;
import com.openhtmltopdf.css.sheet.FontFaceRule;
import com.openhtmltopdf.css.sheet.Stylesheet;
import com.openhtmltopdf.css.sheet.StylesheetInfo;
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
    public void testPageRulesAreLayered() throws IOException {
        color("@layer base { @page { size: 300px 200px; margin: 0; } } @page { size: 400px 200px; margin: 0; }");
        assertEquals("unlayered wins", 400, rootBox.getWidth() / 20);
        color("@layer a, b; @layer b { @page { size: 300px 200px; margin: 0; } } " +
                "@layer a { @page :first { size: 400px 200px; margin: 0; } }");
        assertEquals("a later layer beats a more specific page selector", 300, rootBox.getWidth() / 20);
    }

    @Test
    public void testFontFaceRulesAreOrderedByLayer() throws IOException {
        Stylesheet sheet = new CSSParser((uri, message) -> { }).parseStylesheet("test://css", StylesheetInfo.AUTHOR,
                new StringReader("@layer a, b; @layer b { @font-face { font-family: B; } } " +
                        "@font-face { font-family: U; } @layer a { @font-face { font-family: A; } }"));
        Matcher matcher = new Matcher(new DOMTreeResolver(), null, null, Collections.singletonList(sheet), "print");
        List<FontFaceRule> inSource = sheet.getFontFaceRules(); // B, U, A
        assertEquals(Arrays.asList(inSource.get(2), inSource.get(0), inSource.get(1)), matcher.getFontFaceRules());
    }

    @Test
    public void testRevertLayerRollsBackToThePreviousLayer() throws IOException {
        assertEquals(BLUE, color("@layer base { div { color: blue; } } @layer theme { div { color: red; } " +
                "#x { color: revert-layer; } }"));
        // Without a lower layer it rolls back to the previous origin, so the color is inherited.
        assertEquals(GREEN, color("body { color: green; } div { color: red; } #x { color: revert-layer; }"));
        // In the style attribute it rolls back to the rules.
        assertEquals(RED, color("div { color: red; }", "color: revert-layer"));
    }

    @Test
    public void testRevertLayerInAMarginBoxIsResolved() throws IOException {
        // Rolls back to the user agent value instead of reaching the computed style unresolved.
        color("@page { margin: 20px; @top-center { content: 'x'; color: revert-layer; } }");
        assertNotNull(rootBox);
    }

    @Test
    public void testRevertLayerThroughACustomPropertyActsAsUnset() throws IOException {
        // After the cascade the layers are gone, so a substituted revert-layer acts as unset:
        // color inherits from the body.
        assertEquals(GREEN, color("body { color: green; } " +
                "div { --c: revert-layer; color: var(--c); background-color: var(--missing, revert-layer); }"));
    }

    @Test
    public void testRevertLayerOnAnImportantDeclarationRollsBackToTheNextLayer() throws IOException {
        // Important layers in priority order: unlayered, b, a. Reverting a leaves b.
        assertEquals(BLUE, color("@layer a { #x { color: revert-layer !important; } } " +
                "@layer b { div { color: blue !important; } } div { color: red !important; }"));
    }

    @Test
    public void testImportedStyleSheetsCanBeLayered() throws IOException {
        Path imported = Files.createTempFile("cascade-layers", ".css");
        try {
            Files.write(imported, "#x { color: blue; } @layer inner { div { color: green; } }".getBytes(StandardCharsets.UTF_8));
            String url = imported.toUri().toString();
            assertEquals(BLUE, color("@import url('" + url + "'); div { color: red; }"));
            assertEquals(RED, color("@import url('" + url + "') layer(base); div { color: red; }"));
            assertEquals(RED, color("@import url('" + url + "') layer; div { color: red; }"));
            // The imported sheet's own layers nest inside the import layer: base.inner ranks below base.
            assertEquals(BLUE, color("@import url('" + url + "') layer(base); @layer base.inner { #x { color: red; } }"));
            // A layer statement before the import sets the order.
            assertEquals(BLUE, color("@layer theme, base; @import url('" + url + "') layer(base); " +
                    "@layer theme { #x { color: red; } }"));
        } finally {
            Files.delete(imported);
        }
    }

    @Test
    public void testAnInvalidLayerRuleIsSkipped() throws IOException {
        assertEquals(BLUE, color("@layer a b { div { color: red; } } @layer a, b { div { color: red; } } " +
                "div { color: blue; }"));
    }
}
