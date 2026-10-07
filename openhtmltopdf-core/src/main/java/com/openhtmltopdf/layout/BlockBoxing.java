/*
 * {{{ header & license
 * Copyright (c) 2004, 2005 Joshua Marinacci, Torbjoern Gannholm
 * Copyright (c) 2005 Wisconsin Court System
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * }}}
 */
package com.openhtmltopdf.layout;

import java.awt.Dimension;
import java.util.List;
import java.util.TreeSet;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.css.style.CalculatedStyle;
import com.openhtmltopdf.layout.LayoutContext.BlockBoxingState;
import com.openhtmltopdf.render.BlockBox;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.LineBox;
import com.openhtmltopdf.render.PageBox;
import com.openhtmltopdf.render.BlockBox.ContentType;

/**
 * Utility class for laying block content.  It is called when a block box
 * contains block level content.  {@link BoxBuilder} will have made sure that
 * the block we're working on will either contain only inline or block content.
 * If we're in a paged media environment, the various page break related
 * properties are also handled here.  If a rule is violated, the affected run
 * of boxes will be layed out again.  If the rule still cannot be satisfied,
 * the rule will be dropped.
 * <br><br>
 * IMPORTANT: This is quite hard to get right without causing an explosion of layouts
 * caused by re-attempts to satisfy page-break-inside: avoid in deeply nested content.
 * Please be careful when editing these functions.
 */
public class BlockBoxing {
    private static final int NO_PAGE_TRIM = -1;

    private BlockBoxing() {
    }

