package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkedContentReference;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDMarkedContent;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.testlistener.PrintingRunner;

/**
 * Tests what the structure elements of a PDF/UA document hold. Grouping elements
 * (Div, Sect, ...) may hold only other structure elements, never content (#225), and
 * generated content does not get the structure element of the element it belongs to
 * (#96).
 *
 * <p>Trees are written as <code>Tag[kids]</code>, with <code>mc</code> for a piece of
 * marked content, starting below the Div of the body.
 */
@RunWith(PrintingRunner.class)
public class PdfUaStructureNestingTest {

    /**
     * A div that holds only text, on one line or wrapped over several, is one P. Text
     * mixed with inline elements is wrapped in a Span, as in a p.
     */
    @Test
    public void testDivWithTextIsOneParagraph() throws IOException {
        assertEquals("P[mc]", bodyTree("<div>One</div>"));
        assertEquals("P[Span[mc Span[mc] mc]]", bodyTree("<div>One <b>two</b> three</div>"));
        assertEquals("as in a p", "P[Span[mc Span[mc] mc]]", bodyTree("<p>One <b>two</b> three</p>"));

        String wrapped = bodyTree("<div style='width: 60px'>Some text that wraps onto several lines</div>");
        assertTrue("one P for all the lines: " + wrapped, wrapped.matches("P\\[mc( mc)+\\]"));
    }

    /**
     * A grouping element with a meaning of its own keeps it, and its text goes into a P.
     */
    @Test
    public void testSectionTextGoesIntoAParagraph() throws IOException {
        assertEquals("Sect[P[mc]]", bodyTree("<section>Text</section>"));
        assertEquals("BlockQuote[P[mc]]", bodyTree("<blockquote>Text</blockquote>"));
    }

    /**
     * Text around a block in a div becomes a P on each side of it.
     */
    @Test
    public void testTextAroundABlockBecomesParagraphs() throws IOException {
        assertEquals("Div[P[mc] P[mc] P[mc]]", bodyTree("<div>before<p>para</p>after</div>"));
        assertEquals("Div[P[mc] P[mc]]", bodyTree("<div><p>para</p>after</div>"));
    }

    /**
     * A block div of text inside a paragraph, where a P may not go, is a Span.
     */
    @Test
    public void testDivOfTextInAParagraphIsASpan() throws IOException {
        assertEquals("P[Span[mc] Span[mc] Span[mc]]", bodyTree("<p>Some <div>block</div> text</p>"));
    }

    /**
     * A positioned element, which is tagged Sect, hangs under the element of its
     * containing block, not under the document, and its text goes into a P.
     */
    @Test
    public void testPositionedElementHangsUnderItsContainingBlock() throws IOException {
        assertEquals("Sect[P[mc] Sect[P[mc]]]",
                bodyTree("<div style='position: relative'>text<div style='position: absolute; top: 40px'>abs</div></div>"));
    }

    /**
     * An absolutely positioned <code>li::before</code> is not a second LI: the list
     * items stay in their list, and nothing is left outside it (#96).
     */
    @Test
    public void testPositionedListItemMarkerStaysInItsListItem() throws IOException {
        String styles =
            "li { list-style-type: none; position: relative; }" +
            "li::before { content: ''; position: absolute; left: -12px; top: 0; width: 8px; height: 8px; background: red; }";
        assertEquals("L[LI[LBody[mc]] LI[LBody[mc]]]", bodyTree(styles, "<ul><li>First</li><li>Second</li></ul>"));

        // Generated text stays in its list item too, after the item's own text, as it
        // is painted after it.
        String withText = "li { position: relative; list-style-type: none; } li::before { content: '-'; position: absolute; left: -12px; }";
        assertEquals("L[LI[LBody[mc Sect[P[mc]]]]]", bodyTree(withText, "<ul><li>First</li></ul>"));
    }

    private static String bodyTree(String body) throws IOException {
        return bodyTree("", body);
    }

    private static String bodyTree(String styles, String body) throws IOException {
        String html =
            "<html lang='en'><head><title>Nesting</title>" +
            "<meta name='description' content='Structure nesting'/>" +
            "<style>body { margin: 0; font-family: 'TestFont'; font-size: 12px; }" + styles + "</style>" +
            "</head><body>" + body + "</body></html>";

        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(actual);
        builder.testMode(true);
        builder.usePdfUaAccessibility(true);
        builder.useFont(() -> PdfUaStructureNestingTest.class.getClassLoader().getResourceAsStream(
            "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"), "TestFont");
        builder.run();

        try (PDDocument doc = Loader.loadPDF(actual.toByteArray())) {
            PDStructureNode documentElement = (PDStructureNode) doc.getDocumentCatalog().getStructureTreeRoot().getKids().get(0);
            assertEquals("the document holds the body alone", "Document[Div[",
                    tree(documentElement).substring(0, "Document[Div[".length()));
            assertEquals("the document holds the body alone", 1, documentElement.getKids().size());
            PDStructureNode bodyElement = (PDStructureNode) documentElement.getKids().get(0);
            return kids(bodyElement);
        }
    }

    private static String tree(PDStructureNode node) {
        return ((PDStructureElement) node).getStructureType() + "[" + kids(node) + "]";
    }

    private static String kids(PDStructureNode node) {
        List<String> kids = new ArrayList<>();
        for (Object kid : node.getKids()) {
            if (kid instanceof PDStructureElement) {
                kids.add(tree((PDStructureElement) kid));
            } else if (kid instanceof Integer || kid instanceof PDMarkedContent || kid instanceof PDMarkedContentReference) {
                kids.add("mc");
            }
        }
        return String.join(" ", kids);
    }
}
