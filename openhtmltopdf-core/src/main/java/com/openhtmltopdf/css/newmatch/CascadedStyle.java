/*
 * CascadedStyle.java
 * Copyright (c) 2004, 2005 Patrick Wright, Torbjoern Gannholm
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */
package com.openhtmltopdf.css.newmatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.css.parser.CSSPrimitiveValue;
import com.openhtmltopdf.css.parser.PropertyValue;
import com.openhtmltopdf.css.sheet.CustomPropertyDeclaration;
import com.openhtmltopdf.css.sheet.PendingVarPropertyDeclaration;
import com.openhtmltopdf.css.sheet.PropertyDeclaration;
import com.openhtmltopdf.css.sheet.StylesheetInfo;


/**
 * Holds a set of {@link com.openhtmltopdf.css.sheet.PropertyDeclaration}s for
 * each unique CSS property name. What properties belong in the set is not
 * determined, except that multiple entries are resolved into a single set using
 * cascading rules. The set is cascaded during instantiation, so once you have a
 * CascadedStyle, the PropertyDeclarations you retrieve from it will have been
 * resolved following the CSS cascading rules. Note that this class knows
 * nothing about CSS selector-matching rules. Before creating a CascadedStyle,
 * you will need to determine which PropertyDeclarations belong in the set--for
 * example, by matching {@link com.openhtmltopdf.css.sheet.Ruleset}s to {@link
 * org.w3c.dom.Document} {@link org.w3c.dom.Element}s via their selectors. You
 * can get individual properties by using {@link #propertyByName(CSSName)} or an
 * {@link java.util.Iterator} of properties with {@link
 * #getCascadedPropertyDeclarations()}. Check for individual property assignments
 * using {@link #hasProperty(CSSName)}. A CascadedStyle is immutable, as
 * properties can not be added or removed from it once instantiated.
 *
 * @author Torbjoern Gannholm
 * @author Patrick Wright
 */
public class CascadedStyle {
    /**
     * Map of PropertyDeclarations, keyed by {@link CSSName}
     */
    private final Map<CSSName, PropertyDeclaration> cascadedProperties;

    /**
     * Map of CSS custom property ({@code --*}) declarations, keyed by name.
     * Custom properties are not {@link CSSName}s, so they cascade separately.
     * A {@link TreeMap} is used so iteration order (and thus the fingerprint)
     * is deterministic.
     */
    private final Map<String, CustomPropertyDeclaration> customProperties;

    /**
     * Cascade-priority rank per {@link CSSName} (higher = higher priority),
     * assigned in {@link #addProperties} as declarations are applied in
     * ascending-priority order. Lets {@code CalculatedStyle.derive()} order a
     * var()-bearing shorthand against an explicit longhand it expands into, which
     * the per-CSSName cascade cannot.
     */
    private final Map<CSSName, Integer> matchSequence;

    /** Next rank to assign; increases monotonically across {@link #addProperties}. */
    private int nextSequence;

    /**
     * Whether any declaration added here was a {@link PendingVarPropertyDeclaration}.
     * Only then does the order in which declarations are derived matter, so this
     * keeps documents that use no CSS variables off the ordering path entirely.
     * It stays set if such a declaration is later overwritten by a plain one,
     * which costs an unnecessary sort but is never wrong.
     */
    private boolean hasPendingVarProperties;

    private String fingerprint;
    
    /**
     * Constructs a new CascadedStyle, given an {@link java.util.Iterator} of
     * {@link com.openhtmltopdf.css.sheet.PropertyDeclaration}s already sorted
     * by specificity of the CSS selector they came from. The Iterator can have
     * multiple PropertyDeclarations with the same name; the property cascade
     * will be resolved during instantiation, resulting in a set of
     * PropertyDeclarations. Once instantiated, properties may be retrieved
     * using the normal API for the class.
     *
     * @param iter An Iterator containing PropertyDeclarations in order of
     *             specificity.
     */
    CascadedStyle(java.util.Iterator<PropertyDeclaration> iter) {
        this();

        addProperties(iter);
    }

    /**
     * As {@link #CascadedStyle(Iterator)} but also captures CSS custom property
     * ({@code --*}) declarations, given in order of specificity.
     *
     * @param iter        PropertyDeclarations in order of specificity.
     * @param customDecls custom property declarations in order of specificity.
     */
    CascadedStyle(java.util.Iterator<PropertyDeclaration> iter, List<CustomPropertyDeclaration> customDecls) {
        this();

        addProperties(iter);
        addCustomProperties(customDecls);
    }
    