    /**
     * Lays out a {@link BlockBox} where {@link BlockBox#getChildrenContentType()} is
     * {@link ContentType#BLOCK}.
     */
    public static void layoutContent(LayoutContext c, BlockBox block, int contentStart) {
        List<Box> localChildren = block.getChildren();
        int size = localChildren.size();

        int childOffset = block.getHeight() + contentStart;

        AbstractRelayoutDataList relayoutDataList = null;
        BlockBoxingState enterState = c.getBlockBoxingState();

        if (c.isPrint()) {
            relayoutDataList = new LiteRelayoutDataList(size);
        }

        int pageCount = NO_PAGE_TRIM;

        BlockBox previousChildBox = null;
        boolean oneChildFailed = false;

        for (int offset = 0; offset < size; offset++) {
            BlockBox child = (BlockBox) localChildren.get(offset);
            LayoutState savedChildLayoutState = null;
            boolean rootPageBreakInsideAvoid = false;

            if (c.isPrint()) {
                savedChildLayoutState = c.copyStateForRelayout();

                relayoutDataList.setLayoutState(offset, savedChildLayoutState);
                relayoutDataList.setChildOffset(offset, childOffset);

                pageCount = c.getRootLayer().getPages().size();

                child.setNeedPageClear(false);

                if ((child.getStyle().isAvoidPageBreakInside() ||
                     child.getStyle().isKeepWithInline()) &&
                    c.getBlockBoxingState() == BlockBoxingState.NOT_SET) {
                    rootPageBreakInsideAvoid = true;
                }
            }

            if (rootPageBreakInsideAvoid) {
                c.setBlockBoxingState(BlockBoxingState.ALLOW);
            } else {
                c.setBlockBoxingState(enterState);
            }

            // Our first try at layout with no page clear beforehand.
            layoutBlockChild(
                    c, block, child, false, childOffset, NO_PAGE_TRIM, savedChildLayoutState);

            if (c.isPrint()) {
                boolean needPageClear = child.isNeedPageClear();

                if (needPageClear || c.getBlockBoxingState() == BlockBoxingState.ALLOW) {
                    boolean pageBreak = child.crossesPageBreak(c);
                    boolean pageBreakAfterRetry = pageBreak;
                    boolean tryToAvoidPageBreak = pageBreak && child.getStyle().isAvoidPageBreakInside();
                    boolean keepWithInline = child.isNeedsKeepWithInline(c);

                    if (tryToAvoidPageBreak || needPageClear || keepWithInline) {
                        c.restoreStateForRelayout(savedChildLayoutState);
                        child.reset(c);

                        c.setBlockBoxingState(BlockBoxingState.DENY);
                        // Our second attempt with page clear beforehand.
                        layoutBlockChild(
                                c, block, child, true, childOffset, pageCount, savedChildLayoutState);
                        c.setBlockBoxingState(enterState);

                        pageBreakAfterRetry = child.crossesPageBreak(c);

                        if (tryToAvoidPageBreak && pageBreakAfterRetry && ! keepWithInline) {
                            c.restoreStateForRelayout(savedChildLayoutState);
                            child.reset(c);

                            c.setBlockBoxingState(BlockBoxingState.ALLOW);
                            // Our second attempt failed, so reset with no page break beforehand.
                            layoutBlockChild(
                                    c, block, child, false, childOffset, pageCount, savedChildLayoutState);
                        }

                        if (pageBreakAfterRetry) {
                            oneChildFailed = true;
                        }

                        if (needPageClear && isBreakBeforeChildBreakBeforeBlock(c, block, localChildren, offset)) {
                            // Nothing of this block would be left on the page, so move the
                            // whole block. Otherwise floats placed before the child (such as
                            // a floated li::before bullet) stay behind on the old page.
                            block.setNeedPageClear(true);
                        }
                    }
                }

                c.getRootLayer().ensureHasPage(c, child);
            }

            Dimension relativeOffset = child.getRelativeOffset();
            if (relativeOffset == null) {
                childOffset = child.getY() + child.getHeight();
            } else {
                // Box will have been positioned by this point so calculate
                // relative to where it would have been if it hadn't been
                // moved
                childOffset = child.getY() - relativeOffset.height + child.getHeight();
            }

            if (childOffset > block.getHeight()) {
                block.setHeight(childOffset);
            }

            if (c.isPrint()) {
                if (child.getStyle().isForcePageBreakAfter()) {
                    block.forcePageBreakAfter(c, child.getStyle().getIdent(CSSName.PAGE_BREAK_AFTER));
                    childOffset = block.getHeight();
                }

                if (previousChildBox != null) {
                    relayoutDataList.configureRun(offset, previousChildBox, child);
                }

                Integer newChildOffset =
                     processPageBreakAvoidRun(
                        c, block, localChildren, offset, relayoutDataList, child);

                if (newChildOffset != null) {
                    childOffset = newChildOffset;
                    if (childOffset > block.getHeight()) {
                        block.setHeight(childOffset);
                    }
                }
            }

            previousChildBox = child;

            if (rootPageBreakInsideAvoid) {
                c.setBlockBoxingState(enterState);
            }
        }

        if (oneChildFailed) {
            // IMPORTANT: If one child failed to satisfy the page-break-inside: avoid
            // constraint we signal to ancestor boxes that they should not try to satisfy
            // the constraint. Otherwise, we get an explosion of layout attempts, that will
            // practically never end for deeply nested blocks. See danfickle#551.
            c.setBlockBoxingState(BlockBoxingState.DENY);
        }
    }

    private static Integer processPageBreakAvoidRun(
            LayoutContext c,
            BlockBox block,
            List<Box> localChildren,
            int offset,
            AbstractRelayoutDataList relayoutDataList,
            BlockBox childBox) {

        if (offset > 0) {
            boolean mightNeedRelayout = false;
            int runEnd = -1;

            if (offset == localChildren.size() - 1 && relayoutDataList.isEndsRun(offset)) {
                mightNeedRelayout = true;
                runEnd = offset;
            } else if (offset > 0) {
                if (relayoutDataList.isEndsRun(offset - 1)) {
                    mightNeedRelayout = true;
                    runEnd = offset - 1;
                }
            }

            if (mightNeedRelayout) {
                int runStart = relayoutDataList.getRunStart(runEnd);
                int newChildOffset;

                if ( isPageBreakBetweenChildBoxes(relayoutDataList, runStart, runEnd, c, block) ) {
                    block.resetChildren(c, runStart, offset);

                    newChildOffset = relayoutRun(
                            c, localChildren, block,
                            relayoutDataList, runStart, offset, true);

                    if ( isPageBreakBetweenChildBoxes(relayoutDataList, runStart, runEnd, c, block) ) {
                        block.resetChildren(c, runStart, offset);
                        newChildOffset = relayoutRun(
                                c, localChildren, block,
                                relayoutDataList, runStart, offset, false);
                    }

                    return Integer.valueOf(newChildOffset);
                }
            }
        }

        return null;
    }

