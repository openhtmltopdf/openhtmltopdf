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
package com.openhtmltopdf.css.sheet;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.parser.CSSPrimitiveValue;
import com.openhtmltopdf.css.parser.PropertyValue;

/**
 * A property declaration whose value contains one or more {@code var()}
 * references. This models the CSS "pending-substitution value": the value
 * cannot be validated or built into a typed value at parse time because the
 * custom properties it references are only known per element at
 * computed-value time.
 *
 * <p>The declaration is keyed by its real {@link CSSName} so it participates
 * normally in the cascade. Its raw value text is kept verbatim and is
 * substituted then re-parsed when the computed style for an element is
 * derived (see {@code com.openhtmltopdf.css.style.CalculatedStyle}).</p>
 */
public class PendingVarPropertyDeclaration extends PropertyDeclaration {

    private final String valueText;
    private String _fingerprint;

    public PendingVarPropertyDeclaration(CSSName cssName, String valueText, boolean important, int origin) {
        // The wrapped value is only a placeholder; the property is never derived
        // directly. Resolution substitutes valueText and re-parses it.
        super(cssName, new PropertyValue(CSSPrimitiveValue.CSS_STRING, valueText, valueText), important, origin);
        this.valueText = valueText;
    }

    /** The raw value text, with {@code var()} references unsubstituted. */
    public String getValueText() {
        return valueText;
    }

    @Override
    public String getFingerprint() {
        if (_fingerprint == null) {
            _fingerprint = "V" + getCSSName().FS_ID + ':' + valueText + (isImportant() ? "!" : "") + ';';
        }
        return _fingerprint;
    }

    @Override
    public void toCSS(StringBuilder sb) {
        sb.append(getPropertyName());
        sb.append(": ");
        sb.append(valueText);
        if (isImportant()) {
            sb.append(" !important");
        }
        sb.append(';');
    }
}
