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
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.testlistener.PrintingRunner;
import com.openhtmltopdf.visualtest.TestSupport;

/**
 * A rowspan cell that crosses a page break is split as in Chrome: each fragment
 * lines up with the rows beside it, and its edges at the break have no border.
 * <p>
 * Body cells paint red borders, the rowspan cells a green background and the
 * other body cells a blue one. Header borders are black and footer cells
 * unpainted, so neither counts as cell paint.
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
        assertFragments("break between rows", html("separate"), 6, 3, 5);
    }

    @Test
    public void testCollapsedBorders() throws IOException {
        assertFragments("break between rows", html("collapse"), 6, 3, 5);
    }

    @Test
    public void testSeparatedBordersOverSeveralPages() throws IOException {
        assertFragments("rowspan over several pages", multiPageHtml("separate"), 4, 3);
    }

    @Test
    public void testCollapsedBordersOverSeveralPages() throws IOException {
        assertFragments("rowspan over several pages", multiPageHtml("collapse"), 4, 3);
    }

    @Test
    public void testSeparatedBordersInsideSplitRow() throws IOException {
        assertFragments("break inside a row", splitRowHtml("separate"), 4, 3);
    }

    @Test
    public void testCollapsedBordersInsideSplitRow() throws IOException {
        assertFragments("break inside a row", splitRowHtml("collapse"), 4, 3);
    }

    @Test
    public void testSeparatedBordersStartingMidDocument() throws IOException {
        assertFragments("rowspan starting mid-document", midDocumentHtml("separate"), 4, 3);
    }

    @Test
    public void testCollapsedBordersStartingMidDocument() throws IOException {
        assertFragments("rowspan starting mid-document", midDocumentHtml("collapse"), 4, 3);
    }

    @Test
    public void testSeparatedBordersWithRepeatedHeaderAndFooter() throws IOException {
        assertFragments("body rowspan with repeated header and footer",
                headerFooterHtml("separate"), 4, 3);
    }

    @Test
    public void testCollapsedBordersWithRepeatedHeaderAndFooter() throws IOException {
        // The collapsed edges at the breaks are shared with the header and footer.
        assertFragments("body rowspan with repeated header and footer",
                headerFooterHtml("collapse"), true, 4, 3);
    }

    @Test
    public void testSeparatedBordersWithRepeatedHeaderAndSpacing() throws IOException {
        assertFragments("rowspan after a repeated header with border spacing",
                headerSpacingHtml(), 4, 3);
    }

    @Test
    public void testSeparatedBordersOverEmptyRows() throws IOException {
        assertFragments("rowspan over empty rows", emptyRowsHtml("separate"), 4, 3);
    }

    @Test
    public void testCollapsedBordersOverEmptyRows() throws IOException {
        assertFragments("rowspan over empty rows", emptyRowsHtml("collapse"), 4, 3);
    }

    /**
     * Like Chrome, a cell that a page break splits ignores vertical-align: middle
     * and bottom, so its content starts on its first page.
     */
    @Test
    public void testSplitCellContentStartsOnFirstPage() throws IOException {
        for (String align : new String[] { "middle", "bottom" }) {
            for (String borderCollapse : new String[] { "separate", "collapse" }) {
                String content = splitCellTextHtml(align, borderCollapse);
                String description = "vertical-align: " + align + ", " + borderCollapse + " borders: ";
                checkEquals(description + "the text is on the first page",
                        true, pageText(content, 1).contains("span"));
                checkEquals(description + "the text is not on the second page",
                        false, pageText(content, 2).contains("span"));
            }
        }
    }

    private static String splitCellTextHtml(String align, String borderCollapse) {
        StringBuilder rows = new StringBuilder();
        for (int i = 1; i < 6; i++) {
            rows.append("<tr><td></td></tr>");
        }
        return document(collapse(borderCollapse), "<table>" +
                "<tr><td rowspan=\"6\" style=\"vertical-align: " + align + "; font-size: 8px;\">span</td>" +
                "<td></td></tr>" + rows +
                "</table>");
    }

    private static String pageText(String htmlContent, int page) throws IOException {
        try (PDDocument document = Loader.loadPDF(renderPdf(htmlContent))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            return stripper.getText(document);
        }
    }

    /**
     * Checks every page at the rowspan column ({@code x = PAGE_SIZE / divisor})
     * against the other columns ({@code x = column * PAGE_SIZE / divisor}): the
     * cell paint of each column starts and ends at the same y, and the rowspan
     * fragment's edges at page breaks have no border.
     */
    private void assertFragments(String description, String htmlContent,
            int divisor, int... otherColumns) throws IOException {
        assertFragments(description, htmlContent, false, divisor, otherColumns);
    }

    /**
     * As {@link #assertFragments(String, String, int, int...)}, but with
     * {@code bordersAtBreaks} the fragment edges at the breaks have a border.
     */
    private void assertFragments(String description, String htmlContent, boolean bordersAtBreaks,
            int divisor, int... otherColumns) throws IOException {
        List<BufferedImage> pages = renderPages(htmlContent);
        int rowspanX = PAGE_SIZE / divisor;
        int firstRowspanPage = -1;
        int lastRowspanPage = -1;
        for (int page = 0; page < pages.size(); page++) {
            if (firstRow(pages.get(page), rowspanX, Paint.ROWSPAN) >= 0) {
                if (firstRowspanPage < 0) {
                    firstRowspanPage = page;
                }
                lastRowspanPage = page;
            }
        }
        assertTrue(description + ": the rowspan cell crosses a page break",
                firstRowspanPage >= 0 && lastRowspanPage > firstRowspanPage);
        for (int page = 0; page < pages.size(); page++) {
            BufferedImage image = pages.get(page);
            String onPage = " on page " + (page + 1);
            int top = firstRow(image, rowspanX, Paint.CELL);
            int bottom = lastRow(image, rowspanX, Paint.CELL);
            for (int column : otherColumns) {
                int x = column * PAGE_SIZE / divisor;
                checkEquals(description + ": top edge at x=" + x + " lines up" + onPage,
                        top, firstRow(image, x, Paint.CELL));
                checkEquals(description + ": bottom edge at x=" + x + " lines up" + onPage,
                        bottom, lastRow(image, x, Paint.CELL));
            }
            // A continuing rowspan fragment is the first cell in its column, and
            // the last one on a page the cell continues past.
            if (page > firstRowspanPage && page <= lastRowspanPage) {
                checkEquals(description + ": border at the top of the fragment" + onPage,
                        bordersAtBreaks, isBorder(image, rowspanX, top));
            }
            if (page >= firstRowspanPage && page < lastRowspanPage) {
                checkEquals(description + ": border at the bottom of the fragment" + onPage,
                        bordersAtBreaks, isBorder(image, rowspanX, bottom));
            }
        }
        checkEquals(description + ": the rowspan cell's top border on its first page",
                true, isBorder(pages.get(firstRowspanPage), rowspanX,
                        firstRow(pages.get(firstRowspanPage), rowspanX, Paint.CELL)));
    }

    private void checkEquals(String message, Object expected, Object actual) {
        errors.checkSucceeds(() -> {
            assertEquals(message, expected, actual);
            return null;
        });
    }

    private static byte[] renderPdf(String htmlContent) throws IOException {
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(htmlContent, null);
        builder.toStream(pdf);
        builder.testMode(true);
        builder.run();
        return pdf.toByteArray();
    }

    private static List<BufferedImage> renderPages(String htmlContent) throws IOException {
        try (PDDocument document = Loader.loadPDF(renderPdf(htmlContent))) {
            PDFRenderer renderer = new PDFRenderer(document);
            List<BufferedImage> pages = new ArrayList<>();
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                pages.add(renderer.renderImageWithDPI(page, 96f, ImageType.RGB));
            }
            return pages;
        }
    }

    private static String document(String borderStyle, String body) {
        return "<html><head><style>" +
                "@page { size: " + PAGE_SIZE + "px " + PAGE_SIZE + "px; margin: 0; }" +
                "body { margin: 0; }" +
                "table { -fs-table-paginate: paginate; width: 100%; " + borderStyle + " }" +
                "td { height: 18px; padding: 2px; border: 2px solid red; background: #0000c8; }" +
                "td[rowspan] { background: #00aa00; }" +
                "th { height: 12px; border: 2px solid black; }" +
                "tfoot td { height: 12px; border: 0; background: none; }" +
                ".tall { height: 20px; }" +
                ".block { height: 40px; }" +
                "</style></head><body>" + body + "</body></html>";
    }

    private static String collapse(String borderCollapse) {
        return "border-collapse: " + borderCollapse + "; border-spacing: 0;";
    }

    private static String html(String borderCollapse) {
        StringBuilder rows = new StringBuilder();
        for (int i = 1; i < 6; i++) {
            rows.append("<tr><td></td><td></td></tr>");
        }
        for (int i = 0; i < 3; i++) {
            rows.append("<tr><td></td><td></td><td></td></tr>");
        }
        return document(collapse(borderCollapse), "<table>" +
                "<tr><td rowspan=\"6\"></td><td></td><td></td></tr>" + rows +
                "</table>");
    }

    private static String multiPageHtml(String borderCollapse) {
        StringBuilder rows = new StringBuilder();
        for (int i = 1; i < MULTI_PAGE_ROWS; i++) {
            rows.append("<tr><td></td></tr>");
        }
        return document(collapse(borderCollapse), "<table>" +
                "<tr><td rowspan=\"" + MULTI_PAGE_ROWS + "\"></td><td></td></tr>" + rows +
                "</table>");
    }

    private static String splitRowHtml(String borderCollapse) {
        StringBuilder tall = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            tall.append("<div class=\"tall\"></div>");
        }
        return document(collapse(borderCollapse), "<table>" +
                "<tr><td rowspan=\"2\"></td><td>" + tall + "</td></tr>" +
                "<tr><td></td></tr>" +
                "</table>");
    }

    private static String midDocumentHtml(String borderCollapse) {
        return document(collapse(borderCollapse), "<div style=\"height: 50px\"></div><table>" +
                "<tr><td rowspan=\"6\"></td><td></td></tr>" +
                singleCells() + fullRows() +
                "</table>");
    }

    private static String headerFooterHtml(String borderCollapse) {
        return document(collapse(borderCollapse), "<table>" +
                "<thead><tr><th></th><th></th></tr></thead>" +
                "<tfoot><tr><td></td><td></td></tr></tfoot>" +
                "<tbody>" +
                "<tr><td rowspan=\"6\"></td><td></td></tr>" +
                singleCells() + fullRows() +
                "</tbody></table>");
    }

    private static String headerSpacingHtml() {
        return document("border-collapse: separate; border-spacing: 4px;", "<table>" +
                "<thead><tr><th></th><th></th></tr></thead>" +
                "<tbody>" +
                "<tr><td rowspan=\"6\"></td><td></td></tr>" +
                singleCells() + fullRows() +
                "</tbody></table>");
    }

    private static String emptyRowsHtml(String borderCollapse) {
        return document(collapse(borderCollapse), "<table>" +
                "<tr><td rowspan=\"6\">" + blocks() + "</td><td rowspan=\"6\"></td></tr>" +
                "<tr></tr><tr></tr><tr></tr><tr></tr><tr></tr>" +
                "</table>");
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
            rows.append("<tr><td></td><td></td></tr>");
        }
        return rows.toString();
    }

    private enum Paint {
        /** Any body cell's border or background. */
        CELL,
        /** The rowspan cell's background. */
        ROWSPAN
    }

    private static int firstRow(BufferedImage image, int x, Paint paint) {
        for (int y = 0; y < image.getHeight(); y++) {
            if (matches(image, x, y, paint)) {
                return y;
            }
        }
        return -1;
    }

    private static int lastRow(BufferedImage image, int x, Paint paint) {
        for (int y = image.getHeight() - 1; y >= 0; y--) {
            if (matches(image, x, y, paint)) {
                return y;
            }
        }
        return -1;
    }

    private static boolean matches(BufferedImage image, int x, int y, Paint paint) {
        int rgb = image.getRGB(x, y);
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        boolean rowspanBackground = green > 120 && red < 100 && blue < 100;
        if (paint == Paint.ROWSPAN) {
            return rowspanBackground;
        }
        boolean cellBackground = blue > 150 && red < 100 && green < 100;
        return rowspanBackground || cellBackground || isBorder(image, x, y);
    }

    private static boolean isBorder(BufferedImage image, int x, int y) {
        int rgb = image.getRGB(x, y);
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        return red > 150 && green < 100 && blue < 100;
    }
}
