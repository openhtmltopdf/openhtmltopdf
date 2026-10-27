package com.openhtmltopdf.pdfboxout;

import java.util.logging.Level;

import com.openhtmltopdf.util.LogMessageId;
import com.openhtmltopdf.util.XRLog;

public class FontRun {
    public final PdfBoxFontResolver.FontDescription description;

    public String string = "";
    public int spaceCharacterCount = 0;
    public int otherCharacterCount = 0;

    // The kerning after each character of the run, or null: set by PdfBoxTextRenderer.kernedRuns only.
    float[] kerning;

    public FontRun(PdfBoxFontResolver.FontDescription description) {
        this.description = description;
    }

    /**
     * The run's width in glyph space (thousandths of an em), with its kerning.
     */
    float width() {
        float width = 0f;
        try {
            width = description.getFont().getStringWidth(string);
        } catch (Exception e) {
            XRLog.log(Level.WARNING, LogMessageId.LogMessageId0Param.RENDER_BUG_FONT_DIDNT_CONTAIN_EXPECTED_CHARACTER, e);
        }
        if (kerning != null) {
            for (float adjustment : kerning) {
                width += adjustment;
            }
        }
        return width;
    }

}
