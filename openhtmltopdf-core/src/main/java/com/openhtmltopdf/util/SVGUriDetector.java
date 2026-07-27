package com.openhtmltopdf.util;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Decides whether a resource is an SVG image, so that it can be handed to the
 * SVG drawer rather than to the raster image decoder.
 *
 * <p>SVG images arrive in a lot of shapes: as a linked <code>.svg</code> file, as a
 * base 64 encoded data URI and as a plain (percent encoded) data URI, the latter being
 * the form typically used for small inline images in CSS.</p>
 */
public class SVGUriDetector {
    private static final String SVG_MIME_TYPE = "image/svg+xml";
    private static final String SVG_EXTENSION = ".svg";
    private static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";

    /**
     * How much of the content we are prepared to look at when sniffing for an
     * <code>svg</code> root element. Enough for a comment, a doctype and a couple
     * of processing instructions to precede it.
     */
    private static final int SNIFF_LENGTH = 1024;

    private SVGUriDetector() {
    }

    /**
     * Returns true if the URI is a data URI carrying an SVG image, no matter whether
     * the content is base 64 encoded or percent encoded.
     */
    public static boolean isSvgDataUri(String uri) {
        if (uri == null || !uri.regionMatches(true, 0, "data:", 0, "data:".length())) {
            return false;
        }

        int comma = uri.indexOf(',');
        if (comma < 0) {
            return false;
        }

        // Everything between the scheme and the first comma is the media type plus
        // its parameters, ie. data:image/svg+xml;charset=utf-8;base64,....
        return uri.regionMatches(true, "data:".length(), SVG_MIME_TYPE, 0, SVG_MIME_TYPE.length());
    }

    /**
     * Returns true if the URI names an SVG image, either by media type (data URIs)
     * or by file extension. A query string or fragment is ignored, so that
     * <code>logo.svg?v=2</code> is recognized too.
     */
    public static boolean isSvgUri(String uri) {
        if (uri == null) {
            return false;
        }

        if (isSvgDataUri(uri)) {
            return true;
        }

        int end = uri.length();
        for (int i = 0; i < uri.length(); i++) {
            char c = uri.charAt(i);
            if (c == '?' || c == '#') {
                end = i;
                break;
            }
        }

        return uri.substring(0, end).toLowerCase(Locale.US).endsWith(SVG_EXTENSION);
    }

    /**
     * Returns true if the content looks like an SVG document. Used for resources whose
     * URI gives nothing away, such as an image served by a script.
     *
     * <p>Only XML that actually contains an <code>svg</code> element near the start is
     * accepted, so this can not mistake a raster image for an SVG: those never start
     * with a <code>&lt;</code>.</p>
     */
    public static boolean looksLikeSvgContent(byte[] content) {
        if (content == null || content.length == 0) {
            return false;
        }

        int start = 0;

        // Skip an UTF-8 byte order mark, which ImageIO would not see either.
        if (content.length >= 3 &&
            (content[0] & 0xFF) == 0xEF && (content[1] & 0xFF) == 0xBB && (content[2] & 0xFF) == 0xBF) {
            start = 3;
        }

        while (start < content.length && Character.isWhitespace((char) (content[start] & 0xFF))) {
            start++;
        }

        if (start >= content.length || content[start] != '<') {
            return false;
        }

        String head = new String(content, start,
                Math.min(SNIFF_LENGTH, content.length - start), StandardCharsets.UTF_8);

        // SVG is XML, so the element name is case sensitive. The namespace is checked
        // as well to catch documents that use a prefix other than the usual svg:.
        return head.contains("<svg") || head.contains(SVG_NAMESPACE);
    }
}
