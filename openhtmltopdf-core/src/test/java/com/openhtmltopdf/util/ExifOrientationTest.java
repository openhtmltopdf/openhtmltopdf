package com.openhtmltopdf.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import org.junit.Test;

public class ExifOrientationTest {
    /** A JPEG start with an APP0 (JFIF), then APP1 (Exif) with the given TIFF data. */
    private static byte[] jpeg(byte[] tiff) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF); out.write(0xD8);
        byte[] app0 = { (byte) 0xFF, (byte) 0xE0, 0, 7, 'J', 'F', 'I', 'F', 0 };
        out.write(app0, 0, app0.length);
        int length = 2 + 6 + tiff.length;
        out.write(0xFF); out.write(0xE1); out.write(length >> 8); out.write(length & 0xFF);
        byte[] exif = { 'E', 'x', 'i', 'f', 0, 0 };
        out.write(exif, 0, exif.length);
        out.write(tiff, 0, tiff.length);
        // Start of scan: no metadata after this.
        out.write(0xFF); out.write(0xDA); out.write(0); out.write(2);
        return out.toByteArray();
    }

    private static byte[] tiff(boolean little, int tag, int type, int value) {
        if (little) {
            return new byte[] { 'I', 'I', 42, 0, 8, 0, 0, 0, 1, 0,
                (byte) tag, (byte) (tag >> 8), (byte) type, 0, 1, 0, 0, 0,
                (byte) value, 0, 0, 0, 0, 0, 0, 0 };
        }
        return new byte[] { 'M', 'M', 0, 42, 0, 0, 0, 8, 0, 1,
            (byte) (tag >> 8), (byte) tag, 0, (byte) type, 0, 0, 0, 1,
            0, (byte) value, 0, 0, 0, 0, 0, 0 };
    }

    @Test
    public void testReadsBothByteOrders() {
        for (int o = 1; o <= 8; o++) {
            assertEquals(o, ExifOrientation.read(jpeg(tiff(true, 0x0112, 3, o))));
            assertEquals(o, ExifOrientation.read(jpeg(tiff(false, 0x0112, 3, o))));
        }
    }

    @Test
    public void testNormalWhenMissingOrInvalid() {
        assertEquals(1, ExifOrientation.read(null));
        assertEquals(1, ExifOrientation.read(new byte[0]));
        // A PNG signature.
        assertEquals(1, ExifOrientation.read(new byte[] { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10 }));
        // Another tag, a wrong type, a value out of range.
        assertEquals(1, ExifOrientation.read(jpeg(tiff(true, 0x0110, 3, 6))));
        assertEquals(1, ExifOrientation.read(jpeg(tiff(true, 0x0112, 4, 6))));
        assertEquals(1, ExifOrientation.read(jpeg(tiff(true, 0x0112, 3, 9))));
    }

    @Test
    public void testNormalWhenCutOff() {
        byte[] full = jpeg(tiff(false, 0x0112, 3, 6));
        for (int length = 0; length < full.length - 4; length++) {
            byte[] cut = Arrays.copyOf(full, length);
            int orientation = ExifOrientation.read(cut);
            assertTrue(orientation == 1 || orientation == 6);
        }
    }

    @Test
    public void testTransformMovesTopLeftOfStoredImage() {
        // Where the top left corner of the stored image ends up in a shown box 30 wide, 20 high.
        double[][] expected = {
            { 0, 0 }, { 30, 0 }, { 30, 20 }, { 0, 20 },
            { 0, 0 }, { 30, 0 }, { 30, 20 }, { 0, 20 } };
        for (int o = 1; o <= 8; o++) {
            AffineTransform t = ExifOrientation.transform(o, 30, 20);
            Point2D p = t.transform(new Point2D.Double(0, 0), null);
            assertEquals("x for " + o, expected[o - 1][0], p.getX(), 0.001);
            assertEquals("y for " + o, expected[o - 1][1], p.getY(), 0.001);
        }
        // Orientation 6 is turned clockwise: the stored bottom left goes to the top left.
        Point2D p = ExifOrientation.transform(6, 30, 20).transform(new Point2D.Double(0, 30), null);
        assertEquals(0, p.getX(), 0.001);
        assertEquals(0, p.getY(), 0.001);
    }

    @Test
    public void testSwapsAxes() {
        for (int o = 1; o <= 4; o++) {
            assertFalse(ExifOrientation.swapsAxes(o));
        }
        for (int o = 5; o <= 8; o++) {
            assertTrue(ExifOrientation.swapsAxes(o));
        }
    }
}
