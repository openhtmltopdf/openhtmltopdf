package com.openhtmltopdf.css.parser;

import com.openhtmltopdf.css.sheet.CustomPropertyDeclaration;
import com.openhtmltopdf.css.sheet.PageRule;
import com.openhtmltopdf.css.sheet.PendingVarPropertyDeclaration;
import com.openhtmltopdf.css.sheet.PropertyDeclaration;
import com.openhtmltopdf.css.sheet.Ruleset;
import com.openhtmltopdf.css.sheet.Stylesheet;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

public class CSSParserTest {

    private final static CSSErrorHandler errorHandler = (uri, message) -> System.out.println(message);
    private final static CSSParser parser = new CSSParser(errorHandler);
    private final static String basePath = "/com/openhtmltopdf/css/parser/";

    private static Stylesheet parseStylesheet(String fileName) {
        String fullPath = basePath + fileName;
        URL url = CSSParserTest.class.getResource(fullPath);
        assert url != null;

        try {
            InputStream inputStream = Files.newInputStream(new File(url.toURI()).toPath());
            return parser.parseStylesheet(url.toString(), 0, new InputStreamReader(inputStream));
        } catch (IOException | URISyntaxException e) {
            fail();
            return new Stylesheet(null, 0);
        }
    }

    @Test
    public void unrecognized_at_rule() {
        Stylesheet stylesheet = parseStylesheet("unrecognized_at_rule.css");
        assertEquals(1, stylesheet.getContents().size());
        assertTrue(stylesheet.getContents().get(0) instanceof PageRule);
    }

    @Test
    public void hsl_color() {
        Stylesheet stylesheet = parseStylesheet("hsl_color.css");
        assertEquals(8, stylesheet.getContents().size());
        for (Object content : stylesheet.getContents()) {
            Ruleset ruleset = (Ruleset) content;
            assertEquals(1, ruleset.getPropertyDeclarations().size());
            assertEquals("#60809f", ruleset.getPropertyDeclarations().get(0).getValue().getCssText());
        }
    }

    private static Ruleset firstRuleset(String css) {
        try {
            Stylesheet stylesheet = parser.parseStylesheet("test://var", 0, new StringReader(css));
            return (Ruleset) stylesheet.getContents().get(0);
        } catch (IOException e) {
            fail();
            return null;
        }
    }

    @Test
    public void rgba_hsla_color() {
        Stylesheet stylesheet = parseStylesheet("rgba_hsla_color.css");
        assertEquals(6, stylesheet.getContents().size());
        for (Object content : stylesheet.getContents()) {
            Ruleset ruleset = (Ruleset) content;
            assertEquals(1, ruleset.getPropertyDeclarations().size());
            assertEquals("rgba(96, 128, 159, 0.5)", ruleset.getPropertyDeclarations().get(0).getValue().getCssText());
        }
    }

    @Test
    public void rgba_hsla_opaque_color() {
        Stylesheet stylesheet = parseStylesheet("rgba_hsla_opaque.css");
        assertEquals(5, stylesheet.getContents().size());
        for (Object content : stylesheet.getContents()) {
            Ruleset ruleset = (Ruleset) content;
            assertEquals(1, ruleset.getPropertyDeclarations().size());
            assertEquals("#60809f", ruleset.getPropertyDeclarations().get(0).getValue().getCssText());
        }
    }

    @Test
    public void customPropertyIsRetained() {
        Ruleset ruleset = firstRuleset(":root { --main-color: red; }");

        // Custom properties are not regular declarations...
        assertTrue(ruleset.getPropertyDeclarations().isEmpty());

        // ...they are kept in a parallel list.
        List<CustomPropertyDeclaration> customProps = ruleset.getCustomPropertyDeclarations();
        assertEquals(1, customProps.size());
        assertEquals("--main-color", customProps.get(0).getName());
        assertEquals("red", customProps.get(0).getValueText());
    }

    @Test
    public void customPropertyMultiValueTextIsPreserved() {
        Ruleset ruleset = firstRuleset(":root { --box: 1px solid red; }");
        assertEquals("1px solid red", ruleset.getCustomPropertyDeclarations().get(0).getValueText());
    }

    @Test
    public void varBearingDeclarationBecomesPending() {
        Ruleset ruleset = firstRuleset("p { color: var(--main-color); }");

        List<PropertyDeclaration> decls = ruleset.getPropertyDeclarations();
        assertEquals(1, decls.size());
        assertTrue(decls.get(0) instanceof PendingVarPropertyDeclaration);

        PendingVarPropertyDeclaration pending = (PendingVarPropertyDeclaration) decls.get(0);
        assertEquals("color", pending.getPropertyName());
        assertEquals("var(--main-color)", pending.getValueText());
    }

    @Test
    public void varFallbackTextIsPreserved() {
        Ruleset ruleset = firstRuleset("p { border: var(--b, 1px solid red); }");
        PendingVarPropertyDeclaration pending =
                (PendingVarPropertyDeclaration) ruleset.getPropertyDeclarations().get(0);
        // The fallback's internal spaces must survive (not become commas).
        assertEquals("var(--b, 1px solid red)", pending.getValueText());
    }

    @Test
    public void varFreeDeclarationIsBuiltNormally() {
        Ruleset ruleset = firstRuleset("p { color: red; }");
        assertEquals(1, ruleset.getPropertyDeclarations().size());
        assertFalse(ruleset.getPropertyDeclarations().get(0) instanceof PendingVarPropertyDeclaration);
    }
}
