package com.openhtmltopdf.layout;

import static org.junit.Assert.assertEquals;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

import com.openhtmltopdf.extend.FSTextBreaker;

public class BreakSpacesTextBreakerTest {

    @Test
    public void testBreaksAfterEverySpace() {
        assertPieces("mill mill mill", "mill ", "mill ", "mill");
        assertPieces("mill   mill", "mill ", " ", " ", "mill");
        assertPieces("  mill  ", " ", " ", "mill ", " ");
    }

    @Test
    public void testKeepsTheBreaksOfTheWrappedBreaker() {
        assertPieces("Pseudo-element  x", "Pseudo-", "element ", " ", "x");
    }

    @Test
    public void testWithoutSpaces() {
        assertPieces("mill", "mill");
        assertPieces("");
    }

    @Test
    public void testSetTextStartsOver() {
        FSTextBreaker breaker = new BreakSpacesTextBreaker(lineBreaker("a b"), "a b");
        breaker.next();
        breaker.setText("c  d");
        assertEquals(Arrays.asList(2, 3, 4), breaks(breaker));
    }

    private static void assertPieces(String text, String... pieces) {
        List<String> actual = new ArrayList<>();
        int last = 0;
        for (int next : breaks(new BreakSpacesTextBreaker(lineBreaker(text), text))) {
            actual.add(text.substring(last, next));
            last = next;
        }
        assertEquals(text, Arrays.asList(pieces), actual);
    }

    private static List<Integer> breaks(FSTextBreaker breaker) {
        List<Integer> breaks = new ArrayList<>();
        int next;
        while ((next = breaker.next()) != BreakIterator.DONE) {
            breaks.add(next);
        }
        return breaks;
    }

    private static FSTextBreaker lineBreaker(String text) {
        FSTextBreaker breaker = new UrlAwareLineBreakIterator(BreakIterator.getLineInstance(Locale.US));
        breaker.setText(text);
        return breaker;
    }
}
