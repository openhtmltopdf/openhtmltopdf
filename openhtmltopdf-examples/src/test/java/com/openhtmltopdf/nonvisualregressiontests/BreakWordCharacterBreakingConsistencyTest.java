package com.openhtmltopdf.nonvisualregressiontests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Test;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.testlistener.PrintingRunner;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

/**
 * A cell under {@code word-wrap: break-word} whose token does not fit in word-breaking mode but
 * fits in character-breaking mode. Before the fix, {@code Breaker.doBreakCharacters} returned
 * {@code CHAR_BREAKING_FINISHED} with the {@code needsNewLine} flag the word-breaking attempt had
 * set still on, and {@code LineBreakContext.checkConsistency} threw an {@link AssertionError}
 * whenever assertions were enabled (as Surefire enables them for this test).
 */
@RunWith(PrintingRunner.class)
public class BreakWordCharacterBreakingConsistencyTest {

    @Test
    public void testCharacterBreakingFinishedClearsNeedsNewLine() throws IOException {
        String html =
            "<html><head><style>" +
            "table.box td { border: .5pt solid #B9C4C1; padding: 1.2mm 1.6mm; word-wrap: break-word; }" +
            "</style></head><body>" +
            "<table class='box'><tr>" +
            "<td><div class='cap'><span class='n'>GUARANTEE REFERENCE NO.</span></div></td>" +
            "<td><div class='cap'><span class='n'>AMOUNT OF GUARANTEE</span></div></td>" +
            "<td><div class='cap'><span class='n'></span></div><div class='v' dir='ltr'>01 Aug 2026</div></td>" +
            "<td class='row'><span class='n'>33</span><span class='lbl'>FEES</span></td>" +
            "<td class='row' colspan='8' dir='ltr'>1250.00</td>" +
            "</tr></table></body></html>";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, null);
        builder.toStream(out);
        builder.run();

        assertTrue(out.size() > 0);
    }
}
