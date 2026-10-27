package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.KerningSubtable;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.openhtmltopdf.bidi.support.ICUBidiReorderer;
import com.openhtmltopdf.bidi.support.ICUBidiSplitter;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.testlistener.PrintingRunner;

/**
 * Tests pair kerning from a font's <code>kern</code> table or <code>GPOS</code>
 * table, switched on with {@link PdfRendererBuilder#useFontKerning(boolean)} or
 * the <code>font-kerning</code> property.
 *
 * <p>The expected values are read from the test font's kern table here, with
 * fontbox, rather than written down, and every test renders the same text with
 * and without kerning and compares the two.
 */
@RunWith(PrintingRunner.class)
public class FontKerningTest {
    private static final String TEST_FONT = "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";
    private static final String CJK_FONT = "visualtest/html/fonts/NotoSansJP-Regular.ttf";
    private static final String FUZZ_FONT = "demos/fonts/fuzz.ttf";
    // Kerning only in GPOS, no kern table.
    private static final String GPOS_FONT = "visualtest/html/fonts/SourceSansPro-Regular.ttf";

    private static final float PT_PER_PX = 0.75f;
    private static final float FONT_SIZE_PX = 20;
    private static final int PAGE_PX = 500;

    private static final float TOLERANCE = 0.01f;

    private static TrueTypeFont font;
    private static KerningSubtable kernPairs;
    private static CmapLookup cmap;

    @BeforeClass
    public static void readKernTable() throws IOException {
        try (InputStream in = resource(TEST_FONT)) {
            font = new TTFParser().parse(new RandomAccessReadBuffer(in));
        }
        kernPairs = font.getKerning().getHorizontalKerningSubtable();
        cmap = font.getUnicodeCmapLookup();

        // The tests are only meaningful if the font really kerns these pairs.
        assertTrue("AV is kerned tighter", kerningPt('A', 'V') < 0);
        assertTrue("To is kerned tighter", kerningPt('T', 'o') < 0);
    }

    /**
     * Each advance changes by exactly the kern value of its pair, and pairs the
     * table does not list keep their advance.
     */
    @Test
    public void testAdvancesChangeByTheKernValueOfEachPair() throws IOException {
        String text = "AVATAR Today";

        float[] plain = advances(false, "", text);
        float[] kerned = advances(true, "", text);

        assertEquals("advance count", plain.length, kerned.length);

        boolean anyKerned = false;
        for (int i = 0; i < plain.length; i++) {
            float expected = kerningPt(text.charAt(i), text.charAt(i + 1));
            anyKerned |= expected != 0;

            assertEquals("advance after '" + text.charAt(i) + "' (" + i + ")",
                    expected, kerned[i] - plain[i], TOLERANCE);
        }
        assertTrue("the text has kerned pairs", anyKerned);
    }

    /**
     * The width used for line breaking includes the kerning, so text that only
     * fits with kerning stays on one line with it and wraps without it.
     */
    @Test
    public void testLineWidthIncludesKerning() throws IOException {
        String text = "AVAVAV AVAVAV";

        float plainPt = lineWidths(false, "", text).get(0);
        float kernedPt = lineWidths(true, "", text).get(0);

        assertEquals("the line is narrower by the sum of its kerning",
                sumOfKerningPt(text), kernedPt - plainPt, TOLERANCE);

        String width = "width: " + ((plainPt + kernedPt) / 2 / PT_PER_PX) + "px";

        assertEquals("lines without kerning", 2, lines(false, width, text).size());
        assertEquals("lines with kerning", 1, lines(true, width, text).size());
    }

    /**
     * Kerning composes with justification: a justified line still ends exactly
     * at the right edge, so what was measured is what was drawn.
     */
    @Test
    public void testJustifiedLinesReachTheRightEdgeWithKerning() throws IOException {
        String css = "width: 200px; text-align: justify;";
        String text = "AVATAR Today WAVE Yoyo Tolly AVAVA VAT Toast Ty";

        List<Float> widths = lineWidths(true, css, text);

        assertTrue("more than one line", widths.size() > 1);

        for (int i = 0; i < widths.size() - 1; i++) {
            assertEquals("justified line " + i + " reaches the right edge",
                    200 * PT_PER_PX, widths.get(i), 0.05f);
        }
    }

