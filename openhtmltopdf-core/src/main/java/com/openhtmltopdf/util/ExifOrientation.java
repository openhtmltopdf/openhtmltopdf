package com.openhtmltopdf.util;

import java.awt.geom.AffineTransform;

/**
 * The orientation a camera stores in the EXIF data of a JPEG file, which browsers
 * apply by default (CSS {@code image-orientation: from-image}). Read without any
 * image library: only the orientation tag of the first image file directory is
 * needed.
 */
public class ExifOrientation {
    /** The image is stored the way it is shown. */
    public static final int NORMAL = 1;

    private static final int TAG_ORIENTATION = 0x0112;
    private static final int TYPE_SHORT = 3;

    private ExifOrientation() {
    }

    /**
     * @return the EXIF orientation of a JPEG image, 1 to 8, or {@link #NORMAL} when
     * the bytes are not a JPEG, carry no orientation or can not be read.
     */
    public static int read(byte[] bytes) {
        try {
            return readJpeg(bytes);
        } catch (RuntimeException e) {
            // Broken or cut off metadata: show the image as it is stored.
            return NORMAL;
        }
    }

    private static int readJpeg(byte[] b) {
        if (b == null || b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) {
            return NORMAL;
        }

        int pos = 2;
        while (pos + 4 <= b.length) {
            if ((b[pos] & 0xFF) != 0xFF) {
                return NORMAL;
            }
            int marker = b[pos + 1] & 0xFF;
            if (marker == 0xFF) {
                // Fill byte.
                pos++;
                continue;
            }
            if (marker == 0xD9 || marker == 0xDA) {
                // End of image or start of the image data: no more metadata.
                return NORMAL;
            }
            int length = ((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF);
            int start = pos + 4;
            int end = pos + 2 + length;
            if (length < 2 || end > b.length) {
                return NORMAL;
            }
            if (marker == 0xE1 && end - start >= 6 &&
                b[start] == 'E' && b[start + 1] == 'x' && b[start + 2] == 'i' &&
                b[start + 3] == 'f' && b[start + 4] == 0 && b[start + 5] == 0) {
                return readTiff(b, start + 6, end);
            }
            pos = end;
        }
        return NORMAL;
    }

    private static int readTiff(byte[] b, int tiff, int end) {
        boolean little;
        if (b[tiff] == 'I' && b[tiff + 1] == 'I') {
            little = true;
        } else if (b[tiff] == 'M' && b[tiff + 1] == 'M') {
            little = false;
        } else {
            return NORMAL;
        }
        if (u16(b, tiff + 2, little) != 42) {
            return NORMAL;
        }

        int ifd = tiff + (int) u32(b, tiff + 4, little);
        if (ifd < tiff + 8 || ifd + 2 > end) {
            return NORMAL;
        }

        int count = u16(b, ifd, little);
        for (int i = 0; i < count; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > end) {
                return NORMAL;
            }
            if (u16(b, entry, little) == TAG_ORIENTATION) {
                if (u16(b, entry + 2, little) != TYPE_SHORT) {
                    return NORMAL;
                }
                int value = u16(b, entry + 8, little);
                return value >= 1 && value <= 8 ? value : NORMAL;
            }
        }
        return NORMAL;
    }

    private static int u16(byte[] b, int pos, boolean little) {
        int b0 = b[pos] & 0xFF;
        int b1 = b[pos + 1] & 0xFF;
        return little ? (b1 << 8) | b0 : (b0 << 8) | b1;
    }

    private static long u32(byte[] b, int pos, boolean little) {
        long hi = u16(b, pos, little);
        long lo = u16(b, pos + 2, little);
        return little ? (lo << 16) | hi : (hi << 16) | lo;
    }

    /**
     * @return whether the orientation turns the image by 90 degrees, so that the shown
     * width is the stored height.
     */
    public static boolean swapsAxes(int orientation) {
        return orientation >= 5 && orientation <= 8;
    }

    /**
     * The transform from the stored image to the shown one, both with y pointing down
     * and the origin at the top left. For orientations that swap axes the stored image
     * is height wide and width high.
     *
     * @param width the width of the image as shown.
     * @param height the height of the image as shown.
     */
    public static AffineTransform transform(int orientation, double width, double height) {
        switch (orientation) {
        case 2: // Mirrored left to right.
            return new AffineTransform(-1, 0, 0, 1, width, 0);
        case 3: // Turned by 180 degrees.
            return new AffineTransform(-1, 0, 0, -1, width, height);
        case 4: // Mirrored top to bottom.
            return new AffineTransform(1, 0, 0, -1, 0, height);
        case 5: // Mirrored along the top-left to bottom-right diagonal.
            return new AffineTransform(0, 1, 1, 0, 0, 0);
        case 6: // Needs turning by 90 degrees clockwise.
            return new AffineTransform(0, 1, -1, 0, width, 0);
        case 7: // Mirrored along the top-right to bottom-left diagonal.
            return new AffineTransform(0, -1, -1, 0, width, height);
        case 8: // Needs turning by 90 degrees counter-clockwise.
            return new AffineTransform(0, -1, 1, 0, 0, height);
        default:
            return new AffineTransform();
        }
    }
}
