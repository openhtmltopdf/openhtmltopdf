package com.openhtmltopdf.css.style.derived;

import java.util.ArrayList;
import java.util.List;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.parser.CSSPrimitiveValue;
import com.openhtmltopdf.css.parser.FSFunction;
import com.openhtmltopdf.css.parser.PropertyValue;
import com.openhtmltopdf.css.parser.Token;
import com.openhtmltopdf.css.style.CalculatedStyle;
import com.openhtmltopdf.css.style.CssContext;
import com.openhtmltopdf.css.style.derived.FSLinearGradient.StopPoint;

/**
 * A radial-gradient() or repeating-radial-gradient() (CSS Images 3) laid out for a
 * box: its center and radii in the box, and its color stops along the gradient
 * ray, which runs from the center to the right with the length of the horizontal
 * radius.
 */
public class FSRadialGradient {
    private static final double SQRT2 = Math.sqrt(2);

    private final float centerX;
    private final float centerY;
    private final float radiusX;
    private final float radiusY;
    private final List<StopPoint> stopPoints;

    public FSRadialGradient(CalculatedStyle style, FSFunction function, int boxWidth, int boxHeight, CssContext ctx) {
        List<PropertyValue> params = function.getParameters();

        // The arguments before the first comma give shape, size and position, unless
        // they are already a color stop.
        int stopsStart = 0;
        if (!FSLinearGradient.isColor(params.get(0))) {
            stopsStart = 1;
            while (stopsStart < params.size() && params.get(stopsStart).getOperator() != Token.TK_COMMA) {
                stopsStart++;
            }
        }

        boolean circle = false;
        boolean shapeGiven = false;
        String sizeKeyword = "farthest-corner";
        List<PropertyValue> sizes = new ArrayList<>(2);
        List<PropertyValue> position = new ArrayList<>(2);
        boolean inPosition = false;

        for (int i = 0; i < stopsStart; i++) {
            PropertyValue p = params.get(i);
            String ident = p.getPrimitiveType() == CSSPrimitiveValue.CSS_IDENT ? p.getStringValue() : null;
            if (inPosition) {
                position.add(p);
            } else if ("at".equals(ident)) {
                inPosition = true;
            } else if ("circle".equals(ident) || "ellipse".equals(ident)) {
                circle = "circle".equals(ident);
                shapeGiven = true;
            } else if (ident != null) {
                sizeKeyword = ident;
            } else {
                sizes.add(p);
            }
        }

        if (!shapeGiven && sizes.size() == 1) {
            circle = true;
        }

        float[] center = resolvePosition(style, ctx, position, boxWidth, boxHeight);
        this.centerX = center[0];
        this.centerY = center[1];

        float closeX = Math.min(centerX, boxWidth - centerX);
        float farX = Math.max(centerX, boxWidth - centerX);
        float closeY = Math.min(centerY, boxHeight - centerY);
        float farY = Math.max(centerY, boxHeight - centerY);

        float rx;
        float ry;
        if (!sizes.isEmpty()) {
            rx = length(style, ctx, sizes.get(0), boxWidth);
            ry = circle ? rx : length(style, ctx, sizes.get(sizes.size() > 1 ? 1 : 0), boxHeight);
        } else if (circle) {
            switch (sizeKeyword) {
            case "closest-side": rx = Math.min(closeX, closeY); break;
            case "farthest-side": rx = Math.max(farX, farY); break;
            case "closest-corner": rx = (float) Math.hypot(closeX, closeY); break;
            default: rx = (float) Math.hypot(farX, farY); break;
            }
            ry = rx;
        } else {
            switch (sizeKeyword) {
            case "closest-side": rx = closeX; ry = closeY; break;
            case "farthest-side": rx = farX; ry = farY; break;
            // The ellipse of the side keyword's proportions through the corner.
            case "closest-corner": rx = (float) (closeX * SQRT2); ry = (float) (closeY * SQRT2); break;
            default: rx = (float) (farX * SQRT2); ry = (float) (farY * SQRT2); break;
            }
        }
        this.radiusX = rx;
        this.radiusY = ry;

        List<StopPoint> stops = FSLinearGradient.calculateStopPoints(params, style, ctx, rx, stopsStart);
        if (function.getName().startsWith("repeating-") && rx > 0 && ry > 0) {
            stops = FSLinearGradient.repeat(stops, 0, farthestCornerDistance(boxWidth, boxHeight));
        }
        this.stopPoints = stops;
    }