    /**
     * A line is measured in pieces, one per break opportunity, but drawn as one
     * string, so a kerned pair across two pieces (here space-A) must be counted
     * in the line's width: a right-aligned line still ends at the right edge.
     */
    @Test
    public void testRightAlignedLineEndsAtTheRightEdge() throws IOException {
        String text = "A A A";
        String css = "width: 200px; text-align: right;";

        assertTrue("space-A is kerned", kerningPt(' ', 'A') != 0);

        for (boolean kerning : new boolean[] { false, true }) {
            List<Float> rightEdges = rightEdges(kerning, css, text);

            assertEquals("lines", 1, rightEdges.size());
            assertEquals("right edge, kerning " + kerning,
                    200 * PT_PER_PX, rightEdges.get(0), 0.1f);
        }
    }

    /**
     * Breaking a word anywhere measures it character by character; the kerned
     * pairs between those characters count towards what fits.
     */
    @Test
    public void testBreakWordFitsAsManyCharactersAsTheKernedWidthAllows() throws IOException {
        String text = "AVAVAVAV";
        float widthPx = 50;
        String css = "width: " + widthPx + "px; word-wrap: break-word;";

        int fitting = 0;
        while (fitting < text.length() &&
               kernedWidthPt(text.substring(0, fitting + 1)) <= widthPx * PT_PER_PX) {
            fitting++;
        }
        assertTrue("kerning lets one more character fit",
                fitting > 0 && unkernedWidthPt(text.substring(0, fitting)) > widthPx * PT_PER_PX);

        List<List<TextPosition>> lines = lines(true, css, text);

        assertEquals("characters on the first line", fitting, lines.get(0).size());
    }

    /**
     * With word-wrap: break-word one line can be filled by several breaking
     * passes (a word broken anywhere, then the next words), each measured on its
     * own; the pairs across those parts count too, so every right-aligned line
     * ends at the right edge.
     */
    @Test
    public void testRightAlignedBrokenWordLinesEndAtTheRightEdge() throws IOException {
        // "A " fits, then the long word is broken anywhere on the same line: the
        // pair space-A sits at the join of those two parts.
        String text = "A AVAVAVAVAVAV A";
        String css = "width: 50px; word-wrap: break-word; text-align: right;";

        List<Float> rightEdges = rightEdges(true, css, text);

        assertTrue("several lines", rightEdges.size() > 2);
        for (int i = 0; i < rightEdges.size(); i++) {
            assertEquals("right edge of line " + i, 50 * PT_PER_PX, rightEdges.get(i), 0.1f);
        }
    }

    /**
     * Right-to-left runs are not kerned, neither when measured nor when drawn:
     * they are measured in logical order but drawn reversed, so a kerned width
     * would not be the drawn one. A right-aligned run still ends at the edge,
     * and its glyphs sit where they sit without kerning.
     */
    @Test
    public void testRightToLeftRunsAreNotKerned() throws IOException {
        // "To" kerns only in logical order, "AVA" also drawn reversed (V-A, A-V).
        assertRightToLeftRunIsNotKerned("\u202ETo\u202C");
        assertRightToLeftRunIsNotKerned("\u202EAVA\u202C");
    }

    private static void assertRightToLeftRunIsNotKerned(String text) throws IOException {
        String css = "width: 200px; text-align: right;";

        List<List<TextPosition>> kerned = lines(true, css, text, true);
        List<List<TextPosition>> plain = lines(false, css, text, true);

        assertEquals("lines", 1, kerned.size());
        TextPosition last = kerned.get(0).get(kerned.get(0).size() - 1);
        assertEquals("right edge", 200 * PT_PER_PX, last.getXDirAdj() + last.getWidthDirAdj(), 0.1f);

        assertEquals("glyphs", plain.get(0).size(), kerned.get(0).size());
        for (int i = 0; i < plain.get(0).size(); i++) {
            assertEquals("glyph " + i + " where it sits without kerning",
                    plain.get(0).get(i).getXDirAdj(), kerned.get(0).get(i).getXDirAdj(), TOLERANCE);
        }
    }

