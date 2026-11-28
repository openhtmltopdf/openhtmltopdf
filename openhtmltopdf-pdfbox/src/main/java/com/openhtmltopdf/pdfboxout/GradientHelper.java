package com.openhtmltopdf.pdfboxout;

import java.util.List;
import java.util.logging.Level;
import java.awt.geom.*;
import java.awt.Rectangle;
import java.awt.Shape;

import com.openhtmltopdf.css.parser.FSRGBColor;
import com.openhtmltopdf.css.style.derived.FSLinearGradient;
import com.openhtmltopdf.css.style.derived.FSRadialGradient;
import com.openhtmltopdf.css.style.derived.FSLinearGradient.StopPoint;
import com.openhtmltopdf.util.LogMessageId;
import com.openhtmltopdf.util.XRLog;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBoolean;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.common.function.PDFunctionType3;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShading;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShadingType2;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShadingType3;

public class GradientHelper {
    /**
     * This method is used for creating linear gradient with its components.
     * 
     * @return shading for rendering linear gradient in PDF
     */
    public static PDShading createLinearGradient(PdfBoxFastOutputDevice od, AffineTransform transform, FSLinearGradient gradient, Shape bounds)
    {
        PDShadingType2 shading = new PDShadingType2(new COSDictionary());
        shading.setShadingType(PDShading.SHADING_TYPE2);
        shading.setColorSpace(PDDeviceRGB.INSTANCE);

        List<StopPoint> stopPoints = gradient.getStopPoints();

        for (StopPoint stop : stopPoints) {
            if (stop.getColor() instanceof FSRGBColor && ((FSRGBColor) stop.getColor()).hasAlpha()) {
                XRLog.log(Level.WARNING, LogMessageId.LogMessageId0Param.RENDER_GRADIENT_STOP_ALPHA_NOT_SUPPORTED);
                break;
            }
        }

        Rectangle rect = bounds.getBounds();

        // The stops are positioned along the gradient line and the first and last of
        // them may sit well short of its ends, so the shading axis runs from the first
        // stop to the last one rather than from end to end. Whatever of the box is
        // left over before and after is covered by extending the outermost colours.
        float axisStart = gradient.getAxisStart();
        float axisEnd = gradient.getAxisEnd();

        Point2D ptStart = pointOnGradientLine(gradient, rect, axisStart);
        Point2D ptEnd = pointOnGradientLine(gradient, rect, axisEnd);

        Point2D ptStartDevice = transform.transform(ptStart, null);
        Point2D ptEndDevice = transform.transform(ptEnd, null);

        float startX = (float) ptStartDevice.getX();
        float startY = od.normalizeY((float) ptStartDevice.getY());
        float endX = (float) ptEndDevice.getX();
        float endY = od.normalizeY((float) ptEndDevice.getY());

        float[] axis = { startX, startY, endX, endY };
        float[] domain = null;
        if (od.limitsRealsToPage()) {
            domain = fitAxisToPage(axis, od.pageWidth(), od.pageHeight());
        }

        COSArray coords = new COSArray();
        for (float coord : axis) {
            coords.add(new COSFloat(coord));
        }
        shading.setCoords(coords);
        if (domain != null) {
            COSArray shadingDomain = new COSArray();
            shadingDomain.add(new COSFloat(domain[0]));
            shadingDomain.add(new COSFloat(domain[1]));
            shading.getCOSObject().setItem(COSName.DOMAIN, shadingDomain);
        }

        PDFunctionType3 type3 = buildType3Function(stopPoints, axisStart, axisEnd - axisStart);

        COSArray extend = new COSArray();
        extend.add(COSBoolean.TRUE);
        extend.add(COSBoolean.TRUE);
        shading.setFunction(type3);
        shading.setExtend(extend);
        return shading;
    }

