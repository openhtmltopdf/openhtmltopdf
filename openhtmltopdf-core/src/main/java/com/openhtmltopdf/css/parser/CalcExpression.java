package com.openhtmltopdf.css.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A parsed calc(), min(), max() or clamp() expression (CSS Values 4). Lengths and
 * percentages in it can only be resolved during layout, when the percentage base
 * is known, so the tree is kept and evaluated by {@link #evaluate(Resolver)}.
 */
public abstract class CalcExpression {
    /** The kind of value an expression results in. */
    public enum Kind {
        /** A plain number, such as 2 or 1.5. */
        NUMBER,
        /** A length or a percentage of one. */
        LENGTH
    }

    /**
     * Resolves a single length or percentage to an absolute value.
     */
    public interface Resolver {
        /**
         * @param primitiveType the unit, a constant of {@link CSSPrimitiveValue}.
         * @param value the number in that unit.
         * @param cssText the value as written.
         */
        float resolve(short primitiveType, float value, String cssText);
    }

    CalcExpression() {
    }

    public abstract Kind getKind();

    /**
     * @return whether a percentage occurs anywhere in the expression, so that it can
     * only be evaluated against a percentage base.
     */
    public abstract boolean hasPercentage();

    public abstract float evaluate(Resolver resolver);

    /**
     * @return the expression as CSS text, so that it can be parsed again.
     */
    public abstract String toCSS();

    @Override
    public String toString() {
        return toCSS();
    }

    /**
     * A number, length or percentage.
     */
    static final class Leaf extends CalcExpression {
        private final PropertyValue value;
        private final Kind kind;

        Leaf(PropertyValue value, Kind kind) {
            this.value = value;
            this.kind = kind;
        }

        @Override
        public Kind getKind() {
            return kind;
        }

        @Override
        public boolean hasPercentage() {
            return value.getPrimitiveType() == CSSPrimitiveValue.CSS_PERCENTAGE;
        }

        @Override
        public float evaluate(Resolver resolver) {
            if (kind == Kind.NUMBER) {
                return value.getFloatValue();
            }
            return resolver.resolve(value.getPrimitiveType(), value.getFloatValue(), value.getCssText());
        }

        @Override
        public String toCSS() {
            return value.getCssText();
        }
    }

    /**
     * A var() reference inside an expression: such an expression is not evaluated, the
     * declaration is parsed again once the reference is substituted.
     */
    static final class VarReference extends CalcExpression {
        private final PropertyValue value;

        VarReference(PropertyValue value) {
            this.value = value;
        }

        PropertyValue getValue() {
            return value;
        }

        @Override
        public Kind getKind() {
            return Kind.LENGTH;
        }

        @Override
        public boolean hasPercentage() {
            return false;
        }

        @Override
        public float evaluate(Resolver resolver) {
            throw new IllegalStateException("var() must be substituted before evaluating " + toCSS());
        }

        @Override
        public String toCSS() {
            return CSSValueText.toCSS(Collections.singletonList(value));
        }
    }

    /**
     * Terms added or subtracted: a + b - c.
     */
    static final class Sum extends CalcExpression {
        private final List<CalcExpression> terms;
        /** For each term after the first, whether it is subtracted. */
        private final List<Boolean> subtract;

        Sum(List<CalcExpression> terms, List<Boolean> subtract) {
            this.terms = terms;
            this.subtract = subtract;
        }

        @Override
        public Kind getKind() {
            return terms.get(0).getKind();
        }

        @Override
        public boolean hasPercentage() {
            return terms.stream().anyMatch(CalcExpression::hasPercentage);
        }

        @Override
        public float evaluate(Resolver resolver) {
            float result = terms.get(0).evaluate(resolver);
            for (int i = 1; i < terms.size(); i++) {
                float term = terms.get(i).evaluate(resolver);
                result = subtract.get(i - 1) ? result - term : result + term;
            }
            return result;
        }

        @Override
        public String toCSS() {
            StringBuilder sb = new StringBuilder("(").append(terms.get(0).toCSS());
            for (int i = 1; i < terms.size(); i++) {
                sb.append(subtract.get(i - 1) ? " - " : " + ").append(terms.get(i).toCSS());
            }
            return sb.append(')').toString();
        }
    }

    /**
     * a * b or a / b, where at least one factor (the divisor for /) is a number.
     */
    static final class Product extends CalcExpression {
        private final CalcExpression left;
        private final CalcExpression right;
        private final boolean divide;

        Product(CalcExpression left, CalcExpression right, boolean divide) {
            this.left = left;
            this.right = right;
            this.divide = divide;
        }

        @Override
        public Kind getKind() {
            return left.getKind() == Kind.LENGTH || right.getKind() == Kind.LENGTH ? Kind.LENGTH : Kind.NUMBER;
        }

        @Override
        public boolean hasPercentage() {
            return left.hasPercentage() || right.hasPercentage();
        }

        @Override
        public float evaluate(Resolver resolver) {
            float l = left.evaluate(resolver);
            float r = right.evaluate(resolver);
            return divide ? l / r : l * r;
        }

        @Override
        public String toCSS() {
            return left.toCSS() + (divide ? " / " : " * ") + right.toCSS();
        }
    }

    /**
     * min(), max() and clamp(MIN, VAL, MAX), which is max(MIN, min(VAL, MAX)).
     */
    static final class Comparison extends CalcExpression {
        private final String name;
        private final List<CalcExpression> arguments;

        Comparison(String name, List<CalcExpression> arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        @Override
        public Kind getKind() {
            return arguments.get(0).getKind();
        }

        @Override
        public boolean hasPercentage() {
            return arguments.stream().anyMatch(CalcExpression::hasPercentage);
        }

        @Override
        public float evaluate(Resolver resolver) {
            if ("clamp".equals(name)) {
                float min = arguments.get(0).evaluate(resolver);
                float value = arguments.get(1).evaluate(resolver);
                float max = arguments.get(2).evaluate(resolver);
                return Math.max(min, Math.min(value, max));
            }

            boolean isMin = "min".equals(name);
            float result = arguments.get(0).evaluate(resolver);
            for (int i = 1; i < arguments.size(); i++) {
                float value = arguments.get(i).evaluate(resolver);
                result = isMin ? Math.min(result, value) : Math.max(result, value);
            }
            return result;
        }

        @Override
        public String toCSS() {
            StringBuilder sb = new StringBuilder(name).append('(');
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(arguments.get(i).toCSS());
            }
            return sb.append(')').toString();
        }
    }

    /**
     * calc(...) itself, kept so that the expression prints as it was written.
     */
    static final class Calc extends CalcExpression {
        private final CalcExpression content;

        Calc(CalcExpression content) {
            this.content = content;
        }

        @Override
        public Kind getKind() {
            return content.getKind();
        }

        @Override
        public boolean hasPercentage() {
            return content.hasPercentage();
        }

        @Override
        public float evaluate(Resolver resolver) {
            return content.evaluate(resolver);
        }

        @Override
        public String toCSS() {
            String inner = content.toCSS();
            if (content instanceof Sum) {
                // Its own parentheses are those of calc().
                inner = inner.substring(1, inner.length() - 1);
            }
            return "calc(" + inner + ")";
        }
    }

    /**
     * @return the var() references in the expression, empty if there are none.
     */
    public List<PropertyValue> varReferences() {
        List<PropertyValue> result = new ArrayList<>();
        collectVarReferences(this, result);
        return result;
    }

    private static void collectVarReferences(CalcExpression e, List<PropertyValue> result) {
        if (e instanceof VarReference) {
            result.add(((VarReference) e).getValue());
        } else if (e instanceof Sum) {
            ((Sum) e).terms.forEach(t -> collectVarReferences(t, result));
        } else if (e instanceof Product) {
            collectVarReferences(((Product) e).left, result);
            collectVarReferences(((Product) e).right, result);
        } else if (e instanceof Comparison) {
            ((Comparison) e).arguments.forEach(a -> collectVarReferences(a, result));
        } else if (e instanceof Calc) {
            collectVarReferences(((Calc) e).content, result);
        }
    }

    /**
     * @return why the expression mixes kinds of values it must not (a number added
     * to a length, a length multiplied by a length, a division by a length), or null.
     */
    public static String typeError(CalcExpression e) {
        if (e instanceof Sum) {
            Sum sum = (Sum) e;
            for (CalcExpression term : sum.terms) {
                String error = typeError(term);
                if (error != null) {
                    return error;
                }
                if (term.getKind() != sum.terms.get(0).getKind()) {
                    return "A number and a length are added";
                }
            }
        } else if (e instanceof Product) {
            Product product = (Product) e;
            String error = typeError(product.left);
            if (error == null) {
                error = typeError(product.right);
            }
            if (error != null) {
                return error;
            }
            if (product.right.getKind() == Kind.LENGTH && (product.divide || product.left.getKind() == Kind.LENGTH)) {
                return product.divide ? "A division by a length" : "Two lengths are multiplied";
            }
        } else if (e instanceof Comparison) {
            Comparison comparison = (Comparison) e;
            for (CalcExpression argument : comparison.arguments) {
                String error = typeError(argument);
                if (error != null) {
                    return error;
                }
                if (argument.getKind() != comparison.arguments.get(0).getKind()) {
                    return "Numbers and lengths are compared";
                }
            }
        } else if (e instanceof Calc) {
            return typeError(((Calc) e).content);
        }
        return null;
    }

    private static final java.util.Map<String, CalcExpression> PARSED =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<String, CalcExpression>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, CalcExpression> eldest) {
                    return size() > 256;
                }
            });

    /**
     * @return the expression written as cssText, as {@link #toCSS()} gives it, or
     * null if it is not one.
     */
    public static CalcExpression fromCSS(String cssText) {
        CalcExpression cached = PARSED.get(cssText);
        if (cached != null) {
            return cached;
        }
        CSSParser parser = new CSSParser((uri, message) -> { });
        PropertyValue value = parser.parsePropertyValue(
                com.openhtmltopdf.css.constants.CSSName.WIDTH,
                com.openhtmltopdf.css.sheet.StylesheetInfo.AUTHOR, cssText);
        CalcExpression result = value == null ? null : value.getCalc();
        if (result != null) {
            PARSED.put(cssText, result);
        }
        return result;
    }

    /**
     * @return whether name, including its opening parenthesis as the tokenizer gives
     * it, is one of the math functions.
     */
    public static boolean isMathFunction(String functionToken) {
        String name = functionToken.toLowerCase(Locale.ROOT);
        return name.equals("calc(") || name.equals("min(") || name.equals("max(") || name.equals("clamp(");
    }
}
