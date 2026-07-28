/*
 * {{{ header & license
 * ValueConstants.java
 * Copyright (c) 2004, 2005 Patrick Wright
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * }}}
 */
package com.openhtmltopdf.css.constants;

import com.openhtmltopdf.css.parser.CSSPrimitiveValue;
import com.openhtmltopdf.util.LogMessageId;
import com.openhtmltopdf.util.XRLog;
import java.util.*;
import java.util.logging.Level;


/**
 * Utility class for working with <code>CSSValue</code> instances.
 */
public final class ValueConstants {
    private final static Map<Short, String> sacTypesStrings;

    /**
     * Given a unit constant like <code>CSSPrimitiveValue.CSS_EMS</code>
     * will return the unit suffix like <code>em</code>.
     *
     * FIXME: Not exhaustive.
     */
    public static String stringForSACPrimitiveType(short type) {
        return sacTypesStrings.get(type);
    }

    /** CSS fixes the physical units to 96 pixels per inch. */
    private final static float PX__PER__IN = 96f;
    private final static float PT__PER__IN = 72f;
    private final static float PC__PER__IN = 6f;
    private final static float CM__PER__IN = 2.54f;
    private final static float MM__PER__IN = 25.4f;

    /**
     * Converts a length in an absolute unit to CSS pixels.
     *
     * <p>Unlike {@link com.openhtmltopdf.css.style.derived.LengthValue#calcFloatProportionalValue},
     * which converts to dots and therefore needs a style and a context, this only handles the
     * units whose conversion is fixed by CSS, and so needs neither. Useful where a length turns
     * up outside a styled element, such as the <code>width</code> attribute of an SVG that is
     * being used as an image.</p>
     *
     * <p>A unitless number is treated as pixels, as CSS and SVG both do.</p>
     *
     * @param type the unit, one of the <code>CSSPrimitiveValue</code> constants
     * @param value the length in that unit
     *
     * @return the length in CSS pixels, or null for a unit that can not be resolved without
     * knowing the surroundings, such as a percentage or a font relative unit.
     */
    public static Float absoluteLengthToPixels(short type, float value) {
        switch (type) {
            case CSSPrimitiveValue.CSS_NUMBER:
            case CSSPrimitiveValue.CSS_PX:
                return value;
            case CSSPrimitiveValue.CSS_IN:
                return value * PX__PER__IN;
            case CSSPrimitiveValue.CSS_CM:
                return value * (PX__PER__IN / CM__PER__IN);
            case CSSPrimitiveValue.CSS_MM:
                return value * (PX__PER__IN / MM__PER__IN);
            case CSSPrimitiveValue.CSS_PT:
                return value * (PX__PER__IN / PT__PER__IN);
            case CSSPrimitiveValue.CSS_PC:
                return value * (PX__PER__IN / PC__PER__IN);
            default:
                // A percentage, em, ex, rem or something that is not a length at all.
                return null;
        }
    }

    /**
     * Returns true if the specified type absolute (even if we have a computed
     * value for it), meaning that either the value can be used directly (e.g.
     * pixels) or there is a fixed context-independent conversion for it (e.g.
     * inches). Proportional types (e.g. %) return false.
     *
     * FIXME: Font proportional units are returned as absolute. Probably
     * wrong method name rather than wrong behavior.
     *
     * @param type The CSSValue type to check.
     * @return See desc.
     */
    public static boolean isAbsoluteUnit(short type) {
        // note, all types are included here to make sure none are missed
        switch (type) {
            // proportional length or size
            case CSSPrimitiveValue.CSS_PERCENTAGE:
                return false;
                // refer to values known to the DerivedValue instance (tobe)
            case CSSPrimitiveValue.CSS_EMS:
            case CSSPrimitiveValue.CSS_REMS:
            case CSSPrimitiveValue.CSS_EXS:
                // length
            case CSSPrimitiveValue.CSS_IN:
            case CSSPrimitiveValue.CSS_CM:
            case CSSPrimitiveValue.CSS_MM:
            case CSSPrimitiveValue.CSS_PT:
            case CSSPrimitiveValue.CSS_PC:
            case CSSPrimitiveValue.CSS_PX:

                // color
            case CSSPrimitiveValue.CSS_RGBCOLOR:

                // ?
            case CSSPrimitiveValue.CSS_ATTR:
            case CSSPrimitiveValue.CSS_DIMENSION:
            case CSSPrimitiveValue.CSS_NUMBER:
            case CSSPrimitiveValue.CSS_RECT:

                // counters
            case CSSPrimitiveValue.CSS_COUNTER:

                // angles
            case CSSPrimitiveValue.CSS_DEG:
            case CSSPrimitiveValue.CSS_GRAD:
            case CSSPrimitiveValue.CSS_RAD:

                // aural - freq
            case CSSPrimitiveValue.CSS_HZ:
            case CSSPrimitiveValue.CSS_KHZ:

                // time
            case CSSPrimitiveValue.CSS_S:
            case CSSPrimitiveValue.CSS_MS:

                // URI
            case CSSPrimitiveValue.CSS_URI:

            case CSSPrimitiveValue.CSS_IDENT:
            case CSSPrimitiveValue.CSS_STRING:
                return true;
            case CSSPrimitiveValue.CSS_UNKNOWN:
                XRLog.log(Level.WARNING, LogMessageId.LogMessageId0Param.CASCADE_IS_ABSOLUTE_CSS_UNKNOWN_GIVEN, new Exception());
                // fall-through
            default:
                return false;
        }
    }

    /**
     * Returns true if the SAC primitive value type is a number unit--a unit
     * that can only contain a numeric value. This is a shorthand way of saying,
     * did the user declare this as a number unit (like px)?
     * 
     * @deprecated Only used by the broken DOMInspector.
     */
    @Deprecated
    public static boolean isNumber(short cssPrimitiveType) {
        switch (cssPrimitiveType) {
            // fall thru on all these
            // relative length or size
            case CSSPrimitiveValue.CSS_EMS:
            case CSSPrimitiveValue.CSS_EXS:
            case CSSPrimitiveValue.CSS_PERCENTAGE:
                // relatives will be treated separately from lengths;
                return false;
                // length
            case CSSPrimitiveValue.CSS_PX:
            case CSSPrimitiveValue.CSS_IN:
            case CSSPrimitiveValue.CSS_CM:
            case CSSPrimitiveValue.CSS_MM:
            case CSSPrimitiveValue.CSS_PT:
            case CSSPrimitiveValue.CSS_PC:
                return true;
            default:
                return false;
        }
    }

    static {
        sacTypesStrings = new HashMap<>(25);
        sacTypesStrings.put(CSSPrimitiveValue.CSS_EMS, "em");
        sacTypesStrings.put(CSSPrimitiveValue.CSS_EXS, "ex");
        sacTypesStrings.put(CSSPrimitiveValue.CSS_PX, "px");
        sacTypesStrings.put(CSSPrimitiveValue.CSS_PERCENTAGE, "%");
        sacTypesStrings.put(CSSPrimitiveValue.CSS_IN, "in");
        sacTypesStrings.put(CSSPrimitiveValue.CSS_CM, "cm");
        sacTypesStrings.put(CSSPrimitiveValue.CSS_MM, "mm");
        sacTypesStrings.put(CSSPrimitiveValue.CSS_PT, "pt");
        sacTypesStrings.put(CSSPrimitiveValue.CSS_PC, "pc");
    }
}
