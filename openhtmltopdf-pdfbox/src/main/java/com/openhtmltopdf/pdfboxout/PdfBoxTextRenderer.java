/*
 * {{{ header & license
 * Copyright (c) 2006 Wisconsin Court System
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * }}}
 */
package com.openhtmltopdf.pdfboxout;

import com.openhtmltopdf.bidi.BidiReorderer;
import com.openhtmltopdf.bidi.BidiSplitter;
import com.openhtmltopdf.extend.FontContext;
import com.openhtmltopdf.extend.OutputDevice;
import com.openhtmltopdf.extend.TextRenderer;
import com.openhtmltopdf.pdfboxout.PdfBoxFontResolver.FontDescription;
import com.openhtmltopdf.render.FSFont;
import com.openhtmltopdf.render.FSFontMetrics;
import com.openhtmltopdf.render.JustificationInfo;
import com.openhtmltopdf.util.LogMessageId;
import com.openhtmltopdf.util.OpenUtil;
import com.openhtmltopdf.util.ThreadCtx;
import com.openhtmltopdf.util.XRLog;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public class PdfBoxTextRenderer implements TextRenderer {

    private static final float TEXT_MEASURING_DELTA = 0.01f;
    private static final int FAST_FONT_RUN_MINIMUM_LENGTH = 32;
    private static final int FAST_FONT_RUN_PARTITION_FACTOR = 3;

    private BidiReorderer _reorderer;

    // These will mean only first missing font/metrics
    // is logged but they should have already got a loading warning.
    // Quiet the font is missing log message.
    private boolean _loggedMissingFont = false;

    // Quiet the font metrics not available message.
    private boolean _loggedMissingMetrics = false;

    public void setup(FontContext context, BidiReorderer reorderer) {
        this._reorderer = reorderer;
    }

    @Override
    public void drawString(OutputDevice outputDevice, String string, float x, float y) {
        ((PdfBoxOutputDevice) outputDevice).drawString(string, x, y, null);
    }

    @Override
    public void drawString(
            OutputDevice outputDevice, String string, float x, float y, JustificationInfo info) {
        ((PdfBoxOutputDevice) outputDevice).drawString(string, x, y, info);
    }

    @Override
    public FSFontMetrics getFSFontMetrics(FontContext context, FSFont font, String string) {
        List<FontDescription> descrs = ((PdfBoxFSFont) font).getFontDescriptions();
        float size = font.getSize2D();
        PdfBoxFSFontMetrics result = new PdfBoxFSFontMetrics();

        float largestAscent = -Float.MAX_VALUE;
        float largestDescent = -Float.MAX_VALUE;
        float largestStrikethroughOffset = -Float.MAX_VALUE;
        float largestStrikethroughThickness = -Float.MAX_VALUE;
        float largestUnderlinePosition = -Float.MAX_VALUE;
        float largestUnderlineThickness = -Float.MAX_VALUE;
        float largestTypoDescent = -Float.MAX_VALUE;
        // Fonts trade ascent, descent and line gap off against each other, so the
        // largest of each taken on its own can add up to a taller line than any
        // font in the list asks for. Track the tallest line asked for instead.
        float tallestLine = 0f;

        for (FontDescription des : descrs) {
            PdfBoxRawPDFontMetrics metrics = des.getFontMetrics();

            if (metrics == null) {
                if (!_loggedMissingMetrics) {
                    XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_FONT_METRICS_NOT_AVAILABLE, des);
                    _loggedMissingMetrics = true;
                }
                continue;
            }

            float loopAscent = metrics._ascent;
            float loopDescent = metrics._descent;
            float loopStrikethroughOffset = metrics._strikethroughOffset;
            float loopStrikethroughThickness = metrics._strikethroughThickness;
            float loopUnderlinePosition = metrics._underlinePosition;
            float loopUnderlineThickness = metrics._underlineThickness;

            if (loopAscent > largestAscent) {
                largestAscent = loopAscent;
            }

            if (loopDescent > largestDescent) {
                largestDescent = loopDescent;
            }

            if (loopStrikethroughOffset > largestStrikethroughOffset) {
                largestStrikethroughOffset = loopStrikethroughOffset;
            }

            if (loopStrikethroughThickness > largestStrikethroughThickness) {
                largestStrikethroughThickness = loopStrikethroughThickness;
            }

            if (loopUnderlinePosition > largestUnderlinePosition) {
                largestUnderlinePosition = loopUnderlinePosition;
            }

            if (loopUnderlineThickness > largestUnderlineThickness) {
                largestUnderlineThickness = loopUnderlineThickness;
            }

            if (metrics._typoDescent > largestTypoDescent) {
                largestTypoDescent = metrics._typoDescent;
            }

            float loopLine = loopAscent + loopDescent + metrics._lineGap;

            if (loopLine > tallestLine) {
                tallestLine = loopLine;
            }
        }

        result.setAscent(largestAscent / 1000f * size);
        result.setDescent(largestDescent / 1000f * size);
        result.setStrikethroughOffset(largestStrikethroughOffset / 1000f * size);

        if (largestStrikethroughThickness > 0) {
            result.setStrikethroughThickness(largestStrikethroughThickness / 1000f * size);
        } else {
            result.setStrikethroughThickness(size / 12.0f);
        }

        result.setUnderlineOffset(largestUnderlinePosition / 1000f * size);
        result.setUnderlineThickness(largestUnderlineThickness / 1000f * size);
        result.setTypoDescent(largestTypoDescent / 1000f * size);

        // Whatever is left of the tallest line once the ascent and descent above
        // are accounted for, and zero when no font had usable metrics: unlike the
        // values above this one is added to them rather than compared with them,
        // so the sentinel must not reach the result.
        float lineGap = tallestLine > 0f
                ? Math.max(0f, tallestLine - (largestAscent + largestDescent))
                : 0f;

        result.setLineGap(lineGap / 1000f * size);

        return result;
    }

    private static class ReplacementChar {
        String replacement;
        FontDescription fontDescription;
    }

    public static boolean isJustificationSpace(int c) {
        return c == ' ' || c == '\u00a0' || c == '\u3000';
    }

    private static ReplacementChar getReplacementChar(FSFont font) {
        String replaceStr = ThreadCtx.get().sharedContext().getReplacementText();
        List<FontDescription> descriptions = ((PdfBoxFSFont) font).getFontDescriptions();

        for (FontDescription des : descriptions) {
            try {
                des.getFont().getStringWidth(replaceStr);

                // Got here without throwing, so the text exists in font.
                ReplacementChar replace = new ReplacementChar();
                replace.replacement = replaceStr;
                replace.fontDescription = des;
                return replace;
            } catch (Exception e) {
                // Could not use replacement character in this font.
            }
        }

        // Still haven't found a font supporting our replacement text, try space character.
        replaceStr = " ";
        for (FontDescription des : descriptions) {
            try {
                des.getFont().getStringWidth(replaceStr);

                // Got here without throwing, so the char exists in font.
                ReplacementChar replace = new ReplacementChar();
                replace.replacement = " ";
                replace.fontDescription = des;
                return replace;
            } catch (Exception e) {
                // Could not use space in this font!
            }
        }

        // Really?, no font support for either replacement text or space!
        XRLog.log(Level.INFO, LogMessageId.LogMessageId0Param.GENERAL_PDF_SPECIFIED_FONTS_DONT_CONTAIN_A_SPACE_CHARACTER);
        ReplacementChar replace = new ReplacementChar();
        replace.replacement = "";
        replace.fontDescription = descriptions.get(0);
        return replace;
    }

    private static boolean containsCodePoint(PDFont pdFont, int codePoint) {
        if (pdFont instanceof PDType0Font) {
            return ((PDType0Font) pdFont).getCmapLookup().getGlyphId(codePoint) != 0;
        }
        try {
            pdFont.getStringWidth(new String(Character.toChars(codePoint)));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean containsAllCodePoints(PDFont pdFont, String text) {
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (!containsCodePoint(pdFont, cp)) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return true;
    }

    public static List<FontRun> divideIntoFontRuns(PdfBoxFSFont font, String str, BidiReorderer reorderer) {
        StringBuilder stringBuilder = new StringBuilder();
        List<FontDescription> fontDescriptions = font.getFontDescriptions();
        List<FontRun> runs = new ArrayList<>();
        FontRun currentRun = null;

        for (int i = 0; i < str.length(); ) {
            int unicode = str.codePointAt(i);
            i += Character.charCount(unicode);
            String ch = String.valueOf(Character.toChars(unicode));

            if (!OpenUtil.isSafeFontCodePointToPrint(unicode)) {
                // Filter out characters that should never be visible (such
                // as soft-hyphen) but are in some fonts.
                continue;
            }

            FontDescription applicableDescription = null;
            for (FontDescription description : fontDescriptions) {
                PDFont pdFont = description.getFont();
                if (pdFont == null) {
                    continue;
                }

                if (containsCodePoint(pdFont, unicode)) {
                    applicableDescription = description;
                } else if (!reorderer.isLiveImplementation()) {
                    continue;
                }

                if (applicableDescription == null) {
                    // Character is not in font. Try deshaping (may expand one
                    // code point into several, so all must match) before moving on.
                    // e.g. Arabic ligature U+FEFB (lam-alef) deshapes to
                    // U+0644 U+0627 (lam + alef).
                    String deshaped = reorderer.deshapeText(ch);
                    if (containsAllCodePoints(pdFont, deshaped)) {
                        applicableDescription = description;
                        ch = deshaped;
                    } else {
                        // Keep trying with next font.
                        continue;
                    }
                }

                if (currentRun == null) {
                    // First character of run.
                    currentRun = new FontRun(applicableDescription);
                } else if (applicableDescription != currentRun.description) {
                    // We have changed font, so we'll start a new font run.
                    currentRun.string = stringBuilder.toString();
                    runs.add(currentRun);
                    currentRun = new FontRun(applicableDescription);
                    stringBuilder = new StringBuilder();
                }

                if (isJustificationSpace(unicode)) {
                    currentRun.spaceCharacterCount++;
                } else {
                    currentRun.otherCharacterCount++;
                }

                stringBuilder.append(ch);
                break;
            }

            if (applicableDescription == null) {
                if (!OpenUtil.isCodePointPrintable(unicode)) {
                    // Filter out control and similar characters when they are not present in any font.
                    continue;
                }

                // We still don't have the character after all that. So use replacement character.
                ReplacementChar replace = PdfBoxTextRenderer.getReplacementChar(font);
                if (currentRun == null) {
                    // First character of run.
                    currentRun = new FontRun(replace.fontDescription);
                } else if (replace.fontDescription != currentRun.description) {
                    // We have changed font, so we'll start a new font run.
                    currentRun.string = stringBuilder.toString();
                    runs.add(currentRun);
                    currentRun = new FontRun(replace.fontDescription);
                    stringBuilder = new StringBuilder();
                }

                if (Character.isSpaceChar(unicode) || Character.isWhitespace(unicode)) {
                    currentRun.spaceCharacterCount++;
                    stringBuilder.append(' ');
                } else {
                    currentRun.otherCharacterCount++;
                    stringBuilder.append(replace.replacement);
                }
            }
        }

        if (stringBuilder.length() > 0) {
            assert currentRun != null;

            currentRun.string = stringBuilder.toString();
            runs.add(currentRun);
        }

        return runs;
    }

    private float getStringWidthSlow(PdfBoxFSFont font, String str) {
        List<FontRun> runs = divideIntoFontRuns(font, str, _reorderer);
        float strWidth = 0;

        for (FontRun run : runs) {
            try {
                strWidth += run.description.getFont().getStringWidth(run.string);
            } catch (Exception e) {
                XRLog.log(Level.WARNING, LogMessageId.LogMessageId0Param.RENDER_BUG_FONT_DIDNT_CONTAIN_EXPECTED_CHARACTER, e);
            }
        }

        return strWidth;
    }

    @Override
    public int getWidth(FontContext context, FSFont font, String string) {
        PdfBoxFSFont pdfBoxFont = (PdfBoxFSFont) font;
        String effectiveString = TextRenderer.getEffectivePrintableString(string);

        if (pdfBoxFont.getFontDescriptions() == null || pdfBoxFont.getFontDescriptions().isEmpty()) {
            XRLog.log(Level.WARNING, LogMessageId.LogMessageId0Param.RENDER_FONT_LIST_IS_EMPTY);
            return 0;
        }

        Optional<FontDescription> description = getFontDescription(pdfBoxFont);
        if (!description.isPresent()) {
            return 0;
        }

        float result = 0f;
        try {
            result = description.get().getFont().getStringWidth(effectiveString) / 1000f * pdfBoxFont.getSize2D();
        } catch (IllegalArgumentException e) {
            /* PDFont::getStringWidth throws an IllegalArgumentException if the character doesn't exist in the font.
               We can do it one character by character instead, but first let's partition the string logarithmically
               (e.g. merge-sort) to minimize the length of the string which must be parsed slowly. */

            if (effectiveString.length() < FAST_FONT_RUN_MINIMUM_LENGTH) {
                result = getStringWidthSlow(pdfBoxFont, effectiveString) / 1000f * pdfBoxFont.getSize2D();
            } else {
                for (int i = 0; i < FAST_FONT_RUN_PARTITION_FACTOR; i++) {
                    int chunkSize = effectiveString.length() / FAST_FONT_RUN_PARTITION_FACTOR;
                    int left = i * chunkSize;
                    int right = i + 1 == FAST_FONT_RUN_PARTITION_FACTOR ? effectiveString.length() : (i + 1) * chunkSize;
                    String chunk = effectiveString.substring(left, right);
                    result += getWidth(context, pdfBoxFont, chunk);
                }
            }
        } catch (IOException e) {
            throw new PdfContentStreamAdapter.PdfException("getWidth", e);
        }

        if (result - Math.floor(result) < TEXT_MEASURING_DELTA) {
            return (int) result;
        } else {
            return (int) Math.ceil(result);
        }
    }

    /**
     * A left-to-right run in a kerned font ({@link PdfBoxFSFont#isKerning()}) is
     * measured with {@link #kernedRuns}, the runs the output device draws it with;
     * every other width, including this class's directionless
     * {@link #getWidth(FontContext, FSFont, String)}, is unkerned.
     */
    @Override
    public int getWidth(FontContext context, FSFont font, String string, byte direction) {
        PdfBoxFSFont pdfBoxFont = (PdfBoxFSFont) font;
        if (!pdfBoxFont.isKerning() || direction != BidiSplitter.LTR) {
            return getWidth(context, font, string);
        }

        if (pdfBoxFont.getFontDescriptions() == null || pdfBoxFont.getFontDescriptions().isEmpty()) {
            XRLog.log(Level.WARNING, LogMessageId.LogMessageId0Param.RENDER_FONT_LIST_IS_EMPTY);
            return 0;
        }

        float result = 0f;
        for (FontRun run : kernedRuns(pdfBoxFont, string, _reorderer)) {
            result += run.width() / 1000f * pdfBoxFont.getSize2D();
        }

        if (result - Math.floor(result) < TEXT_MEASURING_DELTA) {
            return (int) result;
        } else {
            return (int) Math.ceil(result);
        }
    }

    /**
     * The kerning {@link #kernedRuns} applies between the characters on either side
     * of <code>index</code>: none unless the text is a left-to-right run, both
     * characters are drawn (a soft hyphen, or any other character that is not drawn,
     * stops kerning) and both are drawn in the same font, the first font that has the
     * character, as in {@link #divideIntoFontRuns(PdfBoxFSFont, String, BidiReorderer)}.
     */
    @Override
    public float getKerning(FontContext context, FSFont font, String text, int index, byte direction) {
        PdfBoxFSFont pdfBoxFont = (PdfBoxFSFont) font;
        if (!pdfBoxFont.isKerning() || direction != BidiSplitter.LTR || index <= 0 || index >= text.length()) {
            return 0f;
        }

        int left = text.codePointBefore(index);
        int right = text.codePointAt(index);
        if (!OpenUtil.isSafeFontCodePointToPrint(left) || !OpenUtil.isSafeFontCodePointToPrint(right)) {
            return 0f;
        }

        FontDescription description = drawingFont(pdfBoxFont, left);
        if (description == null || description != drawingFont(pdfBoxFont, right)) {
            return 0f;
        }

        return description.getKerning().pair(left, right) / 1000f * pdfBoxFont.getSize2D();
    }

    private static FontDescription drawingFont(PdfBoxFSFont font, int codePoint) {
        for (FontDescription description : font.getFontDescriptions()) {
            PDFont pdFont = description.getFont();
            if (pdFont != null && containsCodePoint(pdFont, codePoint)) {
                return description;
            }
        }
        return null;
    }

    /**
     * How a left-to-right run is drawn with kerning, and so how it is measured: the
     * one width path for both. Characters that are not drawn (a soft hyphen, also
     * the one the output device puts before a displayed hyphen) split the text, and no pair across a
     * split is kerned. Each part is drawn in the first font when that font has every
     * character of it, and otherwise in font runs, as unkerned text is; adjoining
     * runs in one font are joined into one. Each run carries the font's pair
     * kerning after each of its characters.
     */
    static List<FontRun> kernedRuns(PdfBoxFSFont font, String raw, BidiReorderer reorderer) {
        List<FontRun> runs = new ArrayList<>();
        Map<FontRun, List<Integer>> splits = new IdentityHashMap<>();
        StringBuilder part = new StringBuilder();

        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);

            if (OpenUtil.isSafeFontCodePointToPrint(cp)) {
                part.appendCodePoint(cp);
            } else {
                addPart(font, part.toString(), reorderer, runs, splits);
                part.setLength(0);
            }
        }
        addPart(font, part.toString(), reorderer, runs, splits);

        for (FontRun run : runs) {
            float[] kerning = run.description.getKerning().adjustments(run.string);
            if (kerning != null) {
                for (int split : splits.getOrDefault(run, Collections.emptyList())) {
                    kerning[split] = 0f;
                }
            }
            run.kerning = kerning;
        }

        return runs;
    }

    /**
     * Appends the runs of one part. Where the part continues the previous run's font
     * the two are joined, and the join is recorded as a split of that run: the index
     * of the last character before it, whose kerning is zero.
     */
    private static void addPart(PdfBoxFSFont font, String part, BidiReorderer reorderer,
            List<FontRun> runs, Map<FontRun, List<Integer>> splits) {
        if (part.isEmpty()) {
            return;
        }

        List<FontRun> partRuns;
        FontDescription first = font.getFontDescriptions().get(0);
        if (fitsEntirely(first, part)) {
            FontRun run = new FontRun(first);
            run.string = part;
            part.codePoints().forEach(cp -> {
                if (isJustificationSpace(cp)) {
                    run.spaceCharacterCount++;
                } else {
                    run.otherCharacterCount++;
                }
            });
            partRuns = new ArrayList<>();
            partRuns.add(run);
        } else {
            partRuns = divideIntoFontRuns(font, part, reorderer);
        }

        for (int i = 0; i < partRuns.size(); i++) {
            FontRun next = partRuns.get(i);
            FontRun last = runs.isEmpty() ? null : runs.get(runs.size() - 1);

            if (i == 0 && last != null && last.description == next.description) {
                int lastIndex = last.string.codePointCount(0, last.string.length()) - 1;
                splits.computeIfAbsent(last, run -> new ArrayList<>()).add(lastIndex);
                last.string = last.string + next.string;
                last.spaceCharacterCount += next.spaceCharacterCount;
                last.otherCharacterCount += next.otherCharacterCount;
            } else {
                runs.add(next);
            }
        }
    }

    private static boolean fitsEntirely(FontDescription description, String part) {
        try {
            PDFont pdFont = description.getFont();
            if (pdFont == null) {
                return false;
            }
            pdFont.getStringWidth(part);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Optional<FontDescription> getFontDescription(PdfBoxFSFont pdfBoxFont) {
        for (FontDescription d : pdfBoxFont.getFontDescriptions()) {
            if (d.getFont() != null) {
                return Optional.of(d);
            }
            logMissingFont(d);
        }
        return Optional.empty();
    }

    private void logMissingFont(FontDescription fontDescription) {
        if (_loggedMissingFont) {
            return;
        }

        XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.RENDER_FONT_IS_NULL, fontDescription);
        _loggedMissingFont = true;
    }

}
