package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Element;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.css.parser.FSRGBColor;
import com.openhtmltopdf.css.style.CalculatedStyle;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.testlistener.PrintingRunner;

/**
 * Tests the CSS <code>currentColor</code> keyword, which stands for the
 * element's own text color wherever a color is expected.
 *
 * @see <a href="https://github.com/openhtmltopdf/openhtmltopdf/issues/115">Issue 115</a>
 */
@RunWith(PrintingRunner.class)
public class CurrentColorTest {
    private static final FSRGBColor RED = new FSRGBColor(255, 0, 0);
    private static final FSRGBColor GREEN = new FSRGBColor(0, 128, 0);
    private static final FSRGBColor BLACK = new FSRGBColor(0, 0, 0);

    /**
     * Lays the markup out and hands back the style of the element with the
     * given id.
     */
    private static CalculatedStyle styleOf(String css, String body, String id) throws IOException {
        String html =
            "<html><head><style>@page { size: 400px 400px; }" + css + "</style></head>" +
            "<body>" + body + "</body></html>";

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(os);
        builder.testMode(true);

        CalculatedStyle found;
        try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
            renderer.layout();
            found = styleOfBox(renderer.getRootBox(), id);
        }

        if (found == null) {
            throw new AssertionError("No box with id " + id);
        }
        return found;
    }

    private static CalculatedStyle styleOfBox(Box box, String id) {
        Element element = box.getElement();
        if (element != null && id.equals(element.getAttribute("id"))) {
            return box.getStyle();
        }

        for (int i = 0; i < box.getChildCount(); i++) {
            CalculatedStyle result = styleOfBox(box.getChild(i), id);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /*
     * Each case sets a different color first, so that a dropped currentColor
     * declaration would leave that one behind rather than the expected one.
     * Without this, an unsupported keyword would look supported: an unset
     * border color already falls back to the text color on its own.
     */

    @Test
    public void testBorderColor() throws IOException {
        CalculatedStyle style = styleOf(
                "#target { color: red; border-color: blue; border: 1px solid currentColor; }",
                "<div id='target'>text</div>", "target");

        assertEquals(RED, style.asColor(CSSName.BORDER_TOP_COLOR));
        assertEquals(RED, style.asColor(CSSName.BORDER_RIGHT_COLOR));
        assertEquals(RED, style.asColor(CSSName.BORDER_BOTTOM_COLOR));
        assertEquals(RED, style.asColor(CSSName.BORDER_LEFT_COLOR));
    }

    @Test
    public void testBorderColorLonghand() throws IOException {
        CalculatedStyle style = styleOf(
                "#target { color: red; border-top-color: blue; border-top-color: currentColor; }",
                "<div id='target'>text</div>", "target");

        assertEquals(RED, style.asColor(CSSName.BORDER_TOP_COLOR));
    }

    /** border-color takes one to four values, all of which may be the keyword. */
    @Test
    public void testBorderColorOneToFour() throws IOException {
        CalculatedStyle style = styleOf(
                "#target { color: red; border-color: blue; border-color: currentColor green; }",
                "<div id='target'>text</div>", "target");

        assertEquals(RED, style.asColor(CSSName.BORDER_TOP_COLOR));
        assertEquals(GREEN, style.asColor(CSSName.BORDER_RIGHT_COLOR));
        assertEquals(RED, style.asColor(CSSName.BORDER_BOTTOM_COLOR));
        assertEquals(GREEN, style.asColor(CSSName.BORDER_LEFT_COLOR));
    }

    @Test
    public void testBackgroundColor() throws IOException {
        CalculatedStyle style = styleOf(
                "#target { color: green; background-color: blue; background-color: currentColor; }",
                "<div id='target'>text</div>", "target");

        assertEquals(GREEN, style.getBackgroundColor());
    }

    @Test
    public void testBackgroundShorthand() throws IOException {
        CalculatedStyle style = styleOf(
                "#target { color: green; background: blue; background: currentColor; }",
                "<div id='target'>text</div>", "target");

        assertEquals(GREEN, style.getBackgroundColor());
    }

    /**
     * currentColor takes the element's own color, not the one it inherited, so
     * a color set on the element itself wins.
     */
    @Test
    public void testTakesTheElementsOwnColor() throws IOException {
        CalculatedStyle style = styleOf(
                "#parent { color: red; }" +
                "#target { color: green; border-top-color: blue; border-top-color: currentColor; }",
                "<div id='parent'><div id='target'>text</div></div>", "target");

        assertEquals(GREEN, style.asColor(CSSName.BORDER_TOP_COLOR));
    }

    /** With no color of its own, the inherited one is the element's color. */
    @Test
    public void testFallsBackToTheInheritedColor() throws IOException {
        CalculatedStyle style = styleOf(
                "#parent { color: red; }" +
                "#target { border-top-color: blue; border-top-color: currentColor; }",
                "<div id='parent'><div id='target'>text</div></div>", "target");

        assertEquals(RED, style.asColor(CSSName.BORDER_TOP_COLOR));
    }

    /**
     * On the color property itself, currentColor means the inherited color -
     * anything else would be circular.
     */
    @Test
    public void testOnTheColorPropertyMeansInherited() throws IOException {
        CalculatedStyle style = styleOf(
                "#parent { color: red; } #target { color: blue; color: currentColor; }",
                "<div id='parent'><div id='target'>text</div></div>", "target");

        assertEquals(RED, style.getColor());
    }

    /** With nothing to inherit from, the initial color is all that is left. */
    @Test
    public void testOnTheColorPropertyAtTheRoot() throws IOException {
        CalculatedStyle style = styleOf(
                "#target { color: blue; color: currentColor; }",
                "<div id='target'>text</div>", "target");

        assertEquals(BLACK, style.getColor());
    }

    /** The keyword is a keyword, so case does not matter. */
    @Test
    public void testIsCaseInsensitive() throws IOException {
        CalculatedStyle style = styleOf(
                "#target { color: red; border-top-color: blue; border-top-color: CURRENTCOLOR; }",
                "<div id='target'>text</div>", "target");

        assertEquals(RED, style.asColor(CSSName.BORDER_TOP_COLOR));
    }

    /** The decoration color keeps working through the same resolution. */
    @Test
    public void testTextDecorationColor() throws IOException {
        CalculatedStyle style = styleOf(
                "#target { color: green; text-decoration: underline blue;" +
                " text-decoration-color: currentColor; }",
                "<div id='target'>text</div>", "target");

        assertEquals(GREEN, style.asColor(CSSName.TEXT_DECORATION_COLOR));
    }

    /**
     * The keyword is also a color the text-decoration shorthand can carry. The
     * shorthand comes last here, so a dropped declaration would leave the red
     * behind rather than reset it - the keyword resolving to green is the only
     * way to get there.
     */
    @Test
    public void testTextDecorationShorthandCurrentColor() throws IOException {
        CalculatedStyle style = styleOf(
                "#target { color: green; text-decoration-color: red;" +
                " text-decoration: underline currentColor; }",
                "<div id='target'>text</div>", "target");

        assertEquals(GREEN, style.asColor(CSSName.TEXT_DECORATION_COLOR));
        assertEquals(Collections.singletonList(IdentValue.UNDERLINE), style.getTextDecorations());
    }

    /** transparent is the other color keyword the shorthand has to take. */
    @Test
    public void testTextDecorationShorthandTransparent() throws IOException {
        CalculatedStyle style = styleOf(
                "#target { color: green; text-decoration-color: red;" +
                " text-decoration: underline transparent; }",
                "<div id='target'>text</div>", "target");

        assertEquals(FSRGBColor.TRANSPARENT, style.asColor(CSSName.TEXT_DECORATION_COLOR));
        assertEquals(Collections.singletonList(IdentValue.UNDERLINE), style.getTextDecorations());
    }
}
