package com.openhtmltopdf.css.parser;

public interface CSSPrimitiveValue extends CSSValue {
    public static final short CSS_UNKNOWN = 0;
    public static final short CSS_NUMBER = 1;
    public static final short CSS_PERCENTAGE = 2;
    public static final short CSS_EMS = 3;
    public static final short CSS_EXS = 4;
    public static final short CSS_PX = 5;
    public static final short CSS_CM = 6;
    public static final short CSS_MM = 7;
    public static final short CSS_IN = 8;
    public static final short CSS_PT = 9;
    public static final short CSS_PC = 10;
    public static final short CSS_DEG = 11;
    public static final short CSS_RAD = 12;
    public static final short CSS_GRAD = 13;
    public static final short CSS_MS = 14;
    public static final short CSS_S = 15;
    public static final short CSS_HZ = 16;
    public static final short CSS_KHZ = 17;
    public static final short CSS_DIMENSION = 18;
    public static final short CSS_STRING = 19;
    public static final short CSS_URI = 20;
    public static final short CSS_IDENT = 21;
    public static final short CSS_ATTR = 22;
    public static final short CSS_COUNTER = 23;
    public static final short CSS_RECT = 24;
    public static final short CSS_RGBCOLOR = 25;

    /** rems unit, not official, added by danfickle. */
    public static final short CSS_REMS = 26;

    /** The advance of "0" in the element's font. */
    public static final short CSS_CH = 27;
    /** 1% of the width of the viewport, which is the page area of the first page. */
    public static final short CSS_VW = 28;
    /** 1% of the height of the viewport. */
    public static final short CSS_VH = 29;
    /** 1% of the smaller of the viewport's width and height. */
    public static final short CSS_VMIN = 30;
    /** 1% of the larger of the viewport's width and height. */
    public static final short CSS_VMAX = 31;
    /** A calc(), min(), max() or clamp() expression resulting in a length, see {@link CalcExpression}. */
    public static final short CSS_CALC = 32;

    public short getPrimitiveType();
    public float getFloatValue(short unitType);
    public String getStringValue();
    
}
