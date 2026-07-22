/*
 * Matcher.java
 * Copyright (c) 2004, 2005 Torbjoern Gannholm
 * Copyright (c) 2006 Wisconsin Court System
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
 *
 */
package com.openhtmltopdf.css.newmatch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import com.openhtmltopdf.css.constants.MarginBoxName;
import com.openhtmltopdf.css.extend.AttributeResolver;
import com.openhtmltopdf.css.extend.StylesheetFactory;
import com.openhtmltopdf.css.extend.TreeResolver;
import com.openhtmltopdf.css.sheet.*;
import com.openhtmltopdf.util.LogMessageId;
import com.openhtmltopdf.util.XRLog;


/**
 * @author Torbjoern Gannholm
 */
public class Matcher {
    private final Mapper docMapper;
    private final AttributeResolver _attRes;
    private final TreeResolver _treeRes;
    private final StylesheetFactory _styleFactory;

    private final Map<Object, Mapper> _map = new HashMap<>();

    /**
     * RuleIndex per axes list (by identity). Child Mappers usually share their
     * parent's descendant selectors as their axes, so they share one index.
     */
    private final Map<List<Selector>, RuleIndex> _indexCache = new IdentityHashMap<>();

    private final Set<Object> _hoverElements = new HashSet<>();
    private final Set<Object> _activeElements = new HashSet<>();
    private final Set<Object> _focusElements = new HashSet<>();
    private final Set<Object> _visitElements = new HashSet<>();
    private final Set<Object> _markerElements = new HashSet<>();

    private final List<PageRule> _pageRules = new ArrayList<>();
    private final List<FontFaceRule> _fontFaceRules = new ArrayList<>();

    public Matcher(
            TreeResolver tr, AttributeResolver ar, StylesheetFactory factory, List<Stylesheet> stylesheets, String medium) {
        _treeRes = tr;
        _attRes = ar;
        _styleFactory = factory;

        docMapper = createDocumentMapper(stylesheets, medium);
    }

    public CascadedStyle getCascadedStyle(Object e, boolean restyle) {
            Mapper em;
            if (!restyle) {
                em = getMapper(e);
            } else {
                em = matchElement(e);
            }
            return em.getCascadedStyle(e);
    }

    /**
     * Returns CSS rulesets for descendants of e.
     * For example, if e is an svg element and we have the ruleset
     * 'svg rect { .. }' then the string returned will be 'rect { .. }'.
     * 
     * FIXME: Does not correctly handle sibling selectors.
     */
    public String getCSSForAllDescendants(Object e) {
        // We must use the parent mapper as a starting point
        // to correctly handle direct child selectors such as 'body > svg rect'.
        Object parent = _treeRes.getParentElement(e);
        Mapper child = parent != null ? getMapper(parent) : docMapper;

        AllDescendantMapper descendants = new AllDescendantMapper(child.axes(), _attRes, _treeRes);
        descendants.map(e);

        return descendants.toCSS();
    }

    /**
     * May return null.
     * We assume that restyle has already been done by a getCascadedStyle if necessary.
     */
    public CascadedStyle getPECascadedStyle(Object e, String pseudoElement) {
        //synchronized (e) {
            Mapper em = getMapper(e);
            return em.getPECascadedStyle(e, pseudoElement);
        //}
    }
    
    public PageInfo getPageCascadedStyle(String pageName, String pseudoPage) {
        List<PropertyDeclaration> props = new ArrayList<>();
        Map<MarginBoxName, List<PropertyDeclaration>>  marginBoxes = new HashMap<>();
        List<PropertyDeclaration> footnote = new ArrayList<>();

        for (PageRule pageRule : _pageRules) {
            if (pageRule.applies(pageName, pseudoPage)) {
                props.addAll(pageRule.getRuleset().getPropertyDeclarations());
                marginBoxes.putAll(pageRule.getMarginBoxes());

                if (pageRule.getFootnoteAreaProperties() != null) {
                    footnote.addAll(pageRule.getFootnoteAreaProperties());
                }
            }
        }
        
        CascadedStyle style;
        if (props.isEmpty()) {
            style = CascadedStyle.emptyCascadedStyle;
        } else {
            style = new CascadedStyle(props.iterator());
        }
        
        return new PageInfo(props, style, marginBoxes, footnote);
    }
    
