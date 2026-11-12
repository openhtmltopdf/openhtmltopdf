package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.testlistener.PrintingRunner;
import com.openhtmltopdf.visualtest.TestSupport;

/**
 * The fragment borders of a rowspan cell that crosses a page break line up with
 * the interrupted row's other cells.
 */
@RunWith(PrintingRunner.class)
public class RowspanPageBreakBorderTest {
    private static final int PAGE_SIZE = 100;
    private static final int MULTI_PAGE_ROWS = 20;
    @Rule
    public final ErrorCollector errors = new ErrorCollector();

    @BeforeClass
    public static void configure() {
        TestSupport.quietLogs();
    }

    @Test
    public void testSeparatedBorders() throws IOException {
        assertFragmentBorders("separate");
    }

    @Test
    public void testCollapsedBorders() throws IOException {
        assertFragmentBorders("collapse");
    }

    @Test
    public void testSeparatedBordersOverSeveralPages() throws IOException {
        assertFragmentBordersOverSeveralPages("separate");
    }

    @Test
    public void testCollapsedBordersOverSeveralPages() throws IOException {
        assertFragmentBordersOverSeveralPages("collapse");
    }

    @Test
    public void testSeparatedBordersInsideSplitRow() throws IOException {
        assertFragmentBordersOnEveryPage("break inside a row",
                splitRowHtml("separate"));
    }

    @Test
    public void testCollapsedBordersInsideSplitRow() throws IOException {
        assertFragmentBordersOnEveryPage("break inside a row",
                splitRowHtml("collapse"));
    }

    @Test
    public void testSeparatedBordersStartingMidDocument() throws IOException {
        assertFragmentBordersOnEveryPage("rowspan starting mid-document",
                midDocumentHtml("separate"));
    }

    @Test
    public void testCollapsedBordersStartingMidDocument() throws IOException {
        assertFragmentBordersOnEveryPage("rowspan starting mid-document",
                midDocumentHtml("collapse"));
    }

    @Test
    public void testSeparatedBordersWithRepeatedHeaderAndFooter() throws IOException {
        assertFragmentBordersOnEveryPage("body rowspan with repeated header and footer",
                headerFooterHtml("separate"));
    }

    @Test
    public void testCollapsedBordersWithRepeatedHeaderAndFooter() throws IOException {
        assertFragmentBordersOnEveryPage("body rowspan with repeated header and footer",
                headerFooterHtml("collapse"));
    }

    @Test
    public void testSeparatedBordersWithRepeatedHeaderAndSpacing() throws IOException {
        assertFragmentBordersOnEveryPage("rowspan after a repeated header with border spacing",
                headerSpacingHtml());
    }

    @Test
    public void testSeparatedBordersOverEmptyRows() throws IOException {
        assertFragmentBordersOnEveryPage("rowspan over empty rows", emptyRowsHtml("separate"));
    }

    @Test
    public void testCollapsedBordersOverEmptyRows() throws IOException {
        assertFragmentBordersOnEveryPage("rowspan over empty rows", emptyRowsHtml("collapse"));
    }

    /**
     * The break lands on a row boundary: all three cells end at the same y on
     * the first page and start at the same y on the next.
     */
    private void assertFragmentBorders(String borderCollapse) throws IOException {
        List<BufferedImage> pages = renderPages(html(borderCollapse));
        assertTrue("the table spans more than one page", pages.size() > 1);
        BufferedImage firstPage = pages.get(0);
        int rowspanBottom = lastBorderRow(firstPage, PAGE_SIZE / 6);
        int middleBottom = lastBorderRow(firstPage, PAGE_SIZE / 2);
        int rightBottom = lastBorderRow(firstPage, 5 * PAGE_SIZE / 6);
        assertTrue("the row's middle cell paints a bottom border on the first page",
                middleBottom >= 0);
        checkEquals("the rowspan cell's bottom border lines up with the row's middle cell",
                middleBottom, rowspanBottom);
        checkEquals("the rowspan cell's bottom border lines up with the row's right cell",
                rightBottom, rowspanBottom);
        BufferedImage secondPage = pages.get(1);
        int rowspanTop = firstBorderRow(secondPage, PAGE_SIZE / 6);
        int middleTop = firstBorderRow(secondPage, PAGE_SIZE / 2);
        int rightTop = firstBorderRow(secondPage, 5 * PAGE_SIZE / 6);
        assertTrue("the row's middle cell paints a top border on the second page",
                middleTop >= 0);
        checkEquals("the rowspan cell's top border lines up with the row's middle cell",
                middleTop, rowspanTop);
        checkEquals("the rowspan cell's top border lines up with the row's right cell",
                rightTop, rowspanTop);
    }

