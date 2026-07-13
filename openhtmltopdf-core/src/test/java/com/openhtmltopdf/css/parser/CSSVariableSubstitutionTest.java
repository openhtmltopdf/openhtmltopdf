package com.openhtmltopdf.css.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.openhtmltopdf.css.parser.CSSVariableSubstitution.Result;
import com.openhtmltopdf.css.parser.CSSVariableSubstitution.VarResolver;

public class CSSVariableSubstitutionTest {

    private static VarResolver resolver(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map::get;
    }

    private static String resolved(String input, VarResolver resolver) {
        Result r = CSSVariableSubstitution.substitute(input, resolver);
        assertTrue("expected '" + input + "' to be fully resolved", r.isResolved());
        return r.getText();
    }

    @Test
    public void simpleSubstitution() {
        assertEquals("red", resolved("var(--a)", resolver("--a", "red")));
    }

    @Test
    public void surroundingTextIsPreserved() {
        assertEquals("1px solid red", resolved("1px solid var(--c)", resolver("--c", "red")));
    }

    @Test
    public void multipleReferences() {
        assertEquals("1px 2px", resolved("var(--a) var(--b)", resolver("--a", "1px", "--b", "2px")));
    }

    @Test
    public void fallbackWhenUndefined() {
        assertEquals("blue", resolved("var(--missing, blue)", resolver()));
    }

    @Test
    public void fallbackPreservesSpaces() {
        // Must NOT collapse to "1px,solid,red".
        assertEquals("1px solid red", resolved("var(--m, 1px solid red)", resolver()));
    }

    @Test
    public void fallbackCanContainVar() {
        assertEquals("green", resolved("var(--missing, var(--b))", resolver("--b", "green")));
    }

    @Test
    public void nestedInsideFunction() {
        assertEquals("calc(10px * 2)", resolved("calc(var(--a) * 2)", resolver("--a", "10px")));
    }

    @Test
    public void commaInsideFallbackFunctionIsNotASeparator() {
        assertEquals("rgb(1, 2, 3)", resolved("var(--x, rgb(1, 2, 3))", resolver()));
    }

    @Test
    public void parenInsideStringIsRespected() {
        assertEquals("\"a)b\"", resolved("var(--x, \"a)b\")", resolver()));
    }

    @Test
    public void caseInsensitiveAndWhitespace() {
        assertEquals("red", resolved("VAR( --a )", resolver("--a", "red")));
    }

    @Test
    public void undefinedWithoutFallbackIsUnresolved() {
        Result r = CSSVariableSubstitution.substitute("var(--missing)", resolver());
        assertFalse(r.isResolved());
    }

    @Test
    public void notAVarFunction() {
        // "sidebar(" ends in "bar(" but the "var(" is part of an identifier.
        assertFalse(CSSVariableSubstitution.containsVar("sidebar(--a)"));
        Result r = CSSVariableSubstitution.substitute("sidebar(--a)", resolver("--a", "x"));
        assertTrue(r.isResolved());
        assertEquals("sidebar(--a)", r.getText());
    }

    @Test
    public void containsVarDetection() {
        assertTrue(CSSVariableSubstitution.containsVar("1px var(--a)"));
        assertFalse(CSSVariableSubstitution.containsVar("1px solid red"));
    }

    @Test
    public void emptyFallbackResolvesToEmpty() {
        // var(--undefined,) is valid and resolves to an empty value.
        assertEquals("", resolved("var(--missing,)", resolver()));
    }

    @Test
    public void bareVarIsUnresolved() {
        Result r = CSSVariableSubstitution.substitute("var()", resolver());
        assertFalse(r.isResolved());
    }

    @Test
    public void emptyNameFallsBackToFallback() {
        // No valid custom-property name, but a fallback is present.
        assertEquals("blue", resolved("var(, blue)", resolver()));
    }

    @Test
    public void unterminatedVarIsUnresolved() {
        Result r = CSSVariableSubstitution.substitute("var(--a", resolver("--a", "x"));
        assertFalse(r.isResolved());
    }

    @Test
    public void varInsideStringIsLiteralNotAReference() {
        // A string token is opaque: "var(" inside it is literal text. Only the
        // real var(--b) is substituted; the string is left untouched.
        assertEquals("\"func(var(--a))\" YYY",
                resolved("\"func(var(--a))\" var(--b)", resolver("--a", "XXX", "--b", "YYY")));
    }

    @Test
    public void containsVarIgnoresVarInsideString() {
        // The whole value is a single string that happens to contain "var(".
        assertFalse(CSSVariableSubstitution.containsVar("\"hello var(--y)\""));
    }

    @Test
    public void varRightAfterAClosedStringIsAReference() {
        // The string closes at the quote, so the adjacent var( is a real function.
        assertEquals("\"abc\"Z", resolved("\"abc\"var(--x)", resolver("--x", "Z")));
    }
}
