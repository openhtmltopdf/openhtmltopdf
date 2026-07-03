package com.openhtmltopdf.css.newmatch;

import com.openhtmltopdf.css.extend.AttributeResolver;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConditionTest {

    private static final Object ANY_ELEMENT = new Object();

    /**
     * Regression test for the bug introduced by PR #8: when a single-character
     * class like "a" appears earlier in the class attribute as a substring of
     * another class (e.g. "alpha"), {@code ClassCondition.matches} only inspects
     * the first {@code indexOf} occurrence, finds it not to be word-delimited,
     * and returns false, even though the element does have a properly-delimited
     * "a" class later on.
     */
    @Test
    public void classConditionMatchesWhenStandaloneFollowsContaminatingClass() {
        AttributeResolver attRes = new ClassOnlyAttributeResolver("alpha a b");
        assertTrue("`.a` should match class=\"alpha a b\"",
                Condition.createClassCondition("a").matches(ANY_ELEMENT, attRes, null));
        assertTrue("`.b` should match class=\"alpha a b\"",
                Condition.createClassCondition("b").matches(ANY_ELEMENT, attRes, null));
    }

    @Test
    public void classConditionMatchesStandaloneClass() {
        AttributeResolver attRes = new ClassOnlyAttributeResolver("a b");
        assertTrue(Condition.createClassCondition("a").matches(ANY_ELEMENT, attRes, null));
        assertTrue(Condition.createClassCondition("b").matches(ANY_ELEMENT, attRes, null));
    }

    @Test
    public void classConditionDoesNotMatchSubstringOnly() {
        // An element whose only class is "alpha" must NOT match the selector ".a"
        AttributeResolver attRes = new ClassOnlyAttributeResolver("alpha");
        assertFalse(Condition.createClassCondition("a").matches(ANY_ELEMENT, attRes, null));
    }

    /**
     * Minimal {@link AttributeResolver} that only knows how to report a class
     * attribute. All other methods return defaults.
     */
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
