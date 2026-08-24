package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Element;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.parser.FSRGBColor;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.render.BlockBox;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.visualtest.TestSupport;

/**
 * End-to-end tests for CSS custom properties ({@code --*}) and {@code var()},
 * exercising the real cascade and per-element computed-value resolution.
 */
public class CSSCustomPropertiesVarTest {

    @BeforeClass
    public static void configure() {
        TestSupport.quietLogs();
    }

    private static final String HTML =
        "<html><head><style>\n" +
        ":root { --main: rgb(10, 20, 30); --gap: 7px; }\n" +
        "#a { color: var(--main); }\n" +                          // :root variable
        "#b { --main: rgb(40, 50, 60); color: var(--main); }\n" + // own override
        "#b-child { color: var(--main); }\n" +                    // inherits #b's --main
        "#c { color: var(--missing, rgb(70, 80, 90)); }\n" +      // fallback
        "#d { padding: var(--gap); }\n" +                         // shorthand
        "#e { color: var(--undefined); }\n" +                     // unresolved -> unset -> inherit
        "#f { --x: var(--y); --y: var(--x); color: var(--x); }\n" + // reference cycle
        "</style></head>\n" +
        "<body style=\"color: rgb(1, 2, 3)\">\n" +
        "  <div id=\"a\">A</div>\n" +
        "  <div id=\"b\">B<div id=\"b-child\">child</div></div>\n" +
        "  <div id=\"c\">C</div>\n" +
        "  <div id=\"d\">D</div>\n" +
        "  <div id=\"e\">E</div>\n" +
        "  <div id=\"f\">F</div>\n" +
        "</body></html>";

    private BlockBox rootBox;

    private void layout() throws IOException {
        layoutHtml(HTML);
    }

