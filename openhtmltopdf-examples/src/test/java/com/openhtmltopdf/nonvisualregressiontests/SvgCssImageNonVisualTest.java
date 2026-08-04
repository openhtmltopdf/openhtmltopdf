package com.openhtmltopdf.nonvisualregressiontests;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import com.openhtmltopdf.testlistener.PrintingRunner;
import com.openhtmltopdf.util.Diagnostic;
import com.openhtmltopdf.util.OpenUtil;
import com.openhtmltopdf.visualtest.TestSupport;

/**
 * Tests for SVGs used as CSS images, ie. as a <code>background-image</code>, rather than as a
 * replaced <code>svg</code> element. Issue 32.
 *
 * <p>The visual side of this is covered by the visual regression tests. What is checked here is
 * that the artwork ends up in the document once rather than once per tile, and that a document
 * that asks for a SVG image without registering a SVG drawer is told what is wrong.</p>
 */
@RunWith(PrintingRunner.class)
public class SvgCssImageNonVisualTest {
    /** A 20x20 circle, percent encoded the way an inline CSS image usually is. */
    private static final String SVG_DATA_URI =
            "data:image/svg+xml,%3Csvg%20xmlns='http://www.w3.org/2000/svg'%20width='20'%20height='20'" +
            "%3E%3Ccircle%20cx='10'%20cy='10'%20r='8'%20fill='%23cc0000'/%3E%3C/svg%3E";

    private static final String TILED_HTML =
            "<html><head><style>@page { size: 200px 200px; margin: 0; }" +
            "div { width: 200px; height: 200px; background-image: url(\"" + SVG_DATA_URI + "\"); }" +
            "</style></head><body><div></div></body></html>";

    @BeforeClass
    public static void configure() {
        TestSupport.quietLogs();
    }

    private static PDDocument render(String html, Consumer<PdfRendererBuilder> extraConfig) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(os);
        builder.testMode(true);
        extraConfig.accept(builder);
        builder.run();

