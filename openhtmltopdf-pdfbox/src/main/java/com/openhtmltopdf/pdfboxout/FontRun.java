package com.openhtmltopdf.pdfboxout;

public class FontRun {
    public final PdfBoxFontResolver.FontDescription description;

    public String string = "";
    public int spaceCharacterCount = 0;
    public int otherCharacterCount = 0;

    public FontRun(PdfBoxFontResolver.FontDescription description) {
        this.description = description;
    }
}
