package com.openhtmltopdf.css.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.css.parser.FSColor;
import com.openhtmltopdf.css.parser.PropertyValue;
import com.openhtmltopdf.css.parser.property.BoxShadowPropertyBuilder;
import com.openhtmltopdf.css.style.derived.LengthValue;
import com.openhtmltopdf.css.style.derived.ListValue;

/**
 * A single resolved {@code box-shadow} value (CSS Backgrounds and Borders Module Level 3).
 */
public class BoxShadow {
    private final boolean _inset;
    private final float _offsetX;
    private final float _offsetY;
    private final float _blurRadius;
    private final float _spreadRadius;
    private final FSColor _color;

    private BoxShadow(boolean inset, float offsetX, float offsetY, float blurRadius, float spreadRadius, FSColor color) {
        _inset = inset;
        _offsetX = offsetX;
        _offsetY = offsetY;
        _blurRadius = blurRadius;
        _spreadRadius = spreadRadius;
        _color = color;
    }

    public boolean isInset() {
        return _inset;
    }

    public float getOffsetX() {
        return _offsetX;
    }

    public float getOffsetY() {
        return _offsetY;
    }

    public float getBlurRadius() {
        return _blurRadius;
    }

    public float getSpreadRadius() {
        return _spreadRadius;
    }

    public FSColor getColor() {
        return _color;
    }

    public static List<BoxShadow> fromStyle(CalculatedStyle style, CssContext ctx) {
        FSDerivedValue value = style.valueByName(CSSName.BOX_SHADOW);

        if (!(value instanceof ListValue)) {
            return Collections.emptyList();
        }

        List<PropertyValue> tokens = ((ListValue) value).getValues();
        int shadowCount = tokens.size() / BoxShadowPropertyBuilder.VALUES_PER_SHADOW;
        List<BoxShadow> result = new ArrayList<>(shadowCount);

        for (int i = 0; i + BoxShadowPropertyBuilder.VALUES_PER_SHADOW <= tokens.size(); i += BoxShadowPropertyBuilder.VALUES_PER_SHADOW) {
            boolean inset = tokens.get(i).getIdentValue() == IdentValue.INSET;
            float offsetX = resolveLength(style, tokens.get(i + 1), ctx);
            float offsetY = resolveLength(style, tokens.get(i + 2), ctx);
            float blurRadius = resolveLength(style, tokens.get(i + 3), ctx);
            float spreadRadius = resolveLength(style, tokens.get(i + 4), ctx);
            FSColor color = tokens.get(i + 5).getFSColor();

            result.add(new BoxShadow(inset, offsetX, offsetY, blurRadius, spreadRadius, color));
        }

        return result;
    }

    private static float resolveLength(CalculatedStyle style, PropertyValue value, CssContext ctx) {
        return LengthValue.calcFloatProportionalValue(
                style,
                CSSName.BOX_SHADOW,
                value.getCssText(),
                value.getFloatValue(),
                value.getPrimitiveType(),
                0,
                ctx);
    }
}