    /**
     * Creates a <code>CascadedStyle</code> using the provided property
     * declarations.  It is used when a box requires a style that does not
     * correspond to anything in the parsed stylesheets.
     * @param decls An array of PropertyDeclaration objects created with 
     * {@link #createLayoutPropertyDeclaration(CSSName, IdentValue)}
     * @see #createLayoutPropertyDeclaration(CSSName, IdentValue)
     */
    public static CascadedStyle createLayoutStyle(PropertyDeclaration[] decls) {
        return new CascadedStyle(Arrays.asList(decls).iterator());
    }
    
    public static CascadedStyle createLayoutStyle(List<PropertyDeclaration> decls) {
        return new CascadedStyle(decls.iterator());
    }    
    
    /**
     * Creates a <code>CascadedStyle</code> using style information from
     * <code>startingPoint</code> and then adding the property declarations
     * from <code>decls</code>.
     * @param decls An array of PropertyDeclaration objects created with 
     * {@link #createLayoutPropertyDeclaration(CSSName, IdentValue)}
     * @see #createLayoutPropertyDeclaration(CSSName, IdentValue)
     */
    public static CascadedStyle createLayoutStyle(
            CascadedStyle startingPoint, PropertyDeclaration[] decls) {
        return new CascadedStyle(startingPoint, Arrays.asList(decls).iterator());
    }

    /**
     * Creates a <code>PropertyDeclaration</code> suitable for passing to
     * {@link #createLayoutStyle(PropertyDeclaration[])} or
     * {@link #createLayoutStyle(CascadedStyle, PropertyDeclaration[])}
     */
    public static PropertyDeclaration createLayoutPropertyDeclaration(
            CSSName cssName, IdentValue display) {
        CSSPrimitiveValue val = new PropertyValue(display);
        // Urk... kind of ugly, but we really want this value to be used
        return new PropertyDeclaration(cssName, val, true, StylesheetInfo.USER);
    }

    private CascadedStyle(CascadedStyle startingPoint, Iterator<PropertyDeclaration> props) {
        cascadedProperties = new TreeMap<>(startingPoint.cascadedProperties);
        customProperties = new TreeMap<>(startingPoint.customProperties);
        matchSequence = new HashMap<>(startingPoint.matchSequence);
        nextSequence = startingPoint.nextSequence;
        hasPendingVarProperties = startingPoint.hasPendingVarProperties;

        addProperties(props);
    }

    /**
     * Default constructor with no initialization. Don't use this to instantiate
     * the class, as the class is immutable and this will leave it without any
     * properties.
     */
    private CascadedStyle() {
        cascadedProperties = new TreeMap<>();
        customProperties = new TreeMap<>();
        matchSequence = new HashMap<>();
    }

    /**
     * Creates an otherwise empty <code>CascadedStyle</code>, setting the display property
     * to the value of the <code>display</code> parameter.
     */
    public static CascadedStyle createAnonymousStyle(IdentValue display) {
        CSSPrimitiveValue val = new PropertyValue(display);

        List<PropertyDeclaration> props = Collections.singletonList(
                new PropertyDeclaration(CSSName.DISPLAY, val, true, StylesheetInfo.USER));

        return new CascadedStyle(props.iterator());
    }

    private void addProperties(java.util.Iterator<PropertyDeclaration> iter) {
		/*
		 * do a bucket-sort on importance and origin /properties should already be in
		 * order of specificity
		 */
        //noinspection unchecked
        @SuppressWarnings("unchecked")
        java.util.List<PropertyDeclaration>[] buckets =  new java.util.List[PropertyDeclaration.IMPORTANCE_AND_ORIGIN_COUNT];

        while (iter.hasNext()) {
            PropertyDeclaration prop = iter.next();
            List<PropertyDeclaration> bucket = buckets[prop.getImportanceAndOrigin()];
			if (bucket == null) {
				bucket = new ArrayList<>();
				buckets[prop.getImportanceAndOrigin()]  = bucket;
			}
            bucket.add(prop);
        }

        for (List<PropertyDeclaration> bucket : buckets) {
			if (bucket == null)
				continue;
            for (PropertyDeclaration prop : bucket) {
                cascadedProperties.put(prop.getCSSName(), prop);
                // Record cascade priority: entries are visited in ascending
                // priority, so a later put means higher priority.
                matchSequence.put(prop.getCSSName(), nextSequence++);
                if (prop instanceof PendingVarPropertyDeclaration) {
                    hasPendingVarProperties = true;
                }
            }
        }
    }

