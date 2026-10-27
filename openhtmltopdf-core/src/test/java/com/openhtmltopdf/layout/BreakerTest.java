package com.openhtmltopdf.layout;

import org.junit.Test;

import com.openhtmltopdf.layout.LineBreakContext.LineBreakResult;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.hamcrest.MatcherAssert.assertThat;

import static com.openhtmltopdf.layout.BreakerTestSupport.*;

public class BreakerTest {
    /**
     * Characters are measured one by one, but the pair kerning between them is
     * drawn, so it counts towards what fits: four characters of width one with a
     * kerning of -1 between "b" and "c" take three.
     */
    @Test
    public void testCharacterBreakerCountsKerningBetweenCharacters() {
        String whole = "abcd";
        int avail = 3;
        TextSpacing spacing = TextSpacing.NONE;
        LineBreakContext context = createContext(whole);
        Breaker.PairKerning kerning = (text, index) -> text.charAt(index - 1) == 'b' && text.charAt(index) == 'c' ? -1 : 0;

        Breaker.doBreakCharacters(whole, createLine(whole), createChar(whole), context, avail, spacing, MEASURER, kerning);

        assertFalse(context.isNeedsNewLine());
        assertThat(context.getWidth(), equalTo(3));
        assertThat(context.getEnd(), equalTo(4));
    }

    /**
     * Without kerning the same four characters need a new line after three.
     */
    @Test
    public void testCharacterBreakerWithoutKerning() {
        String whole = "abcd";
        int avail = 3;
        TextSpacing spacing = TextSpacing.NONE;
        LineBreakContext context = createContext(whole);

        Breaker.doBreakCharacters(whole, createLine(whole), createChar(whole), context, avail, spacing, MEASURER);

        assertTrue(context.isNeedsNewLine());
        assertThat(context.getWidth(), equalTo(3));
        assertThat(context.getEnd(), equalTo(3));
    }

    /**
     * The word-breaking attempt leaves needsNewLine set before handing the word to
     * the character breaker; when the whole word then fits, the flag must be cleared.
     */
    @Test
    public void testCharacterBreakerFinishedClearsStaleNeedsNewLine() {
        String whole = "abc";
        int avail = 5;
        TextSpacing spacing = TextSpacing.NONE;
        LineBreakContext context = createContext(whole);
        context.setNeedsNewLine(true);

        LineBreakResult res = Breaker.doBreakCharacters(whole, createLine(whole), createChar(whole), context, avail, spacing, MEASURER);

        assertThat(res, equalTo(LineBreakResult.CHAR_BREAKING_FINISHED));
        assertFalse(context.isNeedsNewLine());
        assertThat(context.getEnd(), equalTo(3));
    }

    @Test
    public void testCharacterBreakerSingleChar() {
        String whole = "A";
        int avail = 5;
        TextSpacing spacing = TextSpacing.NONE;
        LineBreakContext context = createContext(whole);
        
        Breaker.doBreakCharacters(whole, createLine(whole), createChar(whole), context, avail, spacing, MEASURER);
        
        assertFalse(context.isUnbreakable());
        assertFalse(context.isNeedsNewLine());
        assertThat(context.getWidth(), equalTo(1));
        assertThat(context.getEnd(), equalTo(1));
    }

    @Test
    public void testCharacterBreakerEmptyString() {
        String whole = "";
        int avail = 5;
        TextSpacing spacing = TextSpacing.NONE;
        LineBreakContext context = createContext(whole);
        
        Breaker.doBreakCharacters(whole, createLine(whole), createChar(whole), context, avail, spacing, MEASURER);
        
        assertFalse(context.isUnbreakable());
        assertFalse(context.isNeedsNewLine());
        assertThat(context.getWidth(), equalTo(0));
        assertThat(context.getEnd(), equalTo(0));
    }
    
    @Test
    public void testCharacterBreakerUntilWord() {
        String whole = "ABCD WORD";
        int avail = 15;
        TextSpacing spacing = TextSpacing.NONE;
        LineBreakContext context = createContext(whole);
        
        Breaker.doBreakCharacters(whole, createLine(whole), createChar(whole), context, avail, spacing, MEASURER);
        
        assertFalse(context.isUnbreakable());
        assertFalse(context.isNeedsNewLine());
        assertThat(context.getWidth(), equalTo(5));
        assertThat(context.getEnd(), equalTo(5));
    }

    @Test
    public void testCharacterBreakerNoFit() {
        String whole = "ABCDEF";
        int avail = 4;
        TextSpacing spacing = TextSpacing.NONE;
        LineBreakContext context = createContext(whole);
        
        Breaker.doBreakCharacters(whole, createLine(whole), createChar(whole), context, avail, spacing, MEASURER);
        
        assertFalse(context.isUnbreakable());
        assertTrue(context.isNeedsNewLine());
        assertThat(context.getWidth(), equalTo(4));
        assertThat(context.getEnd(), equalTo(4));
        assertThat(whole.substring(context.getEnd()), equalTo("EF"));
    }

    @Test
    public void testCharacterBreakerExactFit() {
        String whole = "ABCD";
        int avail = 4;
        TextSpacing spacing = TextSpacing.NONE;
        LineBreakContext context = createContext(whole);
        
        Breaker.doBreakCharacters(whole, createLine(whole), createChar(whole), context, avail, spacing, MEASURER);
        
        assertFalse(context.isUnbreakable());
        assertFalse(context.isNeedsNewLine());
        assertThat(context.getWidth(), equalTo(4));
        assertThat(context.getEnd(), equalTo(4));
        assertThat(whole.substring(context.getEnd()), equalTo(""));
    }
    
    @Test
    public void testCharacterBreakerPartialFit() {
        String whole = "ABCDEF";
        int avail = 13;
        TextSpacing spacing = TextSpacing.NONE;
        LineBreakContext context = createContext(whole);
        
        Breaker.doBreakCharacters(whole, createLine(whole), createChar(whole), context, avail, spacing, MEASURER3);
        
        assertFalse(context.isUnbreakable());
        assertTrue(context.isNeedsNewLine());
        assertThat(context.getWidth(), equalTo(12));
        assertThat(context.getEnd(), equalTo(4));
        assertThat(whole.substring(context.getEnd()), equalTo("EF"));
    }
    
    @Test
    public void testCharacterBreakerNoFit2() {
        String whole = "ABCDEF";
        int avail = 2;
        TextSpacing spacing = TextSpacing.NONE;
        LineBreakContext context = createContext(whole);
        
        Breaker.doBreakCharacters(whole, createLine(whole), createChar(whole), context, avail, spacing, MEASURER3);
        
        // Breaks off minimum of one character.
        assertTrue(context.isUnbreakable());
        assertTrue(context.isNeedsNewLine());
        assertThat(context.getWidth(), equalTo(3));
        assertThat(context.getEnd(), equalTo(1));
        assertThat(whole.substring(context.getEnd()), equalTo("BCDEF"));
    }
    
    @Test
    public void testCharacterBreakerWordBreakAtStart() {
        String whole = "  ABCDEF";
        int avail = 20;
        TextSpacing spacing = TextSpacing.NONE;
        LineBreakContext context = createContext(whole);
        
        Breaker.doBreakCharacters(whole, createLine(whole), createChar(whole), context, avail, spacing, MEASURER);
        
        assertFalse(context.isUnbreakable());
        assertFalse(context.isNeedsNewLine());
        
        // Should always consume one space character.
        assertThat(context.getWidth(), equalTo(1));
        assertThat(context.getEnd(), equalTo(1));
        assertThat(whole.substring(context.getEnd()), equalTo(" ABCDEF"));
    }
}
