package com.openhtmltopdf.extend;

/**
 * Interface for a custom hyphenation implementation.
 */
@FunctionalInterface
public interface Hyphenator {
    /**
     * Hyphenates a given String by inserting soft-hyphens
     * at desired hyphenation points and returns the result.
     *
     * @param text String to hyphenate
     * @return with soft-hyphens hyphenated String
     */
    String hyphenateText(String text);
}