    private void layoutHtml(String html) throws IOException {
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(new ByteArrayOutputStream());
        try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
            renderer.layout();
            rootBox = renderer.getRootBox();
        }
    }

    private String renderToText(String html) throws IOException {
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        builder.toStream(os);
        builder.run();
        try (PDDocument doc = Loader.loadPDF(os.toByteArray())) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private Box byId(String id) {
        Box found = findById(rootBox, id);
        assertNotNull("no box for #" + id, found);
        return found;
    }

    private static Box findById(Box box, String id) {
        Element e = box.getElement();
        if (e != null && id.equals(e.getAttribute("id"))) {
            return box;
        }
        for (int i = 0; i < box.getChildCount(); i++) {
            Box found = findById(box.getChild(i), id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void assertColor(String id, int r, int g, int b) {
        assertColorOf(id, CSSName.COLOR, r, g, b);
    }

    private void assertColorOf(String id, CSSName name, int r, int g, int b) {
        FSRGBColor rgb = (FSRGBColor) byId(id).getStyle().asColor(name);
        assertEquals("#" + id + " " + name + " red", r, rgb.getRed());
        assertEquals("#" + id + " " + name + " green", g, rgb.getGreen());
        assertEquals("#" + id + " " + name + " blue", b, rgb.getBlue());
    }

    @Test
    public void rootVariable() throws IOException {
        layout();
        assertColor("a", 10, 20, 30);
    }

    @Test
    public void perElementOverride() throws IOException {
        layout();
        assertColor("b", 40, 50, 60);
    }

    @Test
    public void inheritedCustomPropertyScoping() throws IOException {
        layout();
        // #b-child inherits --main from #b (40,50,60), not :root's (10,20,30).
        assertColor("b-child", 40, 50, 60);
    }

    @Test
    public void fallbackWhenUndefined() throws IOException {
        layout();
        assertColor("c", 70, 80, 90);
    }

    @Test
    public void shorthandWithVar() throws IOException {
        layout();
        assertEquals(7f, byId("d").getStyle().asFloat(CSSName.PADDING_LEFT), 0.01f);
        assertEquals(7f, byId("d").getStyle().asFloat(CSSName.PADDING_TOP), 0.01f);
    }

    @Test
    public void unresolvedVarFallsBackToInherited() throws IOException {
        layout();
        // var(--undefined) with no fallback -> unset -> inherits body color (1,2,3).
        assertColor("e", 1, 2, 3);
    }

    @Test
    public void cyclicReferenceIsTreatedAsUnset() throws IOException {
        layout();
        // --x and --y form a cycle, so var(--x) is invalid -> unset -> inherit (1,2,3).
        assertColor("f", 1, 2, 3);
    }

    @Test
    public void explicitLonghandOverridesVarShorthand() throws IOException {
        // A var()-bearing shorthand must compete in the cascade at the longhand
        // level, exactly like a normal shorthand. Here the later, same-specificity
        // explicit padding-left/right: 0 must win over the earlier padding shorthand,
        // while padding-top/bottom keep the shorthand's 16px. This mirrors Orbeon's
        // ".fr-section-content { padding: var(--x) }" plus
        // "@media print { ...; padding-left: 0; padding-right: 0 }".
        String html =
            "<html><head><style>" +
            ":root { --pad: 16px; }" +
            "#x { padding: var(--pad); }" +
            "#x { padding-left: 0; padding-right: 0; }" +
            "</style></head><body><div id=\"x\">X</div></body></html>";

        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(new ByteArrayOutputStream());
        try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
            renderer.layout();
            rootBox = renderer.getRootBox();
        }

        assertEquals("padding-left (explicit, later) must override the var shorthand",
                0f, byId("x").getStyle().asFloat(CSSName.PADDING_LEFT), 0.01f);
        assertEquals("padding-right (explicit, later) must override the var shorthand",
                0f, byId("x").getStyle().asFloat(CSSName.PADDING_RIGHT), 0.01f);
        assertEquals("padding-top must keep the var shorthand value",
                16f, byId("x").getStyle().asFloat(CSSName.PADDING_TOP), 0.01f);
        assertEquals("padding-bottom must keep the var shorthand value",
                16f, byId("x").getStyle().asFloat(CSSName.PADDING_BOTTOM), 0.01f);
    }

    @Test
    public void varShorthandOverridesEarlierExplicitLonghand() throws IOException {
        // The reverse order: a var()-bearing shorthand declared after an explicit
        // longhand (same specificity) must win for every side it sets.
        String html =
            "<html><head><style>" +
            ":root { --pad: 16px; }" +
            "#x { padding-left: 0; }" +
            "#x { padding: var(--pad); }" +
            "</style></head><body><div id=\"x\">X</div></body></html>";

        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        builder.toStream(new ByteArrayOutputStream());
        try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
            renderer.layout();
            rootBox = renderer.getRootBox();
        }

        assertEquals("later var shorthand must override the earlier explicit longhand",
                16f, byId("x").getStyle().asFloat(CSSName.PADDING_LEFT), 0.01f);
    }

    @Test
    public void varInContentProperty() throws IOException {
        String html =
            "<html><head><style>" +
            ":root { --label: \"GENERATED\"; }" +
            "#x::before { content: var(--label); }" +
            "</style></head><body><div id=\"x\"></div></body></html>";

        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        builder.toStream(os);
        builder.run();

        try (PDDocument doc = Loader.loadPDF(os.toByteArray())) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue("expected generated content from var(), got: " + text,
                    text.contains("GENERATED"));
        }
    }

    @Test
    public void selfReferentialCycleIsUnset() throws IOException {
        // --x references itself: invalid at computed-value time -> unset -> inherit.
        layoutHtml("<html><head><style>" +
            "#x { --x: var(--x); color: var(--x); }" +
            "</style></head><body style=\"color: rgb(1, 2, 3)\"><div id=\"x\">X</div></body></html>");
        assertColor("x", 1, 2, 3);
    }

    @Test
    public void undefinedVarOnNonInheritedPropertyIsInitial() throws IOException {
        // padding is not inherited, so an unresolved var() with no fallback -> unset
        // -> the initial value (0), not the parent's 40px.
        layoutHtml("<html><head><style>" +
            "#p { padding-left: 40px; }" +
            "#c { padding-left: var(--missing); }" +
            "</style></head><body><div id=\"p\"><div id=\"c\">C</div></div></body></html>");
        assertEquals(0f, byId("c").getStyle().asFloat(CSSName.PADDING_LEFT), 0.01f);
    }

    @Test
    public void varResolvingToPropertyInvalidValueIsUnset() throws IOException {
        // --bad resolves fine as text, but "12px" is invalid for color, so the
        // re-parse fails -> unset -> inherit the body color.
        layoutHtml("<html><head><style>" +
            "#x { --bad: 12px; color: var(--bad); }" +
            "</style></head><body style=\"color: rgb(1, 2, 3)\"><div id=\"x\">X</div></body></html>");
        assertColor("x", 1, 2, 3);
    }

    @Test
    public void customPropertyNameIsCaseSensitive() throws IOException {
        // --Foo and --foo are distinct names; var(--Foo) must pick --Foo.
        layoutHtml("<html><head><style>" +
            "#x { --Foo: rgb(11, 22, 33); --foo: rgb(99, 88, 77); color: var(--Foo); }" +
            "</style></head><body><div id=\"x\">X</div></body></html>");
        assertColor("x", 11, 22, 33);
    }

    @Test
    public void importantOnVarDeclarationWins() throws IOException {
        // The !important var declaration beats the later non-important one.
        layoutHtml("<html><head><style>" +
            ":root { --a: rgb(7, 7, 7); }" +
            "#x { color: var(--a) !important; }" +
            "#x { color: rgb(5, 5, 5); }" +
            "</style></head><body><div id=\"x\">X</div></body></html>");
        assertColor("x", 7, 7, 7);
    }

    @Test
    public void borderShorthandWithVar() throws IOException {
        // A var() border shorthand expands to the border-* longhands.
        layoutHtml("<html><head><style>" +
            "#x { --b: 2px solid rgb(10, 20, 30); border: var(--b); }" +
            "</style></head><body><div id=\"x\">X</div></body></html>");
        assertEquals(2f, byId("x").getStyle().asFloat(CSSName.BORDER_TOP_WIDTH), 0.01f);
        assertColorOf("x", CSSName.BORDER_TOP_COLOR, 10, 20, 30);
    }

    @Test
    public void varCombinedWithStaticToken() throws IOException {
        // var() and a literal token in the same value: margin: 5px 10px.
        layoutHtml("<html><head><style>" +
            "#x { --c: 5px; margin: var(--c) 10px; }" +
            "</style></head><body><div id=\"x\">X</div></body></html>");
        assertEquals(5f, byId("x").getStyle().asFloat(CSSName.MARGIN_TOP), 0.01f);
        assertEquals(10f, byId("x").getStyle().asFloat(CSSName.MARGIN_LEFT), 0.01f);
    }

    @Test
    public void customPropertyCascadeBySpecificity() throws IOException {
        // The higher-specificity #x rule's --x wins over the div rule's.
        layoutHtml("<html><head><style>" +
            "div { --x: rgb(1, 1, 1); }" +
            "#x { --x: rgb(2, 2, 2); }" +
            "#x { color: var(--x); }" +
            "</style></head><body><div id=\"x\">X</div></body></html>");
        assertColor("x", 2, 2, 2);
    }

    @Test
    public void multiLevelVarChain() throws IOException {
        // --a -> --b -> --c -> 9px (more than one level of indirection).
        layoutHtml("<html><head><style>" +
            "#x { --a: var(--b); --b: var(--c); --c: 9px; padding-left: var(--a); }" +
            "</style></head><body><div id=\"x\">X</div></body></html>");
        assertEquals(9f, byId("x").getStyle().asFloat(CSSName.PADDING_LEFT), 0.01f);
    }

    @Test
    public void identicalChildStylesResolveAgainstTheirOwnParent() throws IOException {
        // l1 and l2 match the same rule (same fingerprint) but inherit different
        // --x from their parents. The per-parent style cache must not collide.
        layoutHtml("<html><head><style>" +
            "#p1 { --x: rgb(1, 2, 3); } #p2 { --x: rgb(4, 5, 6); }" +
            ".leaf { color: var(--x); }" +
            "</style></head><body>" +
            "<div id=\"p1\"><div class=\"leaf\" id=\"l1\">1</div></div>" +
            "<div id=\"p2\"><div class=\"leaf\" id=\"l2\">2</div></div>" +
            "</body></html>");
        assertColor("l1", 1, 2, 3);
        assertColor("l2", 4, 5, 6);
    }

    @Test
    public void pseudoElementLocalCustomProperty() throws IOException {
        // A custom property declared on the pseudo-element rule itself must be
        // available to var() in that rule (regression test for getPECascadedStyle).
        String text = renderToText("<html><head><style>" +
            "#x::before { --c: \"LOCALVAR\"; content: var(--c); }" +
            "</style></head><body><div id=\"x\"></div></body></html>");
        assertTrue("expected pseudo-element-local var content, got: " + text,
                text.contains("LOCALVAR"));
    }

    @Test
    public void sameVarUsedTwiceIsNotACycle() throws IOException {
        // The cycle guard tracks the current resolution stack, not "already seen":
        // --g (which itself uses var) is resolved once per occurrence, so
        // "var(--g) var(--g)" yields 4px on both sides rather than going unset.
        layoutHtml("<html><head><style>" +
            "#x { --base: 4px; --g: var(--base); padding: var(--g) var(--g); }" +
            "</style></head><body><div id=\"x\">X</div></body></html>");
        assertEquals(4f, byId("x").getStyle().asFloat(CSSName.PADDING_LEFT), 0.01f);
        assertEquals(4f, byId("x").getStyle().asFloat(CSSName.PADDING_TOP), 0.01f);
    }

    @Test
    public void nestedFallbackChain() throws IOException {
        // Both --m and --n are undefined, so the value falls through to the
        // innermost fallback.
        layoutHtml("<html><head><style>" +
            "#x { color: var(--m, var(--n, rgb(11, 22, 33))); }" +
            "</style></head><body><div id=\"x\">X</div></body></html>");
        assertColor("x", 11, 22, 33);
    }

    @Test
    public void varAsRgbComponent() throws IOException {
        // rgb()/rgba()/hsl()/cmyk() are evaluated while parsing, so a var()
        // parameter has to defer the whole function to the pending path.
        layoutHtml("<html><head><style>" +
            "#x { --r: 10; color: rgb(var(--r), 20, 30); }" +
            "</style></head><body><div id=\"x\">X</div></body></html>");
        assertColor("x", 10, 20, 30);
    }

    @Test
    public void varAsWholeRgbaArgumentList() throws IOException {
        // One custom property standing in for several arguments at once.
        layoutHtml("<html><head><style>" +
            "#x { --rgb: 10, 20, 30; color: rgba(var(--rgb), 0.5); }" +
            "</style></head><body><div id=\"x\">X</div></body></html>");
        assertColor("x", 10, 20, 30);
    }

    @Test
    public void varAsHslComponent() throws IOException {
        layoutHtml("<html><head><style>" +
            "#x { --h: 210; color: hsl(var(--h), 50%, 50%); }" +
            "</style></head><body><div id=\"x\">X</div></body></html>");
        assertColor("x", 64, 128, 191);
    }

    @Test
    public void undefinedVarInRgbIsUnset() throws IOException {
        // The deferred function must still go unset (here: inherit) when the
        // reference cannot be resolved, not render as an unevaluated function.
        layoutHtml("<html><head><style>" +
            "body { color: rgb(7, 7, 7); }" +
            "#x { color: rgb(var(--nope), 20, 30); }" +
            "</style></head><body><div id=\"x\">X</div></body></html>");
        assertColor("x", 7, 7, 7);
    }
}
