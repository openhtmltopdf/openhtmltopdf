package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Element;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.render.BlockBox;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.visualtest.TestSupport;

/**
 * Tests break-before, break-after and break-inside, and the CSS 2 page-break-*
 * properties as their legacy shorthands (CSS Fragmentation Level 3).
 * https://github.com/openhtmltopdf/openhtmltopdf/issues/272
 */
public class BreakPropertiesTest {

    @BeforeClass
    public static void configure() {
        TestSupport.quietLogs();
    }

    private static final String STYLE_HTML =
        "<html><head><style>\n" +
        "#legacy-always { page-break-before: always; page-break-after: always; }\n" +
        "#legacy-avoid { page-break-before: avoid; page-break-after: left; page-break-inside: avoid; }\n" +
        "#modern { break-before: avoid-page; break-after: column; break-inside: avoid-column; }\n" +
        "#legacy-wins { break-inside: auto; page-break-inside: avoid; }\n" +
        "#modern-wins { page-break-inside: avoid; break-inside: auto; }\n" +
        "#legacy-invalid { break-before: left; page-break-before: page; }\n" +
        "#legacy-initial { break-inside: avoid; page-break-inside: initial; }\n" +
        "#outer { break-inside: avoid; }\n" +
        "</style></head>\n" +
        "<body>\n" +
        "  <div id=\"legacy-always\">A</div>\n" +
        "  <div id=\"legacy-avoid\">B</div>\n" +
        "  <div id=\"modern\">C</div>\n" +
        "  <div id=\"legacy-wins\">D</div>\n" +
        "  <div id=\"modern-wins\">E</div>\n" +
        "  <div id=\"legacy-invalid\">F</div>\n" +
        "  <div id=\"legacy-initial\">G</div>\n" +
        "  <div id=\"outer\"><div id=\"inner\">H</div></div>\n" +
        "</body></html>";

    private BlockBox rootBox;

