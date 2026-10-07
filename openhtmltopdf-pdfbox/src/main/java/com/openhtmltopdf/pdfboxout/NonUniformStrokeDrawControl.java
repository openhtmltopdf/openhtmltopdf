package com.openhtmltopdf.pdfboxout;

import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;

import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D;
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2DDrawControlDefault;

/**
 * Draws strokes correctly under a non-uniform scale or a skew.
 * <p>
 * {@link PdfBoxGraphics2D} transforms the shape and writes the line width as a
 * single number, scaled by the transform's horizontal factor only. Under
 * {@code scale(1, 20)} a horizontal line then keeps a hairline width instead of
 * becoming 20 times thicker. For such transforms we fill the stroke's outline
 * instead, which the transform maps exactly. Uniformly scaled strokes are drawn
 * as before.
 */
class NonUniformStrokeDrawControl extends PdfBoxGraphics2DDrawControlDefault {
    static final NonUniformStrokeDrawControl INSTANCE = new NonUniformStrokeDrawControl();

    private static final double EPSILON = 1e-6;

    @Override
    public Shape transformShapeBeforeDraw(Shape shape, IDrawControlEnv env) {
        PdfBoxGraphics2D g = env.getGraphics();
        Stroke stroke = g.getStroke();

        // A zero width means "thinnest line the device can draw", which an
        // outline can't express.
        if (stroke instanceof BasicStroke &&
            ((BasicStroke) stroke).getLineWidth() > 0 &&
            !isUniform(g.getTransform())) {
            g.fill(stroke.createStrokedShape(shape));
            return null;
        }

        return shape;
    }

    /**
     * True if the transform scales lengths equally in every direction, i.e. its
     * columns are orthogonal and of equal length (rotation, reflection and
     * translation are fine).
     */
    static boolean isUniform(AffineTransform tf) {
        double ax = tf.getScaleX(), ay = tf.getShearY();
        double bx = tf.getShearX(), by = tf.getScaleY();
        double lenA = ax * ax + ay * ay;
        double lenB = bx * bx + by * by;
        double tolerance = EPSILON * Math.max(1, Math.max(lenA, lenB));
        return Math.abs(lenA - lenB) <= tolerance &&
               Math.abs(ax * bx + ay * by) <= tolerance;
    }
}
