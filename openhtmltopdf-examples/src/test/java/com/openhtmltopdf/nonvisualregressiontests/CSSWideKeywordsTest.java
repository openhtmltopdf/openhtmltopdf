package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Element;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.css.parser.FSRGBColor;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.render.BlockBox;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.visualtest.TestSupport;

/**
 * Tests the CSS-wide keywords {@code initial} and {@code unset}.
 * https://github.com/openhtmltopdf/openhtmltopdf/issues/131
 */
public class CSSWideKeywordsTest {

    @BeforeClass
    public static void configure() {
        TestSupport.quietLogs();
    }

    private static final String HTML =
        "<html><head><style>\n" +
        "body { color: rgb(1, 2, 3); }\n" +
        ".pad { padding: 5px; margin-left: 9px; }\n" +
        "#padding-initial { padding: initial; }\n" +           // shorthand
        "#margin-initial { margin-left: initial; }\n" +        // longhand
        "#padding-unset { padding: unset; }\n" +               // not inherited -> initial
        "#color-initial { color: initial; }\n" +               // inherited -> initial
        "#color-unset { color: unset; }\n" +                   // inherited -> inherit
        "#display-initial { display: InItIaL; }\n" +           // case-insensitive
        "</style></head>\n" +
        "<body>\n" +
        "  <ul>\n" +
        "    <li style=\"font-weight: bold;\">Outer\n" +
        "      <ul id=\"weight-initial\" style=\"font-weight: initial;\">\n" +
        "        <li id=\"weight-initial-li\">Inner</li>\n" +
        "      </ul>\n" +
        "    </li>\n" +
        "  </ul>\n" +
        "  <div class=\"pad\" id=\"padding-initial\">A</div>\n" +
        "  <div class=\"pad\" id=\"margin-initial\">B</div>\n" +
        "  <div class=\"pad\" id=\"padding-unset\">C</div>\n" +
        "  <div id=\"color-initial\">D</div>\n" +
        "  <div id=\"color-unset\">E</div>\n" +
        "  <div><div id=\"display-initial\">F</div></div>\n" +
        "</body></html>";

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

    private Box byId(String id) {
        Box found = findById(rootBox, id);
        assertNotNull("no box for #" + id, found);
        return found;
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

    private void assertColor(String id, int r, int g, int b) {
        FSRGBColor rgb = (FSRGBColor) byId(id).getStyle().asColor(CSSName.COLOR);
        assertEquals("#" + id + " red", r, rgb.getRed());
        assertEquals("#" + id + " green", g, rgb.getGreen());
        assertEquals("#" + id + " blue", b, rgb.getBlue());
    }

    /**
     * The reproduction from issue 131: font-weight: initial on a nested list
     * must stop the bold inherited from the outer li.
     */
    @Test
    public void testFontWeightInitialStopsInheritance() throws IOException {
        layout();
        assertEquals(IdentValue.NORMAL, byId("weight-initial").getStyle().getIdent(CSSName.FONT_WEIGHT));
        assertEquals(IdentValue.NORMAL, byId("weight-initial-li").getStyle().getIdent(CSSName.FONT_WEIGHT));
    }

    @Test
    public void testInitialOnShorthandResetsAllLonghands() throws IOException {
        layout();
        Box box = byId("padding-initial");
        assertEquals(0f, box.getStyle().asFloat(CSSName.PADDING_TOP), 0.01f);
        assertEquals(0f, box.getStyle().asFloat(CSSName.PADDING_RIGHT), 0.01f);
        assertEquals(0f, box.getStyle().asFloat(CSSName.PADDING_BOTTOM), 0.01f);
        assertEquals(0f, box.getStyle().asFloat(CSSName.PADDING_LEFT), 0.01f);
        assertEquals("margin-left untouched", 9f, box.getStyle().asFloat(CSSName.MARGIN_LEFT), 0.01f);
    }

    @Test
    public void testInitialOnLonghand() throws IOException {
        layout();
        Box box = byId("margin-initial");
        assertEquals(0f, box.getStyle().asFloat(CSSName.MARGIN_LEFT), 0.01f);
        assertEquals("padding untouched", 5f, box.getStyle().asFloat(CSSName.PADDING_LEFT), 0.01f);
    }

    @Test
    public void testUnsetOnNonInheritedPropertyIsInitial() throws IOException {
        layout();
        assertEquals(0f, byId("padding-unset").getStyle().asFloat(CSSName.PADDING_LEFT), 0.01f);
    }

    @Test
    public void testInitialOnInheritedPropertyIgnoresParent() throws IOException {
        layout();
        assertColor("color-initial", 0, 0, 0);
    }

    @Test
    public void testUnsetOnInheritedPropertyInherits() throws IOException {
        layout();
        assertColor("color-unset", 1, 2, 3);
    }

    @Test
    public void testKeywordIsCaseInsensitive() throws IOException {
        layout();
        assertEquals(IdentValue.INLINE, byId("display-initial").getStyle().getIdent(CSSName.DISPLAY));
    }
}