    private static boolean isPageBreakBetweenChildBoxes(
            AbstractRelayoutDataList relayoutDataList,
            int runStart, int runEnd, LayoutContext c, BlockBox block) {

        for ( int i = runStart; i < runEnd; i++ ) {
            Box prevChild = block.getChild(i);
            Box nextChild = block.getChild(i+1);

            // if nextChild is made of several lines, then only the first line
            // is relevant for "page-break-before: avoid".
            Box nextLine = getFirstLine(nextChild) == null ? nextChild : getFirstLine(nextChild);
            int prevChildEnd = prevChild.getAbsY() + prevChild.getHeight();
            int nextLineEnd = nextLine.getAbsY() + nextLine.getHeight();

            if ( c.getRootLayer().crossesPageBreak(c, prevChildEnd, nextLineEnd) ) {
                return true;
            }
        }

        return false;
    }

    /**
     * The first line box in document order. Searches past children without lines,
     * such as an anonymous block that only holds a float, instead of following
     * the first child alone.
     */
    private static LineBox getFirstLine(Box box) {
        if (box instanceof LineBox) {
            return (LineBox) box;
        }
        for (int i = 0; i < box.getChildCount(); i++) {
            LineBox line = getFirstLine(box.getChild(i));
            if (line != null) {
                return line;
            }
        }
        return null;
    }

    private static int relayoutRun(
            LayoutContext c, List<Box> localChildren, BlockBox block,
            AbstractRelayoutDataList relayoutDataList, int start, int end, boolean onNewPage) {
        int childOffset = relayoutDataList.getChildOffset(start);

        if (onNewPage) {
            Box startBox = localChildren.get(start);
            PageBox startPageBox = c.getRootLayer().getFirstPage(c, startBox);
            childOffset += startPageBox.getBottom() - startBox.getAbsY();
        }

        // reset height of parent as it is used for Y-setting of children
        block.setHeight(childOffset);

        for (int i = start; i <= end; i++) {
            BlockBox child = (BlockBox) localChildren.get(i);
            int pageCount = c.getRootLayer().getPages().size();

            LayoutState restoredChildLayoutState = relayoutDataList.getLayoutState(i);
            c.restoreStateForRelayout(restoredChildLayoutState);

            relayoutDataList.setChildOffset(i, childOffset);
            boolean mayCheckKeepTogether = false;

            if ((child.getStyle().isAvoidPageBreakInside() || child.getStyle().isKeepWithInline())
                    && c.isMayCheckKeepTogether()) {
                mayCheckKeepTogether = true;
                c.setMayCheckKeepTogether(false);
            }

            layoutBlockChild(
                    c, block, child, false, childOffset, NO_PAGE_TRIM, restoredChildLayoutState);

            if (mayCheckKeepTogether) {
                c.setMayCheckKeepTogether(true);

                boolean tryToAvoidPageBreak =
                    child.getStyle().isAvoidPageBreakInside() && child.crossesPageBreak(c);

                boolean needPageClear = child.isNeedPageClear();
                boolean keepWithInline = child.isNeedsKeepWithInline(c);

                if (tryToAvoidPageBreak || needPageClear || keepWithInline) {
                    c.restoreStateForRelayout(restoredChildLayoutState);
                    child.reset(c);

                    layoutBlockChild(
                            c, block, child, true, childOffset, pageCount, restoredChildLayoutState);

                    if (tryToAvoidPageBreak && child.crossesPageBreak(c) && ! keepWithInline) {
                        c.restoreStateForRelayout(restoredChildLayoutState);
                        child.reset(c);

                        layoutBlockChild(
                                c, block, child, false, childOffset, pageCount, restoredChildLayoutState);
                    }
                }
            }

            c.getRootLayer().ensureHasPage(c, child);

            Dimension relativeOffset = child.getRelativeOffset();

            if (relativeOffset == null) {
                childOffset = child.getY() + child.getHeight();
            } else {
                childOffset = child.getY() - relativeOffset.height + child.getHeight();
            }

            if (childOffset > block.getHeight()) {
                block.setHeight(childOffset);
            }

            if (child.getStyle().isForcePageBreakAfter()) {
                block.forcePageBreakAfter(c, child.getStyle().getIdent(CSSName.PAGE_BREAK_AFTER));
                childOffset = block.getHeight();
            }
        }

        return childOffset;
    }

