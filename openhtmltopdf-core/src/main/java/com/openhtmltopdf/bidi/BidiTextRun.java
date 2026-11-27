package com.openhtmltopdf.bidi;


/**
 * Simple class to hold a visual ordering text run.
 */
public class BidiTextRun {
    private final int startIndex;
    private final int length;
    private final byte direction;
    private final int level;

    public BidiTextRun(int startIndex, int length, byte direction) {
        this(startIndex, length, direction, -1);
    }

    /**
     * @param level the Unicode bidi embedding level of the run, or -1 if unknown.
     */
    public BidiTextRun(int startIndex, int length, byte direction, int level) {
        this.startIndex = startIndex;
        this.length = length;
        this.direction = direction;
        this.level = level;
    }

    /**
     * @return the Unicode bidi embedding level of the run, or -1 if the
     * splitter does not provide levels.
     */
    public int getLevel() {
        return level;
    }
    
    public int getStart() {
        return startIndex;
    }
    
    public int getLength() {
        return length;
    }

    /**
     * @return either LTR or RTL.
     */
    public byte getDirection() {
        return direction;
    }
}
