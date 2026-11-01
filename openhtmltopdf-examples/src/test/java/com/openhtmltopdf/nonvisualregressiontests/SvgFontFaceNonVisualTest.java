package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.font.TextAttribute;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.batik.gvt.font.GVTFont;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import com.openhtmltopdf.testlistener.PrintingRunner;

/**
 * Tests that SVG text selects the right face of a family declared with several
 * <code>@font-face</code> rules. Batik looks a face up by weight and posture, so
 * the faces have to be registered under those values, and a request without an
 * exact match has to fall back predictably.
 *
 * <p>Noto Sans JP can display 日 and Karla cannot, which tells the two faces apart.</p>
 */
@RunWith(PrintingRunner.class)
public class SvgFontFaceNonVisualTest {
    private static final char NOTO_ONLY_CHAR = '日';

    private static final String NOTO_FONT_SRC = "url('fonts/NotoSansJP-Regular.ttf')";
    private static final String KARLA_FONT_SRC = "url('fonts/Karla-Bold.ttf')";

    @Test
    public void testExplicitWeightAndStyleMatchBatikLookup() throws IOException {
        BatikSVGDrawer svg = render(
                face(NOTO_FONT_SRC, "font-weight: 400; font-style: normal;"),
                face(KARLA_FONT_SRC, "font-weight: 700; font-style: normal;"));

        assertUsesNotoFace(svg, TextAttribute.WEIGHT_REGULAR, TextAttribute.POSTURE_REGULAR);
        assertUsesKarlaFace(svg, TextAttribute.WEIGHT_BOLD, TextAttribute.POSTURE_REGULAR);
    }

    @Test
    public void testOmittedWeightAndStyleDefaultToNormal() throws IOException {
        BatikSVGDrawer svg = render(
                face(NOTO_FONT_SRC, ""),
                face(KARLA_FONT_SRC, "font-weight: 700;"));

        assertUsesNotoFace(svg, TextAttribute.WEIGHT_REGULAR, TextAttribute.POSTURE_REGULAR);
        assertUsesKarlaFace(svg, TextAttribute.WEIGHT_BOLD, TextAttribute.POSTURE_REGULAR);
    }

    /**
     * A weight without a face of its own falls back as CSS font matching and HTML text
     * do: up to 500 the nearest lighter face first, above 500 the nearest heavier one.
     */
    @Test
    public void testMissingWeightUsesCssFontMatching() throws IOException {
        BatikSVGDrawer svg = render(
                face(NOTO_FONT_SRC, "font-weight: 400; font-style: normal;"),
                face(KARLA_FONT_SRC, "font-weight: 700; font-style: normal;"));

        assertUsesNotoFace(svg, TextAttribute.WEIGHT_LIGHT, TextAttribute.POSTURE_REGULAR);      // 200
        assertUsesNotoFace(svg, TextAttribute.WEIGHT_SEMIBOLD, TextAttribute.POSTURE_REGULAR);   // 500
        assertUsesKarlaFace(svg, TextAttribute.WEIGHT_MEDIUM, TextAttribute.POSTURE_REGULAR);    // 600
        assertUsesKarlaFace(svg, TextAttribute.WEIGHT_EXTRABOLD, TextAttribute.POSTURE_REGULAR);
    }

    /**
     * Batik asks 300 as DEMILIGHT and 600 as MEDIUM, so faces declared 200 and 300,
     * or 500 and 600, must stay apart.
     */
    @Test
    public void testNeighbouringWeightsKeepTheirOwnFaces() throws IOException {
        BatikSVGDrawer light = render(
                face(NOTO_FONT_SRC, "font-weight: 200;"),
                face(KARLA_FONT_SRC, "font-weight: 300;"));

        assertUsesNotoFace(light, TextAttribute.WEIGHT_LIGHT, TextAttribute.POSTURE_REGULAR);      // 200
        assertUsesKarlaFace(light, TextAttribute.WEIGHT_DEMILIGHT, TextAttribute.POSTURE_REGULAR); // 300

        BatikSVGDrawer medium = render(
                face(NOTO_FONT_SRC, "font-weight: 500;"),
                face(KARLA_FONT_SRC, "font-weight: 600;"));

        assertUsesNotoFace(medium, TextAttribute.WEIGHT_SEMIBOLD, TextAttribute.POSTURE_REGULAR);  // 500
        assertUsesKarlaFace(medium, TextAttribute.WEIGHT_MEDIUM, TextAttribute.POSTURE_REGULAR);   // 600
    }

