package com.openhtmltopdf.pdfboxout;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Pair kerning read from an OpenType <code>GPOS</code> table: the pair
 * adjustment lookups (type 2, formats 1 and 2, also behind an extension lookup)
 * of the <code>kern</code> feature, in every script and language system. Only the
 * horizontal advance of the first glyph is used, which is what a kerning value is;
 * placement, the second glyph's values and device tables are ignored.
 *
 * <p>As in an OpenType shaper, the subtables of one lookup are tried in order
 * until one applies, and the values of separate lookups add up. Lookup flags are
 * not applied: pairs are only ever looked up between neighbouring glyphs.
 *
 * <p>Values are in font units.
 */
final class GposPairKerning {
    private static final int LOOKUP_PAIR_ADJUSTMENT = 2;
    private static final int LOOKUP_EXTENSION = 9;

    private static final int VALUE_X_ADVANCE = 0x0004;

    private final ByteBuffer _table;

    // Per lookup, the offsets of its pair adjustment subtables, in order.
    private final List<int[]> _lookups;

    private GposPairKerning(ByteBuffer table, List<int[]> lookups) {
        this._table = table;
        this._lookups = lookups;
    }

    /**
     * The kerning of a <code>GPOS</code> table, or null when it has no pair
     * adjustment lookups in a <code>kern</code> feature.
     *
     * @throws RuntimeException when the table is malformed
     */
    static GposPairKerning of(byte[] gpos) {
        ByteBuffer table = ByteBuffer.wrap(gpos);

        int featureList = u16(table, 6);
        int lookupList = u16(table, 8);

        TreeSet<Integer> lookupIndices = new TreeSet<>();
        int featureCount = u16(table, featureList);
        for (int i = 0; i < featureCount; i++) {
            int record = featureList + 2 + i * 6;
            if (tag(table, record).equals("kern")) {
                int feature = featureList + u16(table, record + 4);
                int indexCount = u16(table, feature + 2);
                for (int j = 0; j < indexCount; j++) {
                    lookupIndices.add(u16(table, feature + 4 + j * 2));
                }
            }
        }

        List<int[]> lookups = new ArrayList<>();
        int lookupCount = u16(table, lookupList);
        for (int index : lookupIndices) {
            if (index >= lookupCount) {
                continue;
            }

            int lookup = lookupList + u16(table, lookupList + 2 + index * 2);
            int type = u16(table, lookup);
            int subTableCount = u16(table, lookup + 4);
            List<Integer> subTables = new ArrayList<>();

            for (int j = 0; j < subTableCount; j++) {
                int subTable = lookup + u16(table, lookup + 6 + j * 2);
                int subTableType = type;

                if (type == LOOKUP_EXTENSION) {
                    subTableType = u16(table, subTable + 2);
                    subTable += table.getInt(subTable + 4);
                }

                int format = u16(table, subTable);
                if (subTableType == LOOKUP_PAIR_ADJUSTMENT && (format == 1 || format == 2)) {
                    subTables.add(subTable);
                }
            }

            if (!subTables.isEmpty()) {
                lookups.add(subTables.stream().mapToInt(Integer::intValue).toArray());
            }
        }

        return lookups.isEmpty() ? null : new GposPairKerning(table, lookups);
    }

    /**
     * The kerning between two glyphs drawn next to each other, in font units;
     * zero where the table is malformed.
     */
    int getKerning(int left, int right) {
        try {
            return kerning(left, right);
        } catch (IndexOutOfBoundsException e) {
            return 0;
        }
    }

    private int kerning(int left, int right) {
        int kerning = 0;

        for (int[] subTables : _lookups) {
            for (int subTable : subTables) {
                Integer value = u16(_table, subTable) == 1 ?
                        pairFormat1(subTable, left, right) :
                        pairFormat2(subTable, left, right);

                if (value != null) {
                    kerning += value;
                    break;
                }
            }
        }

        return kerning;
    }