    /**
     * @return how far along the gradient ray the farthest corner of the box is, for
     * a repeating gradient to cover the box.
     */
    private float farthestCornerDistance(int w, int h) {
        double max = 0;
        for (float[] corner : new float[][] { { 0, 0 }, { w, 0 }, { 0, h }, { w, h } }) {
            double dx = (corner[0] - centerX) / radiusX;
            double dy = (corner[1] - centerY) / radiusY;
            max = Math.max(max, Math.hypot(dx, dy));
        }
        return (float) (max * radiusX);
    }

    private static float length(CalculatedStyle style, CssContext ctx, PropertyValue value, float base) {
        return LengthValue.calcFloatProportionalValue(style, CSSName.BACKGROUND_IMAGE, value.getCssText(),
                value.getFloatValue(), value.getPrimitiveType(), base, ctx);
    }

    /**
     * The position after "at", like a background-position of one or two values;
     * the center of the box by default.
     */
    private static float[] resolvePosition(CalculatedStyle style, CssContext ctx, List<PropertyValue> position, int w, int h) {
        PropertyValue x = null;
        PropertyValue y = null;

        if (position.size() == 1) {
            PropertyValue p = position.get(0);
            if (isVertical(p)) {
                y = p;
            } else {
                x = p;
            }
        } else if (position.size() >= 2) {
            PropertyValue first = position.get(0);
            PropertyValue second = position.get(1);
            if (isVertical(first) || isHorizontal(second)) {
                x = second;
                y = first;
            } else {
                x = first;
                y = second;
            }
        }

        return new float[] { coordinate(style, ctx, x, w), coordinate(style, ctx, y, h) };
    }

    private static boolean isVertical(PropertyValue p) {
        String s = p.getStringValue();
        return p.getPrimitiveType() == CSSPrimitiveValue.CSS_IDENT && ("top".equals(s) || "bottom".equals(s));
    }

    private static boolean isHorizontal(PropertyValue p) {
        String s = p.getStringValue();
        return p.getPrimitiveType() == CSSPrimitiveValue.CSS_IDENT && ("left".equals(s) || "right".equals(s));
    }

    private static float coordinate(CalculatedStyle style, CssContext ctx, PropertyValue p, int size) {
        if (p == null) {
            return size / 2f;
        }
        if (p.getPrimitiveType() == CSSPrimitiveValue.CSS_IDENT) {
            switch (p.getStringValue()) {
            case "left":
            case "top":
                return 0;
            case "right":
            case "bottom":
                return size;
            default:
                return size / 2f;
            }
        }
        return length(style, ctx, p, size);
    }

    /** The center, from the top left corner of the box. */
    public float getCenterX() {
        return centerX;
    }

    public float getCenterY() {
        return centerY;
    }

    public float getRadiusX() {
        return radiusX;
    }

    public float getRadiusY() {
        return radiusY;
    }

    /** The color stops, positioned along the gradient ray. */
    public List<StopPoint> getStopPoints() {
        return stopPoints;
    }

    /** The position of the first stop along the gradient ray. */
    public float getAxisStart() {
        return FSLinearGradient.axisStart(stopPoints);
    }

    /** The position of the last stop, held a little after the first. */
    public float getAxisEnd() {
        return FSLinearGradient.axisEnd(stopPoints, getAxisStart());
    }

    /**
     * @return whether there is anything to draw: two stops and an ellipse that is not flat.
     */
    public boolean isDrawable() {
        return stopPoints.size() >= 2 && radiusX > 0 && radiusY > 0;
    }
}
