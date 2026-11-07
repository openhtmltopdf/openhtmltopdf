package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.openhtmltopdf.java2d.api.BufferedImagePageProcessor;
import com.openhtmltopdf.java2d.api.Java2DRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder.PdfAConformance;
import com.openhtmltopdf.testlistener.PrintingRunner;

/**
 * Tests the <code>opacity</code> property (#50) by the colors it paints, in the PDF
 * and in the Java2D output: an element and its content are composited as one group,
 * so where two of its children overlap the color is the same as where they do not,
 * as in browsers.
 *
 * <p>The page is 1 CSS pixel per image pixel; black at opacity 0.5 on white is 127.
 */
@RunWith(PrintingRunner.class)
public class OpacityTest {
    private static final String HTML =
        "<html><head><style>" +
        "@page { size: 200px 60px; margin: 0; }" +
        "body { margin: 0; height: 60px; }" +
        "div { position: absolute; }" +
        ".sq { width: 30px; height: 20px; background: #000; }" +
        "</style></head><body>" +
        // Two black squares overlapping at x 10-30, y 10-20, in one group at 0.5.
        "<div style='left: 0; top: 0; width: 40px; height: 30px; opacity: .5'>" +
        "  <div class='sq' style='left: 0; top: 0'></div><div class='sq' style='left: 10px; top: 10px'></div></div>" +
        // Opacity inside opacity multiplies.
        "<div style='left: 50px; top: 0; opacity: .5'><div style='position: static; opacity: .5; width: 20px; height: 20px; background: #000'></div></div>" +
        // Out of range values are clamped: 2 is opaque, -1 is transparent.
        "<div class='sq' style='left: 80px; top: 0; opacity: 2'></div>" +
        "<div class='sq' style='left: 120px; top: 0; opacity: -1'></div>" +
        // A percentage.
        "<div class='sq' style='left: 160px; top: 0; opacity: 25%'></div>" +
        "</body></html>";

    @Test
    public void testOverlappingChildrenAreCompositedAsOneGroupInThePdf() throws IOException {
        assertColors(renderPdf(PdfAConformance.NONE));
    }

    @Test
    public void testOverlappingChildrenAreCompositedAsOneGroupInJava2D() throws IOException {
        assertColors(renderJava2D());
    }

    /**
     * PDF/A-1 forbids transparency, so there the content is painted opaque, with no
     * transparency group; PDF/A-2 allows it.
     */
    @Test
    public void testPdfA1PaintsOpaqueWithoutGroups() throws IOException {
        byte[] pdfA1 = pdf(PdfAConformance.PDFA_1_B);
        assertFalse("PDF/A-1 has no transparency group", hasTransparencyGroup(pdfA1));
        assertEquals("PDF/A-1: the squares are black", 0, gray(render(pdfA1), 20, 15), 2);

        assertTrue("PDF/A-2 has a transparency group", hasTransparencyGroup(pdf(PdfAConformance.PDFA_2_B)));
    }

    private static void assertColors(BufferedImage image) {
        assertEquals("only the first square", 127, gray(image, 5, 5), 2);
        assertEquals("where the squares overlap", 127, gray(image, 20, 15), 2);
        assertEquals("only the second square", 127, gray(image, 35, 25), 2);
        assertEquals("opacity .5 inside opacity .5", 191, gray(image, 60, 10), 2);
        assertEquals("opacity 2 is opaque", 0, gray(image, 90, 10), 2);
        assertEquals("opacity -1 is transparent", 255, gray(image, 130, 10), 2);
        assertEquals("opacity 25%", 191, gray(image, 170, 10), 2);
    }

    private static int gray(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >> 8) & 0xff;
    }

    private static BufferedImage renderPdf(PdfAConformance conformance) throws IOException {
        return render(pdf(conformance));
    }

    private static BufferedImage render(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFRenderer(doc).renderImageWithDPI(0, 96);
        }
    }

    private static byte[] pdf(PdfAConformance conformance) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(HTML, null);
        builder.toStream(os);
        builder.testMode(true);
        if (conformance != PdfAConformance.NONE) {
            builder.usePdfAConformance(conformance);
            try (InputStream profile = OpacityTest.class.getResourceAsStream("/visualtest/colorspaces/sRGB.icc")) {
                builder.useColorProfile(IOUtils.toByteArray(profile));
            }
        }
        builder.run();
        return os.toByteArray();
    }

    private static boolean hasTransparencyGroup(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDResources resources = doc.getPage(0).getResources();
            for (COSName name : resources.getXObjectNames()) {
                PDXObject xObject = resources.getXObject(name);
                if (xObject instanceof PDFormXObject && ((PDFormXObject) xObject).getGroup() != null) {
                    return true;
                }
            }
            return false;
        }
    }

    private static BufferedImage renderJava2D() throws IOException {
        Java2DRendererBuilder builder = new Java2DRendererBuilder();
        builder.withHtmlContent(HTML, null);
        BufferedImagePageProcessor pages = new BufferedImagePageProcessor(BufferedImage.TYPE_INT_RGB, 1.0);
        builder.toSinglePage(pages).runFirstPage();
        return pages.getPageImages().get(0);
    }
}
