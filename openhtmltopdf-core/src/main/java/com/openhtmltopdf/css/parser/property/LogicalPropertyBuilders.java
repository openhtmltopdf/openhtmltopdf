package com.openhtmltopdf.css.parser.property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.parser.PropertyValue;
import com.openhtmltopdf.css.sheet.PropertyDeclaration;

/**
 * Builders for the logical properties of CSS Logical Properties and Values that are
 * shorthands or aliases. Without writing-mode the block direction is always top to
 * bottom, so block-start and block-end are top and bottom; the inline direction
 * depends on direction, so inline-start and inline-end are logical longhands that
 * CalculatedStyle applies to left or right.
 *
 * <p>The target properties are given as suppliers, because they may be declared
 * after the property that uses the builder.</p>
 */
public class LogicalPropertyBuilders {
    private LogicalPropertyBuilders() {
    }

    /**
     * One value per side, spread like margin: one target takes one value; two targets
     * (start, end) take one or two values; four targets (top, right, bottom, left) take
     * one to four values. Each value is built by its target's own builder.
     */
    public static class Sides extends AbstractPropertyBuilder {
        private final Supplier<CSSName[]> targets;

        public Sides(Supplier<CSSName[]> targets) {
            this.targets = targets;
        }

        @Override
        public List<PropertyDeclaration> buildDeclarations(
                CSSName cssName, List<PropertyValue> values, int origin, boolean important, boolean inheritAllowed) {
            CSSName[] props = targets.get();
            checkValueCount(cssName, 1, props.length, values.size());

            int[] valueIndex;
            if (props.length == 4) {
                switch (values.size()) {
                case 1: valueIndex = new int[] { 0, 0, 0, 0 }; break;
                case 2: valueIndex = new int[] { 0, 1, 0, 1 }; break;
                case 3: valueIndex = new int[] { 0, 1, 2, 1 }; break;
                default: valueIndex = new int[] { 0, 1, 2, 3 }; break;
                }
            } else if (props.length == 2) {
                valueIndex = values.size() == 1 ? new int[] { 0, 0 } : new int[] { 0, 1 };
            } else {
                valueIndex = new int[] { 0 };
            }

            List<PropertyDeclaration> result = new ArrayList<>(props.length);
            for (int i = 0; i < props.length; i++) {
                PropertyValue value = values.get(valueIndex[i]);
                result.addAll(CSSName.getPropertyBuilder(props[i]).buildDeclarations(
                        props[i], Collections.singletonList(value), origin, important,
                        inheritAllowed && values.size() == 1));
            }
            return result;
        }
    }

    /**
     * The same values given to one or more other shorthands, such as border-block
     * to border-top and border-bottom; the declarations they build may be renamed,
     * such as border-left-width to border-inline-start-width.
     */
    public static class SameAs extends AbstractPropertyBuilder {
        private final Supplier<CSSName[]> shorthands;
        private final Supplier<Map<CSSName, CSSName>> renames;

        public SameAs(Supplier<CSSName[]> shorthands, Supplier<Map<CSSName, CSSName>> renames) {
            this.shorthands = shorthands;
            this.renames = renames;
        }

        public SameAs(Supplier<CSSName[]> shorthands) {
            this(shorthands, Collections::emptyMap);
        }

        @Override
        public List<PropertyDeclaration> buildDeclarations(
                CSSName cssName, List<PropertyValue> values, int origin, boolean important, boolean inheritAllowed) {
            Map<CSSName, CSSName> rename = renames.get();
            List<PropertyDeclaration> result = new ArrayList<>();
            for (CSSName shorthand : shorthands.get()) {
                for (PropertyDeclaration decl : CSSName.getPropertyBuilder(shorthand).buildDeclarations(
                        shorthand, values, origin, important, inheritAllowed)) {
                    CSSName target = rename.get(decl.getCSSName());
                    result.add(target == null ? decl : copyOf(decl, target));
                }
            }
            return result;
        }
    }
}
