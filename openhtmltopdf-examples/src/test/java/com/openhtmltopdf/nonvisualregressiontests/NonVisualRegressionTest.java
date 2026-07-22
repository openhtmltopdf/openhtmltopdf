package com.openhtmltopdf.nonvisualregressiontests;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFileAttachment;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDObjectReference;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.text.PDFTextStripper;
import org.hamcrest.CustomTypeSafeMatcher;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.openhtmltopdf.layout.Layer;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.pdfboxout.PagePosition;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.testcases.TestcaseRunner;
import com.openhtmltopdf.testlistener.PrintingRunner;
import com.openhtmltopdf.util.Diagnostic;
import com.openhtmltopdf.util.LogMessageId;
import com.openhtmltopdf.util.OpenUtil;
import com.openhtmltopdf.visualregressiontests.VisualRegressionTest;
import com.openhtmltopdf.visualtest.TestSupport;
import com.openhtmltopdf.visualtest.VisualTester.BuilderConfig;

@RunWith(PrintingRunner.class)
public class NonVisualRegressionTest {
    private static final String RES_PATH = "/visualtest/html/";
    private static final String OUT_PATH = "target/test/visual-tests/test-output/";

    @BeforeClass
    public static void configure() {
        TestSupport.quietLogs();
    }

    private static void render(String fileName, String html, BuilderConfig config) throws IOException {
        ByteArrayOutputStream actual = new ByteArrayOutputStream();

        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, NonVisualRegressionTest.class.getResource(RES_PATH).toString());
        builder.toStream(actual);
        builder.testMode(true);
        config.configure(builder);

        try {
            builder.run();
        } catch (Exception e) {
            System.err.println("Failed to render resource (" + fileName + ")");
            e.printStackTrace();
        }