    /**
     * Pairs touching a soft hyphen are never kerned, whether the line breaks
     * there or not, so a word broken anywhere is measured as it is drawn: every
     * right-aligned line ends at the edge.
     */
    @Test
    public void testThePairAroundASoftHyphenIsCountedOnce() throws IOException {
        String text = "AVA\u00ADVAVAV";
        String css = "width: 30px; word-wrap: break-word; text-align: right;";

        List<Float> rightEdges = rightEdges(true, css, text);

        assertTrue("more than one line", rightEdges.size() > 1);
        for (int i = 0; i < rightEdges.size(); i++) {
            assertEquals("right edge of line " + i, 30 * PT_PER_PX, rightEdges.get(i), 0.1f);
        }
    }

    /**
     * A shrink-to-fit box is sized with the same width its text is drawn with:
     * a right-to-left run (drawn unkerned) does not reach into what follows the
     * box, with kerning on as with it off.
     */
    @Test
    public void testRightToLeftInlineBlockDoesNotOverlapWhatFollows() throws IOException {
        String text = "<span style=\"display: inline-block;\">\u202EAVAVAVAV\u202C</span>X";

        for (boolean kerning : new boolean[] { false, true }) {
            List<TextPosition> glyphs = new ArrayList<>();
            lines(kerning, "", text, true).forEach(glyphs::addAll);
            TextPosition x = glyphs.stream().filter(g -> g.getUnicode().equals("X")).findFirst().get();
            List<TextPosition> run = glyphs.stream()
                    .filter(g -> g.getUnicode().equals("A") || g.getUnicode().equals("V"))
                    .collect(java.util.stream.Collectors.toList());
            float runEnd = (float) run.stream()
                    .mapToDouble(g -> g.getXDirAdj() + g.getWidthDirAdj()).max().getAsDouble();

            assertEquals("kerning " + kerning + ": the X stays on the run's line",
                    run.get(0).getYDirAdj(), x.getYDirAdj(), 0.05f);
            assertTrue("kerning " + kerning + ": the run ends at " + runEnd + ", the X starts at " + x.getXDirAdj(),
                    runEnd <= x.getXDirAdj() + 0.05f);
        }
    }

    /**
     * An auto-sized, right-aligned inline-block holding a right-to-left run puts
     * no glyph before its left edge.
     */
    @Test
    public void testRightToLeftShrinkToFitBoxHoldsItsRun() throws IOException {
        String text = "<span style=\"display: inline-block; text-align: right;\">\u202EAVAVAVAV\u202C</span>";

        for (boolean kerning : new boolean[] { false, true }) {
            List<TextPosition> glyphs = lines(kerning, "", text, true).get(0);
            float first = (float) glyphs.stream()
                    .filter(g -> g.getUnicode().equals("A") || g.getUnicode().equals("V"))
                    .mapToDouble(TextPosition::getXDirAdj).min().getAsDouble();

            assertEquals("kerning " + kerning + ": first glyph x", 0f, first, 0.05f);
        }
    }

    /**
     * A left-to-right run is kerned in a shrink-to-fit box too, and the box is
     * exactly as wide as the kerned run: what follows starts where it ends.
     */
    @Test
    public void testKernedInlineBlockIsAsWideAsItsKernedRun() throws IOException {
        String text = "<span style=\"display: inline-block;\">AVAVAVAV</span>X";

        List<TextPosition> glyphs = lines(true, "", text).get(0);
        TextPosition lastOfRun = glyphs.get(glyphs.size() - 2);
        TextPosition x = glyphs.get(glyphs.size() - 1);

        assertEquals("X", x.getUnicode());
        assertEquals("the run is kerned", kernedWidthPt("AVAVAVAV"),
                lastOfRun.getXDirAdj() + lastOfRun.getWidthDirAdj() - glyphs.get(0).getXDirAdj(), 0.05f);
        assertEquals("the X starts where the kerned run ends",
                lastOfRun.getXDirAdj() + lastOfRun.getWidthDirAdj(), x.getXDirAdj(), 0.1f);
    }

    /**
     * A line that keeps a soft hyphen it passed and breaks a word later is
     * measured and drawn alike: pairs touching a soft hyphen or the displayed
     * hyphen are never kerned.
     */
    @Test
    public void testSoftHyphenUnderBreakWordEndsAtTheEdge() throws IOException {
        String text = "A\u00ADVAVAV";
        String css = "width: 35px; word-wrap: break-word; text-align: right;";

        for (boolean kerning : new boolean[] { false, true }) {
            List<Float> rightEdges = rightEdges(kerning, css, text);
            for (int i = 0; i < rightEdges.size(); i++) {
                assertEquals("kerning " + kerning + ": right edge of line " + i,
                        35 * PT_PER_PX, rightEdges.get(i), 0.1f);
            }
        }
    }

