package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Element;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.render.BlockBox;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.visualtest.TestSupport;

/**
 * Checks that a margin shorthand resolved through var() expands to the same
 * longhand values as the equivalent literal shorthand. Mirrors the Orbeon
 * `.xforms-label { margin: var(--orbeon-grid-label-margin) }` case.
 */
public class VarMarginShorthandTest {

    @BeforeClass
    public static void configure() {
        TestSupport.quietLogs();
    }

    private static final String HTML =
        "<html><head><style>\n" +
        ":root { --m: 2px 0 4px 0; --neg: -10px 0; }\n" +
        "#lit { margin: 2px 0 4px 0; }\n" +
        "#via { margin: var(--m); }\n" +
        "#litNeg { margin: -10px 0; }\n" +
        "#viaNeg { margin: var(--neg); }\n" +
        "</style></head>\n" +
        "<body>\n" +
        "  <div id=\"lit\">lit</div>\n" +
        "  <div id=\"via\">via</div>\n" +
        "  <div id=\"litNeg\">litNeg</div>\n" +
        "  <div id=\"viaNeg\">viaNeg</div>\n" +
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

    private void assertSameMargins(String litId, String viaId) {
        Box lit = byId(litId);
        Box via = byId(viaId);
        for (CSSName side : new CSSName[] {
                CSSName.MARGIN_TOP, CSSName.MARGIN_RIGHT, CSSName.MARGIN_BOTTOM, CSSName.MARGIN_LEFT }) {
            assertEquals(side + " (" + litId + " vs " + viaId + ")",
                    lit.getStyle().asFloat(side), via.getStyle().asFloat(side), 0.001f);
        }
    }

    @Test
    public void fourValueShorthand() throws IOException {
        layout();
        // Expect top=2, right=0, bottom=4, left=0
        assertEquals(2f, byId("lit").getStyle().asFloat(CSSName.MARGIN_TOP), 0.001f);
        assertEquals(4f, byId("lit").getStyle().asFloat(CSSName.MARGIN_BOTTOM), 0.001f);
        assertSameMargins("lit", "via");
    }

    @Test
    public void negativeShorthand() throws IOException {
        layout();
        assertEquals(-10f, byId("litNeg").getStyle().asFloat(CSSName.MARGIN_TOP), 0.001f);
        assertSameMargins("litNeg", "viaNeg");
    }
}
