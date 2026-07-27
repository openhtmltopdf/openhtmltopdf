package com.openhtmltopdf.render;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.w3c.dom.Element;

import com.openhtmltopdf.extend.FSImage;
import com.openhtmltopdf.extend.OutputDevice;
import com.openhtmltopdf.extend.SVGDrawer;
import com.openhtmltopdf.extend.SVGDrawer.SVGImage;
import com.openhtmltopdf.util.LogMessageId;
import com.openhtmltopdf.util.XRLog;

/**
 * An image that is drawn by the SVG drawer rather than decoded into a bitmap, so that SVGs
 * used as CSS images (<code>background-image</code>, <code>list-style-image</code>, ...)
 * stay vector art in the output.
 *
 * <p>Unlike a replaced <code>svg</code> element there is no box to size against here: the
 * image starts out at the size the SVG asks for and is resized by the layout, for example by
 * <code>background-size</code>. Because of that the size is mutable, and each user gets its
 * own instance via {@link #copy()} while the expensive parts - the parsed element and the
 * already built images - stay shared.</p>
 *
 * <p>Output devices recognize this class in <code>drawImage</code> and hand it back to the
 * SVG drawer instead of treating it as a bitmap.</p>
 */
public class FSSVGImage implements FSImage {
    private final SharedSVG shared;

    /** Size to draw at, in dots. */
    private float width;
    private float height;

    /**
     * Whether the size above is still only a placeholder, because the SVG has no size of its
     * own and CSS has not settled one yet.
     */
    private boolean awaitingSize;

    private FSSVGImage(SharedSVG shared, float width, float height, boolean awaitingSize) {
        this.shared = shared;
        this.width = width;
        this.height = height;
        this.awaitingSize = awaitingSize;
    }

    /**
     * Creates an image for the given SVG root element.
     *
     * @return the image, or null if the drawer can not build standalone SVG images or the
     * SVG turned out to have no usable size.
     */
    public static FSSVGImage create(Element svgElement, SVGDrawer drawer, double dotsPerPixel, String uri) {
        SVGImage intrinsic = drawer.buildStandaloneSVGImage(svgElement, uri, -1, -1, dotsPerPixel);

        if (intrinsic == null) {
            // The drawer does not do CSS images and has said so.
            return null;
        }

        if (intrinsic.getIntrinsicWidth() <= 0 || intrinsic.getIntrinsicHeight() <= 0) {
            XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.LOAD_SVG_IMAGE_HAS_NO_USABLE_SIZE, uri);
            return null;
        }

        SharedSVG shared = new SharedSVG(svgElement, drawer, dotsPerPixel, uri, intrinsic.getIntrinsicRatio());
        float width = intrinsic.getIntrinsicWidth();
        float height = intrinsic.getIntrinsicHeight();

        shared.images.put(sizeKey(width, height), intrinsic);

