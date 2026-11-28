package com.openhtmltopdf.render;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.List;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.css.parser.CSSPrimitiveValue;
import com.openhtmltopdf.css.parser.PropertyValue;
import com.openhtmltopdf.css.style.CalculatedStyle;
import com.openhtmltopdf.css.style.CssContext;
import com.openhtmltopdf.css.style.derived.LengthValue;
import com.openhtmltopdf.css.style.derived.ListValue;

/**
 * Where the content of a replaced element (an image, a PDF page) is drawn within
 * its content box, following CSS object-fit and object-position.
 */
public class ObjectFit {
    private ObjectFit() {
    }

    /**
     * @param contentBox the content box of the replaced element.
     * @param naturalWidth the width of the content at its natural size, in the units of contentBox.
     * @param naturalHeight the height of the content at its natural size, in the units of contentBox.
     * @return the rectangle to draw the content into. It may be larger than the content
     * box (cover, none) or start outside it (object-position): the content is then
     * clipped to the content box, see {@link #overflows(Rectangle2D, Rectangle)}.
     */
    public static Rectangle2D destination(
            CssContext c, CalculatedStyle style, Rectangle contentBox,
            double naturalWidth, double naturalHeight) {

        IdentValue fit = style.getIdent(CSSName.OBJECT_FIT);
        double boxWidth = contentBox.getWidth();
        double boxHeight = contentBox.getHeight();

        double width = boxWidth;
        double height = boxHeight;

        if (fit != IdentValue.FILL && naturalWidth > 0 && naturalHeight > 0) {
            if (fit == IdentValue.NONE) {
                width = naturalWidth;
                height = naturalHeight;
            } else {
                double scaleX = boxWidth / naturalWidth;
                double scaleY = boxHeight / naturalHeight;
                double scale = fit == IdentValue.COVER ? Math.max(scaleX, scaleY) : Math.min(scaleX, scaleY);
                if (fit == IdentValue.SCALE_DOWN) {
                    // The smaller of none and contain.
                    scale = Math.min(scale, 1);
                }
                width = naturalWidth * scale;
                height = naturalHeight * scale;
            }
        }

        List<PropertyValue> position = ((ListValue) style.valueByName(CSSName.OBJECT_POSITION)).getValues();

        double x = contentBox.getX() + offset(c, style, position.get(0), boxWidth - width);
        double y = contentBox.getY() + offset(c, style, position.get(1), boxHeight - height);

        return new Rectangle2D.Double(x, y, width, height);
    }

    /**
     * @return whether content drawn into destination would paint outside the content box.
     */
    public static boolean overflows(Rectangle2D destination, Rectangle contentBox) {
        double epsilon = 0.01;
        return destination.getMinX() < contentBox.getMinX() - epsilon ||
               destination.getMinY() < contentBox.getMinY() - epsilon ||
               destination.getMaxX() > contentBox.getMaxX() + epsilon ||
               destination.getMaxY() > contentBox.getMaxY() + epsilon;
    }

    /**
     * A percentage aligns that point of the content with the same point of the box,
     * so it is a share of the free space, which is negative when the content is larger.
     * A length moves the content's edge from the box's edge.
     */
    private static double offset(CssContext c, CalculatedStyle style, PropertyValue value, double freeSpace) {
        if (value.getPrimitiveType() == CSSPrimitiveValue.CSS_PERCENTAGE) {
            return freeSpace * value.getFloatValue() / 100.0;
        }

        return LengthValue.calcFloatProportionalValue(
                style,
                CSSName.OBJECT_POSITION,
                value.getCssText(),
                value.getFloatValue(),
                value.getPrimitiveType(),
                0,
                c);
    }
}
