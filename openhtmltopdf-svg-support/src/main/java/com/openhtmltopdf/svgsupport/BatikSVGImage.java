package com.openhtmltopdf.svgsupport;

import java.awt.Point;
import java.util.Set;
import java.util.logging.Level;

import com.openhtmltopdf.extend.UserAgentCallback;
import com.openhtmltopdf.util.LogMessageId;
import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.constants.ValueConstants;
import com.openhtmltopdf.css.parser.CSSParser;
import com.openhtmltopdf.css.parser.PropertyValue;
import com.openhtmltopdf.css.sheet.StylesheetInfo;
import com.openhtmltopdf.css.style.CssContext;
import com.openhtmltopdf.css.style.derived.LengthValue;
import com.openhtmltopdf.extend.OutputDevice;
import com.openhtmltopdf.extend.SVGDrawer.SVGImage;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.RenderingContext;
import com.openhtmltopdf.svgsupport.PDFTranscoder.OpenHtmlFontResolver;
import com.openhtmltopdf.util.XRLog;

public class BatikSVGImage implements SVGImage {
    private final static int DEFAULT_SVG_WIDTH = 400;
    private final static int DEFAULT_SVG_HEIGHT = 400;
    private final static Point DEFAULT_DIMENSIONS = new Point(DEFAULT_SVG_WIDTH, DEFAULT_SVG_HEIGHT);

    private final Element svgElement;
    private final double dotsPerPixel;
    private OpenHtmlFontResolver fontResolver;
    private final PDFTranscoder pdfTranscoder;
    private UserAgentCallback userAgentCallback;

    /**
     * True for an SVG used as a CSS image rather than as a replaced element. Such an image
     * has no box behind it, is not styled by the containing document and may well be drawn
     * many times over, for example as a repeating background.
     */
    private final boolean standalone;

    /**
     * Whether the SVG root carries an absolute width and height, ie. whether the image has a
     * size of its own. Only meaningful for a standalone image.
     */
    private final boolean sizedByAttributes;

    /**
     * Width to height ratio for an SVG without a size of its own, or 0 when unknown.
     */
    private final float intrinsicRatio;

