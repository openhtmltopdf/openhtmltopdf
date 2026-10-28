package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.testlistener.PrintingRunner;

/**
 * Tests what follows a space that hangs at the end of a full line: a line whose
 * last text fits only without its trailing space keeps that text, so whatever takes
 * width after it must start the next line, and the space must be trimmed wherever
 * the line ends.
 *
 * <p>Every paragraph is right-aligned and as wide as "mill mill" plus half a space,
 * so a line that keeps its trailing space, or anything after it, starts left of
 * its box. Cases from yusuf-bozkurt on PR #265.
 */
@RunWith(PrintingRunner.class)
public class TrailingSpaceHangTest {
    private static final String TEST_FONT = "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";

    private static final float FONT_SIZE_PT = 15;
    private static final float TOLERANCE = 0.05f;

    private static TrueTypeFont font;
    private static CmapLookup cmap;
    private static float boxPt;

    @BeforeClass
    public static void readFont() throws IOException {
        try (InputStream in = resource(TEST_FONT)) {
            font = new TTFParser().parse(new RandomAccessReadBuffer(in));
        }
        cmap = font.getUnicodeCmapLookup();
        boxPt = widthPt("mill mill") + widthPt(" ") / 2;
    }

    /**
     * An inline box that takes no width, after the hung space, does not keep the
     * space from being trimmed: neither an empty element nor the start of one whose
     * text wraps to the next line.
     */
    @Test
    public void testSpaceBeforeAnElementWithoutWidthIsTrimmed() throws IOException {
        assertFirstLineInBox("mill mill <span></span>");
        assertFirstLineInBox("mill mill <b>mill</b>");
        assertEquals("the bold word starts the next line", "mill", text(lines("", "mill mill <b>mill</b>").get(1)));
    }

    /**
     * An inline-block after the hung space starts the next line, and the line it
     * leaves is trimmed.
     */
    @Test
    public void testInlineBlockAfterAHungSpaceStartsTheNextLine() throws IOException {
        assertFirstLineInBox("mill mill <span style=\"display: inline-block\"></span>");
        assertFirstLineInBox("mill mill <span style=\"display: inline-block; width: 10px\"></span>");
    }

    /**
     * An element without content stays with the text before it, as in browsers, even
     * with padding: the space before it still ends the line and is trimmed, and only
     * the padding counts, whether or not it fits. A line too wide for its box is
     * aligned to its start, as in CSS.
     */
    @Test
    public void testEmptyElementWithPaddingStaysWithTheTextBeforeIt() throws IOException {
        List<List<TextPosition>> fits = lines("", "mill mill <span style=\"padding-left: 1px\"></span>");
        assertEquals("1px: lines", 1, fits.size());
        assertEquals("1px: the space is trimmed, the padding ends the line",
                boxPt - widthPt("mill mill") - 0.75f, fits.get(0).get(0).getXDirAdj(), TOLERANCE);

        List<List<TextPosition>> overflows = lines("", "mill mill <span style=\"padding-left: 20px\"></span>");
        assertEquals("20px: lines", 1, overflows.size());
        assertEquals("20px: the line overflows, so it starts at the box's start",
                0, overflows.get(0).get(0).getXDirAdj(), TOLERANCE);

        List<List<TextPosition>> lines = renderLines("p { margin: 0; width: 150pt; text-align: right; }",
                "<p>mill <span style=\"padding-left: 20px; border-left: 1px solid red\"></span></p>");
        assertEquals("where the element fits, the space before it is trimmed too",
                150 - 21 * 0.75f - widthPt("mill"), lines.get(0).get(0).getXDirAdj(), TOLERANCE);
    }

    /**
     * A line that holds only an element with padding does not collapse (CSS 2.1,
     * 9.4.2): it is as high as a line of text, so the paragraph after it starts
     * where it would after a line of text.
     */
    @Test
    public void testLineWithOnlyAPaddedElementHasHeight() throws IOException {
        String styles = "p { margin: 0; }";
        List<List<TextPosition>> afterPadded = renderLines(styles,
                "<p><span style=\"padding-left: 20px\"></span></p><p>mill</p>");
        List<List<TextPosition>> afterText = renderLines(styles, "<p>x</p><p>mill</p>");

        assertEquals("the next paragraph's baseline",
                afterText.get(1).get(0).getYDirAdj(), afterPadded.get(0).get(0).getYDirAdj(), TOLERANCE);
    }

