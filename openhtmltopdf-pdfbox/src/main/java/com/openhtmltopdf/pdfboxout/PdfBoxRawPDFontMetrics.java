package com.openhtmltopdf.pdfboxout;

import java.io.IOException;

import org.apache.fontbox.afm.FontMetrics;
import org.apache.fontbox.ttf.PostScriptTable;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.font.PDCIDFont;
import org.apache.pdfbox.pdmodel.font.PDCIDFontType2;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import com.openhtmltopdf.extend.FSCacheValue;

public class PdfBoxRawPDFontMetrics implements FSCacheValue {
    public final float _ascent;
    public final float _descent;
    public final float _strikethroughOffset;
    public final float _strikethroughThickness;
    public final float _underlinePosition;
    public final float _underlineThickness;
    public final float _typoDescent;

    public PdfBoxRawPDFontMetrics(float ascent, float descent, float strikethroughOffset, float strikethroughThickness,
            float underlinePosition, float underlineThickness, float typoDescent) {
        this._ascent = ascent;
        this._descent = descent;
        this._strikethroughOffset = strikethroughOffset;
        this._strikethroughThickness = strikethroughThickness;
        this._underlinePosition = underlinePosition;
        this._underlineThickness = underlineThickness;
        this._typoDescent = typoDescent;
    }

    /**
     * @deprecated Use the constructor taking a typographic descent instead.
     */
    @Deprecated
    public PdfBoxRawPDFontMetrics(float ascent, float descent, float strikethroughOffset, float strikethroughThickness,
            float underlinePosition, float underlineThickness) {
        this(ascent, descent, strikethroughOffset, strikethroughThickness,
                underlinePosition, underlineThickness, underlinePosition);
    }

    public static PdfBoxRawPDFontMetrics fromPdfBox(PDFont font, PDFontDescriptor descriptor) throws IOException {
        // Size inline boxes from the descriptor's typographic ascent/descent.
        // Some fonts ship descriptors without usable ascent/descent (eg. the built-in Symbol
        // and ZapfDingbats); fall back to the bounding box unless both values are usable.
        float ascent = descriptor.getAscent();
        float descent = -descriptor.getDescent();
        if (!(ascent > 0f && descent > 0f)) {
            ascent = font.getBoundingBox().getUpperRightY();
            descent = -font.getBoundingBox().getLowerLeftY();
        }

        // The typographic descent marks the bottom of the em box. It is used
        // for text-underline-position: under and as the fallback underline
        // position for fonts without underline metrics.
        float typoDescent = -descriptor.getDescent();

        // The font descriptor of a PDF has no underline entries, so fall back
        // to the typographic descent when the font program has no usable ones.
        float[] underline = underlineMetricsFromFontProgram(font);
        float underlinePosition = underline != null && underline[0] > 0f ? underline[0] : typoDescent;
        float underlineThickness = underline != null && underline[1] > 0f ? underline[1] : 50f;

        return new PdfBoxRawPDFontMetrics(
            ascent,
            descent,
            -descriptor.getFontBoundingBox().getUpperRightY() / 3f, // Strikethrough offset
            100f,                                                   // FIXME: Strikethrough thickness
            underlinePosition,
            underlineThickness,
            typoDescent);
    }

    /**
     * The designed underline position (positive below the baseline) and
     * thickness, in 1/1000 em units, or null if the font program does not
     * provide them. The PDF font descriptor lacks these entries, but the
     * embedded TrueType/OpenType font carries them in its post table and the
     * built-in Standard 14 fonts in their AFM metrics.
     */
    private static float[] underlineMetricsFromFontProgram(PDFont font) {
        try {
            if (font instanceof PDType0Font) {
                PDCIDFont descendant = ((PDType0Font) font).getDescendantFont();
                if (descendant instanceof PDCIDFontType2) {
                    TrueTypeFont ttf = ((PDCIDFontType2) descendant).getTrueTypeFont();
                    PostScriptTable post = ttf != null ? ttf.getPostScript() : null;
                    if (post != null && ttf.getUnitsPerEm() > 0) {
                        float scale = 1000f / ttf.getUnitsPerEm();
                        return new float[] {
                            -post.getUnderlinePosition() * scale,
                            post.getUnderlineThickness() * scale };
                    }
                }
            } else {
                FontMetrics afm = Standard14Fonts.getAFM(font.getName());
                if (afm != null) {
                    return new float[] { -afm.getUnderlinePosition(), afm.getUnderlineThickness() };
                }
            }
        } catch (IOException e) {
            // Fall back to the descent based underline position.
        }
        return null;
    }

    @Override
    public int weight() {
        return 7 * 4; // Seven floats.
    }
}
