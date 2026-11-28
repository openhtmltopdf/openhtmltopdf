package com.openhtmltopdf.css.newmatch;

import java.util.List;

import org.w3c.dom.Node;

import com.openhtmltopdf.css.extend.AttributeResolver;
import com.openhtmltopdf.css.extend.TreeResolver;

/**
 * A complex selector inside a functional pseudo-class, such as the arguments of
 * :is(), :where(), :not(), :has() and :nth-child(An+B of S). Unlike the selectors
 * of a rule, which the {@link Matcher} follows down the document, it is matched
 * from its subject (the rightmost compound) back to the left.
 */
public class ComplexSelector {
    public static final int DESCENDANT = 0;
    public static final int CHILD = 1;
    public static final int NEXT_SIBLING = 2;
    public static final int SUBSEQUENT_SIBLING = 3;

    /** The compound selectors, left to right; each without chained or sibling selectors. */
    private final List<Selector> compounds;
    /** The combinator before each compound but the first. */
    private final List<Integer> combinators;
    /**
     * For a relative selector (:has()), the combinator between the anchor element and
     * the first compound; -1 otherwise.
     */
    private final int relativeCombinator;

    public ComplexSelector(List<Selector> compounds, List<Integer> combinators, int relativeCombinator) {
        this.compounds = compounds;
        this.combinators = combinators;
        this.relativeCombinator = relativeCombinator;
    }

    public int getSpecificityB() {
        return compounds.stream().mapToInt(Selector::getSpecificityB).sum();
    }

    public int getSpecificityC() {
        return compounds.stream().mapToInt(Selector::getSpecificityC).sum();
    }

    public int getSpecificityD() {
        return compounds.stream().mapToInt(Selector::getSpecificityD).sum();
    }

    /**
     * @return whether element matches this (not relative) selector.
     */
    public boolean matches(Object element, AttributeResolver attRes, TreeResolver treeRes) {
        return matchesAt(compounds.size() - 1, element, null, attRes, treeRes);
    }

    /**
     * @return whether some element relative to anchor, as the leading combinator
     * says, matches this relative selector.
     */
    public boolean matchesRelativeTo(Object anchor, AttributeResolver attRes, TreeResolver treeRes) {
        if (!(anchor instanceof Node)) {
            return false;
        }
        if (relativeCombinator == DESCENDANT || relativeCombinator == CHILD) {
            return anyInSubtree(((Node) anchor).getFirstChild(), anchor, attRes, treeRes);
        }
        for (Node sibling = ((Node) anchor).getNextSibling(); sibling != null; sibling = sibling.getNextSibling()) {
            if (matchesInSubtree(sibling, anchor, attRes, treeRes)) {
                return true;
            }
        }
        return false;
    }

    private boolean anyInSubtree(Node first, Object anchor, AttributeResolver attRes, TreeResolver treeRes) {
        for (Node n = first; n != null; n = n.getNextSibling()) {
            if (matchesInSubtree(n, anchor, attRes, treeRes)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesInSubtree(Node node, Object anchor, AttributeResolver attRes, TreeResolver treeRes) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return false;
        }
        if (matchesAt(compounds.size() - 1, node, anchor, attRes, treeRes)) {
            return true;
        }
        return anyInSubtree(node.getFirstChild(), anchor, attRes, treeRes);
    }

    /**
     * Whether element matches compound index and everything left of it. For a
     * relative selector, anchor is the element the leftmost compound must be in
     * relation to.
     */
    private boolean matchesAt(int index, Object element, Object anchor, AttributeResolver attRes, TreeResolver treeRes) {
        if (!compounds.get(index).matches(element, attRes, treeRes)) {
            return false;
        }

        if (index == 0) {
            return anchor == null || related(relativeCombinator, element, treeRes,
                    candidate -> candidate == anchor);
        }

        return related(combinators.get(index - 1), element, treeRes,
                candidate -> matchesAt(index - 1, candidate, anchor, attRes, treeRes));
    }

    private interface Test {
        boolean test(Object candidate);
    }

    /**
     * @return whether an element in the relation the combinator names to element (its
     * parent, an ancestor, its previous sibling, a previous sibling) passes the test.
     */
    private static boolean related(int combinator, Object element, TreeResolver treeRes, Test test) {
        switch (combinator) {
        case CHILD: {
            Object parent = treeRes.getParentElement(element);
            return parent != null && test.test(parent);
        }
        case NEXT_SIBLING: {
            Object sibling = treeRes.getPreviousSiblingElement(element);
            return sibling != null && test.test(sibling);
        }
        case SUBSEQUENT_SIBLING:
            for (Object s = treeRes.getPreviousSiblingElement(element); s != null; s = treeRes.getPreviousSiblingElement(s)) {
                if (test.test(s)) {
                    return true;
                }
            }
            return false;
        default:
            for (Object p = treeRes.getParentElement(element); p != null; p = treeRes.getParentElement(p)) {
                if (test.test(p)) {
                    return true;
                }
            }
            return false;
        }
    }

    public void toCSS(StringBuilder sb) {
        if (relativeCombinator > DESCENDANT) {
            sb.append(combinatorText(relativeCombinator));
        }
        for (int i = 0; i < compounds.size(); i++) {
            if (i > 0) {
                int c = combinators.get(i - 1);
                sb.append(c == DESCENDANT ? " " : combinatorText(c));
            }
            compounds.get(i).toCSS(sb, java.util.Collections.emptySet());
        }
    }

    private static String combinatorText(int combinator) {
        switch (combinator) {
        case CHILD: return " > ";
        case NEXT_SIBLING: return " + ";
        case SUBSEQUENT_SIBLING: return " ~ ";
        default: return " ";
        }
    }
}
