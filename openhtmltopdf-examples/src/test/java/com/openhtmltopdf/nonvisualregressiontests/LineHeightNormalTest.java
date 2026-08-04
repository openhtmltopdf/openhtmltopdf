package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FSFontUseCase;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.testlistener.PrintingRunner;
import com.openhtmltopdf.visualtest.TestSupport;

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

    /** Noto Sans JP, in 1/1000 em units, as PDFBox reports them. It asks for no line gap. */
    private static final float JP_ASCENT = 1160f;
    private static final float JP_DESCENT = 288f;

    /** Karla-Bold, in 1/1000 em units, with the line gap we write into it below. */
    private static final float KARLA_ASCENT = 917f;
    private static final float KARLA_DESCENT = 252f;
    private static final int KARLA_LINE_GAP = 200;

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
        float expectedEm = (ASCENT + DESCENT + LINE_GAP) / 1000f;

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
     * Every fallback font sits in every resolved font stack, whether or not a
     * glyph is ever drawn from it. Its line gap must therefore not be piled on
     * top of another font's ascent and descent: fonts trade the three off
     * against each other, so the largest of each taken on its own comes to a
     * taller line than either font asks for.
     *
     * <p>Here the fallback asks for a shorter line than the font in use, gap and
     * all, so it must not move the baselines at all. Reported on
     * <a href="https://github.com/openhtmltopdf/openhtmltopdf/pull/201">PR 201</a>,
     * where registering STIX Two Text as a fallback stretched lines set in Source
     * Sans Pro by a fifth.</p>
     */
    @Test
    public void testFallbackFontLineGapDoesNotStretchTheLine() throws IOException {
        File fallback = TestSupport.makeFontFileWithLineGap(
                "Karla-Bold.ttf", "Karla-Bold-FallbackGap.ttf", KARLA_LINE_GAP);

        assertTrue("the fallback asks for the shorter line",
                KARLA_ASCENT + KARLA_DESCENT + KARLA_LINE_GAP < JP_ASCENT + JP_DESCENT);

        float expectedEm = (JP_ASCENT + JP_DESCENT) / 1000f;

        float distance = baselineDistance("normal", builder -> {
            builder.useFont(() -> LineHeightNormalTest.class.getClassLoader().getResourceAsStream(
                    "visualtest/html/fonts/NotoSansJP-Regular.ttf"), "TestFont");
            builder.useFont(fallback, "FallbackFont", 400, FontStyle.NORMAL, true,
                    EnumSet.of(FSFontUseCase.FALLBACK_FINAL));
        });

        assertEquals("baseline to baseline distance",
                expectedEm * FONT_SIZE_PX * PT_PER_PX, distance, 0.05f);
    }

    private static float baselineDistance(String lineHeight) throws IOException {
        return baselineDistance(lineHeight, builder ->
            builder.useFont(() -> LineHeightNormalTest.class.getClassLoader().getResourceAsStream(
                    "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"), "TestFont"));
    }

    /**
     * Renders two lines and returns the distance between their baselines, in points.
     */
    private static float baselineDistance(String lineHeight, Consumer<PdfRendererBuilder> fonts) throws IOException {
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
        fonts.accept(builder);
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