    /**
     * Whether a break before the child at {@code offset} is the same break as one
     * before {@code block}: nothing in normal flow precedes the child (earlier
     * siblings have no height, e.g. an anonymous block holding only a float) and
     * there is no top border or padding between the block's edge and the child.
     * A block already at the top of its page is never moved, as that cannot help.
     */
    private static boolean isBreakBeforeChildBreakBeforeBlock(
            LayoutContext c, BlockBox block, List<Box> children, int offset) {
        CalculatedStyle style = block.getStyle();
        if (!isNormalFlowBlock(style) || block.getBorder(c).top() != 0 || block.getPadding(c).top() != 0) {
            return false;
        }

        for (int i = 0; i < offset; i++) {
            if (children.get(i).getHeight() != 0) {
                return false;
            }
        }

        PageBox page = c.getRootLayer().getFirstPage(c, block);
        return page != null && block.getAbsY() > page.getTop();
    }

    /**
     * Only a plain block in normal flow is moved by its parent's layout loop;
     * tables and their parts, floats, positioned boxes and inline-blocks paginate
     * by their own rules.
     */
    private static boolean isNormalFlowBlock(CalculatedStyle style) {
        return !(style.isTable() || style.isInlineTable() || style.isTableCell() ||
                 style.isTableRow() || style.isTableSection() || style.isTableCaption() ||
                 style.isFloated() || style.isAbsolute() || style.isFixed() ||
                 style.isInlineBlock());
    }

    private static void layoutBlockChild(
            LayoutContext c, BlockBox parent, BlockBox child,
            boolean needPageClear, int childOffset, int trimmedPageCount, LayoutState layoutState) {
        layoutBlockChild0(c, parent, child, needPageClear, childOffset, trimmedPageCount);
        BreakAtLineContext bContext = child.calcBreakAtLineContext(c);
        if (bContext != null) {
            c.setBreakAtLineContext(bContext);
            c.restoreStateForRelayout(layoutState);
            child.reset(c);
            layoutBlockChild0(c, parent, child, needPageClear, childOffset, trimmedPageCount);
            c.setBreakAtLineContext(null);
        }
    }

    private static void layoutBlockChild0(LayoutContext c, BlockBox parent, BlockBox child,
            boolean needPageClear, int childOffset, int trimmedPageCount) {
        child.setNeedPageClear(needPageClear);

        child.initStaticPos(c, parent, childOffset);

        child.initContainingLayer(c);
        child.calcCanvasLocation();

        c.translate(0, childOffset);
        repositionBox(c, child, trimmedPageCount);
        child.layout(c);
        c.translate(-child.getX(), -child.getY());
    }

    private static void repositionBox(LayoutContext c, BlockBox child, int trimmedPageCount) {
        boolean moved = false;
        if (child.getStyle().isRelative()) {
            Dimension delta = child.positionRelative(c);
            c.translate(delta.width, delta.height);
            moved = true;
        }
        if (c.isPrint()) {
            boolean pageClear = child.isNeedPageClear() ||
                                    child.getStyle().isForcePageBreakBefore() ||
                                    child.isPageBreakNeededBecauseOfMinHeight(c);
            boolean needNewPageContext = child.checkPageContext(c);

            if (needNewPageContext && trimmedPageCount != NO_PAGE_TRIM) {
                c.getRootLayer().trimPageCount(trimmedPageCount);
            }

            if (pageClear || needNewPageContext) {
                int delta = child.forcePageBreakBefore(
                        c,
                        child.getStyle().getIdent(CSSName.PAGE_BREAK_BEFORE),
                        needNewPageContext);
                c.translate(0, delta);
                moved = true;
                child.setNeedPageClear(false);
            }
        }
        if (moved) {
            child.calcCanvasLocation();
        }
    }

