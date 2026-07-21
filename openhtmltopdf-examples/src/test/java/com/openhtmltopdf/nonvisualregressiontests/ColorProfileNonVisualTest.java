package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder.PdfAConformance;
import com.openhtmltopdf.testlistener.PrintingRunner;
import com.openhtmltopdf.visualtest.TestSupport;

/**
 * Tests that a color profile set via useColorProfile is written as an
 * OutputIntent: also for plain PDFs, and exactly once for PDF/A documents.
 */
@RunWith(PrintingRunner.class)
public class ColorProfileNonVisualTest {

    private static final String HTML =
            "<html><body><div style=\"background-color: rgba(255, 0, 0, 0.5); width: 100px; height: 20px;\"></div></body></html>";

    @BeforeClass
    public static void configure() {
        TestSupport.quietLogs();
    }

    private static byte[] srgbProfile() throws IOException {
        try (InputStream is = ColorProfileNonVisualTest.class.getResourceAsStream("/visualtest/colorspaces/sRGB.icc")) {
            return IOUtils.toByteArray(is);
        }
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

    private static int outputIntentCount(PDDocument doc) {
        return doc.getDocumentCatalog().getOutputIntents().size();
    }

    @Test
    public void outputIntentWrittenWithoutPdfA() throws IOException {
        try (PDDocument doc = render(builder -> {
            try {
                builder.useColorProfile(srgbProfile());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        })) {
            assertEquals(1, outputIntentCount(doc));
        }
    }

    @Test
    public void noOutputIntentWithoutColorProfile() throws IOException {
        try (PDDocument doc = render(builder -> { })) {
            assertEquals(0, outputIntentCount(doc));
        }
    }

    @Test
    public void exactlyOneOutputIntentInPdfA2() throws IOException {
        try (PDDocument doc = render(builder -> {
            try {
                builder.usePdfAConformance(PdfAConformance.PDFA_2_B);
                builder.useColorProfile(srgbProfile());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        })) {
            assertEquals(1, outputIntentCount(doc));
        }
    }
}