    /**
     * Creates an image for an SVG used as a CSS image, ie. a <code>background-image</code>
     * or a <code>list-style-image</code>, where there is no box to size the image against.
     *
     * <p>The size the SVG asks for is used unless the caller asks for a specific size, in
     * which case the SVG is fitted into it the same way a browser fits a background image
     * into the size given by <code>background-size</code>.</p>
     *
     * @param targetWidth the width to draw at in dots, or -1 for the SVG's own width
     * @param targetHeight the height to draw at in dots, or -1 for the SVG's own height
     */
    public BatikSVGImage(Element svgElement, double targetWidth, double targetHeight, double dotsPerPixel) {
        this.svgElement = svgElement;
        this.dotsPerPixel = dotsPerPixel;
        this.standalone = true;
        this.pdfTranscoder = new PDFTranscoder(null, dotsPerPixel, targetWidth, targetHeight);

        // The same image at the same size is drawn once and then stamped, rather than
        // emitted again for every tile of a repeating background.
        this.pdfTranscoder.setReuseKey(this);

        // An absolute width and height on the root make the SVG an image with a size of its
        // own, which is scaled as a whole when CSS asks for another size. Without them the
        // size has to come from CSS, and the viewport is set to it when drawing so that
        // percentages inside the SVG resolve against it, as they do in a browser.
        this.sizedByAttributes = hasAbsoluteSize(svgElement);
        this.intrinsicRatio = parseViewBoxRatio(svgElement);

        Point dimensions = parseDimensions(svgElement, null, null);

        double w = targetWidth >= 0 ? targetWidth / dotsPerPixel : dimensions.x;
        double h = targetHeight >= 0 ? targetHeight / dotsPerPixel : dimensions.y;

        // Sizing via transcoding hints rather than by setting width and height on the
        // element: the element is shared between every use of this image, so it must not
        // be modified here.
        this.pdfTranscoder.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, (float) w);
        this.pdfTranscoder.addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT, (float) h);
        this.pdfTranscoder.setImageSize((float) w, (float) h);
    }

    public BatikSVGImage(
            Element svgElement, Box box,
            double cssWidth, double cssHeight,
            double cssMaxWidth, double cssMaxHeight,
            double dotsPerPixel,
            CssContext ctx) {

        this.svgElement = svgElement;
        this.dotsPerPixel = dotsPerPixel;
        this.standalone = false;
        this.sizedByAttributes = false;   // Not used, this image is sized by its box.
        this.intrinsicRatio = 0;
        this.pdfTranscoder = new PDFTranscoder(box, dotsPerPixel, cssWidth, cssHeight);

        if (cssWidth >= 0) {
            this.pdfTranscoder.addTranscodingHint(
                    SVGAbstractTranscoder.KEY_WIDTH,
                    (float) (cssWidth / dotsPerPixel));
        }
        if (cssHeight >= 0) {
            this.pdfTranscoder.addTranscodingHint(
                    SVGAbstractTranscoder.KEY_HEIGHT,
                    (float) (cssHeight / dotsPerPixel));
        }
        if (cssMaxWidth >= 0) {
            this.pdfTranscoder.addTranscodingHint(
                    SVGAbstractTranscoder.KEY_MAX_WIDTH,
                    (float) (cssMaxWidth / dotsPerPixel));
        }
        if (cssMaxHeight >= 0) {
            this.pdfTranscoder.addTranscodingHint(
                    SVGAbstractTranscoder.KEY_MAX_HEIGHT,
                    (float) (cssMaxHeight / dotsPerPixel));
        }
        
        Point dimensions = parseDimensions(svgElement, box, ctx);
        double w;
        double h;
        
        if (dimensions == DEFAULT_DIMENSIONS) {
            if (cssWidth >= 0 && cssHeight >= 0) {
                w = (cssWidth / dotsPerPixel);
                h = (cssHeight / dotsPerPixel);
            } else if (cssWidth >= 0) {
                w = (cssWidth / dotsPerPixel);
                h = DEFAULT_SVG_HEIGHT;
            } else if (cssHeight >= 0) {
                w = DEFAULT_SVG_WIDTH;
                h = (cssHeight / dotsPerPixel);
            } else {
                w = DEFAULT_SVG_WIDTH;
                h = DEFAULT_SVG_HEIGHT;
            }
        } else {
            w = dimensions.x;
            h = dimensions.y;
        }
        
        svgElement.setAttribute("width", Integer.toString((int) w));
        svgElement.setAttribute("height", Integer.toString((int) h));
        this.pdfTranscoder.setImageSize((float) w, (float) h);
    }

    @Override
    public int getIntrinsicWidth() {
        return (int) (this.pdfTranscoder.getWidth() * this.dotsPerPixel);
    }

    @Override
    public int getIntrinsicHeight() {
        return (int) (this.pdfTranscoder.getHeight() * this.dotsPerPixel);
    }

    public void setFontResolver(OpenHtmlFontResolver fontResolver) {
        this.fontResolver = fontResolver;
    }
    
    public void setSecurityOptions(boolean allowScripts, boolean allowExternalResources, Set<String> allowedProtocols) {
        this.pdfTranscoder.setSecurityOptions(allowScripts, allowExternalResources, allowedProtocols);
        this.pdfTranscoder.addTranscodingHint(SVGAbstractTranscoder.KEY_EXECUTE_ONLOAD, allowScripts);
    }

    public void setUserAgentCallback(UserAgentCallback userAgentCallback) {
        this.userAgentCallback = userAgentCallback;
    }

    private Integer parseLength(
            String attrValue,
            CSSName property,
            Box box,
            CssContext ctx) {

        try {
            return Integer.valueOf(attrValue);
        } catch (NumberFormatException e) {
            if (box == null) {
                // A CSS image has no box to resolve a relative length such as 2em or 50%
                // against, so only an absolute pixel length can be honoured here. Anything
                // else falls back to the default size, as an unparseable length does.
                return parsePixelLength(attrValue, property);
            }

            // Not a plain number, probably has a unit (px, cm, etc), so
            // try with css parser.

            CSSParser parser = new CSSParser((uri, msg) ->
                XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.GENERAL_INVALID_INTEGER_PASSED_AS_DIMENSION_FOR_SVG, attrValue));

            PropertyValue value = parser.parsePropertyValue(property, StylesheetInfo.AUTHOR, attrValue);

            if (value == null) {
                // CSS parser couldn't deal with value either.
                return null;
            }

            LengthValue length = new LengthValue(box.getStyle(), property, value);
            float pixels = length.getFloatProportionalTo(property, box.getContainingBlock() == null ? 0 : box.getContainingBlock().getWidth(), ctx);

            return (int) Math.round(pixels / this.dotsPerPixel);
        }
    }

    /**
     * Parses a length in an absolute unit, such as <code>10</code>, <code>10.5px</code> or
     * <code>2cm</code> - the only kind that can be resolved without a box.
     *
     * @return the length in pixels, or null for a length that can not be used, which includes
     * a percentage and a length relative to a font size.
     */
    private static Integer parsePixelLength(String attrValue) {
        String value = attrValue.trim();

        if (value.isEmpty()) {
            return null;
        }

        try {
            // A number without a unit is user units, which for the root element are pixels.
            // The CSS parser rejects those, since CSS wants a unit on everything but zero.
            return toPositivePixels(Float.parseFloat(value));
        } catch (NumberFormatException e) {
            // Has a unit, so let the CSS parser pick it apart.
        }

        // Quiet: an attribute that is not an absolute length is reported by the caller, which
        // knows whether it matters.
        CSSParser parser = new CSSParser((uri, msg) -> { });
        PropertyValue parsed = parser.parsePropertyValue(CSSName.WIDTH, StylesheetInfo.AUTHOR, value);

        if (parsed == null) {
            return null;
        }

        Float pixels = ValueConstants.absoluteLengthToPixels(parsed.getPrimitiveType(), parsed.getFloatValue());

        return pixels != null ? toPositivePixels(pixels) : null;
    }

    private static Integer toPositivePixels(float pixels) {
        return pixels > 0 ? Integer.valueOf(Math.round(pixels)) : null;
    }

    /**
     * As {@link #parsePixelLength(String)}, but reports a length we can not resolve although a
     * browser could, such as <code>2em</code>. A percentage is not reported: it means the image
     * has no size of its own, which is normal and is handled by sizing it from CSS.
     */
    private static Integer parsePixelLength(String attrValue, CSSName property) {
        Integer pixels = parsePixelLength(attrValue);

        if (pixels == null && !attrValue.trim().endsWith("%")) {
            XRLog.log(Level.WARNING, LogMessageId.LogMessageId2Param.GENERAL_UNUSABLE_DIMENSION_FOR_SVG_AS_CSS_IMAGE, property, attrValue);
        }

        return pixels;
    }

    /**
     * Whether the root carries an absolute width and height, ie. whether the SVG is an image
     * with a size of its own. Asked quietly, since an SVG without one is perfectly normal and
     * is sized from its viewBox or by CSS instead.
     */
    private static boolean hasAbsoluteSize(Element e) {
        return parsePixelLength(e.getAttribute("width")) != null &&
               parsePixelLength(e.getAttribute("height")) != null;
    }

    /**
     * The width to height ratio from the <code>viewBox</code>, or 0 when there is no usable
     * one. This is what gives an SVG without a size of its own a shape to be scaled to.
     */
    private static float parseViewBoxRatio(Element e) {
        String[] viewBox = e.getAttribute("viewBox").trim().split("[\\s,]+");

        if (viewBox.length != 4) {
            return 0;
        }

        try {
            float width = Float.parseFloat(viewBox[2]);
            float height = Float.parseFloat(viewBox[3]);

            return width > 0 && height > 0 ? width / height : 0;
        } catch (NumberFormatException e2) {
            return 0;
        }
    }

    @Override
    public boolean hasIntrinsicSize() {
        // Only meaningful for a CSS image. A replaced element is sized by its box, so it
        // always has a size.
        return !this.standalone || this.sizedByAttributes;
    }

    @Override
    public float getIntrinsicRatio() {
        return this.intrinsicRatio;
    }

    private Point parseWidthHeightAttributes(Element e, Box box, CssContext ctx) {
        String widthAttr = e.getAttribute("width");
        Integer width = widthAttr.isEmpty() ? null :
            parseLength(widthAttr, CSSName.WIDTH, box, ctx);

        String heightAttr = e.getAttribute("height");
        Integer height = heightAttr.isEmpty() ? null : 
            parseLength(heightAttr, CSSName.HEIGHT, box, ctx);

        if (width != null && height != null) {
            return new Point(width, height);
        }

        return DEFAULT_DIMENSIONS;
    }

    private Point parseDimensions(Element e, Box box, CssContext ctx) {
        String viewBoxAttr = e.getAttribute("viewBox");
        String[] splitViewBox = viewBoxAttr.split("\\s+");
        if (splitViewBox.length != 4) {
            return parseWidthHeightAttributes(e, box, ctx);
        }
        try {
            int viewBoxWidth = Integer.parseInt(splitViewBox[2]);
            int viewBoxHeight = Integer.parseInt(splitViewBox[3]);

            return new Point(viewBoxWidth, viewBoxHeight);
        } catch (NumberFormatException ex) {
            return parseWidthHeightAttributes(e, box, ctx);
        }
    }

    @Override
    public void drawSVG(OutputDevice outputDevice, RenderingContext ctx,
            double x, double y) {

        OpenHtmlFontResolver fontResolver = this.fontResolver;
        if (fontResolver == null) {
            XRLog.log(Level.INFO, LogMessageId.LogMessageId0Param.GENERAL_IMPORT_FONT_FACE_RULES_HAS_NOT_BEEN_CALLED);
            fontResolver = new OpenHtmlFontResolver();
        }

        pdfTranscoder.setRenderingParameters(outputDevice, ctx, x, y,
                fontResolver, userAgentCallback);

        if (this.standalone && pdfTranscoder.hasGraphicsNode()) {
            // Already built by an earlier draw of this same image at this same size, so
            // just paint it again at the new position. Saves rebuilding the SVG for every
            // tile of a repeating background.
            pdfTranscoder.paintGraphicsNode();
            return;
        }

        // A CSS image is an independent document, so it is not styled by the document that
        // uses it. Only a replaced svg element picks up styles from the containing page.
        String styles = ctx != null ? ctx.getCss().getCSSForAllDescendants(svgElement) : null;

        try {
            DOMImplementation impl = SVGDOMImplementation
                    .getDOMImplementation();
            Document newDocument = impl.createDocument(
                    SVGDOMImplementation.SVG_NAMESPACE_URI, "svg", null);

            if (styles != null && !styles.isEmpty()) {
                Element styleElem = newDocument.createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, "style");
                Text styleText = newDocument.createTextNode(styles);
                styleElem.appendChild(styleText);
                newDocument.getDocumentElement().appendChild(styleElem);
            }

            for (int i = 0; i < svgElement.getChildNodes().getLength(); i++) {
                Node importedNode = newDocument
                        .importNode(svgElement.getChildNodes().item(i), true);
                newDocument.getDocumentElement().appendChild(importedNode);
            }

            // Copy attributes such as viewBox to the new SVG document.
            for (int i = 0; i < svgElement.getAttributes().getLength(); i++) {
                Node importedAttr = svgElement.getAttributes().item(i);
                newDocument.getDocumentElement().setAttribute(
                        importedAttr.getNodeName(),
                        importedAttr.getNodeValue());
            }

            if (this.standalone && !this.sizedByAttributes) {
                // The SVG has no size of its own, so CSS supplies the viewport. Set it on the
                // copy so that percentages inside the SVG resolve against it, exactly as they
                // do in a browser. Without this, a root width such as 50% is resolved by Batik
                // against a viewport of its own choosing, which produces nonsense geometry.
                // An SVG that does carry an absolute width and height is left alone: it is a
                // sized image, and is scaled as a whole rather than laid out again.
                // The copy is written to rather than the element itself, which is shared
                // between every use of this image.
                newDocument.getDocumentElement().setAttribute("width", Integer.toString((int) this.pdfTranscoder.getWidth()));
                newDocument.getDocumentElement().setAttribute("height", Integer.toString((int) this.pdfTranscoder.getHeight()));
            }

            TranscoderInput in = new TranscoderInput(newDocument);
            pdfTranscoder.transcode(in, null);
        } catch (TranscoderException e) {
            XRLog.log(Level.WARNING, LogMessageId.LogMessageId0Param.EXCEPTION_SVG_COULD_NOT_DRAW, e);
        }
    }
}
