package com.openhtmltopdf.svgsupport;

import java.awt.FontFormatException;
import java.awt.font.TextAttribute;
import java.io.File;
import java.io.IOException;
import java.text.AttributedCharacterIterator;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.batik.gvt.font.GVTFont;
import org.apache.batik.gvt.font.GVTFontFace;
import org.apache.batik.gvt.font.GVTFontFamily;

public class OpenHtmlGvtFontFamily implements GVTFontFamily {

    private static class FontDescriptor {
        Float style;
        Float weight;

        @Override
        public boolean equals(Object obj) {
            return (obj != null && obj.getClass() == this.getClass() &&
                    Objects.equals(((FontDescriptor) obj).style, this.style) &&
                    Objects.equals(((FontDescriptor) obj).weight, this.weight));
        }

        @Override
        public int hashCode() {
            return Objects.hash(style, weight);
        }
    }

    private static class FontEntry {
        GVTFont font;
        float size;
    }

	private final Map<FontDescriptor, FontEntry> fonts = new HashMap<>(1);
	private final String fontFamily;
	
	public OpenHtmlGvtFontFamily(String family) {
		this.fontFamily = family;
	}

    public void addFont(File file, float size, Float fontWeight, Float fontStyle) throws IOException, FontFormatException {
        FontDescriptor des = new FontDescriptor();
        des.style = fontStyle;
        des.weight = fontWeight;

        FontEntry entry = new FontEntry();
        entry.size = size;
        entry.font = new OpenHtmlGvtFont(file, this, size, fontWeight, fontStyle);

        fonts.put(des, entry);
    }

    public void addFont(byte[] bytes, float size, Float fontWeight, Float fontStyle) throws FontFormatException {
        FontDescriptor des = new FontDescriptor();
        des.style = fontStyle;
        des.weight = fontWeight;

        FontEntry entry = new FontEntry();
        entry.size = size;
        entry.font = new OpenHtmlGvtFont(bytes, this, size, fontWeight, fontStyle);

        fonts.put(des, entry);
    }

	@Override
	public GVTFont deriveFont(float sz, AttributedCharacterIterator arg1) {
		return deriveFont(sz, arg1.getAttributes());
	}

    @Override
    public GVTFont deriveFont(float size, @SuppressWarnings("rawtypes") Map attrs) {
        Float fontWeight = (Float) attrs.get(TextAttribute.WEIGHT);
        Float fontStyle = (Float) attrs.get(TextAttribute.POSTURE);

        FontDescriptor des = new FontDescriptor();
        des.weight = fontWeight;
        des.style = fontStyle;

        FontEntry fnt = fonts.get(des);

        if (fnt != null) {
            if (fnt.size == size) {
                // We have the exact size.
                return fnt.font;
            } else {
                // Derive a font with the correct size.
                return fnt.font.deriveFont(size);
            }
        } else {
            return nearest(fontWeight, fontStyle).font.deriveFont(size);
        }
    }

    private FontEntry nearest(Float fontWeight, Float fontStyle) {
        float requestedWeight = orDefault(fontWeight, TextAttribute.WEIGHT_REGULAR);
        boolean requestedUpright = orDefault(fontStyle, TextAttribute.POSTURE_REGULAR) == TextAttribute.POSTURE_REGULAR;

        return fonts.entrySet().stream()
                .min(Comparator
                        .comparing((Map.Entry<FontDescriptor, FontEntry> e) ->
                                isUpright(e.getKey()) != requestedUpright)
                        .thenComparingDouble(e -> Math.abs(weightOf(e.getKey()) - requestedWeight))
                        .thenComparingDouble(e -> weightOf(e.getKey())))
                .orElseThrow(() -> new IllegalStateException("Font family " + fontFamily + " has no faces"))
                .getValue();
    }

    private static boolean isUpright(FontDescriptor descriptor) {
        return orDefault(descriptor.style, TextAttribute.POSTURE_REGULAR) == TextAttribute.POSTURE_REGULAR;
    }

    private static float weightOf(FontDescriptor descriptor) {
        return orDefault(descriptor.weight, TextAttribute.WEIGHT_REGULAR);
    }

    private static float orDefault(Float value, float defaultValue) {
        return value != null ? value : defaultValue;
    }

	@Override
	public String getFamilyName() {
		return this.fontFamily;
	}

	@Override
	public GVTFontFace getFontFace() {
		return new GVTFontFace(this.fontFamily);
	}

	@Override
	public boolean isComplex() {
		return false;
	}

}
