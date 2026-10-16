package com.openhtmltopdf.nonvisualregressiontests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Element;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.layout.LayoutContext;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.render.BlockBox;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.PageBox;
import com.openhtmltopdf.testlistener.PrintingRunner;
import com.openhtmltopdf.visualtest.TestSupport;

import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests the {@code margin-break} property (CSS Fragmentation Level 4) on the top margin of a
 * box that starts a page, and on the bottom margin of the box before it.
 */
@RunWith(PrintingRunner.class)
public class MarginBreakTest {

    @BeforeClass
    public static void configure() {
        TestSupport.quietLogs();
    }

    /** The laid-out top margin of #x, in device units, and the page it starts. */
    private static final class Result {
        final float margin;
        final int pageNo;

        Result(float margin, int pageNo) {
            this.margin = margin;
            this.pageNo = pageNo;
        }
    }

    /**
     * Lays out a 200px page whose first 190px are filled, followed by #x with a 30px top
     * margin. #x does not fit and avoids breaking inside, so it always starts the second page.
     */
    private static Result layout(String xStyle) throws IOException {
        return layout(xStyle, 190);
    }

    private static Result layout(String xStyle, int fillHeight) throws IOException {
        String html =
            "<html><head><style>\n" +
            "@page { size: 200px 200px; margin: 0; }\n" +
            "body { margin: 0; }\n" +
            "#fill { height: " + fillHeight + "px; }\n" +
            "#x { margin-top: 30px; height: 50px; page-break-inside: avoid; " + xStyle + " }\n" +
            "</style></head><body><div id='fill'></div><div id='x'></div></body></html>";

        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(new ByteArrayOutputStream());
        try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
            renderer.layout();
            BlockBox root = renderer.getRootBox();
            LayoutContext c = renderer.getSharedContext().newLayoutContextInstance();
            BlockBox x = (BlockBox) findById(root, "x");
            assertNotNull("no box for #x", x);

            List<PageBox> pages = root.getLayer().getPages();
            int pageNo = -1;
            for (PageBox page : pages) {
                if (x.getAbsY() >= page.getTop() && x.getAbsY() < page.getBottom()) {
                    pageNo = page.getPageNo();
                }
            }
            return new Result(x.getMargin(c).top(), pageNo);
        }
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

    private static void assertTruncated(String xStyle) throws IOException {
        Result r = layout(xStyle);
        assertEquals("#x should start the second page: " + xStyle, 1, r.pageNo);
        assertEquals("top margin should be truncated: " + xStyle, 0f, r.margin, 0.01f);
    }

    private static void assertKept(String xStyle) throws IOException {
        // The same box with nothing above it stays on the first page with its margin untouched.
        float untouched = layout("", 0).margin;
        assertTrue("the untouched margin should be positive", untouched > 0);

        Result r = layout(xStyle);
        assertEquals("#x should start the second page: " + xStyle, 1, r.pageNo);
        assertEquals("top margin should be kept: " + xStyle, untouched, r.margin, 0.01f);
    }

    @Test
    public void testAutoTruncatesAfterAnUnforcedBreak() throws IOException {
        assertTruncated("");
        assertTruncated("margin-break: auto;");
    }

    @Test
    public void testAutoKeepsAfterAForcedBreak() throws IOException {
        assertKept("page-break-before: always;");
    }

    @Test
    public void testKeepPreservesTheMarginAfterAnUnforcedBreak() throws IOException {
        assertKept("margin-break: keep;");
    }

    @Test
    public void testKeepPreservesTheMarginAfterAForcedBreak() throws IOException {
        assertKept("margin-break: keep; page-break-before: always;");
    }

    @Test
    public void testDiscardTruncatesAfterAnUnforcedBreak() throws IOException {
        assertTruncated("margin-break: discard;");
    }

    @Test
    public void testDiscardTruncatesAfterAForcedBreak() throws IOException {
        assertTruncated("margin-break: discard; page-break-before: always;");
    }