    /**
     * List markers are never kerned (they are outside the kerning scope): a
     * marker is measured and drawn unkerned, so its glyphs sit where they sit
     * with kerning off and an outside marker still ends where the item's text
     * starts.
     */
    @Test
    public void testListMarkersAreNotKerned() throws IOException {
        String styles = "ul { margin: 0; padding-left: 40px; } li { list-style-type: 'AVAVAVAVAVAVAVAV'; }";
        String body = "<ul><li>X</li></ul>";

        List<TextPosition> plain = new ArrayList<>();
        linesOf(renderDocument(false, styles, body, false)).forEach(plain::addAll);
        List<TextPosition> kerned = new ArrayList<>();
        linesOf(renderDocument(true, styles, body, false)).forEach(kerned::addAll);

        assertEquals("glyphs", plain.size(), kerned.size());
        for (int i = 0; i < plain.size(); i++) {
            assertEquals("glyph " + i + " (" + plain.get(i).getUnicode() + ") where it sits without kerning",
                    plain.get(i).getXDirAdj(), kerned.get(i).getXDirAdj(), TOLERANCE);
        }

        TextPosition x = kerned.stream().filter(g -> g.getUnicode().equals("X")).findFirst().get();
        float markerEnd = (float) kerned.stream().filter(g -> !g.getUnicode().equals("X"))
                .mapToDouble(g -> g.getXDirAdj() + g.getWidthDirAdj()).max().getAsDouble();
        assertEquals("the marker ends where the text starts", x.getXDirAdj(), markerEnd, 0.05f);

        // An inside marker takes its laid-out width from the line, so the text
        // follows it where it follows it without kerning.
        String inside = "ul { margin: 0; padding-left: 40px; list-style-position: inside; } " +
                "li { list-style-type: 'AVAVAVAVAVAV'; }";
        List<TextPosition> insidePlain = new ArrayList<>();
        linesOf(renderDocument(false, inside, body, false)).forEach(insidePlain::addAll);
        List<TextPosition> insideKerned = new ArrayList<>();
        linesOf(renderDocument(true, inside, body, false)).forEach(insideKerned::addAll);

        assertEquals("inside: glyphs", insidePlain.size(), insideKerned.size());
        for (int i = 0; i < insidePlain.size(); i++) {
            assertEquals("inside: glyph " + i + " (" + insidePlain.get(i).getUnicode() + ")",
                    insidePlain.get(i).getXDirAdj(), insideKerned.get(i).getXDirAdj(), TOLERANCE);
        }

        // A ::marker with its own content: laid out with the marker width, and
        // that width is unkerned too.
        String pseudo = "ul { margin: 0; padding-left: 40px; } li::marker { content: 'AVAVAVAVAVAVAVAV'; }";
        List<TextPosition> pseudoPlain = new ArrayList<>();
        linesOf(renderDocument(false, pseudo, body, false)).forEach(pseudoPlain::addAll);
        List<TextPosition> pseudoKerned = new ArrayList<>();
        linesOf(renderDocument(true, pseudo, body, false)).forEach(pseudoKerned::addAll);

        assertEquals("::marker: glyphs", pseudoPlain.size(), pseudoKerned.size());
        for (int i = 0; i < pseudoPlain.size(); i++) {
            assertEquals("::marker: glyph " + i + " (" + pseudoPlain.get(i).getUnicode() + ")",
                    pseudoPlain.get(i).getXDirAdj(), pseudoKerned.get(i).getXDirAdj(), TOLERANCE);
        }
    }

