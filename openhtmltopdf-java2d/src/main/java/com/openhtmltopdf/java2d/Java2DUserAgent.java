package com.openhtmltopdf.java2d;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;

import javax.imageio.ImageIO;

import com.openhtmltopdf.extend.FSImage;
import com.openhtmltopdf.java2d.image.AWTFSImage;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceType;
import com.openhtmltopdf.render.FSSVGImage;
import com.openhtmltopdf.resource.ImageResource;
import com.openhtmltopdf.swing.NaiveUserAgent;
import com.openhtmltopdf.util.LogMessageId;
import com.openhtmltopdf.util.OpenUtil;
import com.openhtmltopdf.util.SVGUriDetector;
import com.openhtmltopdf.util.XRLog;

public class Java2DUserAgent extends NaiveUserAgent {
    private double _dotsPerPixel = 1;

    /**
     * Dots per CSS pixel of the output, used to size SVGs used as CSS images.
     */
    public void setDotsPerPixel(double dotsPerPixel) {
        this._dotsPerPixel = dotsPerPixel;
    }

    /**
     * Retrieves the image located at the given URI. It's assumed the URI does point to an image--the URI will
     * be accessed (using the set HttpStreamFactory or URL::openStream), opened, read and then passed into the JDK image-parsing routines.
     * The result is packed up into an ImageResource for later consumption.
     *
     * @param uri Location of the image source.
     * @return An ImageResource containing the image.
     */
    @Override
    public ImageResource getImageResource(String uri, ExternalResourceType type) {
        ImageResource ir;

        if (!checkAccessAllowed(uri, type, ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI)) {
            return null;
        }

        String resolved = _resolver.resolveURI(this._baseUri, uri);

        if (!checkAccessAllowed(resolved, type, ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI)) {
            return null;
        }

        if (resolved == null) {
            XRLog.log(Level.INFO, LogMessageId.LogMessageId2Param.LOAD_URI_RESOLVER_REJECTED_LOADING_AT_URI, "image resource", uri);
            return null;
        }

        // First, we check the internal per run cache.
        ir = _imageCache.get(resolved);
        if (ir != null) {
            if (ir.getImage() instanceof FSSVGImage) {
                // The size of an SVG image is set by whoever uses it, so hand out a copy.
                return new ImageResource(ir.getImageUri(), ((FSSVGImage) ir.getImage()).copy());
            }
            return ir;
        }

        // Finally we fetch from the network or file, etc.
        try (InputStream is = openStream(resolved)) {
            if (is != null) {
                byte[] content = OpenUtil.readAll(is);

                if (SVGUriDetector.isSvgUri(resolved) ||
                    SVGUriDetector.looksLikeSvgContent(content)) {

                    FSImage svgImage = buildSVGImage(resolved, content, _dotsPerPixel);

                    if (svgImage == null) {
                        // Cache the failure so that a background image used on every page is
                        // not loaded, and warned about, once per box painted.
                        ImageResource failed = new ImageResource(resolved, null);
                        _imageCache.put(resolved, failed);
                        return failed;
                    }

                    _imageCache.put(resolved, new ImageResource(resolved, svgImage));

                    // Hand out a copy, so that sizing this one does not change the cached image.
                    return new ImageResource(resolved, ((FSSVGImage) svgImage).copy());
                }

                BufferedImage img = ImageIO.read(new ByteArrayInputStream(content));

                if (img == null) {
                    throw new IOException("ImageIO.read() returned null");
                }

                AWTFSImage fsImage2 = (AWTFSImage) AWTFSImage.createImage(img);

                ir = new ImageResource(resolved, fsImage2);
                _imageCache.put(resolved, ir);

                return ir;
            }
        } catch (FileNotFoundException e) {
            XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_CANT_READ_IMAGE_FILE_FOR_URI_NOT_FOUND, resolved);
        } catch (IOException e) {
            XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_CANT_READ_IMAGE_FILE_FOR_URI, uri, e);
        }

        // Failed.
        return new ImageResource(resolved, null);
    }
}
