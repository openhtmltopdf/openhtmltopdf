package com.openhtmltopdf.css.newmatch;

import java.util.Set;

import org.junit.Test;

import com.openhtmltopdf.css.extend.AttributeResolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests that {@link Selector#mayMatch} is a sound fast-reject for
 * {@link Selector#matches}: it must never return false for an element the
 * full match would accept (it may return true for elements the full match
 * rejects; those are filtered by the full match).
 */
public class SelectorMayMatchTest {

    private static final Object ANY_ELEMENT = new Object();

    @Test
    public void rejectsOnElementName() {
        Selector sel = new Selector();
        sel.setName("div");
        assertTrue(sel.mayMatch("div", null, null));
        assertFalse(sel.mayMatch("span", null, null));
        assertFalse(sel.mayMatch(null, null, null));
    }

    @Test
    public void doesNotRejectWithoutNameRequirement() {
        Selector sel = new Selector();
        assertTrue(sel.mayMatch("div", null, null));
        assertTrue(sel.mayMatch(null, null, null));
    }

    @Test
    public void rejectsOnId() {
        Selector sel = new Selector();
        sel.addIDCondition("x");
        assertTrue(sel.mayMatch(null, "x", null));
        assertFalse(sel.mayMatch(null, "y", null));
        assertFalse(sel.mayMatch(null, null, null));
    }

    @Test
    public void rejectsOnMissingClasses() {
        Selector sel = new Selector();
        sel.addClassCondition("a");
        sel.addClassCondition("b");

        assertTrue(sel.mayMatch(null, null, Matcher.classTokens("b c a")));
        assertFalse(sel.mayMatch(null, null, Matcher.classTokens("a c")));
        // No class attribute at all
        assertFalse(sel.mayMatch(null, null, null));
        // Empty class attribute
        assertFalse(sel.mayMatch(null, null, Matcher.classTokens("  ")));
    }

    @Test
    public void classTokensSplitsOnAnyWhitespace() {
        Set<String> tokens = Matcher.classTokens("a\tb\nc  d ");
        assertEquals(4, tokens.size());
        assertTrue(tokens.contains("a"));
        assertTrue(tokens.contains("b"));
        assertTrue(tokens.contains("c"));
        assertTrue(tokens.contains("d"));

        assertNull(Matcher.classTokens(null));
        assertTrue(Matcher.classTokens("").isEmpty());
        assertTrue(Matcher.classTokens(" \t").isEmpty());
    }

    /**
     * The class part of mayMatch must agree exactly with ClassCondition,
     * including the substring-prefix collision cases of
     * https://github.com/openhtmltopdf/openhtmltopdf/issues/171.
     */
    @Test
    public void classRejectionAgreesWithClassCondition() {
        String[] classAttrs = {"alpha a b", "a b", "alpha", "ab a", "a", "aa", " a ", "b  a"};
        String[] classNames = {"a", "b", "alpha", "ab", "aa"};

        for (String classAttr : classAttrs) {
            Set<String> tokens = Matcher.classTokens(classAttr);
            AttributeResolver attRes = new ClassOnlyAttributeResolver(classAttr);

            for (String className : classNames) {
                Selector sel = new Selector();
                sel.addClassCondition(className);

                boolean full = Condition.createClassCondition(className)
                        .matches(ANY_ELEMENT, attRes, null);
                boolean fast = sel.mayMatch(null, null, tokens);

                assertEquals("class=\"" + classAttr + "\" vs ." + className, full, fast);
            }
        }
    }

    private static final class ClassOnlyAttributeResolver implements AttributeResolver {
        private final String classValue;

        ClassOnlyAttributeResolver(String classValue) {
            this.classValue = classValue;
        }

        @Override public String getClass(Object e) { return classValue; }
        @Override public String getAttributeValue(Object e, String attrName) { return null; }
        @Override public String getAttributeValue(Object e, String namespaceURI, String attrName) { return null; }
        @Override public String getID(Object e) { return null; }
        @Override public String getNonCssStyling(Object e) { return null; }
        @Override public String getElementStyling(Object e) { return null; }
        @Override public String getLang(Object e) { return null; }
        @Override public boolean isLink(Object e) { return false; }
        @Override public boolean isVisited(Object e) { return false; }
        @Override public boolean isHover(Object e) { return false; }
        @Override public boolean isActive(Object e) { return false; }
        @Override public boolean isFocus(Object e) { return false; }
        @Override public boolean isMarker(Object e) { return false; }
    }
}
