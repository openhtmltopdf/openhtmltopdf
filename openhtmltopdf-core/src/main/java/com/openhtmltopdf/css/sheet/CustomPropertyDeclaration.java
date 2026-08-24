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

/**
 * A CSS custom property declaration (a property whose name starts with
 * {@code --}, e.g. {@code --main-color: red}). Custom properties are not
 * keyed by a {@link com.openhtmltopdf.css.constants.CSSName} (the name is
 * author-defined), so they are not regular {@link PropertyDeclaration}s and
 * are kept in a parallel cascade.
 *
 * <p>The value is stored as raw text (which may itself contain {@code var()}
 * references) and is resolved per element at computed-value time.</p>
 */
public class CustomPropertyDeclaration {

    /** Bucketing of importance and origin, mirroring {@link PropertyDeclaration}. */
    private final static int USER_AGENT = 1;
    private final static int USER_NORMAL = 2;
    private final static int AUTHOR_NORMAL = 3;
    private final static int AUTHOR_IMPORTANT = 4;
    private final static int USER_IMPORTANT = 5;

    private final String name;
    private final String valueText;
    private final boolean important;
    private final int origin;

    private String _fingerprint;

    public CustomPropertyDeclaration(String name, String valueText, boolean important, int origin) {
        this.name = name;
        this.valueText = valueText;
        this.important = important;
        this.origin = origin;
    }

    /** The custom property name, including the leading {@code --}. */
    public String getName() {
        return name;
    }

    /** The raw (unsubstituted) value text; may contain {@code var()} references. */
    public String getValueText() {
        return valueText;
    }

    public boolean isImportant() {
        return important;
    }

    public int getOrigin() {
        return origin;
    }

    /**
     * @see PropertyDeclaration#getImportanceAndOrigin()
     */
    public int getImportanceAndOrigin() {
        if (origin == StylesheetInfo.USER_AGENT) {
            return USER_AGENT;
        } else if (origin == StylesheetInfo.USER) {
            return important ? USER_IMPORTANT : USER_NORMAL;
        } else {
            return important ? AUTHOR_IMPORTANT : AUTHOR_NORMAL;
        }
    }

    public String getFingerprint() {
        if (_fingerprint == null) {
            _fingerprint = "C" + name + ':' + valueText + (important ? "!" : "") + ';';
        }
        return _fingerprint;
    }

    @Override
    public String toString() {
        return name + ": " + valueText + (important ? " !important" : "");
    }
}
