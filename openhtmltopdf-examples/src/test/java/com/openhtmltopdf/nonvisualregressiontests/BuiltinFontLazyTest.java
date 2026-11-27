package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.fontbox.FontBoxFont;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.CIDFontMapping;
import org.apache.pdfbox.pdmodel.font.FontMapper;
import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.apache.pdfbox.pdmodel.font.FontMapping;
import org.apache.pdfbox.pdmodel.font.PDCIDSystemInfo;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.testlistener.PrintingRunner;
import com.openhtmltopdf.visualtest.TestSupport;

/**
 * Tests that the built-in fonts are only created when text is drawn with them: creating
 * a PDType1Font makes PDFBox scan the system fonts and write its font cache, hundreds of
 * milliseconds the first time. Issue #40.
 */
@RunWith(PrintingRunner.class)
public class BuiltinFontLazyTest {
    private FontMapper original;
    private final AtomicInteger lookups = new AtomicInteger();

    @Before
    public void countFontMapperLookups() {
        original = FontMappers.instance();
        FontMappers.set(new FontMapper() {
            @Override
            public FontMapping<TrueTypeFont> getTrueTypeFont(String baseFont, PDFontDescriptor fontDescriptor) {
                lookups.incrementAndGet();
                return original.getTrueTypeFont(baseFont, fontDescriptor);
            }

            @Override
            public FontMapping<FontBoxFont> getFontBoxFont(String baseFont, PDFontDescriptor fontDescriptor) {
                lookups.incrementAndGet();
                return original.getFontBoxFont(baseFont, fontDescriptor);
            }

            @Override
            public CIDFontMapping getCIDFont(String baseFont, PDFontDescriptor fontDescriptor, PDCIDSystemInfo cidSystemInfo) {
                lookups.incrementAndGet();
                return original.getCIDFont(baseFont, fontDescriptor, cidSystemInfo);
            }
        });
    }

    @After
    public void restoreFontMapper() {
        FontMappers.set(original);
    }

    private static byte[] render(String html) throws IOException {
        return render(html, true);
    }

    private static byte[] render(String html, boolean standardFonts) throws IOException {
        TestSupport.makeFontFiles();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.useFont(new File("target/test/visual-tests/Karla-Bold.ttf"), "TestFont");
        builder.useStandard14Fonts(standardFonts);
        builder.toStream(os);
        builder.run();
        return os.toByteArray();
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc).trim();
        }
    }

    @Test
    public void testSuppliedFontOnlyCreatesNoBuiltinFont() throws IOException {
        byte[] pdf = render("<html><body style=\"font-family: TestFont\"><p>Hello <b>world</b></p></body></html>");

        assertEquals("PDFBox font mapper lookups", 0, lookups.get());
        assertEquals("Hello world", text(pdf));
    }

    @Test
    public void testBuiltinFontCreatedWhenUsed() throws IOException {
        byte[] pdf = render("<html><body style=\"font-family: Helvetica\"><p>Hello</p></body></html>");

        assertTrue("PDFBox font mapper lookups", lookups.get() > 0);
        assertEquals("Hello", text(pdf));
    }

    @Test
    public void testBuiltinFallbackCreatedForMissingGlyph() throws IOException {
        // Karla has no Greek letters: the built-in fallback font is looked at for them.
        render("<html><body style=\"font-family: TestFont\"><p>Hello \u03b1</p></body></html>");

        assertTrue("PDFBox font mapper lookups", lookups.get() > 0);
    }

    @Test
    public void testNoStandardFontsNoLookupForMissingGlyph() throws IOException {
        byte[] pdf = render("<html><body><p style=\"font-family: TestFont\">Hello \u03b1</p>" +
                "<p style=\"font-family: serif\">Serif</p></body></html>", false);

        assertEquals("PDFBox font mapper lookups", 0, lookups.get());
        // As in PDF/A: a missing character is drawn as #, text with no font is left out.
        assertEquals("Hello #", text(pdf));
    }
}
