package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;

import com.openhtmltopdf.css.style.CssContext;
import com.openhtmltopdf.css.style.derived.RectPropertySet;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.render.BlockBox;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.visualtest.TestSupport;

/**
 * Tests padding-inline-start/end and margin-inline-start/end: the side they apply to
 * follows the direction, and of a logical property and its physical one the later
 * in the cascade wins.
 */
public class LogicalInlinePropertiesTest {

    @BeforeClass
    public static void configure() {
        TestSupport.quietLogs();
    }

    private static final String HTML =
        "<html><head><style>\n" +
        "#reset { padding: 0; }\n" +
        "#logical-later { padding-left: 5px; padding-inline-start: 20px; }\n" +
        "#physical-later { padding-inline-start: 20px; padding-left: 5px; }\n" +
        "#margins { margin-inline-start: 7px; margin-inline-end: 3px; }\n" +
        "</style></head>\n" +
        "<body>\n" +
        "  <ul id=\"ltr-list\"><li>A</li></ul>\n" +
        "  <ul id=\"rtl-list\" dir=\"rtl\"><li>B</li></ul>\n" +
        "  <ul id=\"reset\" dir=\"rtl\"><li>C</li></ul>\n" +
        "  <div id=\"logical-later\">D</div>\n" +
        "  <div id=\"physical-later\">E</div>\n" +
        "  <div dir=\"rtl\"><div id=\"margins\">F</div></div>\n" +
        "  <dl dir=\"rtl\"><dt>G</dt><dd id=\"rtl-dd\">H</dd></dl>\n" +
        "</body></html>";

    private BlockBox rootBox;
    private CssContext ctx;

    private BlockBox layout() throws IOException {
        if (rootBox == null) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(HTML, null);
            builder.toStream(new ByteArrayOutputStream());
            try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
                renderer.layout();
                rootBox = renderer.getRootBox();
                ctx = renderer.getSharedContext().newLayoutContextInstance();
            }
        }
        return rootBox;
    }

    private static Box findById(Box box, String id) {
        if (box.getElement() != null && id.equals(box.getElement().getAttribute("id"))) {
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

    private RectPropertySet padding(String id) throws IOException {
        Box box = findById(layout(), id);
        assertNotNull("no box for #" + id, box);
        return box.getStyle().getPaddingRect(0, ctx);
    }

    private RectPropertySet margin(String id) throws IOException {
        Box box = findById(layout(), id);
        assertNotNull("no box for #" + id, box);
        return box.getStyle().getMarginRect(0, ctx);
    }

    private static final float PX = 20f; // Dots per pixel in PDF output.

    @Test
    public void testListIndentedAtStart() throws IOException {
        assertEquals(40 * PX, padding("ltr-list").left(), 0.5f);
        assertEquals(0, padding("ltr-list").right(), 0.5f);
        assertEquals(0, padding("rtl-list").left(), 0.5f);
        assertEquals(40 * PX, padding("rtl-list").right(), 0.5f);
        assertEquals(40 * PX, margin("rtl-dd").right(), 0.5f);
        assertEquals(0, margin("rtl-dd").left(), 0.5f);
    }

    @Test
    public void testAuthorPhysicalPaddingOverridesDefault() throws IOException {
        assertEquals(0, padding("reset").left(), 0.5f);
        assertEquals(0, padding("reset").right(), 0.5f);
    }

    @Test
    public void testLaterInCascadeWins() throws IOException {
        assertEquals(20 * PX, padding("logical-later").left(), 0.5f);
        assertEquals(5 * PX, padding("physical-later").left(), 0.5f);
    }

    @Test
    public void testMarginsFollowDirection() throws IOException {
        assertEquals(7 * PX, margin("margins").right(), 0.5f);
        assertEquals(3 * PX, margin("margins").left(), 0.5f);
    }
}