    public List<FontFaceRule> getFontFaceRules() {
        return _fontFaceRules;
    }
    
    public boolean isVisitedStyled(Object e) {
        return _visitElements.contains(e);
    }

    public boolean isHoverStyled(Object e) {
        return _hoverElements.contains(e);
    }

    public boolean isActiveStyled(Object e) {
        return _activeElements.contains(e);
    }

    public boolean isFocusStyled(Object e) {
        return _focusElements.contains(e);
    }

    public boolean isMarkerStyled(Object e) {
        return _markerElements.contains(e);
    }

    protected Mapper matchElement(Object e) {
            Object parent = _treeRes.getParentElement(e);
            Mapper child;
            if (parent != null) {
                Mapper m = getMapper(parent);
                child = m.mapChild(e);
            } else {//has to be document or fragment node
                child = docMapper.mapChild(e);
            }
            return child;
    }

    Mapper createDocumentMapper(List<Stylesheet> stylesheets, String medium) {
        java.util.TreeMap<String,Selector> sorter = new java.util.TreeMap<>();
        addAllStylesheets(stylesheets, sorter, medium);
        XRLog.log(Level.INFO, LogMessageId.LogMessageId1Param.MATCH_MATCHER_CREATED_WITH_SELECTOR, sorter.size());
        return new Mapper(sorter.values());
    }
    
    private void addAllStylesheets(List<Stylesheet> stylesheets, TreeMap<String, Selector> sorter, String medium) {
        int count = 0;
        int pCount = 0;
        for (Stylesheet stylesheet : stylesheets) {
            for (Object obj : stylesheet.getContents()) {
                if (obj instanceof Ruleset) {
                    for (Selector selector : ((Ruleset) obj).getFSSelectors()) {
                        selector.setPos(++count);
                        sorter.put(selector.getOrder(), selector);
                    }
                } else if (obj instanceof PageRule) {
                    ((PageRule) obj).setPos(++pCount);
                    _pageRules.add((PageRule) obj);
                } else if (obj instanceof MediaRule) {
                    MediaRule mediaRule = (MediaRule) obj;
                    if (mediaRule.matches(medium)) {
                        for (Object o : mediaRule.getContents()) {
                            Ruleset ruleset = (Ruleset) o;
                            for (Object o1 : ruleset.getFSSelectors()) {
                                Selector selector = (Selector) o1;
                                selector.setPos(++count);
                                sorter.put(selector.getOrder(), selector);
                            }
                        }
                    }
                }
            }

            _fontFaceRules.addAll(stylesheet.getFontFaceRules());
        }
        
        Collections.sort(_pageRules, new Comparator<PageRule>() {
            @Override
            public int compare(PageRule p1, PageRule p2) {
                if (p1.getOrder() - p2.getOrder() < 0) {
                    return -1;
                } else if (p1.getOrder() == p2.getOrder()) {
                    return 0;
                } else {
                    return 1;
                }
            }
        });
    }

    private void link(Object e, Mapper m) {
        _map.put(e, m);
    }

    /**
     * Class attribute split into whitespace-delimited tokens (same semantics as
     * ClassCondition); null when the attribute is absent.
     */
    static Set<String> classTokens(String classAttr) {
        if (classAttr == null) {
            return null;
        }

        Set<String> result = Collections.emptySet();
        int length = classAttr.length();
        int i = 0;

        while (i < length) {
            while (i < length && Character.isWhitespace(classAttr.charAt(i))) {
                i++;
            }
            int start = i;
            while (i < length && !Character.isWhitespace(classAttr.charAt(i))) {
                i++;
            }
            if (i > start) {
                if (result.isEmpty()) {
                    result = new HashSet<>(8);
                }
                result.add(classAttr.substring(start, i));
            }
        }

        return result;
    }

    private Mapper getMapper(Object e) {
        Mapper m = _map.get(e);
        if (m != null) {
            return m;
        }
        m = matchElement(e);
        return m;
    }

