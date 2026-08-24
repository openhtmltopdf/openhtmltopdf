package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.testlistener.PrintingRunner;

/**
 * Tests the <code>letter-spacing</code> and <code>word-spacing</code> CSS
 * properties, on their own and together with <code>text-align: justify</code>.
 *
 * <p>Rather than compare against fixed glyph positions, which would only say
 * what this font happens to do, each test renders the same text twice and
 * measures how much the spacing moved the glyphs apart.
 */
@RunWith(PrintingRunner.class)
public class TextSpacingTest {
    private static final float PT_PER_PX = 0.75f;

    /** Wide enough that no test here wraps unless it means to. */
    private static final int PAGE_PX = 500;

    private static final float TOLERANCE = 0.05f;

    /**
     * Letter spacing goes after every character, so every advance grows by it.
     */
    @Test
    public void testLetterSpacingAddsAfterEveryCharacter() throws IOException {
        float[] plain = advances("", "AVAV");
        float[] spaced = advances("letter-spacing: 5px", "AVAV");

        assertAdvances("letter spacing", plain, spaced, new float[] {5, 5, 5});
    }

    /**
     * Word spacing goes after word separators only, leaving every other
     * character where it was.
     */
    @Test
    public void testWordSpacingAddsAtSpacesOnly() throws IOException {
        float[] plain = advances("", "aa bb");
        float[] spaced = advances("word-spacing: 20px", "aa bb");

        assertAdvances("word spacing", plain, spaced, new float[] {0, 0, 20, 0});
    }

    /**
     * A separator gets both, so the two add up there and only there.
     */
    @Test
    public void testLetterAndWordSpacingCombine() throws IOException {
        float[] plain = advances("", "aa bb");
        float[] spaced = advances("letter-spacing: 3px; word-spacing: 20px", "aa bb");

        assertAdvances("letter and word spacing", plain, spaced, new float[] {3, 3, 23, 3});
    }

    /**
     * Letter spacing may be negative, pulling characters together.
     */
    @Test
    public void testNegativeLetterSpacing() throws IOException {
        float[] plain = advances("", "AVAV");
        float[] spaced = advances("letter-spacing: -1px", "AVAV");

        assertAdvances("negative letter spacing", plain, spaced, new float[] {-1, -1, -1});
    }

    /**
     * Both properties take a length, so em is relative to the element's own font size.
     */
    @Test
    public void testSpacingInEmUnits() throws IOException {
        float[] plain = advances("", "aa bb");
        float[] spaced = advances("letter-spacing: 0.25em; word-spacing: 0.5em", "aa bb");

        // The test font is set at 20px, so 0.25em is 5px and 0.5em is 10px.
        assertAdvances("em spacing", plain, spaced, new float[] {5, 5, 15, 5});
    }

    /**
     * A no-break space is a word separator too.
     */
    @Test
    public void testWordSpacingAppliesToNoBreakSpace() throws IOException {
        float[] plain = advances("", "aa\u00a0bb");
        float[] spaced = advances("word-spacing: 20px", "aa\u00a0bb");

        assertAdvances("word spacing over nbsp", plain, spaced, new float[] {0, 0, 20, 0});
    }

    /**
     * The spacing is part of the text's width, so a line holds fewer words with it
     * than without.
     */
    @Test
    public void testWordSpacingIsCountedWhenBreakingLines() throws IOException {
        String text = "aaaa bbbb cccc dddd";

        assertEquals("lines without word spacing", 1,
                lines("width: 200px", text).size());
        assertEquals("lines with word spacing", 2,
                lines("width: 200px; word-spacing: 40px", text).size());
    }

