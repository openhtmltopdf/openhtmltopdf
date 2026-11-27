package com.openhtmltopdf.bidi;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class BidiLineOrderTest {
    @Test
    public void testUnknownLevelsKeepLogicalOrder() {
        assertNull(BidiLineOrder.visualOrder(new int[] { -1, -1 }, 0));
    }

    @Test
    public void testLtrLineWithoutRtlRunsIsUnchanged() {
        assertArrayEquals(new int[] { 0, 1, 2 }, BidiLineOrder.visualOrder(new int[] { 0, 0, 0 }, 0));
    }

    /**
     * Issue #148: an RTL stretch with numbers (level 2) inside an LTR paragraph.
     */
    @Test
    public void testNumbersInsideRtlInsideLtr() {
        int[] levels = { 0, 1, 2, 1, 2, 0 };
        assertArrayEquals(new int[] { 0, 4, 3, 2, 1, 5 }, BidiLineOrder.visualOrder(levels, 0));
    }

    @Test
    public void testAdjacentRtlRunsInLtrLineAreSwapped() {
        assertArrayEquals(new int[] { 0, 2, 1, 3 }, BidiLineOrder.visualOrder(new int[] { 0, 1, 1, 0 }, 0));
    }

    @Test
    public void testAdjacentLtrRunsInRtlLineKeepTheirOrder() {
        // Visual order left to right: the trailing RTL run, then the LTR runs in order, then the leading RTL run.
        assertArrayEquals(new int[] { 3, 1, 2, 0 }, BidiLineOrder.visualOrder(new int[] { 1, 2, 2, 1 }, 1));
    }

    @Test
    public void testUnknownLevelBetweenRtlRunsJoinsThem() {
        assertArrayEquals(new int[] { 0, 3, 2, 1 }, BidiLineOrder.visualOrder(new int[] { 0, 1, -1, 1 }, 0));
    }

    /**
     * An LTR isolate (own paragraph, level 0) between Arabic words in an RTL line,
     * as in issue-464-rtl-with-phone-number: it stays between them.
     */
    @Test
    public void testLevelBelowBaseIsTreatedAsNeutral() {
        assertArrayEquals(new int[] { 2, 1, 0 }, BidiLineOrder.visualOrder(new int[] { 1, 0, 1 }, 1));
    }

    @Test
    public void testUnknownLevelAtLineEndTakesBaseLevel() {
        assertArrayEquals(new int[] { 1, 0, 2 }, BidiLineOrder.visualOrder(new int[] { 1, 1, -1 }, 0));
    }
}
