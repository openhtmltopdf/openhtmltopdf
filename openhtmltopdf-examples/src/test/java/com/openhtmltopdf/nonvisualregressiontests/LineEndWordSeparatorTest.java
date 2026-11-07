package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.testlistener.PrintingRunner;

/**
 * Tests that the text of a line that ends before a forced break, or whose trailing
 * space is trimmed at a wrap, is drawn with a space after its last word. Readers
 * that find words in the content stream, as Adobe Reader does, otherwise join that
 * word and the first word of the next line into one (#97).
 *
 * <p>Each string is the text of one text-showing operation, in content stream order.
 */
@RunWith(PrintingRunner.class)
public class LineEndWordSeparatorTest {

    @Test
    public void testLineBeforeABreakEndsInASpace() throws IOException {
        assertEquals(list("I ", "am ", "good, but ", "end"), shownStrings("", "I<br/>am<br/>good, but<br/>end"));
    }

    @Test
    public void testBoldWordBeforeABreakEndsInASpace() throws IOException {
        assertEquals(list("one ", "two ", "three"), shownStrings("", "one<br/><b>two</b><br/>three"));
    }

    @Test
    public void testLinesBeforeConsecutiveBreaksEndInASpace() throws IOException {
        assertEquals(list("is ", "connected"), shownStrings("", "is<br/><br/>connected"));
    }

    /**
     * A right-aligned line wraps with its trailing space trimmed, so that it does
     * not count for the alignment; it is still drawn, past the line's end.
     */
    @Test
    public void testTrimmedSpaceAtAWrapIsStillDrawn() throws IOException {
        assertEquals(list("alpha ", "beta"), shownStrings("width: 60px; text-align: right;", "alpha beta"));
    }

    /**
     * A line that ends at a soft hyphen ends in the hyphen, inside a word.
     */
    @Test
    public void testNoSpaceAfterASoftHyphenBreak() throws IOException {
        List<String> shown = shownStrings("width: 50px;", "alpha&#173;beta");
        assertEquals("lines", 2, shown.size());
        assertFalse("no space after the first line: " + shown, shown.get(0).endsWith(" "));
    }

    private static List<String> list(String... strings) {
        List<String> result = new ArrayList<>();
        for (String string : strings) {
            result.add(string);
        }
        return result;
    }

    private static List<String> shownStrings(String css, String body) throws IOException {
        String html =
            "<html><head><style>" +
            "@page { size: 300px 300px; margin: 0; }" +
            "body { margin: 0; font-family: 'TestFont'; font-size: 20px; }" +
            "p { margin: 0; " + css + " }" +
            "</style></head><body><p>" + body + "</p></body></html>";

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(os);
        builder.testMode(true);
        builder.useFont(() -> LineEndWordSeparatorTest.class.getClassLoader().getResourceAsStream(
                "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"), "TestFont");
        builder.run();

        try (PDDocument doc = Loader.loadPDF(os.toByteArray())) {
            List<String> shown = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper() {
                private StringBuilder current;

                @Override
                public void beginText() {
                    current = new StringBuilder();
                }

                @Override
                protected void processTextPosition(TextPosition text) {
                    current.append(text.getUnicode());
                }

                @Override
                public void endText() {
                    if (current.length() > 0) {
                        shown.add(current.toString());
                    }
                }
            };
            stripper.getText(doc);
            return shown;
        }
    }
}
