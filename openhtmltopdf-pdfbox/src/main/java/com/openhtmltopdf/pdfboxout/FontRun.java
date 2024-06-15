package com.openhtmltopdf.pdfboxout;

public class FontRun {
    final PdfBoxFontResolver.FontDescription description;

    String string = "";
    int spaceCharacterCount = 0;
    int otherCharacterCount = 0;

    public FontRun(PdfBoxFontResolver.FontDescription description) {
        this.description = description;
    }
}