    private static boolean isNullOrEmpty(String str) {
        return str == null || str.length() == 0;
    }

    private com.openhtmltopdf.css.sheet.Ruleset getElementStyle(Object e) {
        //synchronized (e) {
            if (_attRes == null || _styleFactory == null) {
                return null;
            }
            
            String style = _attRes.getElementStyling(e);
            if (isNullOrEmpty(style)) {
                return null;
            }
            
            return _styleFactory.parseStyleDeclaration(com.openhtmltopdf.css.sheet.StylesheetInfo.AUTHOR, style);
        //}
    }

    private com.openhtmltopdf.css.sheet.Ruleset getNonCssStyle(Object e) {
        //synchronized (e) {
            if (_attRes == null || _styleFactory == null) {
                return null;
            }
            String style = _attRes.getNonCssStyling(e);
            if (isNullOrEmpty(style)) {
                return null;
            }
            return _styleFactory.parseStyleDeclaration(com.openhtmltopdf.css.sheet.StylesheetInfo.AUTHOR, style);
        //}
    }

    /**
     * Index of a Mapper's axes by each selector's own simple selector (the
     * {@link Selector#mayMatch} fields: name, id, classes), so that mapChild
     * only visits selectors that may match an element. Buckets hold positions
     * in the indexed list; candidates are returned in ascending position
     * (cascade) order.
     */
    private static class RuleIndex {
        private final Selector[] selectors;
        private final Map<String, int[]> byId;
        private final Map<String, int[]> byClass;
        private final Map<String, int[]> byName;
        /** Selectors with no name/id/class requirement; checked for every element. */
        private final int[] universal;

        /**
         * The descendant-axis selectors in order: the childAxes of an element
         * that activates no chained selector. Shared, never mutated; the
         * indexed list itself when every selector is descendant-axis, so
         * Mappers below share this index.
         */
        final List<Selector> defaultChildAxes;
        /** Positions (in the indexed list) of the defaultChildAxes selectors. */
        private final int[] descendantPositions;

        RuleIndex(List<Selector> axes) {
            int size = axes.size();
            selectors = axes.toArray(new Selector[size]);

            Map<String, List<Integer>> id = new HashMap<>();
            // Sized for class-heavy stylesheets, where most selectors get
            // their own byClass bucket.
            Map<String, List<Integer>> cls = new HashMap<>(Math.max(16, size * 2));
            Map<String, List<Integer>> name = new HashMap<>();
            int[] universalTmp = new int[size];
            int universalCount = 0;
            int[] descendantsTmp = new int[size];
            int descendantCount = 0;

            for (int i = 0; i < size; i++) {
                Selector sel = selectors[i];

                if (sel.getAxis() == Selector.DESCENDANT_AXIS) {
                    descendantsTmp[descendantCount++] = i;
                }

                if (sel.getAxis() == Selector.IMMEDIATE_SIBLING_AXIS) {
                    // Always visited, so mapChild still throws on it.
                    universalTmp[universalCount++] = i;
                } else if (sel.indexId() != null) {
                    id.computeIfAbsent(sel.indexId(), k -> new ArrayList<>()).add(i);
                } else if (sel.indexClass() != null) {
                    cls.computeIfAbsent(sel.indexClass(), k -> new ArrayList<>()).add(i);
                } else if (sel.indexName() != null) {
                    name.computeIfAbsent(sel.indexName(), k -> new ArrayList<>()).add(i);
                } else {
                    universalTmp[universalCount++] = i;
                }
            }

            byId = freeze(id);
            byClass = freeze(cls);
            byName = freeze(name);
            universal = Arrays.copyOf(universalTmp, universalCount);
            descendantPositions = Arrays.copyOf(descendantsTmp, descendantCount);

            if (descendantCount == size) {
                defaultChildAxes = axes;
            } else {
                List<Selector> dca = new ArrayList<>(descendantCount);
                for (int pos : descendantPositions) {
                    dca.add(selectors[pos]);
                }
                defaultChildAxes = dca;
            }
        }

        Selector selector(int pos) {
            return selectors[pos];
        }