    private void layout() throws IOException {
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(STYLE_HTML, null);
        builder.toStream(new ByteArrayOutputStream());
        try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
            renderer.layout();
            rootBox = renderer.getRootBox();
        }
    }

    private IdentValue ident(String id, CSSName name) {
        Box found = findById(rootBox, id);
        assertNotNull("no box for #" + id, found);
        return found.getStyle().getIdent(name);
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
    public void testLegacyAlwaysSetsPage() throws IOException {
        layout();
        assertEquals(IdentValue.PAGE, ident("legacy-always", CSSName.BREAK_BEFORE));
        assertEquals(IdentValue.PAGE, ident("legacy-always", CSSName.BREAK_AFTER));
    }

    @Test
    public void testLegacyValuesCarryOver() throws IOException {
        layout();
        assertEquals(IdentValue.AVOID, ident("legacy-avoid", CSSName.BREAK_BEFORE));
        assertEquals(IdentValue.LEFT, ident("legacy-avoid", CSSName.BREAK_AFTER));
        assertEquals(IdentValue.AVOID, ident("legacy-avoid", CSSName.BREAK_INSIDE));
    }

    @Test
    public void testModernValues() throws IOException {
        layout();
        assertEquals(IdentValue.AVOID_PAGE, ident("modern", CSSName.BREAK_BEFORE));
        assertEquals(IdentValue.COLUMN, ident("modern", CSSName.BREAK_AFTER));
        assertEquals(IdentValue.AVOID_COLUMN, ident("modern", CSSName.BREAK_INSIDE));
    }

    /**
     * Being shorthands, the legacy properties take part in the cascade with their
     * longhands: the later declaration wins.
     */
    @Test
    public void testLaterDeclarationWins() throws IOException {
        layout();
        assertEquals(IdentValue.AVOID, ident("legacy-wins", CSSName.BREAK_INSIDE));
        assertEquals(IdentValue.AUTO, ident("modern-wins", CSSName.BREAK_INSIDE));
    }

    /**
     * page is a break-before value but not a page-break-before one, so the
     * declaration is dropped and the earlier one stays.
     */
    @Test
    public void testLegacyRejectsModernValue() throws IOException {
        layout();
        assertEquals(IdentValue.LEFT, ident("legacy-invalid", CSSName.BREAK_BEFORE));
    }

    @Test
    public void testLegacyInitial() throws IOException {
        layout();
        assertEquals(IdentValue.AUTO, ident("legacy-initial", CSSName.BREAK_INSIDE));
    }

    /**
     * break-inside inherits, as page-break-inside always did here.
     */
    @Test
    public void testBreakInsideInherits() throws IOException {
        layout();
        assertEquals(IdentValue.AVOID, ident("inner", CSSName.BREAK_INSIDE));
    }

    /**
     * Returns the 1-based page the text MARK is on.
     */
    private static int pageOfMark(String css, String body) throws IOException {
        String html =
            "<html><head><style>\n" +
            "@page { size: 200px 200px; margin: 10px; }\n" +
            "body { margin: 0; font-size: 10px; line-height: 12px; }\n" +
            ".spacer { height: 120px; }\n" +
            css +
            "</style></head><body>" + body + "</body></html>";

        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        builder.toStream(os);
        builder.run();

        try (PDDocument doc = Loader.loadPDF(os.toByteArray())) {
            for (int page = 1; page <= doc.getNumberOfPages(); page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                if (stripper.getText(doc).contains("MARK")) {
                    return page;
                }
            }
        }
        return -1;
    }

    /** A six-line block starting 120px down a 180px page: four lines fit. */
    private static final String TALL_BLOCK =
        "<div class=\"spacer\"></div><div class=\"keep\">MARK<br/>2<br/>3<br/>4<br/>5<br/>6</div>";

    private static final String AFTER_TEXT = "<p style=\"margin: 0\">x</p><div class=\"b\">MARK</div>";

    /** A one-line block ending 18px above the page bottom, followed by three lines. */
    private static final String HEADING =
        "<div style=\"height: 150px\"></div><div class=\"h\">MARK</div><div>a<br/>b<br/>c</div>";

    @Test
    public void testBreakInsideAvoidMovesBlock() throws IOException {
        assertEquals(1, pageOfMark("", TALL_BLOCK));
        assertEquals(2, pageOfMark(".keep { break-inside: avoid; }", TALL_BLOCK));
        assertEquals(2, pageOfMark(".keep { break-inside: avoid-page; }", TALL_BLOCK));
        assertEquals(2, pageOfMark(".keep { page-break-inside: avoid; }", TALL_BLOCK));
    }

    @Test
    public void testBreakInsideAvoidColumnDoesNotAvoidPageBreak() throws IOException {
        assertEquals(1, pageOfMark(".keep { break-inside: avoid-column; }", TALL_BLOCK));
    }

    @Test
    public void testBreakBeforePage() throws IOException {
        assertEquals(1, pageOfMark("", AFTER_TEXT));
        assertEquals(2, pageOfMark(".b { break-before: page; }", AFTER_TEXT));
        assertEquals(2, pageOfMark(".b { page-break-before: always; }", AFTER_TEXT));
        assertEquals(1, pageOfMark(".b { break-before: column; }", AFTER_TEXT));
    }

    /**
     * The first page is a right page, so a break to a right page leaves a blank left one.
     */
    @Test
    public void testBreakBeforeLeftAndRight() throws IOException {
        assertEquals(2, pageOfMark(".b { break-before: left; }", AFTER_TEXT));
        assertEquals(3, pageOfMark(".b { break-before: right; }", AFTER_TEXT));
    }

    @Test
    public void testBreakAfterPage() throws IOException {
        String body = "<div class=\"a\">x</div><div>MARK</div>";
        assertEquals(2, pageOfMark(".a { break-after: page; }", body));
        assertEquals(2, pageOfMark(".a { page-break-after: always; }", body));
    }

    @Test
    public void testBreakAfterAvoidKeepsBlockWithNext() throws IOException {
        assertEquals(1, pageOfMark("", HEADING));
        assertEquals(2, pageOfMark(".h { break-after: avoid; }", HEADING));
        assertEquals(2, pageOfMark(".h { break-after: avoid-page; }", HEADING));
        assertEquals(2, pageOfMark(".h { page-break-after: avoid; }", HEADING));
    }
}