    /**
     * The shrink-to-fit width ignores kerning at word joins: an auto-sized
     * inline-block is the sum of its separately measured words (each kerned
     * inside), so it differs from the drawn text by the kerning at the joins,
     * with its sign. Where a join pair is negative the box is wider than the
     * text; where it is positive the box is narrower, and the text then breaks
     * at that join inside the box.
     */
    @Test
    public void testShrinkToFitWidthIgnoresKerningAtWordJoins() throws IOException {
        // Liberation Sans: "A A A" is measured as "A ", "A ", "A"; the joins are
        // space-A twice, both negative, so the box is wider than the drawn text.
        String negative = "<span style=\"display: inline-block;\">A A A</span>X";
        List<TextPosition> plain = lines(false, "", negative).get(0);
        List<TextPosition> kerned = lines(true, "", negative).get(0);
        TextPosition x = kerned.get(kerned.size() - 1);
        TextPosition lastA = kerned.get(kerned.size() - 2);

        assertEquals("X", x.getUnicode());
        assertEquals("the box is the words, kerned inside (A-space twice)",
                plain.get(plain.size() - 1).getXDirAdj() + 2 * kerningPt('A', ' '), x.getXDirAdj(), 0.05f);
        assertEquals("wider than the drawn text by the join kerning (space-A twice)",
                -2 * kerningPt(' ', 'A'), x.getXDirAdj() - (lastA.getXDirAdj() + lastA.getWidthDirAdj()), 0.05f);

        // fuzz.ttf: "a,/a" is measured as "a," and "/a"; the join pair comma-slash
        // is positive, so the box is narrower than the kerned line and the text
        // breaks at that join.
        String positive = "<span style=\"display: inline-block; font-family: 'FuzzFont';\">a,/a</span>X";
        float commaSlash = fuzzKerningPt(',', '/');
        assertTrue("comma-slash is kerned apart", commaSlash > 0);

        List<TextPosition> fuzzPlain = new ArrayList<>();
        lines(false, "", positive).forEach(fuzzPlain::addAll);
        List<TextPosition> fuzzKerned = new ArrayList<>();
        lines(true, "", positive).forEach(fuzzKerned::addAll);
        float plainX = fuzzPlain.stream().filter(g -> g.getUnicode().equals("X")).findFirst().get().getXDirAdj();
        float kernedX = fuzzKerned.stream().filter(g -> g.getUnicode().equals("X")).findFirst().get().getXDirAdj();

        assertEquals("one line without kerning", 1, lines(false, "", positive).size());
        assertEquals("the box is the words, kerned inside (a-comma), without the join",
                plainX + fuzzKerningPt('a', ','), kernedX, 0.05f);
        assertEquals("the kerned line does not fit the box and breaks at the join", 2,
                lines(true, "", positive).size());
    }

    private static float fuzzKerningPt(char left, char right) throws IOException {
        try (InputStream in = resource(FUZZ_FONT)) {
            TrueTypeFont fuzz = new TTFParser().parse(new RandomAccessReadBuffer(in));
            CmapLookup fuzzCmap = fuzz.getUnicodeCmapLookup();
            int units = fuzz.getKerning().getHorizontalKerningSubtable()
                    .getKerning(fuzzCmap.getGlyphId(left), fuzzCmap.getGlyphId(right));
            return units * FONT_SIZE_PX * PT_PER_PX / fuzz.getUnitsPerEm();
        }
    }

    /**
     * Kerning composes with letter spacing: each advance gets both.
     */
    @Test
    public void testKerningAndLetterSpacingAddUp() throws IOException {
        String text = "AVAV";

        float[] plain = advances(false, "", text);
        float[] both = advances(true, "letter-spacing: 2px", text);

        for (int i = 0; i < plain.length; i++) {
            assertEquals("advance after character " + i,
                    2 * PT_PER_PX + kerningPt(text.charAt(i), text.charAt(i + 1)),
                    both[i] - plain[i], TOLERANCE);
        }
    }

    /**
     * When a character is not in the first font the text is drawn in font runs.
     * Pairs are kerned inside each run, and the runs after a kerned one move
     * with it.
     */
    @Test
    public void testFontRunsAreKernedAndPlacedWithTheirKerning() throws IOException {
        String text = "AVあAV";
        String css = "font-family: 'TestFont', 'CjkFont';";

        float[] plain = advances(false, css, text);
        float[] kerned = advances(true, css, text);

        float kern = kerningPt('A', 'V');

        // A-V, V-あ, あ-A, A-V
        assertEquals("first pair", kern, kerned[0] - plain[0], TOLERANCE);
        assertEquals("into the second run", 0, kerned[1] - plain[1], TOLERANCE);
        assertEquals("into the third run", 0, kerned[2] - plain[2], TOLERANCE);
        assertEquals("last pair", kern, kerned[3] - plain[3], TOLERANCE);
    }

    /**
     * Off is the default and draws exactly what is drawn without the setting.
     */
    @Test
    public void testOffByDefault() throws IOException {
        String text = "AVATAR Today";

        assertArrayEquals(pageContent(render(null, "", text)), pageContent(render(false, "", text)));
    }

