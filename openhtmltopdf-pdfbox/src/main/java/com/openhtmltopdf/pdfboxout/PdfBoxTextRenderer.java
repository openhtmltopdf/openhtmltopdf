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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

    public static List<FontRun> divideIntoFontRuns(FSFont font, String str, BidiReorderer reorderer) {
        StringBuilder stringBuilder = new StringBuilder();
        ReplacementChar replace = PdfBoxTextRenderer.getReplacementChar(font);
        List<FontDescription> fontDescriptions = ((PdfBoxFSFont) font).getFontDescriptions();
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
                if (description.getFont() == null) {
                    continue;
                }
                try {
                    description.getFont().getStringWidth(ch);
                    applicableDescription = description;
                } catch (IllegalArgumentException e) {
                    if (!reorderer.isLiveImplementation()) {
                        continue;
                    }

                    // Character is not in font! Next, we try deshaping.
                    String deshaped = reorderer.deshapeText(ch);
                    try {
                        description.getFont().getStringWidth(deshaped);
                        applicableDescription = description;
                    } catch (Exception e2) {
                        // Keep trying with next font.
                        continue;
                    }
                } catch (IOException e) {
                    // Keep trying with next font.
                }
            }

            if (applicableDescription == null) {
                if (!OpenUtil.isCodePointPrintable(unicode)) {
                    // Filter out control and similar characters when they are not present in any font.
                    continue;
                }

                // We still don't have the character after all that. So use replacement character.
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
                continue;
            }

            // We got here, so this font has this character.
            if (currentRun == null) { // First character of run.
                currentRun = new FontRun(applicableDescription);
            } else if (applicableDescription != currentRun.description) { // We have changed font, so we'll start a new font run.
                currentRun.string = stringBuilder.toString();
                runs.add(currentRun);
                currentRun = new FontRun(applicableDescription);
                stringBuilder = new StringBuilder();
            }

            if (isJustificationSpace(unicode)) {
                currentRun.spaceCharacterCount += 1;
            } else {
                currentRun.otherCharacterCount += 1;
            }

            stringBuilder.append(ch);
        }

        if (stringBuilder.length() > 0) {
            assert currentRun != null;

            currentRun.string = stringBuilder.toString();
            runs.add(currentRun);
        }

        return runs;
    }

    private float getStringWidthSlow(FSFont bf, String str) {
        List<FontRun> runs = divideIntoFontRuns(bf, str, _reorderer);
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
                    result += getWidth(context, font, chunk);
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
