package com.openhtmltopdf.css.parser.property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.css.parser.CSSParseException;
import com.openhtmltopdf.css.parser.CSSPrimitiveValue;
import com.openhtmltopdf.css.parser.CSSValue;
import com.openhtmltopdf.css.parser.FSRGBColor;
import com.openhtmltopdf.css.parser.PropertyValue;
import com.openhtmltopdf.css.parser.Token;
import com.openhtmltopdf.css.sheet.PropertyDeclaration;

/**
 * Parses the {@code box-shadow} property, e.g. {@code box-shadow: -4px 4px 0 0 rgba(0,0,0,0.15);}.
 * Multiple comma-separated shadows are supported. Each parsed shadow is normalized to a fixed
 * run of {@link #VALUES_PER_SHADOW} tokens (inset-flag, offset-x, offset-y, blur-radius,
 * spread-radius, color) appended to a single flat list value, so that
 * {@link com.openhtmltopdf.css.style.BoxShadow} can later split it back into groups without
 * needing to re-parse comma operators.
 */
public class BoxShadowPropertyBuilder extends AbstractPropertyBuilder {
    public static final int VALUES_PER_SHADOW = 6;

    private static final PropertyValue ZERO_LENGTH =
            new PropertyValue(CSSPrimitiveValue.CSS_PX, 0f, "0");

    @Override
    public List<PropertyDeclaration> buildDeclarations(
            CSSName cssName, List<PropertyValue> values, int origin, boolean important, boolean inheritAllowed) {
        checkValueCount(cssName, 1, Integer.MAX_VALUE, values.size());

        if (values.size() == 1) {
            PropertyValue value = values.get(0);
            checkInheritAllowed(value, inheritAllowed);

            if (value.getCssValueType() == CSSValue.CSS_INHERIT) {
                return Collections.singletonList(new PropertyDeclaration(cssName, value, important, origin));
            }

            if (value.getPrimitiveType() == CSSPrimitiveValue.CSS_IDENT) {
                IdentValue ident = checkIdent(cssName, value);
                if (ident != IdentValue.NONE) {
                    throw new CSSParseException("Value for " + cssName + " must be 'none' or a shadow list", -1);
                }
                return Collections.singletonList(new PropertyDeclaration(cssName, value, important, origin));
            }
        }

        List<PropertyValue> normalized = new ArrayList<>();
        List<PropertyValue> group = new ArrayList<>();

        for (PropertyValue value : values) {
            checkForbidInherit(value);

            if (value.getOperator() == Token.TK_COMMA && !group.isEmpty()) {
                normalized.addAll(parseShadow(cssName, group));
                group = new ArrayList<>();
            }

            group.add(value);
        }
        normalized.addAll(parseShadow(cssName, group));

        return Collections.singletonList(
                new PropertyDeclaration(cssName, new PropertyValue(normalized), important, origin));
    }

    private List<PropertyValue> parseShadow(CSSName cssName, List<PropertyValue> group) {
        boolean inset = false;
        PropertyValue color = null;
        List<PropertyValue> lengths = new ArrayList<>(4);

        for (PropertyValue token : group) {
            short type = token.getPrimitiveType();

            if (type == CSSPrimitiveValue.CSS_IDENT) {
                if ("inset".equalsIgnoreCase(token.getStringValue())) {
                    if (inset) {
                        throw new CSSParseException("'inset' may only be specified once for " + cssName, -1);
                    }
                    inset = true;
                    continue;
                }

                FSRGBColor namedColor = Conversions.getColor(token.getStringValue());
                if (namedColor == null) {
                    throw new CSSParseException(
                            "Value " + token.getStringValue() + " is not valid for " + cssName, -1);
                }
                if (color != null) {
                    throw new CSSParseException(cssName + " cannot have two colors", -1);
                }
                color = new PropertyValue(namedColor);
            } else if (type == CSSPrimitiveValue.CSS_RGBCOLOR) {
                if (color != null) {
                    throw new CSSParseException(cssName + " cannot have two colors", -1);
                }
                color = token;
            } else if (isLengthHelper(token)) {
                if (lengths.size() == 4) {
                    throw new CSSParseException(cssName + " may not have more than four lengths", -1);
                }
                if (lengths.size() == 2 && token.getFloatValue() < 0.0f) {
                    throw new CSSParseException("blur-radius for " + cssName + " may not be negative", -1);
                }
                lengths.add(token);
            } else {
                throw new CSSParseException(
                        "Value for " + cssName + " must be a length, a color or 'inset'", -1);
            }
        }

        if (lengths.size() < 2) {
            throw new CSSParseException(cssName + " requires an offset-x and offset-y length", -1);
        }

        List<PropertyValue> result = new ArrayList<>(VALUES_PER_SHADOW);
        result.add(new PropertyValue(inset ? IdentValue.INSET : IdentValue.NONE));
        result.add(lengths.get(0));
        result.add(lengths.get(1));
        result.add(lengths.size() >= 3 ? lengths.get(2) : ZERO_LENGTH);
        result.add(lengths.size() == 4 ? lengths.get(3) : ZERO_LENGTH);
        result.add(color != null ? color : new PropertyValue(FSRGBColor.BLACK));
        return result;
    }
}