        return Loader.loadPDF(os.toByteArray());
    }

    private static List<PDFormXObject> formsOnPage(PDDocument doc, int pageNo) throws IOException {
        List<PDFormXObject> forms = new ArrayList<>();
        PDResources resources = doc.getPage(pageNo).getResources();

        for (COSName name : resources.getXObjectNames()) {
            PDXObject xobject = resources.getXObject(name);

            if (xobject instanceof PDFormXObject) {
                forms.add((PDFormXObject) xobject);
            }
        }

        return forms;
    }

    /**
     * A repeating SVG background covers the box with a hundred tiles, but the artwork itself
     * must only be written to the document once and then stamped, rather than copied per tile.
     */
    @Test
    public void testTiledBackgroundUsesOneFormObject() throws IOException {
        try (PDDocument doc = render(TILED_HTML, builder -> builder.useSVGDrawer(new BatikSVGDrawer()))) {
            List<PDFormXObject> forms = formsOnPage(doc, 0);

            assertEquals("the artwork should be in the document exactly once", 1, forms.size());

            // 200x200 box tiled with a 20x20 image, so the one form is placed a hundred times.
            String content = pageContent(doc, 0);
            String formName = doc.getPage(0).getResources().getXObjectNames().iterator().next().getName();

            assertEquals(100, countOccurrences(content, "/" + formName + " Do"));
        }
    }

    /**
     * A form object is reused across pages too, so a background that runs over several pages
     * has to still be placed in the resources of every page it appears on.
     */
    @Test
    public void testBackgroundOnSecondPageStillDrawn() throws IOException {
        String html =
                "<html><head><style>@page { size: 200px 200px; margin: 0; }" +
                "div { width: 200px; height: 350px; background-image: url(\"" + SVG_DATA_URI + "\");" +
                " background-repeat: no-repeat; }" +
                "</style></head><body><div></div></body></html>";

        try (PDDocument doc = render(html, builder -> builder.useSVGDrawer(new BatikSVGDrawer()))) {
            assertEquals(2, doc.getNumberOfPages());

            for (int page = 0; page < 2; page++) {
                assertEquals("page " + page + " should carry the artwork", 1, formsOnPage(doc, page).size());

                String content = pageContent(doc, page);
                String formName = doc.getPage(page).getResources().getXObjectNames().iterator().next().getName();

                assertEquals("page " + page + " should draw the artwork",
                        1, countOccurrences(content, "/" + formName + " Do"));
            }
        }
    }

    /**
     * Without a SVG drawer there is no way to draw the image. That used to be reported as an
     * unrecognized image format, which sent people looking in the wrong place.
     *
     * <p>Said once for the image, not once for every box it is painted in: an unusable image
     * is remembered, so a background on a multi page document does not flood the log or
     * fetch the same resource over and over.</p>
     */
    @Test
    public void testMissingSvgDrawerIsReportedOnce() throws IOException {
        // Three pages worth of background, so a per-paint warning would show up as three.
        String html =
                "<html><head><style>@page { size: 200px 200px; margin: 0; }" +
                "div { width: 200px; height: 550px; background-image: url(\"" + SVG_DATA_URI + "\"); }" +
                "</style></head><body><div></div></body></html>";

        List<Diagnostic> logs = new ArrayList<>();

        try (PDDocument doc = render(html, builder -> builder.withDiagnosticConsumer(logs::add))) {
            assertEquals(3, doc.getNumberOfPages());
            assertThat(formsOnPage(doc, 0).size(), equalTo(0));
        }

        List<String> messages = logs.stream()
                .map(Diagnostic::getFormattedMessage)
                .collect(Collectors.toList());

        assertEquals(1, messages.stream().filter(m -> m.contains("no SVG drawer is registered")).count());
        assertThat(String.join("\n", messages), not(containsString("Unrecognized image format")));
    }

    /**
     * A length in an absolute unit gives the SVG a size of its own, so the background draws at
     * that size rather than filling the box. Covers the units a browser resolves without
     * knowing anything about the surroundings.
     */
    @Test
    public void testAbsolutelySizedSvgKeepsItsOwnSize() throws IOException {
        // 1in = 96px, 1cm = 96/2.54px, 12pt = 16px, so all three are their own size.
        assertEquals(96, backgroundImageSize("width='1in'%20height='1in'").width);
        assertEquals(16, backgroundImageSize("width='12pt'%20height='12pt'").width);
        assertEquals(38, backgroundImageSize("width='1cm'%20height='1cm'").width);
        assertEquals(20, backgroundImageSize("width='20'%20height='20'").width);
        assertEquals(20, backgroundImageSize("width='20px'%20height='20px'").width);
    }

    /**
     * A SVG written in percentages has no size of its own. A browser then sizes it from CSS,
     * so with no background-size it fills the area it is painted into rather than coming out
     * at some made up default. Issue 32.
     */
    @Test
    public void testPercentageSizedSvgFillsTheBox() throws IOException {
        // The box is 100x50, and the image has no size and no ratio, so it fills it.
        Dimension size = backgroundImageSize("width='50%25'%20height='50%25'");

        assertEquals(100, size.width);
        assertEquals(50, size.height);
    }

    /**
     * The same for a SVG with no width and height at all, which is how icons are usually
     * written. The viewBox gives it a ratio, so it is made as large as it can be inside the
     * box without changing shape, rather than being drawn at the viewBox size.
     */
    @Test
    public void testUnsizedSvgWithViewBoxKeepsItsRatio() throws IOException {
        // A 2:1 viewBox inside a 100x50 box comes out exactly 100x50.
        Dimension wide = backgroundImageSize("viewBox='0%200%2040%2020'");
        assertEquals(100, wide.width);
        assertEquals(50, wide.height);

        // A 1:1 viewBox in the same box is limited by the height.
        Dimension square = backgroundImageSize("viewBox='0%200%2020%2020'");
        assertEquals(50, square.width);
        assertEquals(50, square.height);
    }

    /**
     * background-size still wins over all of that, for a SVG with a size and without.
     */
    @Test
    public void testBackgroundSizeWinsOverTheDefaultObjectSize() throws IOException {
        assertEquals(80, backgroundImageSize("width='50%25'%20height='50%25'", "background-size: 80px 20px;").width);
        assertEquals(80, backgroundImageSize("width='20'%20height='20'", "background-size: 80px 20px;").width);
    }

    /**
     * A SVG with a size of its own but no viewBox is a picture, and CSS scales a picture to
     * the size it asks for - stretching it when that size has another shape, exactly as it
     * would a PNG. The artwork inside has to follow, not just the box around it.
     *
     * <p>Batik will only ever rescale such a SVG uniformly, so background-size used to be able
     * to set the box and yet leave the drawing at the size and shape it was written at.</p>
     */
    @Test
    public void testSizedSvgWithoutViewBoxIsStretchedByBackgroundSize() throws IOException {
        // Artwork in fixed user units, so it can only cover the box if it is stretched too.
        String artwork = "%3Crect%20x='0'%20y='0'%20width='10'%20height='10'%20fill='%23cc0000'/%3E";

        assertEquals("the drawing should be stretched to the whole background-size",
                new Rectangle(0, 0, 100, 50),
                paintedArea("width='10'%20height='10'", artwork, "background-size: 100% 100%;"));

        // The same SVG left at its own size is untouched.
        assertEquals(new Rectangle(0, 0, 10, 10),
                paintedArea("width='10'%20height='10'", artwork, ""));
    }

    /**
     * The counterpart: a viewBox says how the drawing maps onto whatever size it is given, so
     * preserveAspectRatio decides, and by default the SVG is fitted rather than stretched.
     * A browser does the same, so this must not be "fixed" into stretching.
     */
    @Test
    public void testSvgWithViewBoxIsFittedRatherThanStretched() throws IOException {
        String artwork = "%3Crect%20x='0'%20y='0'%20width='10'%20height='10'%20fill='%23cc0000'/%3E";
        String size = "background-size: 100% 100%;";

        // A square drawing in a 100x50 box: 50x50, centred, because preserveAspectRatio
        // defaults to xMidYMid meet.
        assertEquals(new Rectangle(25, 0, 50, 50),
                paintedArea("width='10'%20height='10'%20viewBox='0%200%2010%2010'", artwork, size));

        // Unless the SVG says not to fit it.
        assertEquals(new Rectangle(0, 0, 100, 50),
                paintedArea("width='10'%20height='10'%20viewBox='0%200%2010%2010'" +
                            "%20preserveAspectRatio='none'", artwork, size));
    }

    /**
     * The shape this turned up in: a fixed width bar down the side of a table cell, drawn with
     * <code>background-size: 5mm 100%</code>. When the cell ran to several lines the bar kept
     * the height of the drawing instead of growing with the cell.
     */
    @Test
    public void testBackgroundSizeFillsTheHeightOfATableCell() throws IOException {
        String svg = "data:image/svg+xml,%3Csvg%20xmlns='http://www.w3.org/2000/svg'%20width='10'%20height='10'" +
                "%3E%3Crect%20x='0'%20y='0'%20width='10'%20height='10'%20fill='%23cc0000'/%3E%3C/svg%3E";
        String html =
                "<html><head><style>@page { size: 300px 300px; margin: 0; }" +
                "body { margin: 0; } table { border-collapse: collapse; width: 200px; }" +
                "td { padding: 0; font: 20px/1.5 sans-serif; background-repeat: no-repeat;" +
                " background-size: 5mm 100%; background-image: url(\"" + svg + "\"); }" +
                "</style></head><body><table><tr><td>one<br/>two<br/>three</td></tr></table></body></html>";

        Rectangle painted = paintedArea(html);

        // Three lines of 20px/1.5 make the cell 90px tall, and the bar has to grow with it.
        assertEquals("the bar should start at the top of the cell", 0, painted.y);
        assertEquals("the bar should fill the height of the cell", 90, painted.height);

        // 5mm is 18.9px at 96dpi, so the bar reaches into a nineteenth, nine tenths covered,
        // pixel column, rather than stopping at the whole pixel inside it.
        assertEquals("the bar should be a full 5mm wide", 19, painted.width);
    }

    /**
     * A percentage is normal and is not complained about, but a unit we can not resolve
     * although a browser could, such as em, is worth saying out loud.
     */
    @Test
    public void testOnlyUnresolvableUnitsAreReported() throws IOException {
        assertThat(warningsFor("width='50%25'%20height='50%25'"), not(containsString("Can not use the")));
        assertThat(warningsFor("viewBox='0%200%2020%2020'"), not(containsString("Can not use the")));
        assertThat(warningsFor("width='2em'%20height='2em'"), containsString("Can not use the width (2em)"));
    }

    /** The size a background image is actually drawn at, in CSS pixels. */
    private Dimension backgroundImageSize(String svgAttributes) throws IOException {
        return backgroundImageSize(svgAttributes, "");
    }

    private Dimension backgroundImageSize(String svgAttributes, String extraCss) throws IOException {
        String svg = "data:image/svg+xml,%3Csvg%20xmlns='http://www.w3.org/2000/svg'%20" + svgAttributes +
                "%3E%3Crect%20x='0'%20y='0'%20width='100%25'%20height='100%25'%20fill='%23cc0000'/%3E%3C/svg%3E";
        String html =
                "<html><head><style>@page { size: 300px 300px; margin: 0; }" +
                "div { width: 100px; height: 50px; background-repeat: no-repeat;" +
                " background-image: url(\"" + svg + "\");" + extraCss + " }" +
                "</style></head><body><div></div></body></html>";

        try (PDDocument doc = render(html, builder -> builder.useSVGDrawer(new BatikSVGDrawer()))) {
            List<PDFormXObject> forms = formsOnPage(doc, 0);
            assertEquals("the background should have been drawn once", 1, forms.size());

            // The form is built at the size the image is drawn at, in CSS pixels.
            PDRectangle box = forms.get(0).getBBox();
            return new Dimension(Math.round(box.getWidth()), Math.round(box.getHeight()));
        }
    }

    /**
     * The area the background image actually covers, in CSS pixels relative to the box, as
     * opposed to the size of the box the image was given. The two come apart when the artwork
     * inside the image is not laid out to match the size CSS settled on.
     *
     * @param artwork the drawing, which is red, so that it can be picked out of the page
     */
    private Rectangle paintedArea(String svgAttributes, String artwork, String extraCss) throws IOException {
        String svg = "data:image/svg+xml,%3Csvg%20xmlns='http://www.w3.org/2000/svg'%20" + svgAttributes +
                "%3E" + artwork + "%3C/svg%3E";
        String html =
                "<html><head><style>@page { size: 300px 300px; margin: 0; } body { margin: 0; }" +
                "div { width: 100px; height: 50px; background-repeat: no-repeat;" +
                " background-image: url(\"" + svg + "\");" + extraCss + " }" +
                "</style></head><body><div></div></body></html>";

        return paintedArea(html);
    }

    /** As above, for a document that needs more setting up than a single box. */
    private Rectangle paintedArea(String html) throws IOException {
        try (PDDocument doc = render(html, builder -> builder.useSVGDrawer(new BatikSVGDrawer()))) {
            // The page is 300 CSS px, ie. 225pt, so render at 4/3 to measure in CSS pixels.
            BufferedImage img = new PDFRenderer(doc).renderImage(0, 4f / 3f);

            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;

            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int rgb = img.getRGB(x, y);

                    if (((rgb >> 16) & 0xff) > 130 && ((rgb >> 8) & 0xff) < 90 && (rgb & 0xff) < 90) {
                        minX = Math.min(minX, x); minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                    }
                }
            }

            assertThat("nothing was painted", maxX, not(equalTo(-1)));

            return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
        }
    }

    private String warningsFor(String svgAttributes) throws IOException {
        String svg = "data:image/svg+xml,%3Csvg%20xmlns='http://www.w3.org/2000/svg'%20" + svgAttributes +
                "%3E%3Crect%20x='0'%20y='0'%20width='100%25'%20height='100%25'%20fill='%23cc0000'/%3E%3C/svg%3E";
        String html =
                "<html><head><style>@page { size: 300px 300px; margin: 0; }" +
                "div { width: 100px; height: 50px; background-image: url(\"" + svg + "\"); }" +
                "</style></head><body><div></div></body></html>";

        List<Diagnostic> logs = new ArrayList<>();

        try (PDDocument doc = render(html, builder -> {
            builder.useSVGDrawer(new BatikSVGDrawer());
            builder.withDiagnosticConsumer(logs::add);
        })) {
            assertEquals(1, formsOnPage(doc, 0).size());
        }

        return logs.stream().map(Diagnostic::getFormattedMessage).collect(Collectors.joining("\n"));
    }

    /** The content stream of a page, as text. */
    private static String pageContent(PDDocument doc, int pageNo) throws IOException {
        try (InputStream in = doc.getPage(pageNo).getContents()) {
            return new String(OpenUtil.readAll(in), StandardCharsets.UTF_8);
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;

        for (int idx = haystack.indexOf(needle); idx != -1; idx = haystack.indexOf(needle, idx + needle.length())) {
            count++;
        }

        return count;
    }
}
