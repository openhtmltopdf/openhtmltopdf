package com.openhtmltopdf.extend;

import java.awt.FontFormatException;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Level;

import org.w3c.dom.Element;

import com.openhtmltopdf.css.sheet.FontFaceRule;
import com.openhtmltopdf.css.style.CssContext;
import com.openhtmltopdf.layout.SharedContext;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.RenderingContext;
import com.openhtmltopdf.util.LogMessageId;
import com.openhtmltopdf.util.XRLog;

public interface SVGDrawer extends Closeable {
    void importFontFaceRules(List<FontFaceRule> fontFaces,
            SharedContext shared);

    SVGImage buildSVGImage(Element svgElement, Box box, CssContext cssContext, double cssWidth,
            double cssHeight, double dotsPerPixel);

    /**
     * Builds an SVG image that is not tied to a replaced element, so that SVGs can be used
     * as CSS images, for example as a <code>background-image</code> or a
     * <code>list-style-image</code>. In that position there is no box to size the image
     * against, so the image is sized by the SVG itself or by the caller.
     *
     * <p>The default implementation draws nothing, so that implementations written against an
     * earlier version of this interface keep compiling and working. It logs a warning so that
     * a silently undrawn image is at least visible.</p>
     *
     * @param svgElement the root <code>svg</code> element
     * @param uri where the SVG was loaded from, for logging
     * @param targetWidth the width to draw at, in dots, or -1 to use the intrinsic width
     * @param targetHeight the height to draw at, in dots, or -1 to use the intrinsic height
     * @param dotsPerPixel dots per CSS pixel
     *
     * @return the image, or null if this drawer only supports SVGs inside a replaced element.
     */
    default SVGImage buildStandaloneSVGImage(Element svgElement, String uri, double targetWidth,
            double targetHeight, double dotsPerPixel) {
        XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.GENERAL_CSS_IMAGE_IGNORED_BY_SVG_DRAWER, uri);
        return null;
    }

    default void withUserAgent(UserAgentCallback userAgentCallback) {}

    interface SVGImage {
        int getIntrinsicWidth();

        int getIntrinsicHeight();

        /**
         * Whether the SVG has a size of its own, ie. an absolute width and height on its root
         * element. An SVG sized in percentages, or with no width and height at all, has none:
         * as a CSS image it is sized by CSS instead, from <code>background-size</code> or from
         * the area it is painted into, which is what a browser does.
         *
         * <p>The default implementation says yes, so that {@link #getIntrinsicWidth()} and
         * {@link #getIntrinsicHeight()} keep being used as before.</p>
         */
        default boolean hasIntrinsicSize() {
            return true;
        }

        /**
         * The width to height ratio of an SVG that has no size of its own, taken from its
         * <code>viewBox</code>. Used to size the image when CSS leaves the size open.
         *
         * @return the ratio, or 0 when there is none to be had.
         */
        default float getIntrinsicRatio() {
            return 0;
        }

        void drawSVG(OutputDevice outputDevice, RenderingContext ctx,
                double x, double y);
    }

    void addFontFile(File fontFile, String family, Integer weight, FontStyle style) throws IOException, FontFormatException;

    /**
     * Adds a font that is supplied as a stream rather than as a file, so that fonts
     * registered with <code>useFont(FSSupplier&lt;InputStream&gt;, ...)</code> can be used
     * by SVG (or MathML) content too.
     *
     * <p>The default implementation ignores the font, so that implementations written
     * against an earlier version of this interface keep compiling and working. It logs a
     * warning so that a silently ignored font is at least visible.</p>
     */
    default void addFontStream(FSSupplier<InputStream> supplier, String family, Integer weight, FontStyle style) throws IOException, FontFormatException {
        XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.GENERAL_FONT_ADDED_AS_STREAM_IGNORED_BY_SVG_DRAWER, family);
    }
}
