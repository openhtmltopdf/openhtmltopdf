package com.openhtmltopdf.svgsupport;

import java.awt.FontFormatException;
import java.awt.font.TextAttribute;
import java.io.File;
import java.io.IOException;
import java.text.AttributedCharacterIterator;
import java.util.Map;

import org.apache.batik.gvt.font.GVTFont;
import org.apache.batik.gvt.font.GVTFontFace;
import org.apache.batik.gvt.font.GVTFontFamily;

import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.outputdevice.helper.FontFamily;
import com.openhtmltopdf.outputdevice.helper.MinimalFontDescription;

/**
 * The faces of one font family available to SVG text. Faces are kept by their CSS
 * weight and style and chosen with {@link FontFamily#match(int, IdentValue)}, the same
 * matching HTML text uses, so SVG text and HTML text get the same face.
 */
public class OpenHtmlGvtFontFamily implements GVTFontFamily {

    /**
     * The TextAttribute weight Batik asks for, per CSS weight 100 to 900, as in
     * org.apache.batik.bridge.TextUtilities.convertFontWeight.
     */
    private static final float[] BATIK_WEIGHTS = {
        TextAttribute.WEIGHT_EXTRA_LIGHT,
        TextAttribute.WEIGHT_LIGHT,
        TextAttribute.WEIGHT_DEMILIGHT,
        TextAttribute.WEIGHT_REGULAR,
        TextAttribute.WEIGHT_SEMIBOLD,
        TextAttribute.WEIGHT_MEDIUM,
        TextAttribute.WEIGHT_BOLD,
        TextAttribute.WEIGHT_HEAVY,
        TextAttribute.WEIGHT_ULTRABOLD
    };

    private static class Face implements MinimalFontDescription {
        final int weight;
        final IdentValue style;
        final GVTFont font;
        final float size;

        Face(int weight, IdentValue style, GVTFont font, float size) {
            this.weight = weight;
            this.style = style;
            this.font = font;
            this.size = size;
        }

        @Override
        public int getWeight() {
            return weight;
        }

        @Override
        public IdentValue getStyle() {
            return style;
        }
    }

    private final FontFamily<Face> faces;
    private final String fontFamily;

    public OpenHtmlGvtFontFamily(String family) {
        this.fontFamily = family;
        this.faces = new FontFamily<>(family);
    }

    /**
     * @param cssWeight the CSS font weight, 100 to 900
     * @param style {@link IdentValue#NORMAL}, {@link IdentValue#ITALIC} or {@link IdentValue#OBLIQUE}
     */
    public void addFont(File file, float size, int cssWeight, IdentValue style) throws IOException, FontFormatException {
        GVTFont font = new OpenHtmlGvtFont(file, this, size, toBatikWeight(cssWeight), toBatikPosture(style));
        faces.addFontDescription(new Face(cssWeight, style, font, size));
    }

    /**
     * @param cssWeight the CSS font weight, 100 to 900
     * @param style {@link IdentValue#NORMAL}, {@link IdentValue#ITALIC} or {@link IdentValue#OBLIQUE}
     */
    public void addFont(byte[] bytes, float size, int cssWeight, IdentValue style) throws FontFormatException {
        GVTFont font = new OpenHtmlGvtFont(bytes, this, size, toBatikWeight(cssWeight), toBatikPosture(style));
        faces.addFontDescription(new Face(cssWeight, style, font, size));
    }

    /**
     * @deprecated Use {@link #addFont(File, float, int, IdentValue)}, which takes the CSS weight.
     */
    @Deprecated
    public void addFont(File file, float size, Float fontWeight, Float fontStyle) throws IOException, FontFormatException {
        addFont(file, size, toCssWeight(fontWeight), toCssStyle(fontStyle));
    }

    /**
     * @deprecated Use {@link #addFont(byte[], float, int, IdentValue)}, which takes the CSS weight.
     */
    @Deprecated
    public void addFont(byte[] bytes, float size, Float fontWeight, Float fontStyle) throws FontFormatException {
        addFont(bytes, size, toCssWeight(fontWeight), toCssStyle(fontStyle));
    }

	@Override
	public GVTFont deriveFont(float sz, AttributedCharacterIterator arg1) {
		return deriveFont(sz, arg1.getAttributes());
	}

    @Override
    public GVTFont deriveFont(float size, @SuppressWarnings("rawtypes") Map attrs) {
        Face face = faces.match(
                toCssWeight((Float) attrs.get(TextAttribute.WEIGHT)),
                toCssStyle((Float) attrs.get(TextAttribute.POSTURE)));

        if (face == null) {
            throw new IllegalStateException("Font family " + fontFamily + " has no faces");
        }

        return face.size == size ? face.font : face.font.deriveFont(size);
    }

    /**
     * The CSS weight Batik meant by a TextAttribute weight: the one it maps to that
     * value, or the nearest. 400 when there is none.
     */
    static int toCssWeight(Float batikWeight) {
        if (batikWeight == null) {
            return 400;
        }

        int best = 0;
        for (int i = 1; i < BATIK_WEIGHTS.length; i++) {
            if (Math.abs(BATIK_WEIGHTS[i] - batikWeight) < Math.abs(BATIK_WEIGHTS[best] - batikWeight)) {
                best = i;
            }
        }
        return (best + 1) * 100;
    }

    static float toBatikWeight(int cssWeight) {
        int index = Math.round(cssWeight / 100f) - 1;
        return BATIK_WEIGHTS[Math.max(0, Math.min(BATIK_WEIGHTS.length - 1, index))];
    }

    /**
     * Batik gives italic and oblique the same posture, so a slanted request asks for
     * italic, which {@link FontFamily#match(int, IdentValue)} lets fall back to oblique.
     */
    static IdentValue toCssStyle(Float batikPosture) {
        return batikPosture != null && batikPosture > 0f ? IdentValue.ITALIC : IdentValue.NORMAL;
    }

    private static Float toBatikPosture(IdentValue style) {
        return style == IdentValue.ITALIC || style == IdentValue.OBLIQUE
                ? TextAttribute.POSTURE_OBLIQUE
                : TextAttribute.POSTURE_REGULAR;
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
