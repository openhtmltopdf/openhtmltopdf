package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.openhtmltopdf.pdfboxout.PdfBoxRawPDFontMetrics;
import com.openhtmltopdf.testlistener.PrintingRunner;

@RunWith(PrintingRunner.class)
public class PdfBoxRawPDFontMetricsTest {
    /**
     * Tests that ascent/descent come from the descriptor's typographic values,
     * not the font bounding box.
     */
    @Test
    public void testDescriptorMetricsPreferred() throws IOException {
        PDType1Font font = new PDType1Font(FontName.HELVETICA);
        PdfBoxRawPDFontMetrics metrics = PdfBoxRawPDFontMetrics.fromPdfBox(font, font.getFontDescriptor());

        assertEquals("Helvetica AFM Ascender", 718f, metrics._ascent, 0.1f);
        assertEquals("Helvetica AFM Descender", 207f, metrics._descent, 0.1f);
    }

    /**
     * Tests that fonts whose descriptor has no usable ascent/descent (such as the
     * built-in Symbol and ZapfDingbats) fall back to the font bounding box.
     */
    @Test
    public void testBoundingBoxFallbackForUnusableDescriptor() throws IOException {
        PDType1Font symbol = new PDType1Font(FontName.SYMBOL);
        PdfBoxRawPDFontMetrics symbolMetrics = PdfBoxRawPDFontMetrics.fromPdfBox(symbol, symbol.getFontDescriptor());

        assertEquals("Symbol bounding box top", 1010f, symbolMetrics._ascent, 0.1f);
        assertEquals("Symbol bounding box bottom", 293f, symbolMetrics._descent, 0.1f);

        PDType1Font dingbats = new PDType1Font(FontName.ZAPF_DINGBATS);
        PdfBoxRawPDFontMetrics dingbatsMetrics = PdfBoxRawPDFontMetrics.fromPdfBox(dingbats, dingbats.getFontDescriptor());

        assertEquals("ZapfDingbats bounding box top", 820f, dingbatsMetrics._ascent, 0.1f);
        assertEquals("ZapfDingbats bounding box bottom", 143f, dingbatsMetrics._descent, 0.1f);
    }
    /**
     * Tests that a descriptor with only one usable value falls back to the bounding
     * box for both, so ascent and descent always come from the same source.
     */
    @Test
    public void testBoundingBoxFallbackForPartiallyUsableDescriptor() throws IOException {
        PDType1Font font = new PDType1Font(FontName.HELVETICA);

        PDFontDescriptor descriptor = new PDFontDescriptor(new COSDictionary());
        descriptor.setAscent(700f);
        descriptor.setDescent(0f);
        descriptor.setFontBoundingBox(font.getFontDescriptor().getFontBoundingBox());

        PdfBoxRawPDFontMetrics metrics = PdfBoxRawPDFontMetrics.fromPdfBox(font, descriptor);

        assertEquals("Helvetica bounding box top", 931f, metrics._ascent, 0.1f);
        assertEquals("Helvetica bounding box bottom", 225f, metrics._descent, 0.1f);
    }
}