        /**
         * Positions of the selectors that may match an element with the given
         * name, id and class tokens, in ascending (cascade) order. A selector
         * is in exactly one bucket, so there are no duplicates.
         */
        int[] candidates(String name, String id, Set<String> classes) {
            int[] nameBucket = name != null ? byName.get(name) : null;
            int[] idBucket = id != null ? byId.get(id) : null;

            int total = universal.length
                    + (nameBucket != null ? nameBucket.length : 0)
                    + (idBucket != null ? idBucket.length : 0);

            int[][] classBuckets = null;
            int classBucketCount = 0;
            if (classes != null && !classes.isEmpty() && !byClass.isEmpty()) {
                classBuckets = new int[classes.size()][];
                for (String c : classes) {
                    int[] b = byClass.get(c);
                    if (b != null) {
                        classBuckets[classBucketCount++] = b;
                        total += b.length;
                    }
                }
            }

            if (total == universal.length) {
                // No bucket hit; universal is already in ascending order.
                return universal;
            }

            int[] result = new int[total];
            int n = copy(universal, result, 0);
            if (nameBucket != null) {
                n = copy(nameBucket, result, n);
            }
            if (idBucket != null) {
                n = copy(idBucket, result, n);
            }
            for (int i = 0; i < classBucketCount; i++) {
                n = copy(classBuckets[i], result, n);
            }
            Arrays.sort(result);
            return result;
        }

        /**
         * defaultChildAxes with the chained selectors spliced in at their
         * producers' positions. Both inputs are in ascending position order;
         * on equal position the carried descendant selector (the chain's
         * producer) comes first, as in the original single-pass loop.
         */
        List<Selector> mergeChained(List<Selector> chainedSelectors, int[] chainedPositions, int chainedCount) {
            List<Selector> result = new ArrayList<>(defaultChildAxes.size() + chainedCount);
            int d = 0;
            for (int c = 0; c < chainedCount; c++) {
                int pos = chainedPositions[c];
                while (d < descendantPositions.length && descendantPositions[d] <= pos) {
                    result.add(defaultChildAxes.get(d));
                    d++;
                }
                result.add(chainedSelectors.get(c));
            }
            while (d < descendantPositions.length) {
                result.add(defaultChildAxes.get(d));
                d++;
            }
            return result;
        }

        private static Map<String, int[]> freeze(Map<String, List<Integer>> src) {
            if (src.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<String, int[]> result = new HashMap<>(src.size() * 2);
            for (Map.Entry<String, List<Integer>> en : src.entrySet()) {
                result.put(en.getKey(), toIntArray(en.getValue()));
            }
            return result;
        }

        private static int[] toIntArray(List<Integer> src) {
            int[] result = new int[src.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = src.get(i);
            }
            return result;
        }

        private static int copy(int[] src, int[] dst, int at) {
            System.arraycopy(src, 0, dst, at, src.length);
            return at + src.length;
        }
    }

    /**
     * Mapper represents a local CSS for a Node that is used to match the Node's
     * children.
     *
     * @author Torbjoern Gannholm
     */
    class Mapper {
        private List<Selector> axes;
        private final Map<String, List<Selector>> pseudoSelectors;
        private final List<Selector> mappedSelectors;

        private Map<String, Mapper> children;

        /** Lazily built (and shared through Matcher._indexCache) index of axes. */
        private RuleIndex index;

        /**
         * Deferred axes: the parent's index and the chained selectors this
         * Mapper's element activated. Merged into {@link #axes} only when
         * first needed, i.e. if this Mapper ever maps children of its own;
         * cleared after the merge.
         */
        private RuleIndex mergeSource;
        private List<Selector> mergeChains;
        private int[] mergeChainPositions;
        private int mergeChainCount;

        Mapper(Collection<Selector> selectors) {
            this.axes = new ArrayList<>(selectors);
            this.pseudoSelectors = Collections.emptyMap();
            this.mappedSelectors = Collections.emptyList();
        }

        private Mapper(
                List<Selector> axes, 
                List<Selector> mappedSelectors,
                Map<String,List<Selector>> pseudoSelectors) {
            this.axes = axes;
            this.mappedSelectors = mappedSelectors;
            this.pseudoSelectors = pseudoSelectors;
        }

