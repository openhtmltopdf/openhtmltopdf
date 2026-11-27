package com.openhtmltopdf.pdfboxout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Level;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.openhtmltopdf.util.ExifOrientation;
import com.openhtmltopdf.util.LogMessageId;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import com.openhtmltopdf.extend.FSImage;
import com.openhtmltopdf.util.XRLog;

public class PdfBoxImage implements FSImage {
    private byte[] _bytes;
    private final String _uri;

    private float _intrinsicWidth;
    private float _intrinsicHeight;

    private PDImageXObject _xobject;

    /** The EXIF orientation, applied when drawing. Width and height are as shown. */
    private final int _orientation;

    public PdfBoxImage(byte[] image, String uri) throws IOException {
        _bytes = image;
        _uri = uri;
        _orientation = ExifOrientation.read(image);

        ImageReader reader = null;
        
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(_bytes))){
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);

            if (readers.hasNext()) {
                reader = readers.next();
                reader.setInput(in);
                _intrinsicWidth = reader.getWidth(0);
                _intrinsicHeight = reader.getHeight(0);
            } else {
                XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.LOAD_UNRECOGNIZED_IMAGE_FORMAT_FOR_URI, uri);
                // TODO: Avoid throw here.
                throw new IOException("Unrecognized Image format");
            }
        } finally {
            if (reader != null)
                reader.dispose();
        }

        if (ExifOrientation.swapsAxes(_orientation)) {
            float width = _intrinsicWidth;
            _intrinsicWidth = _intrinsicHeight;
            _intrinsicHeight = width;
        }
    }

    public PdfBoxImage(byte[] bytes, String uri, float width, float height,
            PDImageXObject xobject) {
        this(bytes, uri, width, height, xobject, ExifOrientation.NORMAL);
    }

    /**
     * @param width the width as shown, after applying orientation.
     * @param height the height as shown, after applying orientation.
     * @param orientation the EXIF orientation, see {@link ExifOrientation}.
     */
    public PdfBoxImage(byte[] bytes, String uri, float width, float height,
            PDImageXObject xobject, int orientation) {
        this._bytes = bytes;
        this._uri = uri;
        this._intrinsicWidth = width;
        this._intrinsicHeight = height;
        this._xobject = xobject;
        this._orientation = orientation;
    }

    /**
     * @return a copy, so that scaling it does not change this image.
     */
    public PdfBoxImage copy() {
        return new PdfBoxImage(_bytes, _uri, _intrinsicWidth, _intrinsicHeight, _xobject, _orientation);
    }

    @Override
    public FSImage withoutImageOrientation() {
        if (_orientation == ExifOrientation.NORMAL) {
            return this;
        }
        boolean swap = ExifOrientation.swapsAxes(_orientation);
        return new PdfBoxImage(_bytes, _uri,
                swap ? _intrinsicHeight : _intrinsicWidth,
                swap ? _intrinsicWidth : _intrinsicHeight,
                _xobject, ExifOrientation.NORMAL);
    }

    /**
     * @return the EXIF orientation to draw the image with, see {@link ExifOrientation}.
     */
    public int getOrientation() {
        return _orientation;
    }

    public FSImage scaleToOutputResolution(float dotsPerPixel) {
        float factor = dotsPerPixel;
        float width = _intrinsicWidth;
        float height = _intrinsicHeight;

        if (factor != 1.0f) {
            width *= factor;
            height *= factor;
        }

        return new PdfBoxImage(_bytes, _uri, width, height, _xobject, _orientation);
    }

    @Override
    public int getWidth() {
        return (int) _intrinsicWidth;
    }

    @Override
    public int getHeight() {
        return (int) _intrinsicHeight;
    }

    @Override
    public void scale(int width, int height) {
        float setWidth, setHeight;

        if (width != -1) {
            setWidth = width;

            if (height == -1 && _intrinsicWidth != 0) {
                // Use the width ratio to set the height.
                setHeight = (int) ((setWidth / _intrinsicWidth) * _intrinsicHeight);
            } else {
                setHeight = height;
            }
        } else if (height != -1) {
            setHeight = height;

            if (_intrinsicHeight != 0) {
                // Use the height ratio to set the width.
                setWidth = (int) ((setHeight / _intrinsicHeight) * _intrinsicWidth);
            } else {
                setWidth = 0;
            }
        } else {
            setWidth = _intrinsicWidth;
            setHeight = _intrinsicHeight;
        }

        // TODO: Make this class immutable.
        this._intrinsicWidth = setWidth;
        this._intrinsicHeight = setHeight;
    }

    public byte[] getBytes() {
        return _bytes;
    }

    public void clearBytes() {
        _bytes = null;
    }
    
    public PDImageXObject getXObject() {
        return _xobject;
    }
    
    public void setXObject(PDImageXObject xobject) {
        _xobject = xobject;
    }
    
    public String getUri() {
        return _uri;
    }
}
