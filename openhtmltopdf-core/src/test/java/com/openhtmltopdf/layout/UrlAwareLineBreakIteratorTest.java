package com.openhtmltopdf.layout;

import java.text.BreakIterator;
import java.util.Locale;

import com.openhtmltopdf.extend.FSTextBreaker;

import junit.framework.TestCase;


public class UrlAwareLineBreakIteratorTest extends TestCase {

    public void testNext_BreakAtSpace() throws Exception {
        assertBreaksCorrectly("Hello World! World foo",
                new String[] {"Hello ", "World! ", "World ", "foo"});
    }


    public void testNext_BreakAtPunctuation() throws Exception {
        assertBreaksCorrectly("The.quick,brown:fox;jumps!over?the(lazy)[dog]",
                new String[] {"The.", "quick,", "brown:", "fox;", "jumps!", "over?", "the", "(lazy)", "[dog]"});
    }


    public void testNext_BreakAtHyphen() throws Exception {
        assertBreaksCorrectly("Pseudo-element",
                new String[] {"Pseudo-", "element"});
    }


    public void testNext_BreakAtSlash() throws Exception {
        assertBreaksCorrectly("Justice/Law",
                new String[] {"Justice", "/Law"});
    }


    public void testNext_WordBeginsWithSlash() throws Exception {
        assertBreaksCorrectly("Justice /Law",
                new String[] {"Justice ", "/Law"});
    }


    public void testNext_WordEndsWithSlash() throws Exception {
        assertBreaksCorrectly("Justice/ Law",
                new String[] {"Justice/ ", "Law"});
    }


    public void testNext_WordEndsWithSlashMultipleWhitespace() throws Exception {
        assertBreaksCorrectly("Justice/    Law",
                new String[] {"Justice/    ", "Law"});
    }


    public void testNext_SlashSeparatedSequence() throws Exception {
        assertBreaksCorrectly("/this/is/a/long/path/name/",
                new String[] {"/this", "/is", "/a", "/long", "/path", "/name/"});
    }


    public void testNext_UrlInside() throws Exception {
        assertBreaksCorrectly("Sentence with url https://github.com/flyingsaucerproject/flyingsaucer?test=true&param2=false inside.",
                new String[] {"Sentence ", "with ", "url ", "https://github.", "com", "/flyingsaucerproject", "/flyingsaucer?",
                        "test=true&param2=false ", "inside."});
    }


    public void testNext_MultipleSlashesInWord() throws Exception {
        assertBreaksCorrectly("word/////word",
                new String[] {"word", "/////word"});
    }


    public void testNext_MultipleSlashesBeforeWord() throws Exception {
        assertBreaksCorrectly("hello /////world",
                new String[] {"hello ", "/////world"});
    }


    public void testNext_MultipleSlashesAfterWord() throws Exception {
        assertBreaksCorrectly("hello world/////",
                new String[] {"hello ", "world/////"});
    }


    public void testNext_MultipleSlashesAroundWord() throws Exception {
        assertBreaksCorrectly("hello /////world/////",
                new String[] {"hello ", "/////world/////"});
    }


    public void testNext_WhitespaceAfterTrailingSlashes() throws Exception {
        assertBreaksCorrectly("hello world///    ",
                new String[] {"hello ", "world///    "});
    }


    public void testNext_ShortUrl() throws Exception {
        assertBreaksCorrectly("http://localhost",
                new String[] {"http://localhost"});
    }


    public void testNext_IncompleteUrl() throws Exception {
        assertBreaksCorrectly("http://",
                new String[] {"http://"});
    }


    public void testNext_SlashBetweenDigits() throws Exception {
        assertBreaksCorrectly("no. 274/15 and 1/2/2024",
                new String[] {"no. ", "274/15 ", "and ", "1/2/2024"});
    }


    public void testNext_SlashBetweenDigitsInUrl() throws Exception {
        assertBreaksCorrectly("example.org/2024/10/news",
                new String[] {"example.", "org", "/2024/10", "/news"});
    }


    public void testNext_StraightQuotesInWord() throws Exception {
        assertBreaksCorrectly("a 256\"57 board, 3'4 long",
                new String[] {"a ", "256\"57 ", "board, ", "3'4 ", "long"});
    }


    public void testNext_QuotedWords() throws Exception {
        assertBreaksCorrectly("say \"hello\" now",
                new String[] {"say ", "\"hello\" ", "now"});
    }


    private void assertBreaksCorrectly(String input, String[] segments) {
        FSTextBreaker iterator = new UrlAwareLineBreakIterator(BreakIterator.getLineInstance(Locale.US));
        iterator.setText(input);

        int segmentIndex = 0;
        int lastBreakPoint = 0;
        int breakpoint;
        while ((breakpoint = iterator.next()) != BreakIterator.DONE) {
            if (segmentIndex < segments.length) {
                String segment = segments[segmentIndex++];
                assertEquals("Segment #" + segmentIndex + " does not match.", segment, input.substring(lastBreakPoint, breakpoint));
                lastBreakPoint = breakpoint;
            } else {
                fail("Too few segments.");
            }
        }
        assertEquals("Last breakpoint is wrong.", input.length(), lastBreakPoint);
        if (segmentIndex != segments.length) {
            fail("Too many segments.");
        }
    }

}