    /**
     * A radial gradient as a radial shading of a unit circle at the origin, from the
     * first stop's radius to the last's, extended both ways. {@link #radialGradientMatrix}
     * maps the unit circle onto the gradient's ellipse on the page.
     */
    public static PDShading createRadialGradient(FSRadialGradient gradient) {
        PDShadingType3 shading = new PDShadingType3(new COSDictionary());
        shading.setShadingType(PDShading.SHADING_TYPE3);
        shading.setColorSpace(PDDeviceRGB.INSTANCE);

        List<StopPoint> stopPoints = gradient.getStopPoints();
        for (StopPoint stop : stopPoints) {
            if (stop.getColor() instanceof FSRGBColor && ((FSRGBColor) stop.getColor()).hasAlpha()) {
                XRLog.log(Level.WARNING, LogMessageId.LogMessageId0Param.RENDER_GRADIENT_STOP_ALPHA_NOT_SUPPORTED);
                break;
            }
        }

        float axisStart = gradient.getAxisStart();
        float axisEnd = gradient.getAxisEnd();
        float rx = gradient.getRadiusX();

        COSArray coords = new COSArray();
        coords.add(new COSFloat(0));
        coords.add(new COSFloat(0));
        coords.add(new COSFloat(Math.max(0, axisStart / rx)));
        coords.add(new COSFloat(0));
        coords.add(new COSFloat(0));
        coords.add(new COSFloat(axisEnd / rx));
        shading.setCoords(coords);

        if (axisStart < 0) {
            // A repeating gradient's stops may start before the center; the
            // shading starts at the center, at that point of the function.
            COSArray domain = new COSArray();
            domain.add(new COSFloat(-axisStart / (axisEnd - axisStart)));
            domain.add(new COSFloat(1));
            shading.getCOSObject().setItem(COSName.DOMAIN, domain);
        }

        shading.setFunction(buildType3Function(stopPoints, axisStart, axisEnd - axisStart));

        COSArray extend = new COSArray();
        extend.add(COSBoolean.TRUE);
        extend.add(COSBoolean.TRUE);
        shading.setExtend(extend);
        return shading;
    }

    /**
     * The matrix, for the cm operator, that maps the unit circle of
     * {@link #createRadialGradient} onto the gradient's ellipse in bounds.
     */
    public static AffineTransform radialGradientMatrix(PdfBoxFastOutputDevice od, AffineTransform transform,
            FSRadialGradient gradient, Shape bounds) {
        Rectangle rect = bounds.getBounds();
        float cx = (float) rect.getMinX() + gradient.getCenterX();
        float cy = (float) rect.getMinY() + gradient.getCenterY();

        Point2D c = transform.transform(new Point2D.Float(cx, cy), null);
        Point2D u = transform.transform(new Point2D.Float(cx + gradient.getRadiusX(), cy), null);
        Point2D v = transform.transform(new Point2D.Float(cx, cy + gradient.getRadiusY()), null);

        double x0 = c.getX();
        double y0 = od.normalizeY((float) c.getY());

        return new AffineTransform(
                u.getX() - x0, od.normalizeY((float) u.getY()) - y0,
                v.getX() - x0, od.normalizeY((float) v.getY()) - y0,
                x0, y0);
    }

    /**
     * PDF/A-1 caps real numbers at 32767, which the axis of a gradient on a
     * box spanning dozens of pages exceeds (issue 290). Moves the axis next to
     * the page: sliding it sideways leaves the shading unchanged, and only the
     * stretch of it the page covers is kept, with the shading's domain
     * narrowed to match so every point keeps its colour.
     *
     * @param axis start x, start y, end x, end y in PDF page space, replaced in place
     * @return the shading domain to use, or null for the default 0 to 1
     */
    static float[] fitAxisToPage(float[] axis, float pageWidth, float pageHeight) {
        float limit = PdfBoxFastOutputDevice.PDFA1_MAX_REAL;
        if (Math.abs(axis[0]) <= limit && Math.abs(axis[1]) <= limit
                && Math.abs(axis[2]) <= limit && Math.abs(axis[3]) <= limit) {
            return null;
        }

        double sx = axis[0], sy = axis[1];
        double dx = axis[2] - sx, dy = axis[3] - sy;
        double lengthSq = dx * dx + dy * dy;
        if (lengthSq == 0) {
            return null;
        }

        // Position along the axis (0 at its start, 1 at its end) of the page's corners.
        double tMin = Double.MAX_VALUE, tMax = -Double.MAX_VALUE;
        for (double[] corner : new double[][] { { 0, 0 }, { pageWidth, 0 }, { 0, pageHeight }, { pageWidth, pageHeight } }) {
            double t = ((corner[0] - sx) * dx + (corner[1] - sy) * dy) / lengthSq;
            tMin = Math.min(tMin, t);
            tMax = Math.max(tMax, t);
        }

        // The axis, slid sideways to run through the page centre.
        double cx = pageWidth / 2.0, cy = pageHeight / 2.0;
        double tCentre = ((cx - sx) * dx + (cy - sy) * dy) / lengthSq;

        double from, to;
        float[] domain;
        if (tMax <= 0) {
            // The whole page lies before the axis: the start colour, extended.
            from = tMax;
            to = tMax + 1 / Math.sqrt(lengthSq);
            domain = null;
        } else if (tMin >= 1) {
            // The whole page lies after the axis: the end colour, extended.
            from = tMin - 1 / Math.sqrt(lengthSq);
            to = tMin;
            domain = null;
        } else {
            from = Math.max(tMin, 0);
            to = Math.min(tMax, 1);
            domain = new float[] { (float) from, (float) to };
        }

        axis[0] = (float) (cx + (from - tCentre) * dx);
        axis[1] = (float) (cy + (from - tCentre) * dy);
        axis[2] = (float) (cx + (to - tCentre) * dx);
        axis[3] = (float) (cy + (to - tCentre) * dy);
        return domain;
    }

