package com.openhtmltopdf.css.newmatch;

import com.openhtmltopdf.css.extend.AttributeResolver;
import com.openhtmltopdf.css.extend.TreeResolver;
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
     * Regression test for https://github.com/openhtmltopdf/openhtmltopdf/issues/113
     * <p>
     * {@code :nth-child(n+3)} must select the 3rd child and all *following*
     * siblings, not every child. Previously {@code NthChildCondition} matched
     * every element once {@code a} (the step) reached 1, because the code
     * only checked {@code position % a == 0} without also checking that the
     * resulting repeat count {@code n} was non-negative.
     */
    @Test
    public void nthChildWithPositiveStepAndOffsetSkipsElementsBeforeOffset() {
        Condition condition = Condition.createNthChildCondition("n+3");

        assertFalse("1st child should not match :nth-child(n+3)",
                condition.matches(ANY_ELEMENT, null, new PositionOnlyTreeResolver(0)));
        assertFalse("2nd child should not match :nth-child(n+3)",
                condition.matches(ANY_ELEMENT, null, new PositionOnlyTreeResolver(1)));
        assertTrue("3rd child should match :nth-child(n+3)",
                condition.matches(ANY_ELEMENT, null, new PositionOnlyTreeResolver(2)));
        assertTrue("4th child should match :nth-child(n+3)",
                condition.matches(ANY_ELEMENT, null, new PositionOnlyTreeResolver(3)));
        assertTrue("10th child should match :nth-child(n+3)",
                condition.matches(ANY_ELEMENT, null, new PositionOnlyTreeResolver(9)));
    }

    /**
     * Companion case to the above: a negative step with a positive offset
     * (e.g. {@code :nth-child(-n+3)}) should match only the first few
     * children and stop matching afterwards.
     */
    @Test
    public void nthChildWithNegativeStepAndOffsetOnlyMatchesUpToOffset() {
        Condition condition = Condition.createNthChildCondition("-n+3");

        assertTrue("1st child should match :nth-child(-n+3)",
                condition.matches(ANY_ELEMENT, null, new PositionOnlyTreeResolver(0)));
        assertTrue("3rd child should match :nth-child(-n+3)",
                condition.matches(ANY_ELEMENT, null, new PositionOnlyTreeResolver(2)));
        assertFalse("4th child should not match :nth-child(-n+3)",
                condition.matches(ANY_ELEMENT, null, new PositionOnlyTreeResolver(3)));
        assertFalse("10th child should not match :nth-child(-n+3)",
                condition.matches(ANY_ELEMENT, null, new PositionOnlyTreeResolver(9)));
    }

    /**
     * A step other than 1 combined with an offset (e.g. {@code :nth-child(2n+3)})
     * must still only match elements reachable by a non-negative repeat count,
     * not merely ones whose position shares the correct remainder.
     */
    @Test
    public void nthChildWithStepAndOffsetOnlyMatchesReachablePositions() {
        Condition condition = Condition.createNthChildCondition("2n+3");

        assertFalse("1st child should not match :nth-child(2n+3)",
                condition.matches(ANY_ELEMENT, null, new PositionOnlyTreeResolver(0)));
        assertTrue("3rd child should match :nth-child(2n+3)",
                condition.matches(ANY_ELEMENT, null, new PositionOnlyTreeResolver(2)));
        assertFalse("4th child should not match :nth-child(2n+3)",
                condition.matches(ANY_ELEMENT, null, new PositionOnlyTreeResolver(3)));
        assertTrue("5th child should match :nth-child(2n+3)",
                condition.matches(ANY_ELEMENT, null, new PositionOnlyTreeResolver(4)));
    }

    /**
     * :nth-child(even), :nth-child(odd) and a plain :nth-child(3) must keep
     * working exactly as before after fixing #113.
     */
    @Test
    public void nthChildEvenOddAndPlainNumberStillWork() {
        Condition even = Condition.createNthChildCondition("even");
        Condition odd = Condition.createNthChildCondition("odd");
        Condition third = Condition.createNthChildCondition("3");

        assertFalse(even.matches(ANY_ELEMENT, null, new PositionOnlyTreeResolver(0))); // 1st
        assertTrue(even.matches(ANY_ELEMENT, null, new PositionOnlyTreeResolver(1)));  // 2nd
        assertTrue(odd.matches(ANY_ELEMENT, null, new PositionOnlyTreeResolver(0)));   // 1st
        assertFalse(odd.matches(ANY_ELEMENT, null, new PositionOnlyTreeResolver(1)));  // 2nd
        assertFalse(third.matches(ANY_ELEMENT, null, new PositionOnlyTreeResolver(1))); // 2nd
        assertTrue(third.matches(ANY_ELEMENT, null, new PositionOnlyTreeResolver(2)));  // 3rd
        assertFalse(third.matches(ANY_ELEMENT, null, new PositionOnlyTreeResolver(3))); // 4th
    }

    /**
     * Minimal {@link TreeResolver} that only knows how to report the
     * (0-indexed) position of the element among its siblings, which is all
     * that {@code NthChildCondition.matches} relies on.
     */
    private static final class PositionOnlyTreeResolver implements TreeResolver {
        private final int position;

        PositionOnlyTreeResolver(int position) {
            this.position = position;
        }

        @Override public Object getParentElement(Object element) { return null; }
        @Override public String getElementName(Object element) { return null; }
        @Override public Object getPreviousSiblingElement(Object node) { return null; }
        @Override public boolean isFirstChildElement(Object element) { return position == 0; }
        @Override public boolean isLastChildElement(Object element) { return false; }
        @Override public int getPositionOfElement(Object element) { return position; }
        @Override public boolean matchesElement(Object element, String namespaceURI, String name) { return false; }
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
