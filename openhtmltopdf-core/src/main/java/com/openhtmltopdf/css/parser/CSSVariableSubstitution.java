/*
 * {{{ header & license
 * Copyright (c) 2024 openhtmltopdf contributors
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
 * }}}
 */
package com.openhtmltopdf.css.parser;

/**
 * Substitutes CSS {@code var()} references in a value string with resolved custom
 * property values, following the
 * <a href="https://www.w3.org/TR/css-variables-1/#substitute-a-var">CSS Variables
 * substitution algorithm</a> textually. It is paren- and quote-aware (so nested
 * {@code var()} and parens/commas inside strings work, which a regex cannot do);
 * a {@link VarResolver} supplies replacements and fallbacks are substituted
 * recursively.
 *
 * <p>The {@link Result} reports whether substitution fully succeeded. If a
 * reference is undefined with no fallback, the value is "invalid at
 * computed-value time" and the caller should treat the property as {@code unset}.</p>
 */
public final class CSSVariableSubstitution {

    /** Guards against pathologically deep fallback nesting. */
    private static final int MAX_DEPTH = 50;

    private CSSVariableSubstitution() {}

    /**
     * Resolves a custom property name (including the leading {@code --}) to its
     * already fully-substituted value, or {@code null} if undefined or invalid
     * (e.g. part of a reference cycle).
     */
    public interface VarResolver {
        String resolve(String customPropertyName);
    }

    public static final class Result {
        private final String text;
        private final boolean resolved;

        Result(String text, boolean resolved) {
            this.text = text;
            this.resolved = resolved;
        }

        /** The substituted value text (only meaningful when {@link #isResolved()}). */
        public String getText() {
            return text;
        }

        /** True if every {@code var()} reference was resolved (or had a fallback). */
        public boolean isResolved() {
            return resolved;
        }
    }

    /**
     * Returns true if the value text contains a {@code var(} token. Cheap
     * pre-check to avoid running the full substitution on var-free values.
     */
    public static boolean containsVar(String valueText) {
        return indexOfVar(valueText, 0) >= 0;
    }

    public static Result substitute(String valueText, VarResolver resolver) {
        return substitute(valueText, resolver, 0);
    }

    private static Result substitute(String valueText, VarResolver resolver, int depth) {
        if (depth > MAX_DEPTH) {
            return new Result(valueText, false);
        }

        int from = indexOfVar(valueText, 0);
        if (from < 0) {
            return new Result(valueText, true);
        }

        StringBuilder out = new StringBuilder(valueText.length());
        int pos = 0;
        boolean resolved = true;

        while (from >= 0) {
            out.append(valueText, pos, from);

            int open = from + 3; // index of '(' in "var("
            int close = matchingParen(valueText, open);
            if (close < 0) {
                // Unterminated var(): bail out as invalid.
                return new Result(valueText, false);
            }

            String inner = valueText.substring(open + 1, close);
            int comma = topLevelComma(inner);

            String name = (comma < 0 ? inner : inner.substring(0, comma)).trim();
            String fallback = comma < 0 ? null : inner.substring(comma + 1);

            String replacement = name.startsWith("--") ? resolver.resolve(name) : null;

            if (replacement == null) {
                if (fallback != null) {
                    Result fb = substitute(fallback.trim(), resolver, depth + 1);
                    if (!fb.isResolved()) {
                        resolved = false;
                    }
                    out.append(fb.getText());
                } else {
                    // Undefined with no fallback: invalid at computed-value time.
                    resolved = false;
                }
            } else {
                out.append(replacement);
            }

            pos = close + 1;
            from = indexOfVar(valueText, pos);
        }

        out.append(valueText, pos, valueText.length());
        return new Result(out.toString(), resolved);
    }

    /**
     * Finds the next {@code var(} that begins a function token, starting at
     * {@code start}. It must not be part of a longer identifier (e.g.
     * {@code sidebar(}) nor lie inside a quoted string: a string token is opaque,
     * so {@code var(} within it is literal text, not a reference (matching the
     * quote-awareness of {@link #matchingParen}/{@link #topLevelComma}).
     * Returns the index of the {@code v}, or -1.
     */
    private static int indexOfVar(String s, int start) {
        char quote = 0;
        for (int i = Math.max(start, 0); i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++; // skip escaped char
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if ((c == 'v' || c == 'V') && s.regionMatches(true, i, "var(", 0, 4)) {
                char prev = i == 0 ? '\0' : s.charAt(i - 1);
                if (!isIdentChar(prev)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean isIdentChar(char c) {
        return c == '-' || c == '_' || Character.isLetterOrDigit(c);
    }

    /**
     * Given the index of an opening paren, returns the index of its matching
     * closing paren, accounting for nested parens and quoted strings, or -1.
     */
    private static int matchingParen(String s, int open) {
        int depth = 0;
        char quote = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++; // skip escaped char
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Returns the index of the first top-level comma in {@code s} (depth 0, not
     * inside parens or quotes), or -1 if there is none.
     */
    private static int topLevelComma(String s) {
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                return i;
            }
        }
        return -1;
    }
}