        private Mapper(
                RuleIndex mergeSource,
                List<Selector> mergeChains,
                int[] mergeChainPositions,
                int mergeChainCount,
                List<Selector> mappedSelectors,
                Map<String,List<Selector>> pseudoSelectors) {
            this.mergeSource = mergeSource;
            this.mergeChains = mergeChains;
            this.mergeChainPositions = mergeChainPositions;
            this.mergeChainCount = mergeChainCount;
            this.mappedSelectors = mappedSelectors;
            this.pseudoSelectors = pseudoSelectors;
        }

        List<Selector> axes() {
            if (axes == null) {
                axes = mergeSource.mergeChained(mergeChains, mergeChainPositions, mergeChainCount);
                mergeSource = null;
                mergeChains = null;
                mergeChainPositions = null;
            }
            return axes;
        }

        /**
         * Side effect: creates and stores a Mapper for the element
         *
         * @param e
         * @return The selectors that matched, sorted according to specificity
         *         (more correct: preserves the sort order from Matcher creation)
         */
        Mapper mapChild(Object e) {
            List<Selector> mappedSelectors = null;
            Map<String, List<Selector>> pseudoSelectors = null;

            StringBuilder key = new StringBuilder();

            // Read once per element and reused for every candidate selector.
            String elementName = _treeRes.getElementName(e);
            String elementId = _attRes != null ? _attRes.getID(e) : null;
            Set<String> elementClasses = classTokens(_attRes != null ? _attRes.getClass(e) : null);

            if (index == null) {
                index = _indexCache.computeIfAbsent(axes(), RuleIndex::new);
            }

            // Chained selectors activated by this element, with their
            // producers' positions in axes; spliced into defaultChildAxes.
            List<Selector> chainedSelectors = null;
            int[] chainedPositions = null;
            int chainedCount = 0;

            int[] candidates = index.candidates(elementName, elementId, elementClasses);

            for (int pos : candidates) {
                Selector sel = index.selector(pos);

                if (sel.getAxis() == Selector.IMMEDIATE_SIBLING_AXIS) {
                    throw new RuntimeException();
                }

                if (!sel.mayMatch(elementName, elementId, elementClasses)) {
                    continue;
                }

                if (!sel.matches(e, _attRes, _treeRes)) {
                    continue;
                }

                // Assumption: if it is a pseudo-element, it does not also have dynamic pseudo-class
                String pseudoElement = sel.getPseudoElement();

                if (pseudoElement != null) {
                    if (pseudoSelectors == null) {
                        pseudoSelectors = new HashMap<>();
                    }

                    List<Selector> l = pseudoSelectors.computeIfAbsent(pseudoElement, kee -> new ArrayList<>());
                    l.add(sel);

                    key.append(sel.getSelectorID()).append(":");
                    continue;
                }

                if (sel.isPseudoClass(Selector.VISITED_PSEUDOCLASS)) {
                    _visitElements.add(e);
                }
                if (sel.isPseudoClass(Selector.ACTIVE_PSEUDOCLASS)) {
                    _activeElements.add(e);
                }
                if (sel.isPseudoClass(Selector.HOVER_PSEUDOCLASS)) {
                    _hoverElements.add(e);
                }
                if (sel.isPseudoClass(Selector.FOCUS_PSEUDOCLASS)) {
                    _focusElements.add(e);
                }
                if (sel.isPseudoClass(Selector.MARKER_PSEUDOCLASS)) {
                    _markerElements.add(e);
                }

                if (!sel.matchesDynamic(e, _attRes, _treeRes)) {
                    continue;
                }

                key.append(sel.getSelectorID()).append(":");

                Selector chain = sel.getChainedSelector();

                if (chain == null) {
                    if (mappedSelectors == null) {
                        mappedSelectors = new ArrayList<>();
                    }

                    mappedSelectors.add(sel);
                } else if (chain.getAxis() == Selector.IMMEDIATE_SIBLING_AXIS) {
                    throw new RuntimeException();
                } else {
                    if (chainedSelectors == null) {
                        chainedSelectors = new ArrayList<>();
                        chainedPositions = new int[8];
                    } else if (chainedCount == chainedPositions.length) {
                        chainedPositions = Arrays.copyOf(chainedPositions, chainedCount * 2);
                    }

                    chainedSelectors.add(chain);
                    chainedPositions[chainedCount++] = pos;
                }
            }

            if (children == null) {
                children = new HashMap<>();
            }

            String childKey = key.toString();
            Mapper childMapper = children.get(childKey);
            if (childMapper == null) {
                List<Selector> normalisedMappedSelectors = mappedSelectors == null ? Collections.emptyList() : mappedSelectors;
                Map<String, List<Selector>> normalisedPseudoSelectors = pseudoSelectors == null ? Collections.emptyMap() : pseudoSelectors;

                childMapper = chainedSelectors == null
                        ? new Mapper(
                            index.defaultChildAxes,
                            normalisedMappedSelectors,
                            normalisedPseudoSelectors)
                        : new Mapper(
                            index,
                            chainedSelectors,
                            chainedPositions,
                            chainedCount,
                            normalisedMappedSelectors,
                            normalisedPseudoSelectors);
                children.put(childKey, childMapper);
            }

            link(e, childMapper);

            return childMapper;
        }

