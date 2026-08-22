/*
 * {{{ header & license
 * Copyright (c) 2024 openhtmltopdf contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * }}}
 */
package com.openhtmltopdf.css.parser;

import java.util.List;

/**
 * Reconstructs the CSS text of a parsed value (a list of {@link PropertyValue}
 * terms), preserving the comma/slash/space operators between terms and recursing
 * into function arguments and value lists.
 *
 * <p>Unlike {@link FSFunction#toString()} it keeps comma- vs space-separated
 * arguments distinct, which matters when the text is re-parsed (custom property
 * values and {@code var()} fallbacks): {@code var(--x, 1px solid red)} must not
 * collapse to {@code var(--x,1px,solid,red)}.</p>
 *
 * <p>This generalizes the value joining in {@code InvalidPropertyDeclaration.toCSS},
 * which is comma/space-only and non-recursive (so it can't faithfully serialize
 * inside functions).</p>
 */
public final class CSSValueText {
    private CSSValueText() {}

    /**
     * Serializes a list of value terms back to CSS text suitable for re-parsing.
     */
    public static String toCSS(List<PropertyValue> values) {
        StringBuilder sb = new StringBuilder();
        appendValues(sb, values);
        return sb.toString();
    }

    /**
     * Returns true if any term in the value (recursively, including function
     * arguments and nested lists) is a {@code var()} function.
     */
    public static boolean containsVarFunction(List<PropertyValue> values) {
        for (PropertyValue value : values) {
            if (value.getPropertyValueType() == PropertyValue.VALUE_TYPE_FUNCTION &&
                    value.getFunction() != null) {
                if ("var".equalsIgnoreCase(value.getFunction().getName())) {
                    return true;
                }
                if (containsVarFunction(value.getFunction().getParameters())) {
                    return true;
                }
            } else if (value.getPropertyValueType() == PropertyValue.VALUE_TYPE_LIST) {
                if (containsVarFunction(value.getValues())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void appendValues(StringBuilder sb, List<PropertyValue> values) {
        boolean first = true;
        for (PropertyValue value : values) {
            if (!first) {
                Token op = value.getOperator();
                if (op == Token.TK_COMMA) {
                    sb.append(", ");
                } else if (op == Token.TK_VIRGULE) {
                    sb.append(" / ");
                } else {
                    sb.append(' ');
                }
            }
            appendValue(sb, value);
            first = false;
        }
    }

    private static void appendValue(StringBuilder sb, PropertyValue value) {
        if (value.getPropertyValueType() == PropertyValue.VALUE_TYPE_FUNCTION &&
                value.getFunction() != null) {
            FSFunction function = value.getFunction();
            sb.append(function.getName());
            sb.append('(');
            appendValues(sb, function.getParameters());
            sb.append(')');
        } else if (value.getPropertyValueType() == PropertyValue.VALUE_TYPE_LIST) {
            appendValues(sb, value.getValues());
        } else {
            sb.append(value.getCssText());
        }
    }
}
