package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder.PdfAConformance;
import com.openhtmltopdf.testlistener.PrintingRunner;
import com.openhtmltopdf.visualtest.TestSupport;

/**
 * Tests that the alpha of rgba()/hsla() colors is written to the PDF as
 * ExtGState alpha constants (/ca and /CA), reset for opaque content
 * following transparent content, and suppressed for PDF/A-1 which does not
 * allow transparency.
 */
@RunWith(PrintingRunner.class)
public class AlphaColorNonVisualTest {

    // A semi-transparent background followed by an opaque one (alpha must be
    // reset), plus a dashed border which is stroked rather than filled.
    private static final String HTML =
            "<html><body>" +
            "<div style=\"background-color: rgba(255, 0, 0, 0.5); width: 100px; height: 20px;\"></div>" +
            "<div style=\"background-color: #0000ff; width: 100px; height: 20px;\"></div>" +
            "<div style=\"border: 2px dashed hsla(120, 100%, 25%, 0.25); width: 100px; height: 20px;\"></div>" +
            "</body></html>";

    @BeforeClass
    public static void configure() {
        TestSupport.quietLogs();
    }

    private static PDDocument render(Consumer<PdfRendererBuilder> extraConfig) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(HTML, null);
        builder.toStream(os);
        builder.testMode(true);
        extraConfig.accept(builder);
        builder.run();

        return Loader.loadPDF(os.toByteArray());
    }

    private static List<Float> alphaConstants(PDDocument doc, boolean stroking) throws IOException {
        List<Float> result = new ArrayList<>();
        PDResources resources = doc.getPage(0).getResources();

        for (COSName name : resources.getExtGStateNames()) {
            PDExtendedGraphicsState gs = resources.getExtGState(name);
            Float alpha = stroking ? gs.getStrokingAlphaConstant() : gs.getNonStrokingAlphaConstant();
            if (alpha != null) {
                result.add(alpha);
            }
        }

        return result;
    }

    @Test
    public void alphaIsWrittenAndResetForOpaqueContent() throws IOException {
        try (PDDocument doc = render(builder -> { })) {
            List<Float> fillAlphas = alphaConstants(doc, false);

            assertTrue("expected /ca 0.5 for rgba background, got " + fillAlphas,
                    fillAlphas.contains(0.5f));
            assertTrue("expected /ca 1 resetting alpha for following opaque content, got " + fillAlphas,
                    fillAlphas.contains(1f));
        }
    }

    @Test
    public void strokeAlphaIsWrittenForDashedBorder() throws IOException {
        try (PDDocument doc = render(builder -> { })) {
            List<Float> strokeAlphas = alphaConstants(doc, true);

            assertTrue("expected /CA 0.25 for hsla dashed border, got " + strokeAlphas,
                    strokeAlphas.contains(0.25f));
        }
    }

    @Test
    public void alphaIsSuppressedInPdfA1() throws IOException {
        try (PDDocument doc = render(builder -> builder.usePdfAConformance(PdfAConformance.PDFA_1_B))) {
            assertTrue("PDF/A-1 does not allow transparency, no alpha ExtGState may be written",
                    alphaConstants(doc, false).isEmpty() && alphaConstants(doc, true).isEmpty());
        }
    }

    @Test
    public void alphaIsKeptInPdfA2() throws IOException {
        try (PDDocument doc = render(builder -> builder.usePdfAConformance(PdfAConformance.PDFA_2_B))) {
            assertTrue("PDF/A-2 allows transparency, expected /ca 0.5",
                    alphaConstants(doc, false).contains(0.5f));
        }
    }
}
