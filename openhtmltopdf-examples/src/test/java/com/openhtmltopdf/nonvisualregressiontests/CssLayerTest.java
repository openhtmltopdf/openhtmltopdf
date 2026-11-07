package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.testlistener.PrintingRunner;

/**
 * Tests cascade layers (CSS Cascade 5, 6.4): <code>@layer</code> statements and
 * blocks, and <code>@import ... layer(name)</code> (#19).
 *
 * <p>Each case gives a paragraph <code>&lt;p class="x"&gt;</code> different
 * <code>padding-left</code> values in competing rules; the position of its text tells
 * which rule won. The expected values are what Chrome computes for the same style
 * sheets.
 */
@RunWith(PrintingRunner.class)
public class CssLayerTest {
    @ClassRule
    public static final TemporaryFolder FOLDER = new TemporaryFolder();

    @BeforeClass
    public static void writeImportedSheets() throws IOException {
        write("imp.css", "p { padding-left: 30px }");
        write("impx.css", "p.x { padding-left: 30px }");
    }

    /**
     * An <code>@layer</code> statement may come before <code>@import</code>, which
     * still imports.
     */
    @Test
    public void testImportAfterALayerStatement() throws IOException {
        assertEquals(30, paddingPx("@layer a; @import url(imp.css);"));
        assertEquals(30, paddingPx("@layer a, b; @import url(imp.css); @layer c;"));
    }

    /**
     * Rules in no layer beat those in a layer, whatever their specificity.
     */
    @Test
    public void testUnlayeredRulesBeatLayeredOnes() throws IOException {
        assertEquals(20, paddingPx("@layer base { p.x { padding-left: 10px } } p { padding-left: 20px }"));
        assertEquals(20, paddingPx("@layer { p.x { padding-left: 10px } } p { padding-left: 20px }"));
    }

    /**
     * A later layer beats an earlier one, whatever the specificity; layers are
     * ordered by where they are first named.
     */
    @Test
    public void testLayersAreOrderedByWhereTheyAreFirstNamed() throws IOException {
        assertEquals(20, paddingPx("@layer a { p.x { padding-left: 10px } } @layer b { p { padding-left: 20px } }"));
        assertEquals(10, paddingPx(
                "@layer b, a; @layer a { p { padding-left: 10px } } @layer b { p { padding-left: 20px } }"));
    }

    /**
     * The rules directly in a layer beat those of its sublayers.
     */
    @Test
    public void testOwnRulesOfALayerBeatItsSublayers() throws IOException {
        assertEquals(10, paddingPx(
                "@layer outer { p { padding-left: 10px } @layer inner { p.x { padding-left: 20px } } }"));
        assertEquals(10, paddingPx(
                "@layer outer.inner { p.x { padding-left: 20px } } @layer outer { p { padding-left: 10px } }"));
    }

    /**
     * <code>@import ... layer(name)</code> and <code>layer</code> import into a layer;
     * a <code>supports(...)</code> condition is not checked, but does not keep the
     * style sheet from being imported.
     */
    @Test
    public void testImportIntoALayer() throws IOException {
        assertEquals(20, paddingPx("@import url(impx.css) layer(base); p { padding-left: 20px }"));
        assertEquals(20, paddingPx("@import url(impx.css) layer; p { padding-left: 20px }"));
        assertEquals(30, paddingPx("@import url(imp.css) supports(display: block);"));
    }

    /**
     * An <code>@media</code> rule inside a layer applies, in that layer.
     */
    @Test
    public void testMediaRuleInsideALayer() throws IOException {
        assertEquals(10, paddingPx("@layer l { @media all { p { padding-left: 10px } } }"));
        assertEquals(20, paddingPx("@layer l { @media all { p.x { padding-left: 10px } } } p { padding-left: 20px }"));
    }

    private static void write(String name, String css) throws IOException {
        Files.write(new File(FOLDER.getRoot(), name).toPath(), css.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The left padding of the paragraph, in CSS pixels, under these styles.
     */
    private static int paddingPx(String styles) throws IOException {
        String html =
            // The styles come first, where @import is allowed.
            "<html><head><style>" +
            styles +
            "@page { size: 300px 100px; margin: 0; }" +
            "body { margin: 0; font-family: 'TestFont'; font-size: 20px; }" +
            "p { margin: 0; }" +
            "</style></head><body><p class=\"x\">t</p></body></html>";

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, FOLDER.getRoot().toURI().toString());
        builder.toStream(os);
        builder.testMode(true);
        builder.useFont(() -> CssLayerTest.class.getClassLoader().getResourceAsStream(
                "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"), "TestFont");
        builder.run();

        try (PDDocument doc = Loader.loadPDF(os.toByteArray())) {
            List<TextPosition> glyphs = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void processTextPosition(TextPosition text) {
                    glyphs.add(text);
                }
            };
            stripper.getText(doc);
            // The first glyph's x, in points, as CSS pixels.
            return Math.round(glyphs.get(0).getXDirAdj() / 0.75f);
        }
    }
}