        CascadedStyle getCascadedStyle(Object e) {
            Ruleset elementStyling = getElementStyle(e);
            Ruleset nonCssStyling = getNonCssStyle(e);

            List<PropertyDeclaration> propList = new ArrayList<>();

            // Specificity 0,0,0,0
            if (nonCssStyling != null) {
                propList.addAll(nonCssStyling.getPropertyDeclarations());
            }

            // These should have been returned in order of specificity
            for (Selector sel : mappedSelectors) {
                propList.addAll(sel.getRuleset().getPropertyDeclarations());
            }

            // Specificity 1,0,0,0
            if (elementStyling != null) {
                propList.addAll(elementStyling.getPropertyDeclarations());
            }

            if (propList.isEmpty()) {
                return CascadedStyle.emptyCascadedStyle;
            } else {
                return new CascadedStyle(propList.iterator());
            }
        }

        /**
         * May return null.
         * We assume that restyle has already been done by a getCascadedStyle if necessary.
         */
        public CascadedStyle getPECascadedStyle(Object e, String pseudoElement) {
            if (pseudoSelectors.isEmpty()) {
                return null;
            }

            List<Selector> pe = pseudoSelectors.get(pseudoElement);

            if (pe == null) {
                return null;
            }

            List<PropertyDeclaration> propList = new ArrayList<>();

            for (Selector sel : pe) {
                propList.addAll(sel.getRuleset().getPropertyDeclarations());
            }

            if (propList.isEmpty()) {
                return CascadedStyle.emptyCascadedStyle;
            } else {
                return new CascadedStyle(propList.iterator());
            }
        }
    }

    public static class AllDescendantMapper {
        private final List<Selector> axes;
        private final List<Selector> mappedSelectors = new ArrayList<>();
        private final Set<Selector> topSelectors = new HashSet<>();
        private final AttributeResolver attRes;
        private final TreeResolver treeRes;

        AllDescendantMapper(List<Selector> axes, AttributeResolver attRes, TreeResolver treeRes) {
            this.axes = axes;
            this.attRes = attRes;
            this.treeRes = treeRes;
        }

        String toCSS() {
            StringBuilder sb = new StringBuilder();

            for (Selector sel : mappedSelectors) {
                sel.toCSS(sb, topSelectors);
                sel.getRuleset().toCSS(sb);
            }

            return sb.toString();
        }

        void map(Object e) {
            Deque<Selector> queue = new ArrayDeque<>();

            for (Selector sel : axes) {
                if (!sel.matches(e, attRes, treeRes) ||
                    sel.getChainedSelector() == null) {
                    continue;
                }

                queue.addLast(sel);
                this.topSelectors.add(sel);
            }

            while (!queue.isEmpty()) {
                Selector current = queue.removeFirst();

                Selector chain = current.getChainedSelector();

                if (chain == null) {
                    this.mappedSelectors.add(current);
                } else {
                    queue.addLast(chain);
                }
            }
        }
    }
}

