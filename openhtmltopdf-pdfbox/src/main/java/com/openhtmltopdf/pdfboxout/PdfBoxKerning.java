package com.openhtmltopdf.pdfboxout;

import java.io.IOException;
import java.util.logging.Level;

import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.KerningSubtable;
import org.apache.fontbox.ttf.KerningTable;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.font.PDCIDFontType2;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import com.openhtmltopdf.util.LogMessageId;
import com.openhtmltopdf.util.XRLog;

/**
 * Pair kerning read from a TrueType font's <code>kern</code> table: the
 * horizontal subtable in format 0, which is what fontbox parses. Kerning held
 * only in an OpenType <code>GPOS</code> table is not applied.
 *
 * <p>Values are in glyph space, thousandths of an em, the unit of
 * {@link PDFont#getStringWidth(String)} and of the numbers in a TJ array.
 * Pairs are looked up between neighbouring code points of one string drawn in
 * one font, so the text renderer and the output device, given the same string,
 * agree on every value.
 *
 * <p>Only fonts loaded as a {@link PDType0Font} over a TrueType program are
 * kerned, which covers every font added through the renderer builder. The
 * built-in PDF fonts are left as they are.
 */
final class PdfBoxKerning {
    static final PdfBoxKerning NONE = new PdfBoxKerning(null, null, 0f);

    private final KerningSubtable _pairs;
    private final CmapLookup _cmap;
    private final float _scale;

    private PdfBoxKerning(KerningSubtable pairs, CmapLookup cmap, float scale) {
        this._pairs = pairs;
        this._cmap = cmap;
        this._scale = scale;
    }

    /**
     * The kerning of <code>font</code>, or {@link #NONE} when the font has no
     * horizontal format 0 <code>kern</code> subtable.
     */
    static PdfBoxKerning of(PDFont font) {
        if (!(font instanceof PDType0Font)) {
            return NONE;
        }

        PDType0Font type0 = (PDType0Font) font;

        if (!(type0.getDescendantFont() instanceof PDCIDFontType2)) {
            return NONE;
        }

        try {
            TrueTypeFont ttf = ((PDCIDFontType2) type0.getDescendantFont()).getTrueTypeFont();
            KerningTable table = ttf.getKerning();
            KerningSubtable pairs = table != null ? table.getHorizontalKerningSubtable() : null;
            CmapLookup cmap = type0.getCmapLookup();

            if (pairs == null || cmap == null || ttf.getUnitsPerEm() <= 0) {
                return NONE;
            }

            return new PdfBoxKerning(pairs, cmap, 1000f / ttf.getUnitsPerEm());
        } catch (IOException e) {
            XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_COULD_NOT_READ_FONT_KERNING, font.getName(), e);
            return NONE;
        }
    }

    /**
     * The kerning between each code point of <code>s</code> and the next one,
     * indexed by code point (the last entry is always zero), or null when no pair
     * in the string is kerned.
     */
    float[] adjustments(String s) {
        if (_pairs == null) {
            return null;
        }

        int[] codePoints = s.codePoints().toArray();
        float[] result = null;

        for (int i = 0; i + 1 < codePoints.length; i++) {
            int kerning = _pairs.getKerning(
                    _cmap.getGlyphId(codePoints[i]), _cmap.getGlyphId(codePoints[i + 1]));

            if (kerning != 0) {
                if (result == null) {
                    result = new float[codePoints.length];
                }
                result[i] = kerning * _scale;
            }
        }

        return result;
    }

    /**
     * The kerning between two code points drawn next to each other.
     */
    float pair(int left, int right) {
        if (_pairs == null) {
            return 0f;
        }

        return _pairs.getKerning(_cmap.getGlyphId(left), _cmap.getGlyphId(right)) * _scale;
    }
}