    /**
     * With kerning on, text that has kerned pairs is drawn differently, so the
     * comparisons of drawn content in this class can tell the two apart.
     */
    @Test
    public void testKernedTextIsDrawnDifferently() throws IOException {
        String text = "AVATAR Today";

        assertFalse("drawn content differs", Arrays.equals(
                pageContent(render(false, "", text)), pageContent(render(true, "", text))));
    }

    /**
     * Text without a kerned pair is drawn exactly as without kerning: one plain
     * string, not a positioned array.
     */
    @Test
    public void testTextWithoutKernedPairsIsUnchanged() throws IOException {
        String text = "mill";

        assertEquals("no pair in the text is kerned", 0f, sumOfKerningPt(text), 0f);
        assertArrayEquals(pageContent(render(false, "", text)), pageContent(render(true, "", text)));
    }

    /**
     * <code>font-kerning: normal</code> kerns and <code>none</code> does not,
     * whatever the builder says; <code>auto</code>, the initial value, follows it.
     */
    @Test
    public void testFontKerningPropertyOverridesTheBuilder() throws IOException {
        String text = "AVATAR Today";

        float[] plain = advances(false, "", text);
        float[] kerned = advances(true, "", text);

        assertArrayEquals("normal, builder default", kerned, advancesOf(render(null, "font-kerning: normal;", text)), TOLERANCE);
        assertArrayEquals("normal, builder off", kerned, advancesOf(render(false, "font-kerning: normal;", text)), TOLERANCE);
        assertArrayEquals("none, builder on", plain, advancesOf(render(true, "font-kerning: none;", text)), TOLERANCE);
        assertArrayEquals("auto, builder on", kerned, advancesOf(render(true, "font-kerning: auto;", text)), TOLERANCE);
        assertArrayEquals("auto, builder default", plain, advancesOf(render(null, "font-kerning: auto;", text)), TOLERANCE);
    }

    /**
     * <code>font-kerning</code> is inherited, and the width used for line breaking
     * follows it, so a line only fits where it is kerned.
     */
    @Test
    public void testFontKerningPropertyIsInheritedAndMeasured() throws IOException {
        String text = "AVAVAV AVAVAV";

        float plainPt = lineWidths(false, "", text).get(0);
        float kernedPt = lineWidths(true, "", text).get(0);
        String width = "width: " + ((plainPt + kernedPt) / 2 / PT_PER_PX) + "px";

        String styles = "p { margin: 0; " + width + " } .on { font-kerning: normal; } .off { font-kerning: none; }";

        assertEquals("kerned by an ancestor", 1, linesOf(renderDocument(null, styles,
                "<div class=\"on\"><p>" + text + "</p></div>", false)).size());
        assertEquals("unkerned by an ancestor", 2, linesOf(renderDocument(true, styles,
                "<div class=\"off\"><p>" + text + "</p></div>", false)).size());
    }

    /**
     * A font whose kerning is only in its GPOS table is kerned. The expected
     * values are the font's own, as HarfBuzz applies them (units per em: 1000).
     */
    @Test
    public void testGposKerning() throws IOException {
        String css = "font-family: 'GposFont';";
        String text = "AVATAR Today";
        int[] units = { -14, -14, -55, -40, 0, 0, 0, -66, 0, 0, 0 };

        float[] plain = advances(false, css, text);
        float[] kerned = advances(true, css, text);

        assertEquals("advance count", units.length, kerned.length);

        for (int i = 0; i < units.length; i++) {
            assertEquals("advance after '" + text.charAt(i) + "' (" + i + ")",
                    units[i] * FONT_SIZE_PX * PT_PER_PX / 1000, kerned[i] - plain[i], TOLERANCE);
        }
    }

