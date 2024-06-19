package com.openhtmltopdf.extend;

/**
 * Interface for a custom hyphenation implementation.
 */
@FunctionalInterface
public interface Hyphenator {
    String hyphenateText(String text);
}
