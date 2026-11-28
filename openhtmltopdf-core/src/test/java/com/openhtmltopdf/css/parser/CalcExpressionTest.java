package com.openhtmltopdf.css.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.sheet.StylesheetInfo;

/**
 * Parsing and evaluating calc(), min(), max() and clamp().
 */
public class CalcExpressionTest {
    private static PropertyValue parse(CSSName name, String css) {
        CSSParser parser = new CSSParser((uri, message) -> { });
        return parser.parsePropertyValue(name, StylesheetInfo.AUTHOR, css);
    }

    /** Evaluates with 1px = 1 and percentages of 200. */
    private static float eval(String css) {
        CalcExpression calc = parse(CSSName.WIDTH, css).getCalc();
        assertNotNull(css, calc);
        return calc.evaluate((type, value, text) ->
                type == CSSPrimitiveValue.CSS_PERCENTAGE ? value / 100 * 200 : value);
    }

    @Test
    public void arithmetic() {
        assertEquals(80, eval("calc(50% - 20px)"), 0.001);
        assertEquals(40, eval("calc((100% - 80px) / 3)"), 0.001);
        assertEquals(110, eval("calc(10px + 2 * 50px)"), 0.001);
        assertEquals(80, eval("calc(-20px + 100px)"), 0.001);
        assertEquals(50, eval("calc(100%/4)"), 0.001);
    }

    @Test
    public void comparisons() {
        assertEquals(100, eval("min(100px, 50%)"), 0.001);
        assertEquals(200, eval("max(100px, 100%)"), 0.001);
        assertEquals(50, eval("clamp(50px, 10%, 200px)"), 0.001);
        assertEquals(150, eval("clamp(50px, 75%, 200px)"), 0.001);
        assertEquals(150, eval("calc(min(300px, 80%) - 10px)"), 0.001);
    }

    @Test
    public void percentageMakesItRelative() {
        assertTrue(parse(CSSName.WIDTH, "calc(50% - 20px)").getCalc().hasPercentage());
        assertFalse(parse(CSSName.WIDTH, "calc(1em + 20px)").getCalc().hasPercentage());
    }

    @Test
    public void numbersAreEvaluatedWhenParsed() {
        PropertyValue value = parse(CSSName.LINE_HEIGHT, "calc(1.5 * 2)");
        assertEquals(CSSPrimitiveValue.CSS_NUMBER, value.getPrimitiveType());
        assertEquals(3, value.getFloatValue(), 0.001);
    }

    @Test
    public void textParsesAgainToTheSameValue() {
        String css = parse(CSSName.WIDTH, "calc( ( 100% - 40px ) / 3 )").getCssText();
        assertEquals(eval("calc((100% - 40px) / 3)"), eval(css), 0.001);
        assertNotNull(CalcExpression.fromCSS(css));
    }

    @Test
    public void mixedKindsAreInvalid() {
        assertNull(parse(CSSName.WIDTH, "calc(10px + 2)"));
        assertNull(parse(CSSName.WIDTH, "calc(10px * 10px)"));
        assertNull(parse(CSSName.WIDTH, "calc(10px / 1px)"));
        assertNull(parse(CSSName.WIDTH, "clamp(1px, 2px)"));
    }
}