    /**
     * Cascades CSS custom property declarations into {@link #customProperties},
     * keyed by name. Uses the same bucket-sort on importance and origin as
     * {@link #addProperties(Iterator)}; the input is already in order of
     * specificity.
     */
    private void addCustomProperties(List<CustomPropertyDeclaration> decls) {
        if (decls.isEmpty()) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<CustomPropertyDeclaration>[] buckets =
                new java.util.List[PropertyDeclaration.IMPORTANCE_AND_ORIGIN_COUNT];

        for (CustomPropertyDeclaration decl : decls) {
            List<CustomPropertyDeclaration> bucket = buckets[decl.getImportanceAndOrigin()];
            if (bucket == null) {
                bucket = new ArrayList<>();
                buckets[decl.getImportanceAndOrigin()] = bucket;
            }
            bucket.add(decl);
        }

        for (List<CustomPropertyDeclaration> bucket : buckets) {
            if (bucket == null)
                continue;
            for (CustomPropertyDeclaration decl : bucket) {
                customProperties.put(decl.getName(), decl);
            }
        }
    }

    /**
     * The cascaded custom property ({@code --*}) declarations for this style,
     * keyed by name. The values may still contain {@code var()} references; they
     * are resolved per element at computed-value time.
     */
    public Map<String, CustomPropertyDeclaration> getCustomProperties() {
        return customProperties;
    }

    /**
     * Get an empty singleton, used to negate inheritance of properties
     */
    public static final CascadedStyle emptyCascadedStyle = new CascadedStyle();


    /**
     * Returns true if property has been defined in this style.
     *
     * @param cssName The CSS property name, e.g. "font-family".
     * @return True if the property is defined in this set.
     */
    public boolean hasProperty(CSSName cssName) {
        return cascadedProperties.containsKey(cssName);
    }

    /**
     * Returns a {@link com.openhtmltopdf.css.sheet.PropertyDeclaration} by CSS
     * property name, e.g. "font-family". Properties are already cascaded during
     * instantiation, so this will return the actual property (and corresponding
     * value) to use for CSS-based layout and rendering.
     *
     * @param cssName The CSS property name, e.g. "font-family".
     * @return The PropertyDeclaration, if declared in this set, or null
     *         if not found.
     */
    public PropertyDeclaration propertyByName(CSSName cssName) {
        return cascadedProperties.get(cssName);
    }

    /**
     * Gets the ident attribute of the CascadedStyle object
     *
     * @param cssName PARAM
     * @return The ident value
     */
    public IdentValue getIdent(CSSName cssName) {
        PropertyDeclaration pd = propertyByName(cssName);
        return (pd == null ? null : pd.asIdentValue());
    }


    /**
     * Returns an {@link java.util.Iterator} over the set of {@link
     * com.openhtmltopdf.css.sheet.PropertyDeclaration}s already matched in this
     * CascadedStyle. For a given property name, there may be no match, in which
     * case there will be no <code>PropertyDeclaration</code> for that property
     * name in the Iterator.
     *
     * @return Iterator over a set of properly cascaded PropertyDeclarations.
     */
    public java.util.Collection<PropertyDeclaration> getCascadedPropertyDeclarations() {
        return cascadedProperties.values();
    }

    /**
     * Cascade-priority rank of {@code cssName}'s winning declaration (higher
     * wins); {@link Integer#MIN_VALUE} if unknown. Used to derive declarations in
     * priority order.
     *
     * @see #matchSequence
     */
    public int getMatchSequence(CSSName cssName) {
        Integer seq = matchSequence.get(cssName);
        return seq == null ? Integer.MIN_VALUE : seq.intValue();
    }

    /**
     * Whether this style carries a declaration whose value contains {@code var()}
     * and therefore has to be derived in cascade order.
     *
     * @see #hasPendingVarProperties
     */
    public boolean hasPendingVarProperties() {
        return hasPendingVarProperties;
    }

    public int countAssigned() { return cascadedProperties.size(); }

    public String getFingerprint() {
        if (this.fingerprint == null) {
            StringBuilder sb = new StringBuilder();
            for (PropertyDeclaration o : cascadedProperties.values()) {
                sb.append(o.getFingerprint());
            }
            // Custom properties must contribute too: identical regular declarations
            // but different --* values can resolve var() differently, so two such
            // elements must not share a cached style.
            for (CustomPropertyDeclaration o : customProperties.values()) {
                sb.append(o.getFingerprint());
            }
            this.fingerprint = sb.toString();
        }
        return this.fingerprint;
    }
}