    /**
     * Justification used to be dropped entirely as soon as letter-spacing was in
     * play. The two compose: letter spacing sets the advances, justification then
     * spreads whatever room is left over. The line comes to rest one letter space
     * short of the right edge, that being the space that follows its last
     * character, exactly as in a browser.
     */
    @Test
    public void testJustifyWithLetterSpacing() throws IOException {
        int letterSpacingPx = 2;

        List<Float> widths = lineWidths(
                "width: 200px; text-align: justify; letter-spacing: " + letterSpacingPx + "px",
                "alpha beta gamma delta epsilon zeta eta theta iota");

        assertTrue("more than one line", widths.size() > 1);

        // The last line of a justified block is left alone.
        for (int i = 0; i < widths.size() - 1; i++) {
            assertEquals("justified line " + i + " reaches the right edge",
                    (200 - letterSpacingPx) * PT_PER_PX, widths.get(i), TOLERANCE);
        }
    }

    /**
     * Word spacing composes with justification the same way.
     */
    @Test
    public void testJustifyWithWordSpacing() throws IOException {
        List<Float> widths = lineWidths(
                "width: 200px; text-align: justify; word-spacing: 5px",
                "alpha beta gamma delta epsilon zeta eta theta iota");

        assertTrue("more than one line", widths.size() > 1);

        for (int i = 0; i < widths.size() - 1; i++) {
            assertEquals("justified line " + i + " reaches the right edge",
                    200 * PT_PER_PX, widths.get(i), TOLERANCE);
        }
    }

    private static void assertAdvances(String what, float[] plain, float[] spaced, float[] expectedPx) {
        assertEquals(what + ": advance count", plain.length, spaced.length);
        assertEquals(what + ": advance count", expectedPx.length, spaced.length);

        for (int i = 0; i < expectedPx.length; i++) {
            assertEquals(what + " after character " + i,
                    expectedPx[i] * PT_PER_PX, spaced[i] - plain[i], TOLERANCE);
        }
    }

    /**
     * The distance from each glyph to the next, in points, for text laid out on a
     * single line.
     */
    private static float[] advances(String css, String text) throws IOException {
        List<List<TextPosition>> lines = lines(css, text);

        assertEquals("laid out on one line", 1, lines.size());

        List<TextPosition> glyphs = lines.get(0);
        float[] result = new float[glyphs.size() - 1];

        for (int i = 0; i < result.length; i++) {
            result[i] = glyphs.get(i + 1).getXDirAdj() - glyphs.get(i).getXDirAdj();
        }

        return result;
    }

    /**
     * The distance from the left edge of each line's first glyph to the right edge
     * of its last, in points.
     */
    private static List<Float> lineWidths(String css, String text) throws IOException {
        List<Float> result = new ArrayList<>();

        for (List<TextPosition> line : lines(css, text)) {
            TextPosition last = line.get(line.size() - 1);
            result.add(last.getXDirAdj() + last.getWidthDirAdj() - line.get(0).getXDirAdj());
        }

        return result;
    }

    /**
     * Renders the text in a paragraph carrying <code>css</code> and returns the
     * glyphs of each line, in order.
     */
    private static List<List<TextPosition>> lines(String css, String text) throws IOException {
        String html =
            "<html><head><style>" +
            "@page { size: " + PAGE_PX + "px " + PAGE_PX + "px; margin: 0; }" +
            "body { margin: 0; font-family: 'TestFont'; font-size: 20px; }" +
            // Justification will not push characters further apart than this, and
            // the default is tight enough to keep a line off the right edge.
            "p { margin: 0; -fs-max-justification-inter-char: 5mm; " + css + " }" +
            "</style></head><body><p>" + text + "</p></body></html>";

        ByteArrayOutputStream os = new ByteArrayOutputStream();

        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(os);
        builder.testMode(true);
        builder.useFont(() -> TextSpacingTest.class.getClassLoader().getResourceAsStream(
                "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"), "TestFont");
        builder.run();

        try (PDDocument doc = Loader.loadPDF(os.toByteArray())) {
            Map<Integer, List<TextPosition>> byLine = new LinkedHashMap<>();

            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String string, List<TextPosition> positions) {
                    for (TextPosition position : positions) {
                        byLine.computeIfAbsent(Math.round(position.getYDirAdj()),
                                key -> new ArrayList<>()).add(position);
                    }
                }
            };
            stripper.setSortByPosition(true);
            stripper.getText(doc);

            return new ArrayList<>(byLine.values());
        }
    }
}