        writePdfToFile(fileName, actual);
    }

    private static void writePdfToFile(String fileName, ByteArrayOutputStream actual) throws IOException {
        FileUtils.writeByteArrayToFile(new File(OUT_PATH, fileName + ".pdf"), actual.toByteArray());
    }

    private static String loadHtml(String fileName) throws IOException {
        String absResPath = RES_PATH + fileName + ".html";

        try (InputStream is = TestcaseRunner.class.getResourceAsStream(absResPath)) {
            byte[] htmlBytes = IOUtils
                    .toByteArray(is);

            return new String(htmlBytes, StandardCharsets.UTF_8);
        }
    }

    private static PDDocument run(String fileName, BuilderConfig config) throws IOException {
        String html = loadHtml(fileName);

        render(fileName, html, config);

        return load(fileName);
    }

    private static PDDocument run(String filename) throws IOException {
        return run(filename, b -> {
        });
    }

    private static PDDocument load(String filename) throws IOException {
        return Loader.loadPDF(new File(OUT_PATH, filename + ".pdf"));
    }

    private static void remove(String fileName, PDDocument doc) throws IOException {
        OpenUtil.closeQuietly(doc);
        new File(OUT_PATH, fileName + ".pdf").delete();
    }

    private static double cssPixelsToPdfPoints(double cssPixels) {
        return cssPixels * 72d / 96d;
    }

    private static double cssPixelsYToPdfPoints(double cssPixels, double cssPixelsPageHeight) {
        return cssPixelsPageHeight - cssPixelsToPdfPoints(cssPixels);
    }

    private static double pdfPointsToCssPixels(double pdfPoints) {
        return pdfPoints * 96d / 72d;
    }

    private static double cssPixelYToPdfPoints(double cssPixelsY, double cssPixelsPageHeight) {
        return cssPixelsToPdfPoints(cssPixelsPageHeight - cssPixelsY);
    }

    private static class RectangleCompare extends CustomTypeSafeMatcher<PDRectangle> {
        private final PDRectangle expec;
        private final double pageHeight;

        private RectangleCompare(PDRectangle expected, double pageHeight) {
            super("Compare Rectangles");
            this.expec = expected;
            this.pageHeight = pageHeight;
        }

        @Override
        protected boolean matchesSafely(PDRectangle item) {
            String actualInCssPixels = "[" + pdfPointsToCssPixels(item.getLowerLeftX()) + "," + cssPixelsYToPdfPoints(item.getLowerLeftY(), pageHeight) + "," +
                    pdfPointsToCssPixels(item.getUpperRightX()) + "," + cssPixelsYToPdfPoints(item.getUpperRightY(), pageHeight) + "]";
            String message = "Dimensions do not match expected: " + this.expec.toString() + " actual: " + actualInCssPixels;
            assertEquals(message, cssPixelsToPdfPoints(this.expec.getLowerLeftX()), item.getLowerLeftX(), 1.0d);
            assertEquals(message, cssPixelsToPdfPoints(this.expec.getUpperRightX()), item.getUpperRightX(), 1.0d);

            // Note: We swap the Ys here because PDFBOX returns a rect in bottom up units while expected is in topdown units.
            assertEquals(message, cssPixelYToPdfPoints(this.expec.getUpperRightY(), pageHeight), item.getLowerLeftY(), 1.0d);
            assertEquals(message, cssPixelYToPdfPoints(this.expec.getLowerLeftY(), pageHeight), item.getUpperRightY(), 1.0d);

            return true;
        }
    }

    /**
     * Expected rect is in top down CSS pixel units. Actual rect is in bottom up PDF points.
     */
    private CustomTypeSafeMatcher<PDRectangle> rectEquals(PDRectangle expected, double pageHeight) {
        return new RectangleCompare(expected, pageHeight);
    }

    /**
     * Tests meta info: title, author, subject, keywords.
     */
    @Test
    public void testMetaInformation() throws IOException {
        try (PDDocument doc = run("meta-information")) {
            PDDocumentInformation did = doc.getDocumentInformation();

            assertThat(did.getTitle(), equalTo("Test title"));
            assertThat(did.getAuthor(), equalTo("Test author"));
            assertThat(did.getSubject(), equalTo("Test subject"));
            assertThat(did.getKeywords(), equalTo("Test keywords"));

            remove("meta-information", doc);
        }
    }

    /**
     * Tests that a simple head bookmark linking to top of the second page works.
     */
    @Test
    public void testBookmarkHeadSimple() throws IOException {
        try (PDDocument doc = run("bookmark-head-simple")) {
            PDDocumentOutline outline = doc.getDocumentCatalog().getDocumentOutline();

            PDOutlineItem bm = outline.getFirstChild();
            assertThat(bm.getTitle(), equalTo("Test bookmark"));
            assertThat(bm.getDestination(), instanceOf(PDPageXYZDestination.class));
            PDPageXYZDestination dest = (PDPageXYZDestination) bm.getDestination();

            // At top of second page.
            assertEquals(dest.getPage(), doc.getPage(1));
            assertEquals(doc.getPage(1).getMediaBox().getUpperRightY(), dest.getTop(), 1.0d);

            remove("bookmark-head-simple", doc);
        }
    }

    /**
     * Tests that target-counter(attr(href), page) prints the page where the target's
     * content actually paints when the target block straddles a page break: the block's
     * top edge lands in the last few pixels of page one, so its first line (and all
     * of its content) is pushed to page two.
     */
    @Test
    public void testTargetCounterPageStraddle() throws IOException {
        try (PDDocument doc = run("target-counter-page-straddle")) {
            assertEquals(2, doc.getNumberOfPages());

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String page1Text = stripper.getText(doc);

            stripper.setStartPage(2);
            stripper.setEndPage(2);
            String page2Text = stripper.getText(doc);

            // The target's content paints entirely on page two...
            assertThat(page1Text, not(containsString("Target heading")));
            assertThat(page2Text, containsString("Target heading"));

            // ...so the link must print page two.
            assertThat(page1Text, containsString("PAGE-2"));

            remove("target-counter-page-straddle", doc);
        }
    }

    /**
     * Control case for {@link #testTargetCounterPageStraddle()}: the target does not
     * straddle a page break, so target-counter prints the page of the target's top edge.
     */
    @Test
    public void testTargetCounterPageControl() throws IOException {
        try (PDDocument doc = run("target-counter-page-control")) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String page1Text = stripper.getText(doc);

            assertThat(page1Text, containsString("Target heading"));
            assertThat(page1Text, containsString("PAGE-1"));

            remove("target-counter-page-control", doc);
        }
    }

    /**
     * Tests that a bookmark destination points at the page where the target's content
     * actually paints when the target block straddles a page break (same geometry as
     * {@link #testTargetCounterPageStraddle()}).
     */
    @Test
    public void testBookmarkPageStraddle() throws IOException {
        try (PDDocument doc = run("bookmark-page-straddle")) {
            PDDocumentOutline outline = doc.getDocumentCatalog().getDocumentOutline();

            PDOutlineItem bm = outline.getFirstChild();
            assertThat(bm.getTitle(), equalTo("Test bookmark"));
            assertThat(bm.getDestination(), instanceOf(PDPageXYZDestination.class));
            PDPageXYZDestination dest = (PDPageXYZDestination) bm.getDestination();

            // The target's content is pushed entirely to the second page.
            assertEquals(doc.getPage(1), dest.getPage());

            remove("bookmark-page-straddle", doc);
        }
    }

    /**
     * Tests that a simple body bookmark linking to top of the second page works.
     */
    @Test
    public void testBookmarkBodySimple() throws IOException {
        try (PDDocument doc = run("bookmark-body-simple")) {
            PDDocumentOutline outline = doc.getDocumentCatalog().getDocumentOutline();

            PDOutlineItem bm = outline.getFirstChild();
            assertThat(bm.getTitle(), equalTo("Test bookmark"));
            assertThat(bm.getDestination(), instanceOf(PDPageXYZDestination.class));
            PDPageXYZDestination dest = (PDPageXYZDestination) bm.getDestination();

            // At top of second page.
            assertEquals(dest.getPage(), doc.getPage(1));
            assertEquals(doc.getPage(1).getMediaBox().getUpperRightY(), dest.getTop(), 1.0d);

            remove("bookmark-body-simple", doc);
        }
    }

    /**
     * Tests that a head bookmark linking to transformed element (by way of transform) on third page works.
     */
    @Test
    public void testBookmarkHeadTransform() throws IOException {
        try (PDDocument doc = run("bookmark-head-transform")) {
            PDDocumentOutline outline = doc.getDocumentCatalog().getDocumentOutline();

            PDOutlineItem bm = outline.getFirstChild();
            assertThat(bm.getTitle(), equalTo("Test bookmark"));
            assertThat(bm.getDestination(), instanceOf(PDPageXYZDestination.class));
            PDPageXYZDestination dest = (PDPageXYZDestination) bm.getDestination();

            // At top of third page.
            assertEquals(dest.getPage(), doc.getPage(2));
            assertEquals(doc.getPage(2).getMediaBox().getUpperRightY(), dest.getTop(), 1.0d);

            remove("bookmark-head-transform", doc);
        }
    }

    /**
     * Tests that a head bookmark linking to element (on overflow page).
     */
    @Test
    public void testBookmarkHeadOnOverflowPage() throws IOException {
        try (PDDocument doc = run("bookmark-head-on-overflow-page")) {
            PDDocumentOutline outline = doc.getDocumentCatalog().getDocumentOutline();

            PDOutlineItem bm = outline.getFirstChild();
            assertThat(bm.getTitle(), equalTo("Test bookmark"));
            assertThat(bm.getDestination(), instanceOf(PDPageXYZDestination.class));
            PDPageXYZDestination dest = (PDPageXYZDestination) bm.getDestination();

            assertEquals(dest.getPage(), doc.getPage(2));
            // Should be 11px down (10px margin, 1px outer border).
            assertEquals(cssPixelYToPdfPoints(11, 50), dest.getTop(), 1.0d);

            remove("bookmark-head-on-overflow-page", doc);
        }
    }

    /**
     * Tests that a head bookmark linking to an inline element (on page after overflow page) works.
     */
    @Test
    public void testBookmarkHeadAfterOverflowPage() throws IOException {
        try (PDDocument doc = run("bookmark-head-after-overflow-page")) {
            PDDocumentOutline outline = doc.getDocumentCatalog().getDocumentOutline();

            PDOutlineItem bm = outline.getFirstChild();
            assertThat(bm.getTitle(), equalTo("Test bookmark"));
            assertThat(bm.getDestination(), instanceOf(PDPageXYZDestination.class));
            PDPageXYZDestination dest = (PDPageXYZDestination) bm.getDestination();

            assertEquals(dest.getPage(), doc.getPage(3));
            // Should be 10px down (10px page margin).
            assertEquals(cssPixelYToPdfPoints(10, 50), dest.getTop(), 1.0d);

            remove("bookmark-head-after-overflow-page", doc);
        }
    }

    /**
     * Tests that a nested head bookmark linking to top of the third page works.
     */
    @Test
    public void testBookmarkHeadNested() throws IOException {
        try (PDDocument doc = run("bookmark-head-nested")) {
            PDDocumentOutline outline = doc.getDocumentCatalog().getDocumentOutline();

            PDOutlineItem bm1 = outline.getFirstChild();
            assertThat(bm1.getTitle(), equalTo("Outer"));
            assertThat(bm1.getDestination(), instanceOf(PDPageXYZDestination.class));
            PDPageXYZDestination dest1 = (PDPageXYZDestination) bm1.getDestination();

            // At top of second page.
            assertEquals(dest1.getPage(), doc.getPage(1));
            assertEquals(doc.getPage(1).getMediaBox().getUpperRightY(), dest1.getTop(), 1.0d);

            PDOutlineItem bm2 = bm1.getFirstChild();
            assertThat(bm2.getTitle(), equalTo("Inner"));
            assertThat(bm2.getDestination(), instanceOf(PDPageXYZDestination.class));
            PDPageXYZDestination dest2 = (PDPageXYZDestination) bm2.getDestination();

            // At top of third page.
            assertEquals(dest2.getPage(), doc.getPage(2));
            assertEquals(doc.getPage(2).getMediaBox().getUpperRightY(), dest2.getTop(), 1.0d);

            remove("bookmark-head-nested", doc);
        }
    }

    /**
     * Tests bad footnote related content such as:
     * + Paginated table inside footnotes.
     * Primarily to check that these scenarios do not cause infinite loop
     * or out-of-memory and ideally don't throw exceptions.
     * Bad footnote content is not supported and will not produce expected results.
     */
    @Test
    public void testIssue364InvalidFootnoteContent() throws IOException {
        try (PDDocument doc = run("issue-364-invalid-footnote-content")) {
            remove("issue-364-invalid-footnote-content", doc);
        }

    }

    /**
     * Tests bad footnote related content such as:
     * + Pseudos (::footnote-call, ::footnote-marker, ::before, ::after) with float: footnote.
     * + Pseudos in footnotes with position: fixed.
     * + Invalid styles in the footnote at-rule such as position: fixed.
     * <p>
     * Primarily to check that these scenarios do not cause infinite loop
     * or out-of-memory and ideally don't throw exceptions.
     * Bad footnote content is not supported and will not produce expected results.
     */
    @Test
    public void testIssue364InvalidFootnotePseudos() throws IOException {
        TestSupport.withLog((log, builder) -> {
            try (PDDocument doc = run("issue-364-invalid-footnote-pseudos", builder)) {
            }

            assertThat(log, hasItem(LogMessageId.LogMessageId1Param.GENERAL_FOOTNOTE_PSEUDO_INVALID));
            assertThat(log, hasItem(LogMessageId.LogMessageId1Param.GENERAL_FOOTNOTE_CAN_NOT_BE_PSEUDO));
            assertThat(log, hasItem(LogMessageId.LogMessageId2Param.GENERAL_FOOTNOTE_AREA_INVALID_STYLE));
        });

        remove("issue-364-invalid-footnote-pseudos", null);
    }

    /**
     * Tests the positioning, size, name and value of a text type form control.
     */
    @Test
    public void testFormControlText() throws IOException {
        try (PDDocument doc = run("form-control-text")) {

            assertEquals(1, doc.getPage(0).getAnnotations().size());
            assertThat(doc.getPage(0).getAnnotations().get(0), instanceOf(PDAnnotationWidget.class));

            PDAnnotationWidget widget = (PDAnnotationWidget) doc.getPage(0).getAnnotations().get(0);
            assertThat(widget.getRectangle(), rectEquals(new PDRectangle(23f, 23f, 100f, 20f), 200));

            PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
            assertEquals(1, form.getFields().size());
            assertThat(form.getFields().get(0), instanceOf(PDTextField.class));

            PDTextField field = (PDTextField) form.getFields().get(0);
            assertEquals("text-input", field.getFullyQualifiedName());
            assertEquals("Hello World!", field.getValue());

            remove("form-control-text", doc);
        }
    }

    /**
     * Tests the positioning, size, name and value of a form control on an overflow page.
     */
    @Test
    public void testFormControlOverflowPage() throws IOException {
        try (PDDocument doc = run("form-control-overflow-page")) {

            assertEquals(0, doc.getPage(0).getAnnotations().size());
            assertEquals(1, doc.getPage(1).getAnnotations().size());
            assertThat(doc.getPage(1).getAnnotations().get(0), instanceOf(PDAnnotationWidget.class));

            PDAnnotationWidget widget = (PDAnnotationWidget) doc.getPage(1).getAnnotations().get(0);
            assertThat(widget.getRectangle(), rectEquals(new PDRectangle(33f, 14f, 40f, 20f), 100));

            PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
            assertEquals(1, form.getFields().size());
            assertThat(form.getFields().get(0), instanceOf(PDTextField.class));

            PDTextField field = (PDTextField) form.getFields().get(0);
            assertEquals("text-input", field.getFullyQualifiedName());
            assertEquals("Hello World!", field.getValue());


            remove("form-control-overflow-page", doc);
        }
    }

    /**
     * Tests the positioning, size, name and value of a form control on an overflow page.
     */
    @Test
    public void testFormControlOnSecondPage() throws IOException {
        try (PDDocument doc = run("form-control-on-second-page")) {

            PDPage page0 = doc.getPage(0);
            PDPage page1 = doc.getPage(1);
            PDPage page2 = doc.getPage(2);

            assertEquals(1, page0.getAnnotations().size());
            assertEquals(1, page1.getAnnotations().size());
            assertEquals(0, page2.getAnnotations().size());

            assertThat(page0.getAnnotations().get(0), instanceOf(PDAnnotationWidget.class));
            assertThat(page1.getAnnotations().get(0), instanceOf(PDAnnotationWidget.class));

            PDRectangle rectangle0 = page0.getAnnotations().get(0).getRectangle();
            assertTrue(page0.getMediaBox().contains(rectangle0.getLowerLeftX(), rectangle0.getLowerLeftY()));
            assertTrue(page0.getMediaBox().contains(rectangle0.getUpperRightX(), rectangle0.getUpperRightY()));

            PDRectangle rectangle1 = page1.getAnnotations().get(0).getRectangle();
            assertTrue(page1.getMediaBox().contains(rectangle1.getLowerLeftX(), rectangle1.getLowerLeftY()));
            assertTrue(page1.getMediaBox().contains(rectangle1.getUpperRightX(), rectangle1.getUpperRightY()));

            PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
            assertEquals(2, form.getFields().size());
            assertThat(form.getFields().get(0), instanceOf(PDTextField.class));
            assertThat(form.getFields().get(1), instanceOf(PDTextField.class));

            PDTextField field = (PDTextField) form.getFields().get(0);
            assertEquals("Hello World!", field.getValue());
            PDTextField field2 = (PDTextField) form.getFields().get(1);
            assertEquals("Hello Second World!", field2.getValue());

            remove("form-control-on-second-page", doc);
        }
    }

    /**
     * Tests the positioning, size, name and value of a form control appearing after an overflow page.
     */
    @Test
    public void testFormControlAfterOverflowPage() throws IOException {
        try (PDDocument doc = run("form-control-after-overflow-page")) {

            assertEquals(0, doc.getPage(0).getAnnotations().size());
            assertEquals(0, doc.getPage(1).getAnnotations().size());
            assertEquals(1, doc.getPage(2).getAnnotations().size());
            assertThat(doc.getPage(2).getAnnotations().get(0), instanceOf(PDAnnotationWidget.class));

            PDAnnotationWidget widget = (PDAnnotationWidget) doc.getPage(2).getAnnotations().get(0);
            assertThat(widget.getRectangle(), rectEquals(new PDRectangle(13f, 13f, 60f, 30f), 100));

            PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
            assertEquals(1, form.getFields().size());
            assertThat(form.getFields().get(0), instanceOf(PDTextField.class));

            PDTextField field = (PDTextField) form.getFields().get(0);
            assertEquals("text-input", field.getFullyQualifiedName());
            assertEquals("Hello World!", field.getValue());

            remove("form-control-after-overflow-page", doc);
        }
    }

    /**
     * Every font written to the AcroForm default resources must carry the
     * required /BaseFont entry, even when the control's font is used by no
     * page content (its subset would stay empty and be saved unnamed).
     */
    @Test
    public void testFormControlFontHasBaseFont() throws IOException {
        try (PDDocument doc = run("form-control-on-second-page")) {
            // Pass null to inspect the raw written form, without fixups.
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);
            for (COSName fontName : form.getDefaultResources().getFontNames()) {
                assertNotNull("/BaseFont missing for " + fontName.getName(),
                        form.getDefaultResources().getFont(fontName).getName());
            }
            remove("form-control-on-second-page", doc);
        }
    }

    /**
     * Check that an input without name attribute does not launch a NPE.
     * Will now log a warning message.
     * See issue: https://github.com/danfickle/openhtmltopdf/issues/151
     * <p>
     * Additionally, check that a select element without options will not launch a NPE too.
     */
    @Test
    public void testInputWithoutNameAttribute() throws IOException {
        try (PDDocument doc = run("input-without-name-attribute")) {
            // Note: As of PDFBOX 2.0.22 we have the option of recreating
            // the acro form from the widgets. We pass null to avoid this behavior.
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);
            assertEquals(0, form.getFields().size());
            remove("input-without-name-attribute", doc);
        }
    }

    @Test
    public void testIssue338RadioReadOnly() throws IOException {
        try (PDDocument doc = run("issue-338-radio-read-only")) {
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);

            PDRadioButton radio = (PDRadioButton) form.getFields().get(0);
            assertTrue("radio should be readonly", radio.isReadOnly());

            remove("issue-338-radio-read-only", doc);
        }
    }

    private static float[] getQuadPoints(PDDocument doc, int pg, int linkIndex) throws IOException {
        return ((PDAnnotationLink) doc.getPage(pg).getAnnotations().get(linkIndex)).getQuadPoints();
    }

    private static String print(float[] floats) {
        StringBuilder sb = new StringBuilder();

        sb.append("new float[] { ");
        for (float floater : floats) {
            sb.append(floater);
            sb.append("f, ");
        }

        sb.deleteCharAt(sb.length() - 2);
        sb.append("}");

        return sb.toString();
    }

    private static final float QUAD_DELTA = 0.5f;

    private static boolean qAssert(List<float[]> expectedList, float[] actual, StringBuilder sb, int pg, int linkIndex) {
        sb.append("PAGE: " + pg + ", LINK: " + linkIndex + "\n");
        sb.append("   ACT(" + actual.length + "): " + print(actual) + "\n");

        // NOTE: The shapes are returned as a map and are therefore placed on
        // the page in a non-determined order. So we just searh the expected
        // list for a match.
        ALL_EXPECTED:
        for (float[] expected : expectedList) {
            if (expected.length != actual.length) {
                continue;
            }

            for (int i = 0; i < expected.length; i++) {
                float diff = Math.abs(expected[i] - actual[i]);

                if (diff > QUAD_DELTA) {
                    continue ALL_EXPECTED;
                }
            }

            return false;
        }

        sb.append("   !FAILED!");
        sb.append("\n\n");
        return true;
    }

    @Test
    public void testIssue64MissingGlyphIsReplaced() throws IOException {
        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFont(
                () -> NonVisualRegressionTest.class.getClassLoader().getResourceAsStream(
                        "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"),
                "Liberation Sans");
        builder.withHtmlContent(
                "<html><body style=\"font-family: 'Liberation Sans'\">A天B</body></html>",
                null);
        builder.toStream(actual);
        builder.testMode(true);
        builder.run();

        try (PDDocument doc = Loader.loadPDF(actual.toByteArray())) {
            String text = new PDFTextStripper().getText(doc).replaceAll("[\r\n]", "");
            assertEquals("A#B", text);
        }
    }

    /**
     * Tests that there is no repeated text in the page margin area as
     * reported in issue 458.
     */
    @Test
    public void testIssue458PageContentRepeatedInMargin() throws IOException {
        try (PDDocument doc = run("issue-458-content-repeated")) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);

            String expected =
                    IntStream.rangeClosed(1, 9)
                            .mapToObj(i -> "Line " + i + "\r\n")
                            .collect(Collectors.joining()) +
                            "This is \r\n" +
                            "some \r\n" +
                            "flowing \r\n" +
                            "text that \r\n" +
                            "should not \r\n" +
                            "repeat in \r\n" +
                            "page \r\n" +
                            "margins.\r\n" +
                            "1.  \r\n" +
                            "2.  \r\n" +
                            "3.  \r\n" +
                            "One\r\n" +
                            "Two\r\n" +
                            "Three";

            String normalizedExpected = expected.replaceAll("(\\r|\\n)", "");
            String normalizedActual = text.replaceAll("(\\r|\\n)", "");

            assertEquals(normalizedExpected.trim(), normalizedActual.trim());
        }
    }

    /**
     * Table row repeating on two pages. See issue 594.
     */
    @Test
    public void testIssue594RepeatingContentTableRow() throws IOException {
        try (PDDocument doc = run("issue-594-content-repeated")) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc).replaceAll("(\\r|\\n)", "");
            String expected = "One 1" + "Abcdefghij2 2";

            assertEquals(expected, text);
        }
    }

    /**
     * Tests the shaped links support for custom object drawers
     * in the main document area and in the page margin on multiple
     * pages.
     */
    @Test
    public void testPR480LinkShapes() throws IOException {
        try (PDDocument doc = run("pr-480-link-shapes", TestSupport.WITH_SHAPES_DRAWER)) {
            StringBuilder sb = new StringBuilder();
            List<float[]> page0 = new ArrayList<>();
            List<float[]> page1 = new ArrayList<>();
            boolean failure = false;

            page0.add(new float[]{486.75f, 251.25f, 468.0f, 213.75f, 486.75f, 213.75f, 505.5f, 213.75f, 486.75f, 251.25f, 505.5f, 213.75f, 496.125f, 232.5f, 486.75f, 251.25f});
            page0.add(new float[]{449.25f, 270.0f, 449.25f, 251.25f, 458.625f, 251.25f, 468.0f, 251.25f, 449.25f, 270.0f, 468.0f, 251.25f, 468.0f, 260.625f, 468.0f, 270.0f});
            page0.add(new float[]{505.5f, 213.75f, 505.5f, 195.0f, 514.875f, 195.0f, 524.25f, 195.0f, 505.5f, 213.75f, 524.25f, 195.0f, 524.25f, 204.375f, 524.25f, 213.75f});
            page0.add(new float[]{243.0f, 203.25f, 243.0f, 128.25f, 280.5f, 128.25f, 318.0f, 128.25f, 243.0f, 203.25f, 318.0f, 128.25f, 318.0f, 165.75f, 318.0f, 203.25f});
            page0.add(new float[]{168.0f, 353.25f, 93.0f, 203.25f, 168.0f, 203.25f, 243.0f, 203.25f, 168.0f, 353.25f, 243.0f, 203.25f, 205.5f, 278.25f, 168.0f, 353.25f});
            page0.add(new float[]{18.0f, 428.25f, 18.0f, 353.25f, 55.5f, 353.25f, 93.0f, 353.25f, 18.0f, 428.25f, 93.0f, 353.25f, 93.0f, 390.75f, 93.0f, 428.25f});

            failure |= qAssert(page0, getQuadPoints(doc, 0, 0), sb, 0, 0);
            failure |= qAssert(page0, getQuadPoints(doc, 0, 1), sb, 0, 1);
            failure |= qAssert(page0, getQuadPoints(doc, 0, 2), sb, 0, 2);
            failure |= qAssert(page0, getQuadPoints(doc, 0, 3), sb, 0, 3);
            failure |= qAssert(page0, getQuadPoints(doc, 0, 4), sb, 0, 4);
            failure |= qAssert(page0, getQuadPoints(doc, 0, 5), sb, 0, 5);

            page1.add(new float[]{486.75f, 251.25f, 468.0f, 213.75f, 486.75f, 213.75f, 505.5f, 213.75f, 486.75f, 251.25f, 505.5f, 213.75f, 496.125f, 232.5f, 486.75f, 251.25f});
            page1.add(new float[]{449.25f, 270.0f, 449.25f, 251.25f, 458.625f, 251.25f, 468.0f, 251.25f, 449.25f, 270.0f, 468.0f, 251.25f, 468.0f, 260.625f, 468.0f, 270.0f});
            page1.add(new float[]{505.5f, 213.75f, 505.5f, 195.0f, 514.875f, 195.0f, 524.25f, 195.0f, 505.5f, 213.75f, 524.25f, 195.0f, 524.25f, 204.375f, 524.25f, 213.75f});
            page1.add(new float[]{243.0f, 209.25f, 243.0f, 134.25f, 280.5f, 134.25f, 318.0f, 134.25f, 243.0f, 209.25f, 318.0f, 134.25f, 318.0f, 171.75f, 318.0f, 209.25f});
            page1.add(new float[]{168.0f, 359.25f, 93.0f, 209.25f, 168.0f, 209.25f, 243.0f, 209.25f, 168.0f, 359.25f, 243.0f, 209.25f, 205.5f, 284.25f, 168.0f, 359.25f});
            page1.add(new float[]{18.0f, 434.25f, 18.0f, 359.25f, 55.5f, 359.25f, 93.0f, 359.25f, 18.0f, 434.25f, 93.0f, 359.25f, 93.0f, 396.75f, 93.0f, 434.25f});

            failure |= qAssert(page1, getQuadPoints(doc, 1, 0), sb, 1, 0);
            failure |= qAssert(page1, getQuadPoints(doc, 1, 1), sb, 1, 1);
            failure |= qAssert(page1, getQuadPoints(doc, 1, 2), sb, 1, 2);
            failure |= qAssert(page1, getQuadPoints(doc, 1, 3), sb, 1, 3);
            failure |= qAssert(page1, getQuadPoints(doc, 1, 4), sb, 1, 4);
            failure |= qAssert(page1, getQuadPoints(doc, 1, 5), sb, 1, 5);

            if (failure) {
                System.out.print(sb.toString());
                Assert.fail("Quad points were not correct");
            }

            remove("pr-480-link-shapes", doc);
        }
    }

    /**
     * Tests that many footnotes do not take too long.
     */
    @Test
    public void testIssue364ManyFootnotes() throws IOException {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < 200; i++) {
            sb.append("Normal <div style=\"float: footnote;\">Footnote</div>");
        }

        runFuzzTest(sb.toString(), false);
    }

    /**
     * Tests performance of footnotes in many lines of text.
     */
    @Test
    public void testIssue364MuchText() throws IOException {
        StringBuilder sb = new StringBuilder();

        for (int j = 0; j < 50; j++) {
            for (int i = 0; i < 200; i++) {
                sb.append("Hello World!<br/>");
            }
            sb.append("<div style=\"float: footnote; color: green;\">Footnote</div>");
        }

        runFuzzTest(sb.toString(), false);
    }

    /**
     * Tests that footnotes nested very deeply do not take too long.
     */
    @Test
    public void testIssue364FootnotesDeepNesting() throws IOException {
        Function<String, String> deeper = (tag) ->
                IntStream.range(0, 50)
                        .mapToObj(u -> tag)
                        .collect(Collectors.joining());

        String[][] tags = new String[][]{
                {"<div>", "</div>"},
                {"<span>", "</span>"},
                {"<div style=\"position: absolute;\">", "</div>"},
                {"<td>", "</td>"},
                {"<div style=\"float: left;\">", "</div>"},
        };

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < tags.length; i++) {
            sb.append(deeper.apply(tags[i][0]));
            sb.append("Normal <div style=\"float: footnote;\">Footnote</div>");
            sb.append(deeper.apply(tags[i][1]));
        }

        runFuzzTest(sb.toString(), false);
    }

    /**
     * Runs a fuzz test, optionally with PDFBOX included font with non-zero-width
     * soft hyphen.
     */
    private static void runFuzzTest(String html, boolean useFont) throws IOException {
        final String header = useFont ?
                "<html><body style=\"font-family: 'Liberation Sans'\">" :
                "<html><body>";
        final String footer = "</body></html>";

        System.out.println("The test is " + html.length() + " chars long.");

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.toStream(os);
            builder.withHtmlContent(header + html + footer, null);
            if (useFont) {
                builder.useFont(() -> VisualRegressionTest.class.getClassLoader().getResourceAsStream("org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"),
                        "Liberation Sans");
            }
            builder.run();

            // Files.write(Paths.get("./target/html.txt"), html.getBytes(StandardCharsets.UTF_8));
            // java.nio.file.Files.write(java.nio.file.Paths.get("./target/pdf.pdf"), os.toByteArray());

            System.out.println("The result is " + os.size() + " bytes long.");
        }
    }

    /**
     * Creates a fuzz test with random characters given the styles provided in
     * arguments.
     */
    private static void createCombinationTest(
            StringBuilder sb, int widthPx, String whiteSpace, String wordWrap, List<char[]> all, Random rndm, int testCharCount) {
        String start = String.format(Locale.US, "<div style=\"white-space: %s; word-wrap: %s; width: %dpx;\">",
                whiteSpace, wordWrap, widthPx);
        String end = "</div>";

        sb.append(start);

        int len = 0;
        while (true) {
            char[] charCombi = all.get(rndm.nextInt(all.size()));

            if (len + charCombi.length > testCharCount) {
                String combi = String.valueOf(charCombi);
                sb.append(combi.substring(0, testCharCount - len));
                break;
            }

            sb.append(charCombi);
            len += charCombi.length;
        }

        sb.append(end);
    }

    /**
     * Creates all 5 character combinations from a list of characters
     * which have special meaning to the line breaking algorithms.
     */
    private static List<char[]> createAllCombinations() {
        char[] chars = new char[]{'x', '\u00ad', '\n', '\r', ' '};
        int[] loopIndices = new int[chars.length];
        int totalCombinations = (int) Math.pow(loopIndices.length, loopIndices.length);

        List<char[]> ret = new ArrayList<>(totalCombinations);

        for (int i = 0; i < totalCombinations; i++) {
            char[] result = new char[loopIndices.length];

            for (int k = 0; k < loopIndices.length; k++) {
                char ch = chars[loopIndices[k]];
                result[k] = ch;
            }

            ret.add(result);

            boolean carry = true;
            for (int j = loopIndices.length - 1; j >= 0; j--) {
                if (carry) {
                    loopIndices[j]++;
                    carry = false;
                }

                if (loopIndices[j] >= chars.length) {
                    loopIndices[j] = 0;
                    carry = true;
                }
            }
        }

        return ret;
    }

    /**
     * Tests the line breaking algorithms against infinite loop bugs
     * by using many combinations of styles and random character sequences.
     */
    @Test
    public void testPr492InfiniteLoopBugsInLineBreakingFuzz() throws IOException {
        final String[] whiteSpace = new String[]{"normal", "pre", "nowrap", "pre-wrap", "pre-line"};
        final String[] wordWrap = new String[]{"normal", "break-word"};
        final List<char[]> all = createAllCombinations();
        final Random rndm = new Random();
        long seed = rndm.nextLong();

        System.out.println("For NonVisualRegressionTest::testPr492InfiniteLoopBugsInLineBreakingFuzz " +
                "using a random seed of " + seed + " for Random instance.");
        rndm.setSeed(seed);

        List<Integer> lengths = new ArrayList<>();
        lengths.addAll(Arrays.asList(0, 1, 2, 3, 37, 79));

        for (int i = 0; i < 4; i++) {
            lengths.add(rndm.nextInt(150));
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < 100; i++) {
            for (int j = 0; j < whiteSpace.length; j++) {
                for (int k = 0; k < wordWrap.length; k++) {
                    for (Integer len : lengths) {
                        createCombinationTest(sb, i, whiteSpace[j], wordWrap[k], all, rndm, len);
                    }
                }
            }
        }

        runFuzzTest(sb.toString(), false);
        runFuzzTest(sb.toString(), true);
    }

    /**
     * Tests the diagnostic consumer api added to the builder.
     */
    @Test
    public void testPr489DiagnosticConsumer() throws IOException {
        List<Diagnostic> logs = new ArrayList<>();

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();

            builder.withDiagnosticConsumer(logs::add);
            builder.toStream(os);
            builder.withHtmlContent("<html style=\"invalid-prop: invalid-val\"><body>TEST</body></html>", null);
            builder.run();
        }

        Assert.assertTrue(
                logs.stream()
                        .noneMatch(diag -> diag.getLogMessageId() == LogMessageId.LogMessageId1Param.EXCEPTION_CANT_READ_IMAGE_FILE_FOR_URI));

        Assert.assertTrue(
                logs.stream()
                        .anyMatch(diag -> diag.getLogMessageId() == LogMessageId.LogMessageId2Param.CSS_PARSE_GENERIC_MESSAGE));

        Assert.assertTrue(
                logs.stream()
                        .allMatch(diag -> !diag.getFormattedMessage().isEmpty()));
    }

    @Test
    public void testIssue508FileEmbed() throws IOException {
        try (PDDocument doc = run("issue-508-file-embed",
                builder -> {
                    // File embeds are blocked by default, allow everything.
                    builder.useExternalResourceAccessControl((uri, type) -> true, ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
                    builder.useExternalResourceAccessControl((uri, type) -> true, ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);
                })) {

            // There should be multiple file attachment annotations because the link
            // is broken into two boxes on multiple lines.
            assertThat(doc.getPage(0).getAnnotations().size(), equalTo(2));

            PDAnnotationFileAttachment fileAttach1 = (PDAnnotationFileAttachment) doc.getPage(0).getAnnotations().get(0);
            assertThat(fileAttach1.getFile().getFile(), equalTo("basic.css"));

            PDAnnotationFileAttachment fileAttach2 = (PDAnnotationFileAttachment) doc.getPage(0).getAnnotations().get(1);
            assertThat(fileAttach2.getFile().getFile(), equalTo("basic.css"));

            try (COSDocument cosDoc = doc.getDocument()) {
                // Make sure the file is only embedded once.
                List<COSObject> files = cosDoc.getObjectsByType(COSName.FILESPEC);
                assertThat(files.size(), equalTo(1));
            }

            remove("issue-508-file-embed", doc);
        }
    }

    /**
     * Tests the PdfBoxRenderer::getPagePositions and
     * PdfBoxRenderer::getLastYPositionOfContent apis.
     * It does this by drawing a rect around each layer and comparing
     * with the expected document.
     */
    @SuppressWarnings("resource")
    @Test
    public void testIssue427GetBodyPagePositions() throws IOException {
        String filename = "issue-427-body-page-positions";
        String html = loadHtml(filename);
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        PdfRendererBuilder builder = new PdfRendererBuilder();

        builder.withHtmlContent(html, null);
        builder.toStream(os);

        float lastContentLine;

        try (PdfBoxRenderer renderer = builder.buildPdfRenderer();
             PDDocument doc = renderer.createPDFKeepOpen()) {

            List<PagePosition<Layer>> posList = renderer.getLayersPositions();
            lastContentLine = renderer.getLastContentBottom();

            for (int idx = 0; idx < posList.size(); ) {
                int pageIdx = posList.get(idx).getPageNo();
                List<PagePosition<Layer>> pageLayers = new ArrayList<>();

                while (idx < posList.size() && posList.get(idx).getPageNo() == pageIdx) {
                    pageLayers.add(posList.get(idx));
                    idx++;
                }

                try (PDPageContentStream stream = new PDPageContentStream(
                        renderer.getPdfDocument(), renderer.getPdfDocument().getPage(pageIdx),
                        AppendMode.APPEND, false, false)) {

                    stream.setLineWidth(1f);
                    stream.setStrokingColor(Color.ORANGE);

                    for (PagePosition<Layer> pos : pageLayers) {
                        stream.addRect(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight());
                        stream.stroke();
                    }
                }
            }

            renderer.getPdfDocument().save(os);

            writePdfToFile(filename, os);
        }

        assertTrue(TestSupport.comparePdfs(os.toByteArray(), filename));
        assertEquals(111.48, lastContentLine, 0.5);
    }

    @Test
    public void testNamedDestinationsBasic() throws IOException {
        try (PDDocument doc = run("named-destinations-basic")) {
            PDDocumentCatalog catalog = doc.getDocumentCatalog();
            assertNotNull(catalog.getNames());
            assertNotNull(catalog.getNames().getDests());

            Set<String> namedDestinationKeys = catalog.getNames().getDests().getNames().keySet();
            assertEquals(2, namedDestinationKeys.size());
            assertThat(namedDestinationKeys, hasItems("secondpage", "thirdpage"));

            PDPageDestination nd1 = catalog.findNamedDestinationPage(new PDNamedDestination(new COSString("secondpage")));
            assertNotNull(nd1);
            assertThat(nd1, instanceOf(PDPageXYZDestination.class));
            PDPageXYZDestination dest1 = (PDPageXYZDestination) nd1;

            // At top of second page.
            assertEquals(dest1.getPage(), doc.getPage(1));
            assertEquals(doc.getPage(1).getMediaBox().getUpperRightY(), dest1.getTop(), 1.0d);

            PDPageDestination nd2 = catalog.findNamedDestinationPage(new PDNamedDestination(new COSString("thirdpage")));
            assertNotNull(nd2);
            assertThat(nd2, instanceOf(PDPageXYZDestination.class));
            PDPageXYZDestination dest2 = (PDPageXYZDestination) nd2;

            // At top of third page.
            assertEquals(dest2.getPage(), doc.getPage(2));
            assertEquals(doc.getPage(2).getMediaBox().getUpperRightY(), dest2.getTop(), 1.0d);

            remove("named-destinations-basic", doc);
        }
    }

    /**
     * Tests that the PDF structure tree follows DOM order, not CSS paint order.
     * CSS paints backgrounds first, then floats, then inlines - which differs
     * from the DOM order. The structure tree must follow DOM order per
     * PDF/UA-1 rule 7.4.2-1.
     */
    @Test
    public void testStructureTreeFollowsDomOrder() throws IOException {
        String html =
            "<html lang='en'><head>" +
            "<title>Structure Tree Ordering Test</title>" +
            "<meta name='description' content='Test structure tree DOM ordering'/>" +
            "<style>" +
            "body { margin: 0; font-family: 'TestFont'; font-size: 12px; }" +
            "</style></head><body>" +
            "<h1>First</h1>" +
            "<h2>Second</h2>" +
            "<h3>Third</h3>" +
            "</body></html>";

        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(actual);
        builder.testMode(true);
        builder.usePdfUaAccessibility(true);
        builder.useFont(() -> NonVisualRegressionTest.class.getClassLoader().getResourceAsStream(
            "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"), "TestFont");
        builder.run();

        try (PDDocument doc = Loader.loadPDF(actual.toByteArray())) {
            PDStructureTreeRoot root = doc.getDocumentCatalog().getStructureTreeRoot();
            assertNotNull("Structure tree root should exist", root);

            List<String> headingTags = new ArrayList<>();
            collectStructureTags(root, headingTags, "H[1-6]");

            assertEquals("Should find 3 headings", 3, headingTags.size());
            assertEquals("First heading should be H1", "H1", headingTags.get(0));
            assertEquals("Second heading should be H2", "H2", headingTags.get(1));
            assertEquals("Third heading should be H3", "H3", headingTags.get(2));
        }
    }

    /**
     * Recursively collects structure element tags matching the given regex pattern
     * in document order from the PDF structure tree.
     */
    private static void collectStructureTags(PDStructureNode node, List<String> tags, String pattern) {
        for (Object kid : node.getKids()) {
            if (kid instanceof PDStructureElement) {
                PDStructureElement elem = (PDStructureElement) kid;
                String tag = elem.getStructureType();
                if (tag != null && tag.matches(pattern)) {
                    tags.add(tag);
                }
                collectStructureTags(elem, tags, pattern);
            }
        }
    }

    /**
     * Tests that links inside running footers get proper /Link structure elements
     * instead of being completely hidden inside pagination artifacts (PDF/UA-1 §7.18).
     */
    @Test
    public void testRunningFooterLinksGetLinkStructureElements() throws IOException {
        String html =
            "<html lang='en'><head>" +
            "<title>Running Footer Link Test</title>" +
            "<meta name='description' content='Test running footer links'/>" +
            "<style>" +
            "@page { @bottom-center { content: element(footer); } margin-bottom: 50px; }" +
            "body { margin: 0; font-family: 'TestFont'; font-size: 12px; }" +
            "#footer { position: running(footer); }" +
            "</style></head><body>" +
            "<div id='footer'><p>Contact: <a href='tel:+1234567890' title='Call us'>+1 234 567 890</a></p></div>" +
            "<p>Page content</p>" +
            "</body></html>";

        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(actual);
        builder.testMode(true);
        builder.usePdfUaAccessibility(true);
        builder.useFont(() -> NonVisualRegressionTest.class.getClassLoader().getResourceAsStream(
            "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"), "TestFont");
        builder.run();

        try (PDDocument doc = Loader.loadPDF(actual.toByteArray())) {
            PDStructureTreeRoot root = doc.getDocumentCatalog().getStructureTreeRoot();
            assertNotNull("Structure tree root should exist", root);

            // Find /Link structure elements in the tree.
            List<PDStructureElement> linkElements = new ArrayList<>();
            collectLinkStructureElements(root, linkElements);
            assertFalse("Should find at least one /Link structure element", linkElements.isEmpty());

            // Verify the /Link element has both an OBJR kid (annotation) and content (MCID).
            PDStructureElement linkElem = linkElements.get(0);
            boolean hasObjr = false;
            boolean hasContent = false;
            for (Object kid : linkElem.getKids()) {
                if (kid instanceof PDObjectReference) {
                    hasObjr = true;
                } else {
                    hasContent = true;
                }
            }
            assertTrue("/Link should have an OBJR kid (annotation reference)", hasObjr);
            assertTrue("/Link should have tagged content (MCID)", hasContent);
        }
    }

    private static void collectLinkStructureElements(PDStructureNode node, List<PDStructureElement> result) {
        for (Object kid : node.getKids()) {
            if (kid instanceof PDStructureElement) {
                PDStructureElement elem = (PDStructureElement) kid;
                if ("Link".equals(elem.getStructureType())) {
                    result.add(elem);
                }
                collectLinkStructureElements(elem, result);
            }
        }
    }

    /**
     * Diagnostic test: verifies the link annotation rectangle for a running footer link
     * is positioned within the bottom margin area of the page (not at y=0 or in the content area).
     */
    @Test
    public void testRunningFooterLinkAnnotationPosition() throws IOException {
        String html =
            "<html lang='en'><head>" +
            "<title>Link Position Test</title>" +
            "<meta name='description' content='Test link position'/>" +
            "<style>" +
            "@page { size: 200px 400px; margin: 20px 10px 60px 10px; " +
            "  @bottom-center { content: element(footer); } }" +
            "body { margin: 0; font-family: 'TestFont'; font-size: 12px; }" +
            "#footer { position: running(footer); }" +
            "</style></head><body>" +
            "<div id='footer'><a href='https://example.com' title='Example'>Click here</a></div>" +
            "<p>Body text</p>" +
            "</body></html>";

        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(actual);
        builder.testMode(true);
        builder.usePdfUaAccessibility(true);
        builder.useFont(() -> NonVisualRegressionTest.class.getClassLoader().getResourceAsStream(
            "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"), "TestFont");
        builder.run();

        try (PDDocument doc = Loader.loadPDF(actual.toByteArray())) {
            PDPage page = doc.getPage(0);
            List<PDAnnotation> annots = page.getAnnotations();
            assertFalse("Should have at least one annotation", annots.isEmpty());

            PDAnnotationLink linkAnnot = null;
            for (PDAnnotation a : annots) {
                if (a instanceof PDAnnotationLink) {
                    linkAnnot = (PDAnnotationLink) a;
                    break;
                }
            }
            assertNotNull("Should find a link annotation", linkAnnot);

            PDRectangle rect = linkAnnot.getRectangle();
            // Page is 400px = 300pt, bottom margin is 60px = 45pt.
            // The entire link rect should fit inside the bottom margin band (y=0 to ~45pt).
            float bottomMarginPt = 45f;
            assertTrue("Link bottom y=" + rect.getLowerLeftY() + " should be > 0",
                rect.getLowerLeftY() > 0);
            assertTrue("Link top y=" + rect.getUpperRightY() + " should be in bottom margin (< " + bottomMarginPt + ")",
                rect.getUpperRightY() < bottomMarginPt);
            assertTrue("Link should have positive dimensions",
                rect.getHeight() > 0 && rect.getWidth() > 0);
        }
    }

    /**
     * Tests that running footer links work correctly across multiple pages.
     * The /Link structure element should be reused (not duplicated) and each page
     * should have its own link annotation connected via OBJR.
     */
    @Test
    public void testRunningFooterLinkAcrossMultiplePages() throws IOException {
        String html =
            "<html lang='en'><head>" +
            "<title>Multi-page Footer Link Test</title>" +
            "<meta name='description' content='Test multi-page footer links'/>" +
            "<style>" +
            "@page { size: 200px 200px; margin: 10px 10px 40px 10px; " +
            "  @bottom-center { content: element(footer); } }" +
            "body { margin: 0; font-family: 'TestFont'; font-size: 12px; }" +
            "#footer { position: running(footer); }" +
            ".break { page-break-before: always; }" +
            "</style></head><body>" +
            "<div id='footer'><a href='tel:+1234567890' title='Call us'>+1 234 567 890</a></div>" +
            "<p>Page 1</p>" +
            "<p class='break'>Page 2</p>" +
            "</body></html>";

        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(actual);
        builder.testMode(true);
        builder.usePdfUaAccessibility(true);
        builder.useFont(() -> NonVisualRegressionTest.class.getClassLoader().getResourceAsStream(
            "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"), "TestFont");
        builder.run();

        try (PDDocument doc = Loader.loadPDF(actual.toByteArray())) {
            assertEquals("Should have 2 pages", 2, doc.getNumberOfPages());

            PDStructureTreeRoot root = doc.getDocumentCatalog().getStructureTreeRoot();
            assertNotNull("Structure tree root should exist", root);

            // There should be exactly one /Link structure element (reused across pages).
            List<PDStructureElement> linkElements = new ArrayList<>();
            collectLinkStructureElements(root, linkElements);
            assertEquals("Should have exactly one /Link element (reused)", 1, linkElements.size());

            // The /Link should have OBJRs for both pages' annotations.
            PDStructureElement linkElem = linkElements.get(0);
            int objrCount = 0;
            for (Object kid : linkElem.getKids()) {
                if (kid instanceof PDObjectReference) {
                    objrCount++;
                }
            }
            assertEquals("Should have 2 OBJRs (one per page)", 2, objrCount);

            // Both pages should have link annotations.
            for (int i = 0; i < 2; i++) {
                List<PDAnnotation> annots = doc.getPage(i).getAnnotations();
                boolean hasLink = false;
                for (PDAnnotation a : annots) {
                    if (a instanceof PDAnnotationLink) {
                        hasLink = true;
                        break;
                    }
                }
                assertTrue("Page " + (i + 1) + " should have a link annotation", hasLink);
            }
        }
    }

    /**
     * Tests that multiple links in a single running footer each get their own
     * /Link structure element. Exercises the anchor-switching path in
     * ensureRunningLinkStructure when _runningLinkDomElement changes.
     */
    @Test
    public void testRunningFooterMultipleLinks() throws IOException {
        String html =
            "<html lang='en'><head>" +
            "<title>Multiple Footer Links Test</title>" +
            "<meta name='description' content='Test multiple footer links'/>" +
            "<style>" +
            "@page { size: 200px 200px; margin: 10px 10px 50px 10px; " +
            "  @bottom-center { content: element(footer); } }" +
            "body { margin: 0; font-family: 'TestFont'; font-size: 10px; }" +
            "#footer { position: running(footer); }" +
            "</style></head><body>" +
            "<div id='footer'>" +
            "  <a href='tel:+123' title='Phone'>Phone</a>" +
            "  <a href='mailto:a@b.c' title='Email'>Email</a>" +
            "</div>" +
            "<p>Content</p>" +
            "</body></html>";

        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(actual);
        builder.testMode(true);
        builder.usePdfUaAccessibility(true);
        builder.useFont(() -> NonVisualRegressionTest.class.getClassLoader().getResourceAsStream(
            "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"), "TestFont");
        builder.run();

        try (PDDocument doc = Loader.loadPDF(actual.toByteArray())) {
            PDStructureTreeRoot root = doc.getDocumentCatalog().getStructureTreeRoot();
            assertNotNull(root);

            List<PDStructureElement> linkElements = new ArrayList<>();
            collectLinkStructureElements(root, linkElements);
            assertEquals("Should find 2 /Link structure elements", 2, linkElements.size());

            // Each /Link should have both content (MCID) and annotation (OBJR).
            for (int i = 0; i < linkElements.size(); i++) {
                boolean hasObjr = false;
                boolean hasContent = false;
                for (Object kid : linkElements.get(i).getKids()) {
                    if (kid instanceof PDObjectReference) {
                        hasObjr = true;
                    } else {
                        hasContent = true;
                    }
                }
                assertTrue("/Link " + (i + 1) + " should have OBJR", hasObjr);
                assertTrue("/Link " + (i + 1) + " should have content", hasContent);
            }
        }
    }

    /**
     * Tests that an image inside a link in a running footer gets a proper /Figure
     * structure element (with alt text) nested under /Link, not a plain GenericContentItem.
     * Exercises createRunningLinkFigureItem (StructureType.REPLACED path).
     */
    @Test
    public void testRunningFooterImageLinkGetsFigureStructure() throws IOException {
        String html =
            "<html lang='en'><head>" +
            "<title>Image Link Footer Test</title>" +
            "<meta name='description' content='Test image link in footer'/>" +
            "<style>" +
            "@page { size: 200px 200px; margin: 10px 10px 50px 10px; " +
            "  @bottom-center { content: element(footer); } }" +
            "body { margin: 0; font-family: 'TestFont'; font-size: 10px; }" +
            "#footer { position: running(footer); }" +
            "</style></head><body>" +
            "<div id='footer'><a href='https://example.com' title='Logo link'>" +
            "<img src='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==' " +
            "alt='Logo' style='width:10px;height:10px;'/> Home</a></div>" +
            "<p>Content</p>" +
            "</body></html>";

        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(actual);
        builder.testMode(true);
        builder.usePdfUaAccessibility(true);
        builder.useFont(() -> NonVisualRegressionTest.class.getClassLoader().getResourceAsStream(
            "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"), "TestFont");
        builder.run();

        try (PDDocument doc = Loader.loadPDF(actual.toByteArray())) {
            PDStructureTreeRoot root = doc.getDocumentCatalog().getStructureTreeRoot();
            assertNotNull(root);

            // Find /Link structure elements.
            List<PDStructureElement> linkElements = new ArrayList<>();
            collectLinkStructureElements(root, linkElements);
            assertEquals("Should find exactly one /Link", 1, linkElements.size());

            PDStructureElement linkElem = linkElements.get(0);
            // /Link should have an OBJR kid (annotation reference).
            boolean hasObjr = false;
            // /Link should have a /Figure child structure element (not just a generic content item).
            boolean hasFigureChild = false;
            for (Object kid : linkElem.getKids()) {
                if (kid instanceof PDObjectReference) {
                    hasObjr = true;
                } else if (kid instanceof PDStructureElement) {
                    PDStructureElement childElem = (PDStructureElement) kid;
                    if ("Figure".equals(childElem.getStructureType())) {
                        hasFigureChild = true;
                        // Figure should have alt text.
                        assertNotNull("Figure should have alt text",
                            childElem.getAlternateDescription());
                        assertFalse("Figure alt text should not be empty",
                            childElem.getAlternateDescription().isEmpty());
                    }
                }
            }
            assertTrue("/Link should have OBJR", hasObjr);
            assertTrue("/Link should have /Figure child with alt text", hasFigureChild);
        }
    }

    /**
     * Tests that a running footer link with multiple text nodes (e.g. icon + label)
     * reuses the same /Link structure via the cache (exercises _runningLinkCache hit path).
     */
    @Test
    public void testRunningFooterLinkMultipleSpansReusesStructure() throws IOException {
        String html =
            "<html lang='en'><head>" +
            "<title>Multi-span Link Footer Test</title>" +
            "<meta name='description' content='Test multi-span link'/>" +
            "<style>" +
            "@page { size: 200px 200px; margin: 10px 10px 50px 10px; " +
            "  @bottom-center { content: element(footer); } }" +
            "body { margin: 0; font-family: 'TestFont'; font-size: 10px; }" +
            "#footer { position: running(footer); }" +
            "</style></head><body>" +
            "<div id='footer'><a href='tel:+123' title='Call'>" +
            "<span>Icon</span> <span>Call us</span></a></div>" +
            "<p>Content</p>" +
            "</body></html>";

        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(actual);
        builder.testMode(true);
        builder.usePdfUaAccessibility(true);
        builder.useFont(() -> NonVisualRegressionTest.class.getClassLoader().getResourceAsStream(
            "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"), "TestFont");
        builder.run();

        try (PDDocument doc = Loader.loadPDF(actual.toByteArray())) {
            PDStructureTreeRoot root = doc.getDocumentCatalog().getStructureTreeRoot();
            assertNotNull(root);

            // Should still be exactly one /Link (structure reused for both spans).
            List<PDStructureElement> linkElements = new ArrayList<>();
            collectLinkStructureElements(root, linkElements);
            assertEquals("Should find exactly one /Link (reused)", 1, linkElements.size());

            // The /Link should have multiple content kids (one per text node) plus OBJR.
            PDStructureElement linkElem = linkElements.get(0);
            int contentKids = 0;
            boolean hasObjr = false;
            for (Object kid : linkElem.getKids()) {
                if (kid instanceof PDObjectReference) {
                    hasObjr = true;
                } else {
                    contentKids++;
                }
            }
            assertTrue("/Link should have OBJR", hasObjr);
            assertTrue("/Link should have multiple content kids (>1)", contentKids > 1);
        }
    }

    /**
     * Tests that headings split across page breaks preserve correct reading order
     * in the structure tree. Page breaks create new branches in the structure tree,
     * but the heading order must remain H1 → H2 → H3 regardless of page boundaries.
     */
    @Test
    public void testHeadingsAcrossPageBreaksPreserveOrder() throws IOException {
        String html =
            "<html lang='en'><head>" +
            "<title>Headings Across Pages Test</title>" +
            "<meta name='description' content='Test heading order across page breaks'/>" +
            "<style>" +
            "@page { size: 200px 200px; margin: 10px; }" +
            "body { margin: 0; font-family: 'TestFont'; font-size: 12px; }" +
            "</style></head><body>" +
            "<h1>First heading</h1>" +
            "<p>Some content on page one.</p>" +
            "<h2 style='page-break-before: always;'>Second heading on page two</h2>" +
            "<p>Content on page two.</p>" +
            "<h3 style='page-break-before: always;'>Third heading on page three</h3>" +
            "<p>Content on page three.</p>" +
            "</body></html>";

        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(actual);
        builder.testMode(true);
        builder.usePdfUaAccessibility(true);
        builder.useFont(() -> NonVisualRegressionTest.class.getClassLoader().getResourceAsStream(
            "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"), "TestFont");
        builder.run();

        try (PDDocument doc = Loader.loadPDF(actual.toByteArray())) {
            assertEquals("Should have 3 pages", 3, doc.getNumberOfPages());

            PDStructureTreeRoot root = doc.getDocumentCatalog().getStructureTreeRoot();
            assertNotNull("Structure tree root should exist", root);

            List<String> headingTags = new ArrayList<>();
            collectStructureTags(root, headingTags, "H[1-6]");

            assertEquals("Should find 3 headings", 3, headingTags.size());
            assertEquals("First heading should be H1", "H1", headingTags.get(0));
            assertEquals("Second heading should be H2", "H2", headingTags.get(1));
            assertEquals("Third heading should be H3", "H3", headingTags.get(2));
        }
    }

    // TODO:
    // + More form controls.
    // + Custom meta info.
}