    /**
     * The point the given distance along the gradient line, in the coordinate
     * space of the box being painted.
     */
    private static Point2D pointOnGradientLine(FSLinearGradient gradient, Rectangle rect, float distance) {
        float fraction = distance / gradient.getGradientLineLength();

        return new Point2D.Float(
                gradient.getX1() + ((gradient.getX2() - gradient.getX1()) * fraction) + (float) rect.getMinX(),
                gradient.getY1() + ((gradient.getY2() - gradient.getY1()) * fraction) + (float) rect.getMinY());
    }

    /**
     * This method is used for setting colour lengths to linear gradient.
     * 
     * @return the function, which is an important parameter for setting linear
     *         gradient.
     * @param stopPoints
     *            colours and lengths of linear gradient.
     * @param axisStart
     *            distance along the gradient line at which the shading axis starts.
     * @param axisLength
     *            length of the shading axis, ie. how far apart the first and last
     *            stop points are.
     */
    private static PDFunctionType3 buildType3Function(List<StopPoint> stopPoints, float axisStart, float axisLength) {
        COSDictionary function = new COSDictionary();
        function.setInt(COSName.FUNCTION_TYPE, 3);

        COSArray domain = new COSArray();
        domain.add(new COSFloat(0));
        domain.add(new COSFloat(1));

        // One segment from each stop to the next. Two stops at the same position (a
        // hard change of color) get no segment between them: the color jumps where
        // one segment ends and the next begins. A segment of no width there is
        // evaluated differently by different viewers.
        COSArray functions = new COSArray();
        COSArray bounds = new COSArray();
        COSArray encode = new COSArray();

        StopPoint first = stopPoints.get(0);
        StopPoint last = stopPoints.get(stopPoints.size() - 1);

        float previousPos = (first.getLength() - axisStart) / axisLength;
        if (previousPos > 0) {
            // The axis starts a little before a hard change of color at the first
            // stop: the first color up to it.
            addSegment(functions, encode, first, first, domain);
        }

        for (int i = 1; i < stopPoints.size(); i++) {
            float pos = (stopPoints.get(i).getLength() - axisStart) / axisLength;
            if (pos <= previousPos) {
                continue;
            }
            if (functions.size() > 0) {
                bounds.add(new COSFloat(previousPos));
            }
            addSegment(functions, encode, stopPoints.get(i - 1), stopPoints.get(i), domain);
            previousPos = pos;
        }

        if (functions.size() == 0 || previousPos < 1) {
            // The last color from the last stop, after a hard change of color there,
            // to the end of the axis.
            if (functions.size() > 0) {
                bounds.add(new COSFloat(previousPos));
            }
            addSegment(functions, encode, last, last, domain);
        }

        function.setItem(COSName.FUNCTIONS, functions);
        function.setItem(COSName.BOUNDS, bounds);
        function.setItem(COSName.ENCODE, encode);
        PDFunctionType3 type3 = new PDFunctionType3(function);
        type3.setDomainValues(domain);
        return type3;
    }

    private static void addSegment(COSArray functions, COSArray encode,
            StopPoint from, StopPoint to, COSArray domain) {
        functions.add(buildType2Function(from, to, domain));
        encode.add(new COSFloat(0));
        encode.add(new COSFloat(1));
    }

    /**
     * An exponential function (n = 1) from the color of one stop to that of the next.
     */
    private static COSDictionary buildType2Function(StopPoint from, StopPoint to, COSArray domain) {
        COSDictionary type2Function = new COSDictionary();
        type2Function.setInt(COSName.FUNCTION_TYPE, 2);
        type2Function.setItem(COSName.C0, rgb((FSRGBColor) from.getColor()));
        type2Function.setItem(COSName.C1, rgb((FSRGBColor) to.getColor()));
        type2Function.setInt(COSName.N, 1);
        type2Function.setItem(COSName.DOMAIN, domain);
        return type2Function;
    }

    private static COSArray rgb(FSRGBColor color) {
        COSArray c = new COSArray();
        c.add(new COSFloat(color.getRed() / 255f));
        c.add(new COSFloat(color.getGreen() / 255f));
        c.add(new COSFloat(color.getBlue() / 255f));
        return c;
    }
}