    private static float kerningPt(char left, char right) {
        int units = kernPairs.getKerning(cmap.getGlyphId(left), cmap.getGlyphId(right));
        try {
            return units * FONT_SIZE_PX * PT_PER_PX / font.getUnitsPerEm();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static float unkernedWidthPt(String text) {
        float units = 0;
        try {
            for (int i = 0; i < text.length(); i++) {
                units += font.getAdvanceWidth(cmap.getGlyphId(text.charAt(i)));
            }
            return units * FONT_SIZE_PX * PT_PER_PX / font.getUnitsPerEm();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static float kernedWidthPt(String text) {
        return unkernedWidthPt(text) + sumOfKerningPt(text);
    }

    private static float sumOfKerningPt(String text) {
        float sum = 0;
        for (int i = 0; i + 1 < text.length(); i++) {
            sum += kerningPt(text.charAt(i), text.charAt(i + 1));
        }
        return sum;
    }

    /**
     * The distance from each glyph to the next, in points, for text laid out on a
     * single line.
     */
    private static float[] advances(boolean kerning, String css, String text) throws IOException {
        return advancesOf(render(kerning, css, text));
    }

    private static float[] advancesOf(byte[] pdf) throws IOException {
        List<List<TextPosition>> lines = linesOf(pdf);

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
    private static List<Float> lineWidths(boolean kerning, String css, String text) throws IOException {
        List<Float> result = new ArrayList<>();

        for (List<TextPosition> line : lines(kerning, css, text)) {
            TextPosition last = line.get(line.size() - 1);
            result.add(last.getXDirAdj() + last.getWidthDirAdj() - line.get(0).getXDirAdj());
        }

        return result;
    }

    /**
     * The right edge of each line's last glyph, in points.
     */
    private static List<Float> rightEdges(boolean kerning, String css, String text) throws IOException {
        List<Float> result = new ArrayList<>();

        for (List<TextPosition> line : lines(kerning, css, text)) {
            TextPosition last = line.get(line.size() - 1);
            result.add(last.getXDirAdj() + last.getWidthDirAdj());
        }

        return result;
    }

    /**
     * The glyphs of each line, in order.
     */
    private static List<List<TextPosition>> lines(boolean kerning, String css, String text) throws IOException {
        return lines(kerning, css, text, false);
    }

    private static List<List<TextPosition>> lines(boolean kerning, String css, String text, boolean bidi) throws IOException {
        return linesOf(render(kerning, css, text, bidi));
    }

    private static List<List<TextPosition>> linesOf(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
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

    /**
     * Renders the text in a paragraph carrying <code>css</code>; a null
     * <code>kerning</code> leaves the builder at its default.
     */
    private static byte[] render(Boolean kerning, String css, String text) throws IOException {
        return render(kerning, css, text, false);
    }

    /**
     * As {@link #render(Boolean, String, String)}, with the ICU bidi splitter and
     * reorderer when <code>bidi</code> is set.
     */
    private static byte[] render(Boolean kerning, String css, String text, boolean bidi) throws IOException {
        return renderDocument(kerning, "p { margin: 0; -fs-max-justification-inter-char: 5mm; " + css + " }",
                "<p>" + text + "</p>", bidi);
    }

    /**
     * Renders a body with its own style rules, on the test page and font.
     */
    private static byte[] renderDocument(Boolean kerning, String styles, String body, boolean bidi) throws IOException {
        String html =
            "<html><head><style>" +
            "@page { size: " + PAGE_PX + "px " + PAGE_PX + "px; margin: 0; }" +
            "body { margin: 0; font-family: 'TestFont'; font-size: " + FONT_SIZE_PX + "px; }" +
            styles +
            "</style></head><body>" + body + "</body></html>";

        ByteArrayOutputStream os = new ByteArrayOutputStream();

        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(os);
        builder.testMode(true);
        builder.useFont(() -> resource(TEST_FONT), "TestFont");
        builder.useFont(() -> resource(CJK_FONT), "CjkFont");
        builder.useFont(() -> resource(FUZZ_FONT), "FuzzFont");
        builder.useFont(() -> resource(GPOS_FONT), "GposFont");
        if (kerning != null) {
            builder.useFontKerning(kerning);
        }
        if (bidi) {
            builder.useUnicodeBidiSplitter(new ICUBidiSplitter.ICUBidiSplitterFactory());
            builder.useUnicodeBidiReorderer(new ICUBidiReorderer());
        }
        builder.run();

        return os.toByteArray();
    }

    /**
     * The content stream of the first page: the drawing operators, without the
     * document metadata that differs from one run to the next.
     */
    private static byte[] pageContent(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf);
             InputStream contents = doc.getPage(0).getContents()) {
            return IOUtils.toByteArray(contents);
        }
    }

    private static InputStream resource(String path) {
        return FontKerningTest.class.getClassLoader().getResourceAsStream(path);
    }
}
