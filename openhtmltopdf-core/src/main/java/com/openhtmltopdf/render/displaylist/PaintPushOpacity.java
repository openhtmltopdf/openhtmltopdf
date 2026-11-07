package com.openhtmltopdf.render.displaylist;

/**
 * Starts painting a layer with <code>opacity</code> below 1: what is painted until
 * the matching {@link PaintPopOpacity} is composited as one group, at this opacity.
 */
public final class PaintPushOpacity implements DisplayListOperation {
    private final float _opacity;

    public PaintPushOpacity(float opacity) {
        _opacity = opacity;
    }

    public float getOpacity() {
        return _opacity;
    }
}