    /**
     * The rowspan covers the whole table, so every page breaks inside the span.
     */
    private void assertFragmentBordersOverSeveralPages(String borderCollapse) throws IOException {
        List<BufferedImage> pages = renderPages(multiPageHtml(borderCollapse));
        assertTrue("the table spans more than two pages", pages.size() > 2);
        for (int page = 0; page < pages.size(); page++) {
            BufferedImage image = pages.get(page);
            int rowspanBottom = lastBorderRow(image, PAGE_SIZE / 4);
            int otherBottom = lastBorderRow(image, 3 * PAGE_SIZE / 4);
            assertTrue("the second column paints a bottom border on page " + (page + 1),
                    otherBottom >= 0);
            checkEquals("the rowspan cell's bottom border lines up on page " + (page + 1),
                    otherBottom, rowspanBottom);
            if (page > 0) {
                int rowspanTop = firstBorderRow(image, PAGE_SIZE / 4);
                int otherTop = firstBorderRow(image, 3 * PAGE_SIZE / 4);
                assertTrue("the second column paints a top border on page " + (page + 1),
                        otherTop >= 0);
                checkEquals("the rowspan cell's top border lines up on page " + (page + 1),
                        otherTop, rowspanTop);
            }
        }
    }

    /**
     * Both fragment edges line up on every page.
     */
    private void assertFragmentBordersOnEveryPage(String description, String htmlContent)
            throws IOException {
        List<BufferedImage> pages = renderPages(htmlContent);
        assertTrue(description + ": table spans more than one page", pages.size() > 1);
        for (int page = 0; page < pages.size(); page++) {
            BufferedImage image = pages.get(page);
            int rowspanTop = firstBorderRow(image, PAGE_SIZE / 4);
            int otherTop = firstBorderRow(image, 3 * PAGE_SIZE / 4);
            int rowspanBottom = lastBorderRow(image, PAGE_SIZE / 4);
            int otherBottom = lastBorderRow(image, 3 * PAGE_SIZE / 4);
            assertTrue(description + ": second column paints a top border on page " + (page + 1),
                    otherTop >= 0);
            assertTrue(description + ": second column paints a bottom border on page " + (page + 1),
                    otherBottom >= 0);
            checkEquals(description + ": top border lines up on page " + (page + 1),
                    otherTop, rowspanTop);
            checkEquals(description + ": bottom border lines up on page " + (page + 1),
                    otherBottom, rowspanBottom);
        }
    }

    private void checkEquals(String message, int expected, int actual) {
        errors.checkSucceeds(() -> {
            assertEquals(message, expected, actual);
            return null;
        });
    }

