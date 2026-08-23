/*
 * {{{ header & license
 * Copyright (c) 2026 openhtmltopdf contributors
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
package com.openhtmltopdf.layout;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.style.CalculatedStyle;
import com.openhtmltopdf.css.style.CssContext;
import com.openhtmltopdf.render.InlineText;

/**
 * The extra advance width contributed by the <code>letter-spacing</code> and
 * <code>word-spacing</code> CSS properties.
 *
 * <p>Letter spacing is added after every character (including the last one, as
 * browsers do), word spacing only after word separator characters such as the
 * space character.
 *
 * <p>Instances are immutable, so {@link #NONE} can be shared by the (very
 * common) case of a style using neither property.
 */
public class TextSpacing {
    public static final TextSpacing NONE = new TextSpacing(0f, 0f);

    private final float _letterSpacing;
    private final float _wordSpacing;

    private TextSpacing(float letterSpacing, float wordSpacing) {
        _letterSpacing = letterSpacing;
        _wordSpacing = wordSpacing;
    }

    public static TextSpacing of(float letterSpacing, float wordSpacing) {
        return letterSpacing == 0f && wordSpacing == 0f ?
                NONE : new TextSpacing(letterSpacing, wordSpacing);
    }

    public static TextSpacing from(CalculatedStyle style, CssContext c) {
        boolean hasLetterSpacing = style.hasLetterSpacing();
        boolean hasWordSpacing = style.hasWordSpacing();

        if (!hasLetterSpacing && !hasWordSpacing) {
            return NONE;
        }

        float letterSpacing = hasLetterSpacing ?
                style.getFloatPropertyProportionalWidth(CSSName.LETTER_SPACING, 0, c) : 0f;
        float wordSpacing = hasWordSpacing ?
                style.getFloatPropertyProportionalWidth(CSSName.WORD_SPACING, 0, c) : 0f;

        return of(letterSpacing, wordSpacing);
    }

    public boolean isNone() {
        return _letterSpacing == 0f && _wordSpacing == 0f;
    }

    public float getLetterSpacing() {
        return _letterSpacing;
    }

    public float getWordSpacing() {
        return _wordSpacing;
    }

    /**
     * The extra width this spacing adds to the entire string.
     */
    public float extra(String text) {
        return extra(text, 0, text.length());
    }

    /**
     * The extra width this spacing adds to <code>text</code> between the
     * <code>start</code> (inclusive) and <code>end</code> (exclusive) char indexes.
     */
    public float extra(String text, int start, int end) {
        if (isNone()) {
            return 0f;
        }

        // Count code points rather than chars so that a surrogate pair is spaced
        // as the single character it renders as. The output devices space by
        // code point too.
        float result = _letterSpacing * text.codePointCount(start, end);

        if (_wordSpacing != 0f) {
            result += _wordSpacing * countWordSeparators(text, start, end);
        }

        return result;
    }

    /**
     * The extra width contributed by a single word separator character.
     */
    public float extraForSeparator() {
        return _letterSpacing + _wordSpacing;
    }

    private static int countWordSeparators(String text, int start, int end) {
        int count = 0;

        for (int i = start; i < end; i++) {
            if (InlineText.isJustifySpaceCodePoint(text.charAt(i))) {
                count++;
            }
        }

        return count;
    }

    @Override
    public String toString() {
        return "TextSpacing[letter-spacing=" + _letterSpacing + ", word-spacing=" + _wordSpacing + "]";
    }
}
