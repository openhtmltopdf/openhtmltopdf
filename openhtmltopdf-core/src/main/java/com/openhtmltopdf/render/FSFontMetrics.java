/*
 * {{{ header & license
 * Copyright (c) 2006 Wisconsin Court System
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * }}}
 */
package com.openhtmltopdf.render;

public interface FSFontMetrics {
    public float getAscent();
    
    /**
     * In keeping with the JDK {@link java.awt.font.LineMetrics} convention, this number is
     * positive for values below the baseline.
     */
    public float getDescent();
    public float getStrikethroughOffset();
    public float getStrikethroughThickness();
    
    /**
     * In keeping with the JDK {@link java.awt.font.LineMetrics} convention, this number is
     * positive for values below the baseline.
     */
    public float getUnderlineOffset();

    public float getUnderlineThickness();

    /**
     * The distance from the baseline to the bottom of the font's em box (its
     * typographic descent). Positive for values below the baseline, in keeping
     * with the JDK {@link java.awt.font.LineMetrics} convention. Used for
     * <code>text-underline-position: under</code>.
     */
    default float getTypoDescent() {
        return getDescent();
    }

    /**
     * The extra space to leave between consecutive lines (the line gap, also
     * known as external leading), on top of the ascent and descent reported
     * here. Browsers add it when computing <code>line-height: normal</code>, so
     * we do too. Zero when no font asks for one.
     *
     * <p>Where several fonts are in play the ascent and descent above are the
     * largest of each, which taken together can already be more than any one
     * font asks for a line; this is only what is missing on top of them.</p>
     */
    default float getLineGap() {
        return 0f;
    }
}
