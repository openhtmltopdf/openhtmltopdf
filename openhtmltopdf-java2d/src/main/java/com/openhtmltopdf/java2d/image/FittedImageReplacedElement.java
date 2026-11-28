package com.openhtmltopdf.java2d.image;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import com.openhtmltopdf.render.BlockBox;
import com.openhtmltopdf.render.ObjectFit;
import com.openhtmltopdf.render.RenderingContext;

/**
 * An image whose box has another size than the image and whose object-fit is not
 * fill: the image keeps its own size here and is fitted into the box when painted,
 * see {@link ObjectFit}.
 */
public class FittedImageReplacedElement extends ImageReplacedElement {
    private final BufferedImage _natural;
    private final int _boxWidth;
    private final int _boxHeight;

    private Image _scaled;

    public FittedImageReplacedElement(BufferedImage natural, int boxWidth, int boxHeight) {
        super();
        this._natural = natural;
        this._boxWidth = boxWidth;
        this._boxHeight = boxHeight;
        this._image = natural;
    }

    @Override
    public int getIntrinsicWidth() {
        return _boxWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return _boxHeight;
    }

    public void paint(Graphics2D g, RenderingContext c, BlockBox box) {
        Rectangle contentBounds = box.getContentAreaEdge(box.getAbsX(), box.getAbsY(), c);
        Rectangle2D target = ObjectFit.destination(
                c, box.getStyle(), contentBounds, _natural.getWidth(), _natural.getHeight());

        int width = (int) Math.round(target.getWidth());
        int height = (int) Math.round(target.getHeight());

        if (width <= 0 || height <= 0) {
            return;
        }

        if (_scaled == null || _scaled.getWidth(null) != width || _scaled.getHeight(null) != height) {
            _scaled = width == _natural.getWidth() && height == _natural.getHeight() ?
                    _natural : ImageUtil.getScaledInstance(_natural, width, height);
        }

        Shape oldClip = g.getClip();
        if (ObjectFit.overflows(target, contentBounds)) {
            g.clip(contentBounds);
        }

        g.drawImage(_scaled, (int) Math.round(target.getX()), (int) Math.round(target.getY()), null);

        g.setClip(oldClip);
    }
}
