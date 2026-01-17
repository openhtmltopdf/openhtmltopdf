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
                {0, 0.0f, 0.0f, 0, 0, 0},
                {0, 1.0f, 0.0f, 0, 0, 0},
                {0, 0.0f, 0.5f, 128, 128, 128},
                {0, 0.0f, 1.0f, 255, 255, 255},
                {0, 0.5f, 0.25f, 96, 32, 32},
                {0, 0.5f, 0.5f, 191, 64, 64},
                {0, 1.0f, 0.25f, 128, 0, 0},
                {0, 1.0f, 0.5f, 255, 0, 0},
                {0, 1.0f, 1.0f, 255, 255, 255},
                {60, 0.5f, 0.5f, 191, 191, 64},
                {60, 1.0f, 0.25f, 128, 128, 0},
                {60, 1.0f, 0.5f, 255, 255, 0},
                {90, 1.0f, 0.25f, 64, 128, 0},
                {120, 1.0f, 0.25f, 0, 128, 0},
                {150, 1.0f, 0.25f, 0, 128, 64},
                {180, 1.0f, 0.25f, 0, 128, 128},
                {210, 1.0f, 0.25f, 0, 64, 128},
                {240, 1.0f, 0.25f, 0, 0, 128},
                {270, 1.0f, 0.25f, 64, 0, 128},
                {300, 1.0f, 0.25f, 128, 0, 128},
        });
    }

    private final int hue;
    private final float saturation;
    private final float lightness;
    private final int expectedRed;
    private final int expectedGreen;
    private final int expectedBlue;

    public FSRGBColorFromHSLTest(int hue, float saturation, float lightness, int expectedRed, int expectedGreen, int expectedBlue) {
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
