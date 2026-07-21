package com.openhtmltopdf.css.parser;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class FSRGBColorFromHSLTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {0f, 0.0f, 0.0f, 0, 0, 0},
                {0f, 1.0f, 0.0f, 0, 0, 0},
                {0f, 0.0f, 0.5f, 128, 128, 128},
                {0f, 0.0f, 1.0f, 255, 255, 255},
                {0f, 0.5f, 0.25f, 96, 32, 32},
                {0f, 0.5f, 0.5f, 191, 64, 64},
                {0f, 1.0f, 0.25f, 128, 0, 0},
                {0f, 1.0f, 0.5f, 255, 0, 0},
                {0f, 1.0f, 1.0f, 255, 255, 255},
                {60f, 0.5f, 0.5f, 191, 191, 64},
                {60f, 1.0f, 0.25f, 128, 128, 0},
                {60f, 1.0f, 0.5f, 255, 255, 0},
                {90f, 1.0f, 0.25f, 64, 128, 0},
                {120f, 1.0f, 0.25f, 0, 128, 0},
                {150f, 1.0f, 0.25f, 0, 128, 64},
                {180f, 1.0f, 0.25f, 0, 128, 128},
                {210f, 1.0f, 0.25f, 0, 64, 128},
                {240f, 1.0f, 0.25f, 0, 0, 128},
                {270f, 1.0f, 0.25f, 64, 0, 128},
                {300f, 1.0f, 0.25f, 128, 0, 128},
                {360f, 1.0f, 0.5f, 255, 0, 0},
                {480f, 1.0f, 0.5f, 0, 255, 0},
                {-120f, 1.0f, 0.5f, 0, 0, 255},
                {-480f, 1.0f, 0.5f, 0, 0, 255},
                {210.5f, 1.0f, 0.5f, 0, 125, 255},
        });
    }

    private final float hue;
    private final float saturation;
    private final float lightness;
    private final int expectedRed;
    private final int expectedGreen;
    private final int expectedBlue;

    public FSRGBColorFromHSLTest(float hue, float saturation, float lightness, int expectedRed, int expectedGreen, int expectedBlue) {
        this.hue = hue;
        this.saturation = saturation;
        this.lightness = lightness;
        this.expectedRed = expectedRed;
        this.expectedGreen = expectedGreen;
        this.expectedBlue = expectedBlue;
    }

    @Test
    public void fromHSL() {
        FSRGBColor color = FSRGBColor.fromHSL(hue, saturation, lightness);

        assertEquals(expectedRed, color.getRed());
        assertEquals(expectedGreen, color.getGreen());
        assertEquals(expectedBlue, color.getBlue());
    }

}
