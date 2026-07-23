package com.openhtmltopdf.extend;

import java.awt.FontFormatException;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.w3c.dom.Element;

import com.openhtmltopdf.css.sheet.FontFaceRule;
import com.openhtmltopdf.css.style.CssContext;
import com.openhtmltopdf.layout.SharedContext;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.RenderingContext;

public interface SVGDrawer extends Closeable {
    void importFontFaceRules(List<FontFaceRule> fontFaces,
            SharedContext shared);

    SVGImage buildSVGImage(Element svgElement, Box box, CssContext cssContext, double cssWidth,
            double cssHeight, double dotsPerPixel);

    default void withUserAgent(UserAgentCallback userAgentCallback) {}

    interface SVGImage {
        int getIntrinsicWidth();

        int getIntrinsicHeight();

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
     * against an earlier version of this interface keep compiling and working.</p>
     */
    default void addFontStream(FSSupplier<InputStream> supplier, String family, Integer weight, FontStyle style) throws IOException, FontFormatException {
    }
}