    /**
     * A line that holds only an inline-block is as high as a line of text: the strut
     * takes part in its height (CSS 2.1, 10.8), so a box smaller than the font sits
     * on the baseline inside the line, not below it.
     */
    @Test
    public void testLineWithOnlyAnInlineBlockHasTheStrutsHeight() throws IOException {
        String styles = "p { margin: 0; }";
        List<List<TextPosition>> afterBlock = renderLines(styles,
                "<p><span style=\"display: inline-block; width: 10px; height: 10px\"></span></p><p>mill</p>");
        List<List<TextPosition>> afterText = renderLines(styles, "<p>x</p><p>mill</p>");

        assertEquals("the next paragraph's baseline",
                afterText.get(1).get(0).getYDirAdj(), afterBlock.get(0).get(0).getYDirAdj(), TOLERANCE);
    }

    /**
     * An element with content and padding after the hung space starts the next
     * line, laid out in that line's style, not in the <code>::first-line</code> style.
     */
    @Test
    public void testElementMovedToTheNextLineLeavesTheFirstLineStyle() throws IOException {
        List<List<TextPosition>> lines = renderLines(
                "p { margin: 0; width: " + (2 * boxPt) + "pt; text-align: right; }" +
                "p::first-line { font-size: 40px; }",
                "<p>mill mill <span style=\"padding-left: 1px\">next</span></p>");

        assertEquals("first line", "millmill", text(lines.get(0)));
        assertEquals("second line", "next", text(lines.get(1)));
        assertEquals("the second line's font size", FONT_SIZE_PT, lines.get(1).get(0).getFontSizeInPt(), TOLERANCE);
    }

    /**
     * Pre text does not wrap, so after the hung space it starts the next line; a pre
     * text that starts with a line feed ends the full line instead, as a hard break
     * does, without an empty line.
     */
    @Test
    public void testPreTextAfterAHungSpaceStartsTheNextLine() throws IOException {
        assertFirstLineInBox("mill mill <span style=\"white-space: pre\">next</span>");
        assertEquals("second line", "next",
                text(lines("", "mill mill <span style=\"white-space: pre\">next</span>").get(1)));
        assertFirstLineInBox("mill mill <span style=\"white-space: pre\">   </span>x");

        List<List<TextPosition>> withLineFeed = lines("", "mill mill <span style=\"white-space: pre\">&#10;next</span>");
        List<List<TextPosition>> withBreak = lines("", "mill mill<br/>next");
        assertEquals("lines", 2, withLineFeed.size());
        assertEquals("the second line as after a <br>",
                withBreak.get(1).get(0).getYDirAdj(), withLineFeed.get(1).get(0).getYDirAdj(), TOLERANCE);
    }

    private static void assertFirstLineInBox(String text) throws IOException {
        List<TextPosition> first = lines("", text).get(0);
        assertEquals(text + ": first line", "millmill", text(first));
        assertTrue(text + ": the first line starts at " + first.get(0).getXDirAdj() + ", left of its box",
                first.get(0).getXDirAdj() >= -TOLERANCE);
    }

    /**
     * The glyphs of each line of a right-aligned paragraph of the test width.
     */
    private static List<List<TextPosition>> lines(String css, String text) throws IOException {
        return renderLines("p { margin: 0; width: " + boxPt + "pt; text-align: right; " + css + " }",
                "<p>" + text + "</p>");
    }

    private static List<List<TextPosition>> renderLines(String styles, String body) throws IOException {
        String html =
            "<html><head><style>" +
            "@page { size: 500px 500px; margin: 0; }" +
            "body { margin: 0; font-family: 'TestFont'; font-size: 20px; }" +
            styles +
            "</style></head><body>" + body + "</body></html>";

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(os);
        builder.testMode(true);
        builder.useFont(() -> resource(TEST_FONT), "TestFont");
        builder.run();

        try (PDDocument doc = Loader.loadPDF(os.toByteArray())) {
            Map<Integer, List<TextPosition>> byLine = new LinkedHashMap<>();
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String string, List<TextPosition> positions) {
                    for (TextPosition position : positions) {
                        if (!position.getUnicode().trim().isEmpty()) {
                            byLine.computeIfAbsent(Math.round(position.getYDirAdj()),
                                    key -> new ArrayList<>()).add(position);
                        }
                    }
                }
            };
            stripper.setSortByPosition(true);
            stripper.getText(doc);
            return new ArrayList<>(byLine.values());
        }
    }

    private static String text(List<TextPosition> line) {
        StringBuilder text = new StringBuilder();
        for (TextPosition glyph : line) {
            text.append(glyph.getUnicode());
        }
        return text.toString();
    }

    private static float widthPt(String text) throws IOException {
        float units = 0;
        for (int i = 0; i < text.length(); i++) {
            units += font.getAdvanceWidth(cmap.getGlyphId(text.charAt(i)));
        }
        return units / font.getUnitsPerEm() * FONT_SIZE_PT;
    }

    private static InputStream resource(String path) {
        return TrailingSpaceHangTest.class.getClassLoader().getResourceAsStream(path);
    }
}
