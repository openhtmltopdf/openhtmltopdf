package com.openhtmltopdf.outputdevice.helper;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;

import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.util.OpenUtil;

/**
 * A process wide cache of AWT fonts.
 *
 * <p>Every call to {@link Font#createFont(int, InputStream)} and its siblings registers a font
 * with the JDK font manager that is never released again, see
 * <a href="https://bugs.openjdk.org/browse/JDK-8239833">JDK-8239833</a>. Creating the same font
 * over and over - which is what happens when documents using {@code @font-face} rules or
 * {@code useFont} are rendered repeatedly - therefore leaks memory (and temporary files) until
 * the JVM exits. Caching the created fonts avoids that.</p>
 *
 * <p>The cache is shared by all renderers and documents. Unlike for example PDFBox fonts, AWT
 * fonts are immutable and safe to use from more than one document.</p>
 */
public class FontCache {
    private static final ConcurrentHashMap<String, CachedFont> CACHE = new ConcurrentHashMap<>();

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private FontCache() {
    }

    /**
     * A font together with the last modified stamp of the resource it was created from.
     */
    private static class CachedFont {
        private final Font font;
        private final long lastModified;

        private CachedFont(Font font, long lastModified) {
            this.font = font;
            this.lastModified = lastModified;
        }
    }

    /**
     * Thrown out of a supplier so that the checked exceptions of {@link Font#createFont(int, File)}
     * can travel through {@link FSSupplier#supply()}.
     */
    private static class FontCreationException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private FontCreationException(Exception cause) {
            super(cause);
        }
    }

    /**
     * Returns the cached font for the given key, creating it with the supplier if it is not
     * cached yet or if the resource it was created from changed.
     *
     * @param cacheKey identifies the font resource, or null if the caller can not identify it,
     *                 in which case the font is created without being cached.
     * @param lastModified a value that changes whenever the font resource changes, or
     *                 {@link FSSupplier#UNKNOWN_LAST_MODIFIED} if that can not be determined, in
     *                 which case the font is cached until {@link #invalidateAll()} is called.
     * @return the font, or null if the supplier could not supply one.
     */
    public static Font get(String cacheKey, long lastModified, FSSupplier<Font> fontSupplier) {
        if (cacheKey == null) {
            return fontSupplier.supply();
        }

        // We use compute rather than computeIfAbsent so that we can also replace entries whose
        // resource changed. Either way the font is created while holding the lock for its key,
        // which is what stops concurrent renders from creating - and thus leaking - the same
        // font more than once.
        CachedFont cached = CACHE.compute(cacheKey, (key, existing) -> {
            if (existing != null && existing.lastModified == lastModified) {
                return existing;
            }

            Font font = fontSupplier.supply();

            // Returning null removes the mapping, so failures are not cached.
            return font != null ? new CachedFont(font, lastModified) : null;
        });

        return cached != null ? cached.font : null;
    }

    /**
     * Like {@link #get(String, long, FSSupplier)} with the key and last modified stamp taken from
     * the supplier itself.
     */
    public static Font get(FSSupplier<Font> fontSupplier) {
        return get(fontSupplier.cacheKey(), fontSupplier.lastModified(), fontSupplier);
    }

    /**
     * Returns the true type font in the given file, keyed on its path and reloaded when the file
     * changes. Does not handle true type collections.
     */
    public static Font getTrueTypeFont(File fontFile) throws IOException, FontFormatException {
        return getChecked("file:" + fontFile.getAbsolutePath(), lastModified(fontFile), () -> createFont(fontFile));
    }

    /**
     * Returns the true type font in the given bytes, keyed on a hash of the bytes themselves as
     * there is no name to key them on.
     */
    public static Font getTrueTypeFont(byte[] fontBytes) throws IOException, FontFormatException {
        return getChecked(contentCacheKey(fontBytes), FSSupplier.UNKNOWN_LAST_MODIFIED,
                () -> createFont(new ByteArrayInputStream(fontBytes)));
    }

    /**
     * Returns the true type font supplied by the given supplier, keyed on
     * {@link FSSupplier#cacheKey()}.
     */
    public static Font getTrueTypeFont(FSSupplier<InputStream> streamSupplier) throws IOException, FontFormatException {
        return getTrueTypeFont(streamSupplier.cacheKey(), streamSupplier.lastModified(), streamSupplier);
    }

    /**
     * Like {@link #getTrueTypeFont(FSSupplier)} but with an explicit key, so that callers can opt
     * out of caching by passing null.
     *
     * @return the font, or null if the supplier did not supply a stream.
     */
    public static Font getTrueTypeFont(String cacheKey, long lastModified, FSSupplier<InputStream> streamSupplier)
            throws IOException, FontFormatException {

        return getChecked(cacheKey, lastModified, () -> {
            InputStream is = streamSupplier.supply();

            if (is == null) {
                // The supplier has already logged why.
                return null;
            }

            try {
                return createFont(is);
            } finally {
                OpenUtil.closeQuietly(is);
            }
        });
    }

    /**
     * A cache key derived from the content itself, for fonts that are only available as bytes and
     * therefore have no stable name to be keyed on.
     */
    public static String contentCacheKey(byte[] content) {
        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Every Java implementation has to support SHA-256.
            throw new IllegalStateException(e);
        }

        byte[] hash = digest.digest(content);
        StringBuilder sb = new StringBuilder("sha-256:");

        for (byte b : hash) {
            sb.append(HEX[(b >> 4) & 0xf]).append(HEX[b & 0xf]);
        }

        return sb.toString();
    }

    /**
     * Removes every font from the cache, for example because font resources changed in a way this
     * cache can not see, such as a web font served under an unchanged URL.
     *
     * <p>Note that fonts already handed to the JDK font manager can not be released, so recreating
     * the same fonts after clearing the cache will increase memory usage.</p>
     */
    public static void invalidateAll() {
        CACHE.clear();
    }

    /**
     * The number of fonts currently cached. Visible for testing.
     */
    static int size() {
        return CACHE.size();
    }

    /**
     * The stamp used to detect changes to a font file. Mixes in the length so that a font replaced
     * within the resolution of the file system timestamp is picked up too.
     */
    private static long lastModified(File fontFile) {
        return (fontFile.lastModified() * 31) + fontFile.length();
    }

    private static Font getChecked(String cacheKey, long lastModified, FSSupplier<Font> fontSupplier)
            throws IOException, FontFormatException {

        try {
            return get(cacheKey, lastModified, fontSupplier);
        } catch (FontCreationException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw (FontFormatException) e.getCause();
        }
    }

    private static Font createFont(File fontFile) {
        try {
            return Font.createFont(Font.TRUETYPE_FONT, fontFile);
        } catch (IOException | FontFormatException e) {
            throw new FontCreationException(e);
        }
    }

    private static Font createFont(InputStream is) {
        try {
            return Font.createFont(Font.TRUETYPE_FONT, is);
        } catch (IOException | FontFormatException e) {
            throw new FontCreationException(e);
        }
    }
}
