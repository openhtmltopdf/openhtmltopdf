package com.openhtmltopdf.nonvisualregressiontests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Element;

import com.openhtmltopdf.css.parser.FSRGBColor;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.render.BlockBox;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.MarkerData;
import com.openhtmltopdf.visualtest.TestSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Tests that {@code ::marker} font-size and color apply to the disc, circle and square
 * markers (CSS Lists Level 3), not only to marker {@code content}.
 */
public class MarkerGlyphStyleTest {

    @BeforeClass
    public static void configure() {
        TestSupport.quietLogs();
    }

    private static final String HTML =
        "<html><head><style>\n" +
        "body { font-size: 20px; color: rgb(10, 20, 30); }\n" +
        "#half::marker { font-size: 10px; }\n" +
        "#double::marker { font-size: 200%; }\n" +
        "#red::marker { color: rgb(255, 0, 0); }\n" +
        "#plain-rule::marker { white-space: pre; }\n" +
        "#square { list-style-type: square; }\n" +
        "#square::marker { font-size: 10px; color: rgb(0, 0, 255); }\n" +
        "</style></head><body><ul>\n" +
        "<li id='default'>a</li><li id='half'>b</li><li id='double'>c</li>\n" +
        "<li id='red'>d</li><li id='plain-rule'>e</li><li id='square'>f</li>\n" +
        "</ul></body></html>";

    private BlockBox rootBox;

    private void layout() throws IOException {
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(HTML, null);
        builder.toStream(new ByteArrayOutputStream());
        try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
            renderer.layout();
            rootBox = renderer.getRootBox();
        }
    }

    private MarkerData.GlyphMarker glyph(String id) {
        BlockBox box = (BlockBox) findById(rootBox, id);
        assertNotNull("no box for #" + id, box);
        assertNotNull("no marker for #" + id, box.getMarkerData());
        MarkerData.GlyphMarker glyph = box.getMarkerData().getGlyphMarker();
        assertNotNull("no glyph marker for #" + id, glyph);
        return glyph;
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

    private static void assertColor(FSRGBColor color, int r, int g, int b) {
        assertNotNull(color);
        assertEquals(r, color.getRed());
        assertEquals(g, color.getGreen());
        assertEquals(b, color.getBlue());
    }

    @Test
    public void testWithoutAMarkerRuleTheGlyphKeepsTheListItemSizeAndColor() throws IOException {
        layout();
        MarkerData.GlyphMarker glyph = glyph("default");
        assertNull("the list item color is used", glyph.getColor());
        assertEquals(glyph.getDiameter() * 3, glyph.getLayoutWidth());
    }

    @Test
    public void testMarkerFontSizeScalesTheGlyph() throws IOException {
        layout();
        int base = glyph("default").getDiameter();
        // 10px against the list item's 20px halves the glyph, 200% doubles it; casts round down.
        assertEquals(base / 2, glyph("half").getDiameter(), 1);
        assertEquals(base * 2, glyph("double").getDiameter(), 1);
        assertEquals(glyph("half").getDiameter() * 3, glyph("half").getLayoutWidth());
    }

    @Test
    public void testMarkerColorPaintsTheGlyph() throws IOException {
        layout();
        assertColor((FSRGBColor) glyph("red").getColor(), 255, 0, 0);
        assertEquals(glyph("default").getDiameter(), glyph("red").getDiameter());
    }

    @Test
    public void testAMarkerRuleWithoutSizeOrColorChangesNothing() throws IOException {
        layout();
        MarkerData.GlyphMarker glyph = glyph("plain-rule");
        assertEquals(glyph("default").getDiameter(), glyph.getDiameter());
        // The color is inherited from the list item, so it is the color painted before.
        assertColor((FSRGBColor) glyph.getColor(), 10, 20, 30);
    }

    @Test
    public void testSquareMarkersAreStyledToo() throws IOException {
        layout();
        assertEquals(glyph("default").getDiameter() / 2, glyph("square").getDiameter(), 1);
        assertColor((FSRGBColor) glyph("square").getColor(), 0, 0, 255);
    }
}