    @Test
    public void testDiscardTruncatesAtTheStartOfTheFirstPage() throws IOException {
        Result r = layout("margin-break: discard;", 0);
        assertEquals("#x should start the first page", 0, r.pageNo);
        assertEquals("discard truncates at the start of the fragmentation context", 0f, r.margin, 0.01f);
    }

    @Test
    public void testAutoAndKeepPreserveTheMarginOnTheFirstPage() throws IOException {
        float untouched = layout("", 0).margin;
        assertTrue("the untouched margin should be positive", untouched > 0);
        assertEquals(untouched, layout("margin-break: keep;", 0).margin, 0.01f);
    }

    @Test
    public void testInvalidValueFallsBackToAuto() throws IOException {
        assertTruncated("margin-break: always;");
    }

    @Test
    public void testIsNotInherited() throws IOException {
        String html =
            "<html><body><div id='p' style='margin-break: keep'><div id='c'></div></div></body></html>";
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(new ByteArrayOutputStream());
        try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
            renderer.layout();
            BlockBox root = renderer.getRootBox();
            assertEquals(IdentValue.KEEP, findById(root, "p").getStyle().getIdent(CSSName.MARGIN_BREAK));
            assertEquals(IdentValue.AUTO, findById(root, "c").getStyle().getIdent(CSSName.MARGIN_BREAK));
        }
    }

    /**
     * Lays out #a, 150px high with a bottom margin, on a 200px page, then #b, which does not fit
     * after the margin and starts the second page. Returns where #b's border box starts on
     * that page, in px.
     */
    private static int bottomMarginLayout(String aStyle, String bStyle) throws IOException {
        String html =
            "<html><head><style>\n" +
            "@page { size: 200px 200px; margin: 0; }\n" +
            "body { margin: 0; }\n" +
            "#a { height: 150px; margin-bottom: 80px; " + aStyle + " }\n" +
            "#b { height: 30px; " + bStyle + " }\n" +
            "</style></head><body><div id='a'></div><div id='b'></div></body></html>";
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(new ByteArrayOutputStream());
        try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
            renderer.layout();
            BlockBox root = renderer.getRootBox();
            LayoutContext c = renderer.getSharedContext().newLayoutContextInstance();
            Box b = findById(root, "b");
            PageBox second = root.getLayer().getPages().get(1);
            int top = b.getAbsY() + (int) b.getMargin(c).top() - second.getTop();
            assertTrue("#b starts the second page", top >= 0 && b.getAbsY() < second.getBottom());
            return top / renderer.getSharedContext().getDotsPerPixel();
        }
    }

    @Test
    public void testAutoTruncatesTheBottomMarginBeforeABreak() throws IOException {
        assertEquals(0, bottomMarginLayout("", ""));
        assertEquals(0, bottomMarginLayout("", "page-break-before: always;"));
    }

    @Test
    public void testKeepContinuesTheBottomMarginPastAnUnforcedBreak() throws IOException {
        // 50px of the 80px margin fit on the first page; the other 30px continue on the second.
        assertEquals(30, bottomMarginLayout("margin-break: keep;", ""));
        // It collapses with #b's own top margin there.
        assertEquals(30, bottomMarginLayout("margin-break: keep;", "margin-top: 20px;"));
    }

    @Test
    public void testKeepContinuesTheBottomMarginPastAForcedBreak() throws IOException {
        assertEquals(30, bottomMarginLayout("margin-break: keep;", "page-break-before: always;"));
        // Margins on either side of a forced break do not collapse: #b keeps its own 20px too.
        assertEquals(50, bottomMarginLayout("margin-break: keep;", "page-break-before: always; margin-top: 20px;"));
    }

    @Test
    public void testDiscardTruncatesTheBottomMarginBeforeABreak() throws IOException {
        assertEquals(0, bottomMarginLayout("margin-break: discard;", ""));
        assertEquals(0, bottomMarginLayout("margin-break: discard;", "page-break-before: always;"));
    }

    @Test
    public void testAKeptBottomMarginThatFitsBeforeTheBreakDoesNotContinue() throws IOException {
        assertEquals(0, bottomMarginLayout("margin-break: keep; margin-bottom: 40px;", "height: 60px; page-break-inside: avoid;"));
    }
}