        return new FSSVGImage(shared, width, height, !intrinsic.hasIntrinsicSize());
    }

    /**
     * Returns an independently sizable view of this image. The parsed SVG and any images
     * already built from it are shared with the original, so copying is cheap and drawing
     * the same SVG in more than one place does not rebuild it.
     */
    public FSSVGImage copy() {
        return new FSSVGImage(this.shared, this.width, this.height, this.awaitingSize);
    }

    /**
     * Sizes an SVG that has no size of its own to the area it is being painted into, which is
     * what CSS calls the default object size. A ratio from the SVG's <code>viewBox</code> is
     * respected, so the image is made as large as it can be inside the area without changing
     * its shape. Does nothing once the size is settled, or for an SVG that has a size already.
     *
     * <p>This is how a browser sizes a background image whose SVG is written in percentages or
     * carries no width and height at all: the image fills the background positioning area
     * unless <code>background-size</code> says otherwise.</p>
     */
    public void sizeToDefaultObjectSize(int availableWidth, int availableHeight) {
        if (!this.awaitingSize || availableWidth <= 0 || availableHeight <= 0) {
            return;
        }

        float ratio = this.shared.intrinsicRatio;

        if (ratio > 0) {
            // As large as possible inside the area while keeping the shape, ie. 'contain'.
            float scaled = availableWidth / ratio;

            if (scaled <= availableHeight) {
                this.width = availableWidth;
                this.height = scaled;
            } else {
                this.width = availableHeight * ratio;
                this.height = availableHeight;
            }
        } else {
            this.width = availableWidth;
            this.height = availableHeight;
        }

        this.awaitingSize = false;
    }

    /**
     * Whether this image has no size of its own and is still waiting for CSS to give it one.
     */
    public boolean isAwaitingSize() {
        return this.awaitingSize;
    }

    @Override
    public int getWidth() {
        return (int) this.width;
    }

    @Override
    public int getHeight() {
        return (int) this.height;
    }

    @Override
    public void scale(int width, int height) {
        // An explicit size settles an image that was waiting for one.
        if (width != -1 || height != -1) {
            this.awaitingSize = false;
        }

        // Same rules as the bitmap images: -1 for one of the dimensions means keep the
        // aspect ratio, -1 for both means leave the image alone.
        if (width != -1) {
            float setWidth = width;
            float setHeight;

            if (height == -1 && this.width != 0) {
                setHeight = (setWidth / this.width) * this.height;
            } else if (height == -1) {
                setHeight = this.height;
            } else {
                setHeight = height;
            }

            this.width = setWidth;
            this.height = setHeight;
        } else if (height != -1) {
            float setHeight = height;

            this.width = this.height != 0 ? (setHeight / this.height) * this.width : 0;
            this.height = setHeight;
        }
    }

    /**
     * Draws the SVG at its current size, with the top left corner at the given position
     * in dots.
     */
    public void drawSVG(OutputDevice outputDevice, double x, double y) {
        SVGImage image = this.shared.imageAt(this.width, this.height);

        if (image != null) {
            // There is no rendering context here: a CSS image is an independent document,
            // so unlike a replaced svg element it is not styled by the containing page.
            image.drawSVG(outputDevice, null, x, y);
        }
    }

    /**
     * The URI this image was loaded from, for logging.
     */
    public String getUri() {
        return this.shared.uri;
    }

    /**
     * The root <code>svg</code> element, for callers that would rather draw the SVG
     * themselves than go through this image.
     */
    public Element getSVGElement() {
        return this.shared.svgElement;
    }

    private static Long sizeKey(float width, float height) {
        return (((long) Float.floatToIntBits(width)) << 32) | (Float.floatToIntBits(height) & 0xFFFFFFFFL);
    }

    /**
     * The parts of the image that survive copying and resizing.
     */
    private static class SharedSVG {
        private final Element svgElement;
        private final SVGDrawer drawer;
        private final double dotsPerPixel;
        private final String uri;

        /** Width to height ratio, or 0 when the SVG does not imply one. */
        private final float intrinsicRatio;

        /**
         * The images built so far, by size. A repeating background asks for the same size
         * over and over, and rebuilding the SVG for every single tile is expensive.
         */
        private final Map<Long, SVGImage> images = new HashMap<>();

        SharedSVG(Element svgElement, SVGDrawer drawer, double dotsPerPixel, String uri, float intrinsicRatio) {
            this.svgElement = svgElement;
            this.drawer = drawer;
            this.dotsPerPixel = dotsPerPixel;
            this.uri = uri;
            this.intrinsicRatio = intrinsicRatio;
        }

        SVGImage imageAt(float width, float height) {
            Long key = sizeKey(width, height);
            SVGImage image = this.images.get(key);

            if (image == null && !this.images.containsKey(key)) {
                image = this.drawer.buildStandaloneSVGImage(this.svgElement, this.uri, width, height, this.dotsPerPixel);
                // Also remember a failure, so we do not retry for every tile.
                this.images.put(key, image);
            }

            return image;
        }
    }
}