    /**
     * 800 is HEAVY in Batik: it has to find a face declared 800, and above 500 prefer
     * the heavier face when there is none.
     */
    @Test
    public void testHeavyWeights() throws IOException {
        BatikSVGDrawer exact = render(
                face(NOTO_FONT_SRC, "font-weight: 800;"),
                face(KARLA_FONT_SRC, "font-weight: 900;"));

        assertUsesNotoFace(exact, TextAttribute.WEIGHT_HEAVY, TextAttribute.POSTURE_REGULAR);      // 800
        assertUsesKarlaFace(exact, TextAttribute.WEIGHT_ULTRABOLD, TextAttribute.POSTURE_REGULAR); // 900

        BatikSVGDrawer fallback = render(
                face(NOTO_FONT_SRC, "font-weight: 600;"),
                face(KARLA_FONT_SRC, "font-weight: 900;"));

        assertUsesKarlaFace(fallback, TextAttribute.WEIGHT_HEAVY, TextAttribute.POSTURE_REGULAR);  // 800
    }

    @Test
    public void testExplicitItalicFaceMatchesBatikLookup() throws IOException {
        BatikSVGDrawer svg = render(
                face(NOTO_FONT_SRC, "font-weight: 400; font-style: italic;"),
                face(KARLA_FONT_SRC, "font-weight: 700; font-style: normal;"));

        assertUsesNotoFace(svg, TextAttribute.WEIGHT_REGULAR, TextAttribute.POSTURE_OBLIQUE);
        assertUsesKarlaFace(svg, TextAttribute.WEIGHT_BOLD, TextAttribute.POSTURE_REGULAR);
    }

    @Test
    public void testPostureIsPreferredOverWeight() throws IOException {
        BatikSVGDrawer svg = render(
                face(NOTO_FONT_SRC, "font-weight: 400; font-style: normal;"),
                face(KARLA_FONT_SRC, "font-weight: 700; font-style: italic;"));

        assertUsesKarlaFace(svg, TextAttribute.WEIGHT_REGULAR, TextAttribute.POSTURE_OBLIQUE);
        assertUsesNotoFace(svg, TextAttribute.WEIGHT_BOLD, TextAttribute.POSTURE_REGULAR);
    }

    private static String face(String src, String descriptors) {
        return "@font-face { font-family: 'Test'; src: " + src + "; " + descriptors + " }";
    }

    private static BatikSVGDrawer render(String... faces) throws IOException {
        String html =
                "<html><head><style>" + String.join("", faces) + "</style></head><body>" +
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"50\">" +
                "<text x=\"0\" y=\"20\" font-family=\"Test\">Regular</text>" +
                "<text x=\"0\" y=\"40\" font-family=\"Test\" font-weight=\"bold\">Bold</text>" +
                "</svg></body></html>";

        BatikSVGDrawer svg = new BatikSVGDrawer();

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, Objects.requireNonNull(
                    SvgFontFaceNonVisualTest.class.getResource("/visualtest/html/")).toString());
            builder.useSVGDrawer(svg);
            builder.toStream(os);
            builder.run();
        }

        return svg;
    }

    private static void assertUsesNotoFace(BatikSVGDrawer svg, float weight, float posture) {
        assertTrue(message(weight, posture, "Noto Sans JP"), font(svg, weight, posture).canDisplay(NOTO_ONLY_CHAR));
    }

    private static void assertUsesKarlaFace(BatikSVGDrawer svg, float weight, float posture) {
        assertFalse(message(weight, posture, "Karla"), font(svg, weight, posture).canDisplay(NOTO_ONLY_CHAR));
    }

    private static String message(float weight, float posture, String expectedFace) {
        return "weight " + weight + ", posture " + posture + " should use the " + expectedFace + " face";
    }

    private static GVTFont font(BatikSVGDrawer svg, float weight, float posture) {
        return svg.fontResolver.resolve("Test").deriveFont(12, attributes(weight, posture));
    }

    private static Map<TextAttribute, Object> attributes(float weight, float posture) {
        Map<TextAttribute, Object> attributes = new HashMap<>();
        attributes.put(TextAttribute.WEIGHT, weight);
        attributes.put(TextAttribute.POSTURE, posture);
        return attributes;
    }
}
