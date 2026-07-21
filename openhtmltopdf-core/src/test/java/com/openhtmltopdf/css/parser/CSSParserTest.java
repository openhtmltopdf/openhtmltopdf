package com.openhtmltopdf.css.parser;

import com.openhtmltopdf.css.sheet.PageRule;
import com.openhtmltopdf.css.sheet.Ruleset;
import com.openhtmltopdf.css.sheet.Stylesheet;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;

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

}
