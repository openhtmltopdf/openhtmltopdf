package com.openhtmltopdf.layout;

import java.text.BreakIterator;

import com.openhtmltopdf.extend.FSTextBreaker;

/**
 * The line break opportunities of <code>white-space: break-spaces</code>: those of
 * the line breaker it wraps, and one after every space, including between spaces
 * (CSS Text 3, 3), so a run of spaces can wrap anywhere inside it.
 */
public class BreakSpacesTextBreaker implements FSTextBreaker {
    private final FSTextBreaker base;
    private String text;
    private int position;
    private int baseNext;

    /**
     * @param base a line breaker already set to {@code text}
     */
    public BreakSpacesTextBreaker(FSTextBreaker base, String text) {
        this.base = base;
        this.text = text;
        this.baseNext = base.next();
    }

    @Override
    public int next() {
        while (baseNext != BreakIterator.DONE && baseNext <= position) {
            baseNext = base.next();
        }

        int afterSpace = text.indexOf(' ', position);
        int next = afterSpace < 0 ? baseNext :
                   baseNext == BreakIterator.DONE ? afterSpace + 1 :
                   Math.min(afterSpace + 1, baseNext);

        if (next != BreakIterator.DONE) {
            position = next;
        }
        return next;
    }

    @Override
    public void setText(String newText) {
        base.setText(newText);
        text = newText;
        position = 0;
        baseNext = base.next();
    }
}