    private static List<BufferedImage> renderPages(String htmlContent) throws IOException {
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(htmlContent, null);
        builder.toStream(pdf);
        builder.testMode(true);
        builder.run();
        try (PDDocument document = Loader.loadPDF(pdf.toByteArray())) {
            PDFRenderer renderer = new PDFRenderer(document);
            List<BufferedImage> pages = new ArrayList<>();
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                pages.add(renderer.renderImageWithDPI(page, 96f, ImageType.RGB));
            }
            return pages;
        }
    }

    private static String html(String borderCollapse) {
        return "<html><head><style>" +
                "@page { size: " + PAGE_SIZE + "px " + PAGE_SIZE + "px; margin: 0; }" +
                "body { margin: 0; }" +
                "table { -fs-table-paginate: paginate; width: 100%;" +
                "        border-collapse: " + borderCollapse + "; border-spacing: 0; }" +
                "td { height: 18px; padding: 2px; border: 2px solid red; }" +
                "</style></head><body><table>" +
                "<tr><td rowspan=\"6\"></td><td></td><td></td></tr>" +
                row() + row() + row() + row() + row() + row() + row() + row() +
                "</table></body></html>";
    }

    private static String multiPageHtml(String borderCollapse) {
        StringBuilder rows = new StringBuilder();
        for (int i = 1; i < MULTI_PAGE_ROWS; i++) {
            rows.append("<tr><td></td></tr>");
        }
        return "<html><head><style>" +
                "@page { size: " + PAGE_SIZE + "px " + PAGE_SIZE + "px; margin: 0; }" +
                "body { margin: 0; }" +
                "table { -fs-table-paginate: paginate; width: 100%;" +
                "        border-collapse: " + borderCollapse + "; border-spacing: 0; }" +
                "td { height: 18px; padding: 2px; border: 2px solid red; }" +
                "</style></head><body><table>" +
                "<tr><td rowspan=\"" + MULTI_PAGE_ROWS + "\"></td><td></td></tr>" + rows +
                "</table></body></html>";
    }

    private static String splitRowHtml(String borderCollapse) {
        StringBuilder tall = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            tall.append("<div class=\"tall\"></div>");
        }
        return "<html><head><style>" +
                "@page { size: " + PAGE_SIZE + "px " + PAGE_SIZE + "px; margin: 0; }" +
                "body { margin: 0; }" +
                "table { -fs-table-paginate: paginate; width: 100%;" +
                "        border-collapse: " + borderCollapse + "; border-spacing: 0; }" +
                "td { height: 18px; padding: 2px; border: 2px solid red; }" +
                ".tall { height: 20px; }" +
                "</style></head><body><table>" +
                "<tr><td rowspan=\"2\"></td><td>" + tall + "</td></tr>" +
                "<tr><td></td></tr>" +
                "</table></body></html>";
    }

    private static String midDocumentHtml(String borderCollapse) {
        return "<html><head><style>" +
                "@page { size: " + PAGE_SIZE + "px " + PAGE_SIZE + "px; margin: 0; }" +
                "body { margin: 0; }" +
                "table { -fs-table-paginate: paginate; width: 100%;" +
                "        border-collapse: " + borderCollapse + "; border-spacing: 0; }" +
                "td { height: 18px; padding: 2px; border: 2px solid red; }" +
                "</style></head><body><div style=\"height: 50px\"></div><table>" +
                "<tr><td rowspan=\"6\"></td><td></td></tr>" +
                singleCells() + fullRows() +
                "</table></body></html>";
    }

    private static String headerFooterHtml(String borderCollapse) {
        return "<html><head><style>" +
                "@page { size: " + PAGE_SIZE + "px " + PAGE_SIZE + "px; margin: 0; }" +
                "body { margin: 0; }" +
                "table { -fs-table-paginate: paginate; width: 100%;" +
                "        border-collapse: " + borderCollapse + "; border-spacing: 0; }" +
                "td { height: 18px; padding: 2px; border: 2px solid red; }" +
                "th { height: 12px; border: 2px solid blue; }" +
                "tfoot td { height: 12px; border: 0; }" +
                "</style></head><body><table>" +
                "<thead><tr><th></th><th></th></tr></thead>" +
                "<tfoot><tr><td></td><td></td></tr></tfoot>" +
                "<tbody>" +
                "<tr><td rowspan=\"6\"></td><td></td></tr>" +
                singleCells() + fullRows() +
                "</tbody></table></body></html>";
    }

    private static String headerSpacingHtml() {
        return "<html><head><style>" +
                "@page { size: " + PAGE_SIZE + "px " + PAGE_SIZE + "px; margin: 0; }" +
                "body { margin: 0; }" +
                "table { -fs-table-paginate: paginate; width: 100%;" +
                "        border-collapse: separate; border-spacing: 4px; }" +
                "td { height: 18px; padding: 2px; border: 2px solid red; }" +
                "th { height: 12px; border: 2px solid blue; }" +
                "</style></head><body><table>" +
                "<thead><tr><th></th><th></th></tr></thead>" +
                "<tbody>" +
                "<tr><td rowspan=\"6\"></td><td></td></tr>" +
                singleCells() + fullRows() +
                "</tbody></table></body></html>";
    }

    private static String emptyRowsHtml(String borderCollapse) {
        return "<html><head><style>" +
                "@page { size: " + PAGE_SIZE + "px " + PAGE_SIZE + "px; margin: 0; }" +
                "body { margin: 0; }" +
                "table { -fs-table-paginate: paginate; width: 100%;" +
                "        border-collapse: " + borderCollapse + "; border-spacing: 0; }" +
                "td { height: 18px; padding: 2px; border: 2px solid red; }" +
                ".block { height: 40px; }" +
                "</style></head><body><table>" +
                "<tr><td rowspan=\"6\">" + blocks() + "</td><td rowspan=\"6\"></td></tr>" +
                "<tr></tr><tr></tr><tr></tr><tr></tr><tr></tr>" +
                "</table></body></html>";
    }

    private static String blocks() {
        StringBuilder blocks = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            blocks.append("<div class=\"block\"></div>");
        }
        return blocks.toString();
    }

    private static String singleCells() {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            rows.append("<tr><td></td></tr>");
        }
        return rows.toString();
    }

    private static String fullRows() {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            rows.append(row());
        }
        return rows.toString();
    }

    private static String row() {
        return "<tr><td></td><td></td></tr>";
    }

    /**
     * The first image row on which a solid horizontal red border is painted at x.
     */
    private static int firstBorderRow(BufferedImage image, int x) {
        for (int y = 0; y < image.getHeight(); y++) {
            if (isBorder(image, x, y)) {
                return y;
            }
        }
        return -1;
    }

    /**
     * The last image row on which a solid horizontal red border is painted at x.
     */
    private static int lastBorderRow(BufferedImage image, int x) {
        for (int y = image.getHeight() - 1; y >= 0; y--) {
            if (isBorder(image, x, y)) {
                return y;
            }
        }
        return -1;
    }

    private static boolean isBorder(BufferedImage image, int x, int y) {
        int rgb = image.getRGB(x, y);
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        return red > 150 && green < 100 && blue < 100;
    }
}
