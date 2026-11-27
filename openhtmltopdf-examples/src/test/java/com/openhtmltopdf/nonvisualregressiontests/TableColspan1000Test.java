package com.openhtmltopdf.nonvisualregressiontests;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.testlistener.PrintingRunner;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests that a row with {@code colspan="1000"} does not shift the upper
 * rows of a 4-column table to the left.
 */
@RunWith(PrintingRunner.class)
public class TableColspan1000Test {

    @Test
    public void testColspan1000DoesNotShiftUpperRows() throws IOException {
        List<TextPosition> control = glyphs("");
        List<TextPosition> withWide = glyphs("<tr><td colspan='1000'>WIDE</td></tr>");

        for (String label : new String[]{"A", "B", "C", "D"}) {
            assertEquals("x of '" + label + "'", xPositionOf(control, label), xPositionOf(withWide, label), 1.0f);
        }
    }

    private static float xPositionOf(List<TextPosition> glyphs, String label) {
        return glyphs.stream()
                .filter(g -> g.getUnicode().equals(label))
                .findFirst()
                .get()
                .getXDirAdj();
    }

    private static List<TextPosition> glyphs(String extraRow) throws IOException {
        String html =
            "<html><head><style>" +
            "@page { size: 800px 600px; margin: 0; }" +
            "body { margin: 0; font-family: 'TestFont'; font-size: 20px; }" +
            "table { border-collapse: collapse; width: 400px; }" +
            "td { padding: 0 5px; border: 1px solid black; text-align: center; }" +
            "</style></head><body>" +
            "<table>" +
            "<colgroup>" +
            "<col style='width:100px'/><col style='width:100px'/>" +
            "<col style='width:100px'/><col style='width:100px'/>" +
            "</colgroup>" +
            "<tr><td>A</td><td>B</td><td>C</td><td>D</td></tr>" +
            extraRow +
            "</table>" +
            "</body></html>";

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(os);
        builder.testMode(true);
        builder.useFont(() -> TableColspan1000Test.class.getClassLoader().getResourceAsStream(
                "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"), "TestFont");
        builder.run();

        try (PDDocument doc = Loader.loadPDF(os.toByteArray())) {
            List<TextPosition> result = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    result.addAll(positions);
                }
            };
            stripper.setSortByPosition(true);
            stripper.getText(doc);
            return result;
        }
    }
}
