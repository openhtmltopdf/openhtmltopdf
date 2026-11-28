package com.openhtmltopdf.css.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.openhtmltopdf.css.parser.property.Conversions;

/**
 * color-mix(in COLORSPACE, COLOR [P%], COLOR [P%]) of CSS Color 5, in the srgb,
 * srgb-linear, oklab and oklch color spaces. Colors are mixed premultiplied by
 * their alpha; percentages that add up to less than 100% make the result
 * transparent by that much.
 */
public class ColorMix {
    private ColorMix() {
    }

    /**
     * @param params the arguments of color-mix(), as the parser gives them.
     * @return the mixed color.
     * @throws CSSParseException if the arguments are not valid.
     */
    public static FSRGBColor mix(List<PropertyValue> params, int line) {
        // Split into the comma separated groups.
        List<List<PropertyValue>> groups = new ArrayList<>();
        for (PropertyValue p : params) {
            if (groups.isEmpty() || p.getOperator() == Token.TK_COMMA) {
                groups.add(new ArrayList<>());
            }
            groups.get(groups.size() - 1).add(p);
        }

        if (groups.size() != 3 || groups.get(0).size() < 2 ||
            !"in".equalsIgnoreCase(groups.get(0).get(0).getStringValue())) {
            throw new CSSParseException("color-mix() takes 'in' a color space and two colors", line);
        }

        String space = groups.get(0).get(1).getStringValue().toLowerCase(Locale.ROOT);
        if (!space.equals("srgb") && !space.equals("srgb-linear") && !space.equals("oklab") && !space.equals("oklch")) {
            throw new CSSParseException("Unsupported color space for color-mix(): " + space, line);
        }

        FSRGBColor c1 = color(groups.get(1), line);
        FSRGBColor c2 = color(groups.get(2), line);
        Float p1 = percentage(groups.get(1), line);
        Float p2 = percentage(groups.get(2), line);

        // Normalize the percentages (CSS Color 5, 2.1).
        if (p1 == null && p2 == null) {
            p1 = 50f;
            p2 = 50f;
        } else if (p1 == null) {
            p1 = 100 - p2;
        } else if (p2 == null) {
            p2 = 100 - p1;
        }
        float sum = p1 + p2;
        if (sum <= 0) {
            throw new CSSParseException("The percentages of color-mix() add up to zero", line);
        }
        float alphaMultiplier = Math.min(sum, 100) / 100;
        float t = p2 / sum;

        double[] a = toSpace(c1, space);
        double[] b = toSpace(c2, space);
        double alpha1 = c1.getAlpha();
        double alpha2 = c2.getAlpha();
        double alpha = alpha1 * (1 - t) + alpha2 * t;

        double[] mixed = new double[3];
        if (space.equals("oklch")) {
            // Lightness and chroma premultiplied; hue the shorter way round.
            mixed[0] = premultiplied(a[0], alpha1, b[0], alpha2, t, alpha);
            mixed[1] = premultiplied(a[1], alpha1, b[1], alpha2, t, alpha);
            double h1 = a[2];
            double h2 = b[2];
            if (h2 - h1 > 180) {
                h1 += 360;
            } else if (h2 - h1 < -180) {
                h2 += 360;
            }
            mixed[2] = (h1 * (1 - t) + h2 * t) % 360;
        } else {
            for (int i = 0; i < 3; i++) {
                mixed[i] = premultiplied(a[i], alpha1, b[i], alpha2, t, alpha);
            }
        }

        int[] rgb = fromSpace(mixed, space);
        return new FSRGBColor(rgb[0], rgb[1], rgb[2], (float) Math.max(0, Math.min(1, alpha * alphaMultiplier)));
    }

    private static double premultiplied(double v1, double a1, double v2, double a2, double t, double alpha) {
        if (alpha == 0) {
            return v1 * (1 - t) + v2 * t;
        }
        return (v1 * a1 * (1 - t) + v2 * a2 * t) / alpha;
    }

    private static FSRGBColor color(List<PropertyValue> group, int line) {
        for (PropertyValue p : group) {
            if (p.getPrimitiveType() == CSSPrimitiveValue.CSS_RGBCOLOR && p.getFSColor() instanceof FSRGBColor) {
                return (FSRGBColor) p.getFSColor();
            }
            if (p.getPrimitiveType() == CSSPrimitiveValue.CSS_IDENT) {
                if ("transparent".equalsIgnoreCase(p.getStringValue())) {
                    return FSRGBColor.TRANSPARENT;
                }
                FSRGBColor named = Conversions.getColor(p.getStringValue());
                if (named != null) {
                    return named;
                }
            }
        }
        throw new CSSParseException("color-mix() needs a color in each of its last two arguments", line);
    }

    private static Float percentage(List<PropertyValue> group, int line) {
        for (PropertyValue p : group) {
            if (p.getPrimitiveType() == CSSPrimitiveValue.CSS_PERCENTAGE) {
                float value = p.getFloatValue();
                if (value < 0 || value > 100) {
                    throw new CSSParseException("Percentages in color-mix() must be from 0% to 100%", line);
                }
                return value;
            }
        }
        return null;
    }

    private static double toLinear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double fromLinear(double c) {
        return c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
    }

    private static double[] toSpace(FSRGBColor c, String space) {
        double r = c.getRed() / 255.0;
        double g = c.getGreen() / 255.0;
        double b = c.getBlue() / 255.0;
        if (space.equals("srgb")) {
            return new double[] { r, g, b };
        }
        double lr = toLinear(r);
        double lg = toLinear(g);
        double lb = toLinear(b);
        if (space.equals("srgb-linear")) {
            return new double[] { lr, lg, lb };
        }
        // Linear sRGB to OKLab (Björn Ottosson).
        double l = Math.cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb);
        double m = Math.cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb);
        double s = Math.cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb);
        double L = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s;
        double A = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s;
        double B = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s;
        if (space.equals("oklab")) {
            return new double[] { L, A, B };
        }
        double chroma = Math.hypot(A, B);
        double hue = Math.toDegrees(Math.atan2(B, A));
        if (hue < 0) {
            hue += 360;
        }
        return new double[] { L, chroma, hue };
    }

    private static int[] fromSpace(double[] v, String space) {
        double r;
        double g;
        double b;
        if (space.equals("srgb")) {
            r = v[0];
            g = v[1];
            b = v[2];
        } else {
            double lr;
            double lg;
            double lb;
            if (space.equals("srgb-linear")) {
                lr = v[0];
                lg = v[1];
                lb = v[2];
            } else {
                double L = v[0];
                double A = v[1];
                double B = v[2];
                if (space.equals("oklch")) {
                    double h = Math.toRadians(v[2]);
                    A = v[1] * Math.cos(h);
                    B = v[1] * Math.sin(h);
                }
                double l = Math.pow(L + 0.3963377774 * A + 0.2158037573 * B, 3);
                double m = Math.pow(L - 0.1055613458 * A - 0.0638541728 * B, 3);
                double s = Math.pow(L - 0.0894841775 * A - 1.2914855480 * B, 3);
                lr = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
                lg = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
                lb = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s;
            }
            r = fromLinear(lr);
            g = fromLinear(lg);
            b = fromLinear(lb);
        }
        return new int[] { channel(r), channel(g), channel(b) };
    }

    private static int channel(double v) {
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }
}
