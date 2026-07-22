package com.openhtmltopdf.pdfboxout.fontstore;

import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.layout.SharedContext;
import com.openhtmltopdf.outputdevice.helper.FontFamily;
import com.openhtmltopdf.outputdevice.helper.FontResolverHelper;
import com.openhtmltopdf.pdfboxout.PdfBoxFontResolver;
import com.openhtmltopdf.pdfboxout.PdfBoxFontResolver.FontDescription;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.util.Map;
import java.util.TreeMap;

public abstract class AbstractFontStore {
    public abstract FontDescription resolveFont(
            SharedContext ctx,
            String fontFamily,
            float size,
            IdentValue weight,
            IdentValue style,
            IdentValue variant);

    public static class EmptyFontStore extends AbstractFontStore {
        @Override
        public FontDescription resolveFont(
                SharedContext ctx, String fontFamily, float size, IdentValue weight,
                IdentValue style, IdentValue variant) {
            return null;
        }
    }

    public static class BuiltinFontStore extends AbstractFontStore {
        // Must be per instance (ie. per document): the PDType1Font objects and
        // their COS dictionaries end up in every document that uses them, and
        // PDFBox (>= 3.0.4) caches object keys on the COS objects while saving,
        // so a COS object shared between documents is written into the second
        // document as a dangling reference ("found wrong object number"
        // warnings, empty font dictionaries on read-back).
        final Map<String, FontFamily<PdfBoxFontResolver.FontDescription>> _fontFamilies = createInitialFontMap();

        public BuiltinFontStore() {
        }

        static Map<String, FontFamily<PdfBoxFontResolver.FontDescription>> createInitialFontMap() {
            // Family-name lookup is case-insensitive per the CSS spec (CSS Fonts Level 3,
            // section 5.1: https://www.w3.org/TR/css-fonts-3/#font-family-casing).
            Map<String, FontFamily<PdfBoxFontResolver.FontDescription>> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            addCourier(result);
            addTimes(result);
            addHelvetica(result);
            addSymbol(result);
            addZapfDingbats(result);

            return result;
        }

        static void addCourier(Map<String, FontFamily<PdfBoxFontResolver.FontDescription>> result) {
            FontFamily<PdfBoxFontResolver.FontDescription> courier = new FontFamily<>("Courier");

            courier.addFontDescription(new PdfBoxFontResolver.FontDescription(new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD_OBLIQUE), IdentValue.OBLIQUE, 700));
            courier.addFontDescription(new PdfBoxFontResolver.FontDescription(new PDType1Font(Standard14Fonts.FontName.COURIER_OBLIQUE), IdentValue.OBLIQUE, 400));
            courier.addFontDescription(new PdfBoxFontResolver.FontDescription(new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD), IdentValue.NORMAL, 700));
            courier.addFontDescription(new PdfBoxFontResolver.FontDescription(new PDType1Font(Standard14Fonts.FontName.COURIER), IdentValue.NORMAL, 400));

            result.put("DialogInput", courier);
            result.put("Monospaced", courier);
            result.put("Courier", courier);
        }

        static void addTimes(Map<String, FontFamily<PdfBoxFontResolver.FontDescription>> result) {
            FontFamily<PdfBoxFontResolver.FontDescription> times = new FontFamily<>("Times");

            times.addFontDescription(new PdfBoxFontResolver.FontDescription(new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD_ITALIC), IdentValue.ITALIC, 700));
            times.addFontDescription(new PdfBoxFontResolver.FontDescription(new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC), IdentValue.ITALIC, 400));
            times.addFontDescription(new PdfBoxFontResolver.FontDescription(new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD), IdentValue.NORMAL, 700));
            times.addFontDescription(new PdfBoxFontResolver.FontDescription(new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN), IdentValue.NORMAL, 400));

            result.put("Serif", times);
            result.put("TimesRoman", times);
        }

        static void addHelvetica(Map<String, FontFamily<PdfBoxFontResolver.FontDescription>> result) {
            FontFamily<PdfBoxFontResolver.FontDescription> helvetica = new FontFamily<>("Helvetica");

            helvetica.addFontDescription(new PdfBoxFontResolver.FontDescription(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE), IdentValue.OBLIQUE, 700));
            helvetica.addFontDescription(new PdfBoxFontResolver.FontDescription(new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), IdentValue.OBLIQUE, 400));
            helvetica.addFontDescription(new PdfBoxFontResolver.FontDescription(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), IdentValue.NORMAL, 700));
            helvetica.addFontDescription(new PdfBoxFontResolver.FontDescription(new PDType1Font(Standard14Fonts.FontName.HELVETICA), IdentValue.NORMAL, 400));

            result.put("Dialog", helvetica);
            result.put("SansSerif", helvetica);
            result.put("Helvetica", helvetica);
        }

        static void addSymbol(Map<String, FontFamily<PdfBoxFontResolver.FontDescription>> result) {
            FontFamily<PdfBoxFontResolver.FontDescription> fontFamily = new FontFamily<>("Symbol");

            fontFamily.addFontDescription(new PdfBoxFontResolver.FontDescription(new PDType1Font(Standard14Fonts.FontName.SYMBOL), IdentValue.NORMAL, 400));

            result.put("Symbol", fontFamily);
        }

        static void addZapfDingbats(Map<String, FontFamily<PdfBoxFontResolver.FontDescription>> result) {
            FontFamily<PdfBoxFontResolver.FontDescription> fontFamily = new FontFamily<>("ZapfDingbats");

            fontFamily.addFontDescription(new PdfBoxFontResolver.FontDescription(new PDType1Font(Standard14Fonts.FontName.ZAPF_DINGBATS), IdentValue.NORMAL, 400));

            result.put("ZapfDingbats", fontFamily);
        }

        @Override
        public PdfBoxFontResolver.FontDescription resolveFont(
                SharedContext ctx,
                String fontFamily,
                float size,
                IdentValue weight,
                IdentValue style,
                IdentValue variant) {

            String normalizedFontFamily = FontUtil.normalizeFontFamily(fontFamily);
            FontFamily<PdfBoxFontResolver.FontDescription> family = _fontFamilies.get(normalizedFontFamily);

            if (family != null) {
                return family.match(FontResolverHelper.convertWeightToInt(weight), style);
            }

            return null;
        }
    }
}
