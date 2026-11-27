package com.openhtmltopdf.bidi;

import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.InlineLayoutBox;
import com.openhtmltopdf.render.InlineText;

/**
 * Visual ordering of the inline children of a line or inline box, following rule L2
 * of the Unicode bidi algorithm (UAX #9): from the highest embedding level down to
 * the lowest odd level, every maximal sequence of items at that level or higher is
 * reversed.
 *
 * <p>The bidi splitter only cuts text into runs; without this step runs were laid
 * out in logical order (LTR lines) or reversed logical order (RTL lines), which is
 * wrong as soon as runs nest, such as numbers inside Arabic text inside an LTR
 * paragraph.</p>
 *
 * <p>An inline box is ordered as a unit at the lowest level of its text; its own
 * children are ordered when it is positioned. Items without a known level
 * (replaced and inline-block boxes, text when no splitter supplies levels) take the
 * level of the run around them, as a neutral would.</p>
 */
public class BidiLineOrder {
    private BidiLineOrder() {
    }

    /**
     * @return the lowest known bidi level of the text inside child, or -1 if unknown.
     */
    public static int levelOf(Object child) {
        if (child instanceof InlineText) {
            return ((InlineText) child).getBidiLevel();
        } else if (child instanceof InlineLayoutBox) {
            InlineLayoutBox iB = (InlineLayoutBox) child;
            int min = -1;
            for (int i = 0; i < iB.getInlineChildCount(); i++) {
                int level = levelOf(iB.getInlineChild(i));
                if (level >= 0 && (min < 0 || level < min)) {
                    min = level;
                }
            }
            return min;
        }
        return -1;
    }

    /**
     * The order in which to place the children of a line box: visual order (left to
     * right) for LTR lines, reverse visual order for RTL lines, which are placed from the
     * right edge. Without known levels this is the logical order.
     */
    public static int[] placementOrder(Box current, boolean rtl) {
        int[] levels = new int[current.getChildCount()];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = levelOf(current.getChild(i));
        }
        return placementOrder(levels, rtl);
    }

    /**
     * Same as {@link #placementOrder(Box, boolean)} for the inline children of an inline box.
     */
    public static int[] placementOrder(InlineLayoutBox current, boolean rtl) {
        int[] levels = new int[current.getInlineChildCount()];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = levelOf(current.getInlineChild(i));
        }
        return placementOrder(levels, rtl);
    }

    private static int[] placementOrder(int[] levels, boolean rtl) {
        int n = levels.length;
        int[] visual = visualOrder(levels, rtl ? 1 : 0);
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            if (visual == null) {
                order[i] = i;
            } else {
                order[i] = rtl ? visual[n - 1 - i] : visual[i];
            }
        }
        return order;
    }

    /**
     * Computes the visual order of items with the given bidi levels on a line whose
     * base level is baseLevel (0 for LTR, 1 for RTL).
     *
     * @param levels the level of each item in logical order, -1 where unknown.
     * @return the logical indexes in visual (left to right) order, or null if no
     * item has a known level, in which case the caller keeps its previous ordering.
     */
    public static int[] visualOrder(int[] levelsIn, int baseLevel) {
        int n = levelsIn.length;
        int[] levels = new int[n];
        boolean anyKnown = false;
        for (int i = 0; i < n; i++) {
            // Levels are relative to the paragraph the text was split in. A level below
            // the line's base level comes from a nested paragraph (an element with its own
            // direction, dir attribute or bdi), which sits in the line as an isolate:
            // treat it as a neutral of the surrounding run.
            levels[i] = levelsIn[i] >= baseLevel ? levelsIn[i] : -1;
            anyKnown |= levels[i] >= 0;
        }
        if (!anyKnown) {
            return null;
        }

        int[] resolved = new int[n];
        for (int i = 0; i < n; i++) {
            resolved[i] = levels[i] >= 0 ? levels[i] : neutralLevel(levels, i, baseLevel);
        }

        int max = baseLevel;
        for (int level : resolved) {
            max = Math.max(max, level);
        }
        int lowestOdd = (baseLevel % 2 == 1) ? baseLevel : baseLevel + 1;

        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }

        for (int level = max; level >= lowestOdd; level--) {
            int i = 0;
            while (i < n) {
                if (resolved[order[i]] >= level) {
                    int start = i;
                    while (i < n && resolved[order[i]] >= level) {
                        i++;
                    }
                    reverse(order, start, i - 1);
                } else {
                    i++;
                }
            }
        }

        return order;
    }

    /**
     * An item of unknown level between two known levels takes the lower of them
     * (it is inside both runs); at the start or end of the line it takes the base level.
     */
    private static int neutralLevel(int[] levels, int index, int baseLevel) {
        int prev = -1;
        for (int i = index - 1; i >= 0 && prev < 0; i--) {
            prev = levels[i];
        }
        int next = -1;
        for (int i = index + 1; i < levels.length && next < 0; i++) {
            next = levels[i];
        }
        if (prev < 0 || next < 0) {
            return baseLevel;
        }
        return Math.max(baseLevel, Math.min(prev, next));
    }

    private static void reverse(int[] order, int from, int to) {
        while (from < to) {
            int tmp = order[from];
            order[from] = order[to];
            order[to] = tmp;
            from++;
            to--;
        }
    }
}
