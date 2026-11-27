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
package com.openhtmltopdf.extend;

public interface FSImage {
    public int getWidth();
    public int getHeight();
    public void scale(int width, int height);

    /**
     * The image the way it is stored, without turning it as its EXIF orientation says
     * (CSS image-orientation: none). Called before the image is scaled.
     *
     * @return this image if it has no orientation of its own, else a new image.
     */
    default FSImage withoutImageOrientation() {
        return this;
    }
}
