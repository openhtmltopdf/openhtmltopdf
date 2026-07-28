package com.openhtmltopdf.nonvisualregressiontests;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.extend.FSStreamFactory;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import com.openhtmltopdf.testlistener.PrintingRunner;
import com.openhtmltopdf.util.Diagnostic;
import com.openhtmltopdf.visualtest.TestSupport;

/**
 * Tests that a SVG is recognized as one whatever its URL looks like, in particular when it is
 * produced on the fly by a server rather than being a static file. Issue 170.
 *
 * <p>Such a URL carries a query string, <code>image.svg?param=value</code>, or gives nothing
 * away at all, so testing that it ends with <code>.svg</code> is not enough. The other half of
 * the issue is the plain, not base 64 encoded, <code>data:image/svg+xml,</code> URI.</p>
 *
 * <p>Checked by rendering to pixels, because what is at stake is whether the artwork arrives on
 * the page at all: an unrecognized SVG was handed to the raster decoder, which rejected it as an
 * unrecognized image format and drew nothing.</p>
 */
@RunWith(PrintingRunner.class)
public class SvgImageDetectionNonVisualTest {
    /** What our imaginary server returns: a red square filling whatever it is drawn into. */
    private static final String SVG =
            "<svg xmlns='http://www.w3.org/2000/svg' width='100' height='100'>" +
            "<rect x='0' y='0' width='100' height='100' fill='#ff0000'/></svg>";

    private static final byte[] SVG_BYTES = SVG.getBytes(StandardCharsets.UTF_8);

    @BeforeClass
    public static void configure() {
        TestSupport.quietLogs();
    }

    /**
     * Stands in for the server of the issue, handing out the same image for every URL, so that
     * what the URL looks like is all that is being tested.
     */
    private static class GeneratedImageServer implements FSStreamFactory {
        private final byte[] _content;

        GeneratedImageServer(byte[] content) {
            _content = content;
        }

        @Override
        public FSStream getUrl(String url) {
            return new FSStream() {
                @Override
                public InputStream getStream() {
                    return new ByteArrayInputStream(_content);
                }

                @Override
                public Reader getReader() {
                    return new InputStreamReader(getStream(), StandardCharsets.UTF_8);
                }
            };
        }
    }

    /**
     * A SVG file with a query string on it, which is the form the issue is about: the URL no
     * longer ends with the extension.
     */
    @Test
    public void testImgWithQueryStringIsDrawn() throws IOException {
        assertDrawn("<img src=\"https://example.com/image.svg?param=value\" />");
    }

    /**
     * The same for a background image, which goes through the image pipeline rather than
     * through the replaced element factory.
     */
    @Test
    public void testBackgroundImageWithQueryStringIsDrawn() throws IOException {
        assertDrawn("<div style=\"background-image: url('https://example.com/image.svg?param=value')\"></div>");
    }

    /**
     * A URL that says nothing about what it serves is decided by the content instead, so a
     * generator that does not bother to look like a file works too.
     */
    @Test
    public void testImgWithNoExtensionIsRecognizedByItsContent() throws IOException {
        assertDrawn("<img src=\"https://example.com/generate?shape=square\" />");
    }

    @Test
    public void testBackgroundImageWithNoExtensionIsRecognizedByItsContent() throws IOException {
        assertDrawn("<div style=\"background-image: url('https://example.com/generate?shape=square')\"></div>");
    }

    /**
     * The other half of the issue: the plain data URI, which a server can produce without
     * base 64 encoding anything.
     */
    @Test
    public void testImgWithPlainDataUriIsDrawn() throws IOException {
        // The raw < of the SVG is written as an entity, so that the src attribute the parser
        // hands us holds the document itself, exactly as in a browser.
        assertDrawn("<img src=\"data:image/svg+xml," + SVG.replace("\"", "'").replace("<", "&lt;") + "\" />");
    }

    /**
     * Sniffing the content must not take over images that are not SVGs: a raster image from
     * an equally opaque URL is still decoded as one.
     */
    @Test
    public void testRasterImageFromTheSameKindOfUrlIsUnaffected() throws IOException {
        assertDrawn("<img src=\"https://example.com/generate?shape=square\" />", pngSquare(), Color.RED);
    }

    /** Renders the body into a 100x100 page and returns the colour in the middle of it. */
    private void assertDrawn(String bodyHtml) throws IOException {
        assertDrawn(bodyHtml, SVG_BYTES, Color.RED);
    }

    private void assertDrawn(String bodyHtml, byte[] served, Color expected) throws IOException {
        String html =
                "<html><head><style>@page { size: 100px 100px; margin: 0; }" +
                "body { margin: 0; } img, div { width: 100px; height: 100px; }" +
                "</style></head><body>" + bodyHtml + "</body></html>";

        List<Diagnostic> logs = new ArrayList<>();
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, "https://example.com/");
        builder.useSVGDrawer(new BatikSVGDrawer());
        builder.useProtocolsStreamImplementation(new GeneratedImageServer(served), "http", "https");
        builder.withDiagnosticConsumer(logs::add);
        builder.toStream(os);
        builder.testMode(true);
        builder.run();

        Color actual;

        try (PDDocument doc = Loader.loadPDF(os.toByteArray())) {
            BufferedImage page = new PDFRenderer(doc).renderImageWithDPI(0, 96);
            actual = new Color(page.getRGB(page.getWidth() / 2, page.getHeight() / 2));
        }

        String messages = logs.stream().map(Diagnostic::getFormattedMessage).collect(Collectors.joining("\n"));

        assertThat(messages, not(containsString("Unrecognized image format")));
        assertEquals("the image should have been drawn", expected, actual);
    }

    /** A red PNG, so that a raster image and the SVG are told apart by nothing but their bytes. */
    private static byte[] pngSquare() {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);

        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                image.setRGB(x, y, Color.RED.getRGB());
            }
        }

        ByteArrayOutputStream os = new ByteArrayOutputStream();

        try {
            ImageIO.write(image, "png", os);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return os.toByteArray();
    }
}
