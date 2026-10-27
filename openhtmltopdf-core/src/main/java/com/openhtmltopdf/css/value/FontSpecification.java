package com.openhtmltopdf.css.value;

import com.openhtmltopdf.css.constants.IdentValue;

import java.util.Arrays;

public class FontSpecification {
    public float size;
    public IdentValue fontWeight;
    public String[] families;
    public IdentValue fontStyle;
    public IdentValue variant;
    public IdentValue kerning;

    @Override
    public String toString() {
        return new StringBuilder("Font specification:")
                .append(" families: ")
                .append(Arrays.asList(families))
                .append(" size: ").append(size)
                .append(" weight: ").append(fontWeight)
                .append(" style: ").append(fontStyle)
                .append(" variant: ").append(variant)
                .append(" kerning: ").append(kerning)
                .toString();
    }
}
