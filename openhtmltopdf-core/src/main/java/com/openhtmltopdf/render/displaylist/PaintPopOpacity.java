package com.openhtmltopdf.render.displaylist;

/**
 * Ends the group started by the matching {@link PaintPushOpacity}.
 */
public final class PaintPopOpacity implements DisplayListOperation {
    public static final PaintPopOpacity INSTANCE = new PaintPopOpacity();

    private PaintPopOpacity() {
    }
}
