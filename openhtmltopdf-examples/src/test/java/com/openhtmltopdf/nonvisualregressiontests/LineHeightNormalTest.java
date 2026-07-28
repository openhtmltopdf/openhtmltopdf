package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.testlistener.PrintingRunner;

/**
 * Tests that <code>line-height: normal</code> spaces lines the way browsers do,
 * by the font's own design metrics rather than a fixed multiple of the font size.
 *
 * @see <a href="https://github.com/openhtmltopdf/openhtmltopdf/issues/42">Issue 42</a>
 */
@RunWith(PrintingRunner.class)
public class LineHeightNormalTest {
    /** Liberation Sans, in 1/1000 em units, as PDFBox reports them. */
    private static final float ASCENT = 1854f * 1000 / 2048;
    private static final float DESCENT = 434f * 1000 / 2048;
    private static final float LINE_GAP = 67f * 1000 / 2048;

    /** The built-in serif font is always appended as the last-resort fallback. */
    private static final float BUILTIN_SERIF_DESCENT = 217f;

    private static final float FONT_SIZE_PX = 16f;
    private static final float PT_PER_PX = 0.75f;

    /**
     * The baselines of consecutive lines must be a font line's worth apart:
     * ascent plus descent plus the line gap the font asks for. Before the line
     * gap was honoured, lines set in fonts that ask for one (such as Liberation
     * Sans, Arial and Times New Roman) came out around 3% tighter than in a browser.
     */
    @Test
    public void testNormalUsesAscentDescentAndLineGap() throws IOException {
        float expectedEm = (ASCENT + Math.max(DESCENT, BUILTIN_SERIF_DESCENT) + LINE_GAP) / 1000f;

        assertEquals("baseline to baseline distance",
                expectedEm * FONT_SIZE_PX * PT_PER_PX, baselineDistance("normal"), 0.05f);
    }

    /**
     * A number is still a plain multiple of the font size, and stays well clear
     * of the font's design metrics.
     */
    @Test
    public void testNumberIsAMultipleOfTheFontSize() throws IOException {
        assertEquals("baseline to baseline distance",
                1.5f * FONT_SIZE_PX * PT_PER_PX, baselineDistance("1.5"), 0.05f);
    }

    /**
     * Renders two lines and returns the distance between their baselines, in points.
     */
    private static float baselineDistance(String lineHeight) throws IOException {
        String html =
            "<html><head><style>" +
            "@page { size: 400px 400px; margin: 0; }" +
            "body { margin: 0; font-family: 'TestFont'; font-size: " + FONT_SIZE_PX + "px; }" +
            "p { margin: 0; line-height: " + lineHeight + "; }" +
            "</style></head><body>" +
            "<p>First line<br/>Second line</p>" +
            "</body></html>";

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(os);
        builder.testMode(true);
        builder.useFont(() -> LineHeightNormalTest.class.getClassLoader().getResourceAsStream(
                "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"), "TestFont");
        builder.run();

        try (PDDocument doc = Loader.loadPDF(os.toByteArray())) {
            List<Float> baselines = new ArrayList<>();

            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    if (!positions.isEmpty()) {
                        baselines.add(positions.get(0).getYDirAdj());
                    }
                }
            };
            stripper.setSortByPosition(true);
            stripper.getText(doc);

            assertEquals("lines found", 2, baselines.size());
            return baselines.get(1) - baselines.get(0);
        }
    }
}
