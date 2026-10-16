package com.openhtmltopdf.nonvisualregressiontests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Element;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.BorderPainter;
import com.openhtmltopdf.render.InlineLayoutBox;
import com.openhtmltopdf.render.LineBox;
import com.openhtmltopdf.render.PageBox;
import com.openhtmltopdf.visualtest.TestSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests that box-decoration-break: clone keeps a broken box's content clear of the border and
 * padding repeated at each page break (CSS Fragmentation), and that slice is unchanged.
 */
public class BoxDecorationBreakTest {

    @BeforeClass
    public static void configure() {
        TestSupport.quietLogs();
    }

    // Pages are 110px high: 5 lines of 20px after the 10px the box adds above its content
    // (4px border and 6px padding on each side).
    private static final String CSS =
        "@page { size: 300px 110px; margin: 0; } body { margin: 0; font-size: 16px; line-height: 20px; } " +
        ".box { border: 4px solid black; padding: 6px; } .clone { box-decoration-break: clone; } ";

    private static final String TEN_LINES =
        "<br/>2<br/>3<br/>4<br/>5<br/>6<br/>7<br/>8<br/>9<br/>10";

    private Box rootBox;
    private List<PageBox> pages;
    private int dotsPerPixel;

    private void layout(String css, String body) throws IOException {
        String html = "<html><head><style>" + CSS + css + "</style></head><body>" + body + "</body></html>";
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(new ByteArrayOutputStream());
        try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
            renderer.layout();
            rootBox = renderer.getRootBox();
            pages = renderer.getRootBox().getLayer().getPages();
            dotsPerPixel = renderer.getSharedContext().getDotsPerPixel();
        }
    }

    private Box box(String id) {
        Box box = findById(rootBox, id);
        assertNotNull("no box for #" + id, box);
        return box;
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

    private static void collectLines(Box box, List<LineBox> lines) {
        if (box instanceof LineBox) {
            lines.add((LineBox) box);
            return;
        }
        for (int i = 0; i < box.getChildCount(); i++) {
            collectLines(box.getChild(i), lines);
        }
    }

    /** The first line of the box on the given page, as px below the top of that page. */
    private int firstLineOffset(String id, int page) {
        PageBox pageBox = pages.get(page);
        List<LineBox> lines = new ArrayList<>();
        collectLines(box(id), lines);
        for (LineBox line : lines) {
            if (line.getAbsY() >= pageBox.getTop() && line.getAbsY() < pageBox.getBottom()) {
                return (line.getAbsY() - pageBox.getTop()) / dotsPerPixel;
            }
        }
        throw new AssertionError("no line of #" + id + " on page " + page);
    }

    /** The space below the last line of the box on the given page, in px. */
    private int spaceBelowLastLine(String id, int page) {
        PageBox pageBox = pages.get(page);
        List<LineBox> lines = new ArrayList<>();
        collectLines(box(id), lines);
        int bottom = -1;
        for (LineBox line : lines) {
            if (line.getAbsY() >= pageBox.getTop() && line.getAbsY() < pageBox.getBottom()) {
                bottom = line.getAbsY() + line.getHeight();
            }
        }
        assertTrue("no line of #" + id + " on page " + page, bottom >= 0);
        return (pageBox.getBottom() - bottom) / dotsPerPixel;
    }

    @Test
    public void testThePropertyDefaultsToSliceAndIsNotInherited() throws IOException {
        layout(".x { box-decoration-break: bogus; }",
                "<div id='plain'>a</div><div id='clone' class='clone'><div id='child'>b</div></div>" +
                "<div id='invalid' class='x'>c</div>");
        assertEquals(IdentValue.SLICE, box("plain").getStyle().getIdent(CSSName.BOX_DECORATION_BREAK));
        assertEquals(IdentValue.CLONE, box("clone").getStyle().getIdent(CSSName.BOX_DECORATION_BREAK));
        assertEquals(IdentValue.SLICE, box("child").getStyle().getIdent(CSSName.BOX_DECORATION_BREAK));
        assertEquals(IdentValue.SLICE, box("invalid").getStyle().getIdent(CSSName.BOX_DECORATION_BREAK));
    }

    @Test
    public void testSliceContinuesAtThePageTop() throws IOException {
        layout("", "<div id='box' class='box'>1" + TEN_LINES + "</div>");
        assertEquals(0, firstLineOffset("box", 1));
    }

    @Test
    public void testCloneContinuesBelowTheRepeatedBorderAndPadding() throws IOException {
        layout("", "<div id='box' class='box clone'>1" + TEN_LINES + "</div>");
        assertEquals(10, firstLineOffset("box", 1));
    }

    @Test
    public void testCloneLeavesRoomForTheRepeatedBorderAndPaddingAtThePageBottom() throws IOException {
        // Slice fits 5 lines on the first page; clone moves the fifth so 10px stay free below.
        layout("", "<div id='box' class='box clone'>1" + TEN_LINES + "</div>");
        assertTrue(spaceBelowLastLine("box", 0) >= 10);
        layout("", "<div id='box' class='box'>1" + TEN_LINES + "</div>");
        assertTrue("slice fills the page", spaceBelowLastLine("box", 0) < 10);
    }

    @Test
    public void testNestedCloneBoxesRepeatBothBordersAndPaddings() throws IOException {
        layout("", "<div class='box clone'><div id='inner' class='box clone'>1" + TEN_LINES + "</div></div>");
        assertEquals(20, firstLineOffset("inner", 1));
    }

    @Test
    public void testACloneBoxThatIsNotBrokenIsLaidOutAsBefore() throws IOException {
        layout("", "<div id='slice' class='box'>1</div><div id='clone' class='box clone'>1</div>");
        assertEquals(box("slice").getHeight(), box("clone").getHeight());
        int offset = (box("clone").getAbsY() - box("slice").getAbsY()) / dotsPerPixel;
        assertEquals(firstLineOffset("slice", 0) + offset, firstLineOffset("clone", 0));
    }

    // Inline boxes: 4px margin, 2px border and 6px padding on each side, in a 300px wide page.
    private static final String INLINE_CSS =
        ".mark { margin: 0 4px; border: 2px solid black; padding: 0 6px; } ";
    private static final String INLINE_TEXT =
        "one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen";

    private List<InlineLayoutBox> fragments(String id) {
        List<InlineLayoutBox> result = new ArrayList<>();
        collectFragments(rootBox, id, result);
        return result;
    }

    private static void collectFragments(Box box, String id, List<InlineLayoutBox> result) {
        if (box instanceof InlineLayoutBox && box.getElement() != null &&
                id.equals(box.getElement().getAttribute("id"))) {
            result.add((InlineLayoutBox) box);
        }
        for (int i = 0; i < box.getChildCount(); i++) {
            collectFragments(box.getChild(i), id, result);
        }
        if (box instanceof InlineLayoutBox) {
            for (Object child : ((InlineLayoutBox) box).getInlineChildren()) {
                if (child instanceof Box) {
                    collectFragments((Box) child, id, result);
                }
            }
        }
    }

    @Test
    public void testSlicedInlineFragmentsHaveEdgesOnlyAtTheEnds() throws IOException {
        layout(INLINE_CSS, "<span id='m' class='mark'>" + INLINE_TEXT + "</span>");
        List<InlineLayoutBox> f = fragments("m");
        assertTrue("breaks across lines", f.size() > 1);
        assertEquals(BorderPainter.ALL - BorderPainter.RIGHT, f.get(0).getBorderSides());
        assertEquals(0, f.get(1).getLeftMarginBorderPadding(null) / dotsPerPixel);
    }

    @Test
    public void testClonedInlineFragmentsEachHaveBothEdges() throws IOException {
        layout(INLINE_CSS + ".mark { box-decoration-break: clone; }",
                "<span id='m' class='mark'>" + INLINE_TEXT + "</span>");
        List<InlineLayoutBox> f = fragments("m");
        assertTrue("breaks across lines", f.size() > 1);
        int pageWidth = rootBox.getWidth();
        for (InlineLayoutBox fragment : f) {
            assertEquals(BorderPainter.ALL, fragment.getBorderSides());
            assertEquals(12, fragment.getLeftMarginBorderPadding(null) / dotsPerPixel);
            assertEquals(12, fragment.getRightMarginPaddingBorder(null) / dotsPerPixel);
            // The repeated end edge is laid out inside the line, not past it.
            assertTrue(fragment.getAbsX() + fragment.getInlineWidth() <= pageWidth);
        }
    }
}
