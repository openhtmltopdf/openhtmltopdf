package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.testlistener.PrintingRunner;

/**
 * Tests the synthetic bold the renderer falls back on when the font family holds
 * no face as heavy as the requested <code>font-weight</code>: it strokes the glyph
 * outlines on top of the fill, and the stroke has to grow with how much weight is
 * missing rather than jumping straight to full bold.
 *
 * <p>Rather than compare against a rendered page, each test reads the stroke width
 * the renderer actually asked for out of the content stream.
 */
@RunWith(PrintingRunner.class)
public class SyntheticBoldTest {
    private static final float FONT_SIZE_PT = 30;

    /**
     * The stroke a face three weight steps too light is fattened by, which is as
     * wide as the stroke ever gets.
     */
    private static final float FULL_STROKE_PT = FONT_SIZE_PT * 0.04f;

    private static final float TOLERANCE = 0.001f;

    /** Text rendering mode 2, fill then stroke. */
    private static final int FILL_STROKE = 2;

    /**
     * A face of the weight asked for needs no help.
     */
    @Test
    public void testNoSyntheticBoldWhenTheFaceIsHeavyEnough() throws IOException {
        assertEquals("font-weight: 400 on a 400 face", 0, strokeWidth(400, 400), TOLERANCE);
        assertEquals("font-weight: 700 on a 700 face", 0, strokeWidth(700, 700), TOLERANCE);
    }

    /**
     * A face lighter than the text asks for is stroked in proportion to the weight
     * it is missing, so the intermediate weights stay distinct from one another and
     * from bold. They used to share bold's stroke, which rendered
     * <code>font-weight: 500</code> every bit as heavy as <code>font-weight: 700</code>.
     */
    @Test
    public void testSyntheticBoldGrowsWithTheMissingWeight() throws IOException {
        assertEquals("font-weight: 500 on a 400 face",
                FULL_STROKE_PT / 3, strokeWidth(400, 500), TOLERANCE);
        assertEquals("font-weight: 600 on a 400 face",
                FULL_STROKE_PT * 2 / 3, strokeWidth(400, 600), TOLERANCE);
    }

    /**
     * Three weight steps is all the stroke makes up for; past that the glyphs would
     * drown in their own outline. Bold over a regular face, by far the most common
     * way into the synthetic bold, sits exactly on that ceiling, so documents that
     * only ever ask for bold keep the stroke they have always had.
     */
    @Test
    public void testSyntheticBoldStopsGrowingPastThreeSteps() throws IOException {
        assertEquals("font-weight: 700 on a 400 face",
                FULL_STROKE_PT, strokeWidth(400, 700), TOLERANCE);
        assertEquals("font-weight: 800 on a 400 face",
                FULL_STROKE_PT, strokeWidth(400, 800), TOLERANCE);
        assertEquals("font-weight: 900 on a 400 face",
                FULL_STROKE_PT, strokeWidth(400, 900), TOLERANCE);
    }

    /**
     * The gap that matters is the one between the face and the request, not the
     * weight asked for, so the same gap gives the same stroke higher up the scale.
     */
    @Test
    public void testSyntheticBoldMeasuresTheGapNotTheWeight() throws IOException {
        assertEquals("font-weight: 800 on a 700 face",
                FULL_STROKE_PT / 3, strokeWidth(700, 800), TOLERANCE);
        assertEquals("font-weight: 900 on a 700 face",
                FULL_STROKE_PT * 2 / 3, strokeWidth(700, 900), TOLERANCE);
    }

    /**
     * The stroke is a share of the font size, so it tracks the text it fattens.
     */
    @Test
    public void testSyntheticBoldScalesWithFontSize() throws IOException {
        assertTrue("a lighter face is stroked at all", strokeWidth(400, 500) > 0);

        assertEquals("half the font size, half the stroke",
                strokeWidth(400, 500) / 2,
                strokeWidth(400, 500, FONT_SIZE_PT / 2), TOLERANCE);
    }

    private static float strokeWidth(int faceWeight, int requestedWeight) throws IOException {
        return strokeWidth(faceWeight, requestedWeight, FONT_SIZE_PT);
    }

    /**
     * The width, in points, of the stroke the renderer lays over the text, or zero
     * if it draws the text fill-only.
     */
    private static float strokeWidth(int faceWeight, int requestedWeight, float fontSizePt)
            throws IOException {
        String html =
            "<html><head><style>" +
            "@page { size: 300pt 100pt; margin: 0; }" +
            "body { margin: 0; font-family: 'TestFont';" +
            " font-size: " + fontSizePt + "pt; font-weight: " + requestedWeight + " }" +
            "</style></head><body>Weight</body></html>";

        ByteArrayOutputStream os = new ByteArrayOutputStream();

        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(os);
        builder.testMode(true);
        builder.useFont(() -> SyntheticBoldTest.class.getClassLoader().getResourceAsStream(
                "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"),
                "TestFont", faceWeight, FontStyle.NORMAL, true);
        builder.run();

        try (PDDocument doc = Loader.loadPDF(os.toByteArray())) {
            return strokeWidthOfFirstText(doc.getPage(0));
        }
    }

    /**
     * Walks the page's operators until text is shown, then reports the stroke the
     * graphics state carried into it.
     */
    private static float strokeWidthOfFirstText(PDPage page) throws IOException {
        float lineWidth = 0;
        int renderingMode = 0;

        List<COSBase> operands = new ArrayList<>();
        PDFStreamParser parser = new PDFStreamParser(page);
        List<Object> tokens;

        try {
            tokens = parser.parse();
        } finally {
            parser.close();
        }

        for (Object token : tokens) {
            if (token instanceof COSBase) {
                operands.add((COSBase) token);
                continue;
            }

            String operator = ((Operator) token).getName();

            if ("w".equals(operator)) {
                lineWidth = ((COSNumber) operands.get(0)).floatValue();
            } else if ("Tr".equals(operator)) {
                renderingMode = ((COSNumber) operands.get(0)).intValue();
            } else if ("Tj".equals(operator) || "TJ".equals(operator)) {
                return renderingMode == FILL_STROKE ? lineWidth : 0;
            }

            operands.clear();
        }

        throw new AssertionError("the page drew no text");
    }
}