    /**
     * Individual pairs: the value, or null when the pair is not in the subtable.
     */
    private Integer pairFormat1(int subTable, int left, int right) {
        int coverageIndex = coverageIndex(subTable + u16(_table, subTable + 2), left);
        if (coverageIndex < 0 || coverageIndex >= u16(_table, subTable + 8)) {
            return null;
        }

        int valueFormat1 = u16(_table, subTable + 4);
        int valueFormat2 = u16(_table, subTable + 6);
        int recordSize = 2 + valueRecordSize(valueFormat1) + valueRecordSize(valueFormat2);

        int pairSet = subTable + u16(_table, subTable + 10 + coverageIndex * 2);
        int low = 0;
        int high = u16(_table, pairSet) - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int record = pairSet + 2 + mid * recordSize;
            int glyph = u16(_table, record);

            if (glyph < right) {
                low = mid + 1;
            } else if (glyph > right) {
                high = mid - 1;
            } else {
                return xAdvance(record + 2, valueFormat1);
            }
        }

        return null;
    }

    /**
     * Class pairs: the value, or null when the first glyph is not covered.
     */
    private Integer pairFormat2(int subTable, int left, int right) {
        if (coverageIndex(subTable + u16(_table, subTable + 2), left) < 0) {
            return null;
        }

        int valueFormat1 = u16(_table, subTable + 4);
        int valueFormat2 = u16(_table, subTable + 6);
        int class1 = glyphClass(subTable + u16(_table, subTable + 8), left);
        int class2 = glyphClass(subTable + u16(_table, subTable + 10), right);
        int class1Count = u16(_table, subTable + 12);
        int class2Count = u16(_table, subTable + 14);

        if (class1 >= class1Count || class2 >= class2Count) {
            return null;
        }

        int recordSize = valueRecordSize(valueFormat1) + valueRecordSize(valueFormat2);
        return xAdvance(subTable + 16 + (class1 * class2Count + class2) * recordSize, valueFormat1);
    }

    private int xAdvance(int valueRecord, int valueFormat) {
        if ((valueFormat & VALUE_X_ADVANCE) == 0) {
            return 0;
        }

        // XAdvance follows XPlacement and YPlacement, where present.
        return _table.getShort(valueRecord + 2 * Integer.bitCount(valueFormat & 0x0003));
    }

    private static int valueRecordSize(int valueFormat) {
        return 2 * Integer.bitCount(valueFormat & 0x00FF);
    }

    private int coverageIndex(int coverage, int glyph) {
        int format = u16(_table, coverage);
        int count = u16(_table, coverage + 2);
        int low = 0;
        int high = count - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;

            if (format == 1) {
                int found = u16(_table, coverage + 4 + mid * 2);
                if (found < glyph) {
                    low = mid + 1;
                } else if (found > glyph) {
                    high = mid - 1;
                } else {
                    return mid;
                }
            } else if (format == 2) {
                int range = coverage + 4 + mid * 6;
                if (u16(_table, range + 2) < glyph) {
                    low = mid + 1;
                } else if (u16(_table, range) > glyph) {
                    high = mid - 1;
                } else {
                    return u16(_table, range + 4) + glyph - u16(_table, range);
                }
            } else {
                return -1;
            }
        }

        return -1;
    }

    private int glyphClass(int classDef, int glyph) {
        int format = u16(_table, classDef);

        if (format == 1) {
            int start = u16(_table, classDef + 2);
            int count = u16(_table, classDef + 4);
            return glyph >= start && glyph < start + count ? u16(_table, classDef + 6 + (glyph - start) * 2) : 0;
        } else if (format == 2) {
            int low = 0;
            int high = u16(_table, classDef + 2) - 1;

            while (low <= high) {
                int mid = (low + high) >>> 1;
                int range = classDef + 4 + mid * 6;
                if (u16(_table, range + 2) < glyph) {
                    low = mid + 1;
                } else if (u16(_table, range) > glyph) {
                    high = mid - 1;
                } else {
                    return u16(_table, range + 4);
                }
            }
        }

        return 0;
    }

    private static int u16(ByteBuffer table, int offset) {
        return table.getShort(offset) & 0xFFFF;
    }

    private static String tag(ByteBuffer table, int offset) {
        return new String(new char[] {
                (char) table.get(offset), (char) table.get(offset + 1),
                (char) table.get(offset + 2), (char) table.get(offset + 3) });
    }
}