    /**
     * If we should try to avoid a page break between two block boxes.
     */
    public static boolean avoidPageBreakBetween(BlockBox previous, BlockBox current) {
        IdentValue previousAfter =
                previous.getStyle().getIdent(CSSName.PAGE_BREAK_AFTER);
        IdentValue currentBefore =
                current.getStyle().getIdent(CSSName.PAGE_BREAK_BEFORE);

        return (previousAfter == IdentValue.AVOID && currentBefore == IdentValue.AUTO) ||
                (previousAfter == IdentValue.AUTO && currentBefore == IdentValue.AVOID) ||
                (previousAfter == IdentValue.AVOID && currentBefore == IdentValue.AVOID);
    }

    private abstract static class AbstractRelayoutDataList {
        abstract int getChildOffset(int boxIndex);
        abstract LayoutState getLayoutState(int boxIndex);

        abstract void setLayoutState(int boxIndex, LayoutState state);
        abstract void setChildOffset(int boxIndex, int childOffset);

        abstract int getRunStart(int endRunIndex);

        abstract boolean isEndsRun(int boxIndex);

        abstract void configureRun(int boxIndex, BlockBox previous, BlockBox current);
    }

    private static class LiteRelayoutDataList extends AbstractRelayoutDataList {
        final int[] childOffsets;
        final LayoutState[] layoutStates;

        TreeSet<Integer> runStarts;
        TreeSet<Integer> runEnds;

        LiteRelayoutDataList(int size) {
            childOffsets = new int[size];
            layoutStates = new LayoutState[size];
        }

        @Override
        int getChildOffset(int boxIndex) {
            return childOffsets[boxIndex];
        }

        @Override
        LayoutState getLayoutState(int boxIndex) {
            return layoutStates[boxIndex];
        }

        @Override
        void setLayoutState(int boxIndex, LayoutState state) {
            layoutStates[boxIndex] = state;
        }

        @Override
        void setChildOffset(int boxIndex, int childOffset) {
            childOffsets[boxIndex] = childOffset;
        }

        @Override
        boolean isEndsRun(int boxIndex) {
            return runEnds != null && runEnds.contains(boxIndex);
        }

        @Override
        int getRunStart(int endRunIndex) {
            return runStarts.floor(endRunIndex);
        }

        boolean isInRun(int boxIndex) {
            if (runStarts == null) {
                return false;
            }

            Integer lastRunStart = runStarts.floor(boxIndex);
            if (lastRunStart != null) {
                Integer lastRunEnd = runEnds != null ? runEnds.ceiling(lastRunStart) : null;
                return (lastRunEnd == null ||
                        lastRunEnd >= boxIndex);
            }

            return false;
        }

        void addRunStart(int boxIndex) {
            if (runStarts == null) {
                runStarts = new TreeSet<>();
            }
            runStarts.add(boxIndex);
        }

        void addRunEnd(int boxIndex) {
            if (runEnds == null) {
                runEnds = new TreeSet<>();
            }
            runEnds.add(boxIndex);
        }

        /**
         * Marks two consecutive block boxes as being in a run of boxes where
         * a page break should not occur between them as set in the
         * <code>page-break-after</code> and <code>page-break-before</code>
         * CSS properties.
         */
        @Override
        public void configureRun(int offset, BlockBox previous, BlockBox current) {
            boolean previousInRun = isInRun(offset - 1);

            if (avoidPageBreakBetween(previous, current)) {
                if (!previousInRun) {
                    addRunStart(offset - 1);
                }

                if (offset == childOffsets.length - 1) {
                    addRunEnd(offset);
                }
            } else if (previousInRun) {
                addRunEnd(offset - 1);
            }
        }
    }

}
