package com.openhtmltopdf.nonvisualregressiontests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.apache.pdfbox.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.outputdevice.helper.FontCache;

/**
 * Tests {@link FontCache} with real true type fonts. Every {@code Font.createFont} call registers
 * a font with the JDK font manager that is never released, see
 * <a href="https://bugs.openjdk.org/browse/JDK-8239833">JDK-8239833</a>, so repeated renders of
 * the same document have to end up with the very same font instance.
 */
public class FontCacheNonVisualTest {
    private static final String KARLA = "/visualtest/html/fonts/Karla-Bold.ttf";
    private static final String SOURCE_SANS = "/visualtest/html/fonts/SourceSansPro-Regular.ttf";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void emptyCache() {
        FontCache.invalidateAll();
    }

    private static byte[] font(String resource) throws IOException {
        try (InputStream is = FontCacheNonVisualTest.class.getResourceAsStream(resource)) {
            return IOUtils.toByteArray(is);
        }
    }

    private File fontFile(String name, String resource) throws IOException {
        File file = folder.newFile(name);
        Files.write(file.toPath(), font(resource));
        return file;
    }

    @Test
    public void testBytesAreKeyedOnTheirContent() throws IOException, FontFormatException {
        byte[] karla = font(KARLA);

        Font first = FontCache.getTrueTypeFont(karla);
        Font second = FontCache.getTrueTypeFont(karla.clone());

        assertSame(first, second);
        assertNotSame(first, FontCache.getTrueTypeFont(font(SOURCE_SANS)));
    }

    @Test
    public void testFileIsKeyedOnItsPath() throws IOException, FontFormatException {
        File file = fontFile("font.ttf", KARLA);

        Font first = FontCache.getTrueTypeFont(file);
        Font second = FontCache.getTrueTypeFont(new File(file.getAbsolutePath()));

        assertSame(first, second);
        assertEquals("Karla", first.getFamily());
    }

    /**
     * A font file that is replaced on disk has to be picked up again, otherwise a long running
     * application would serve the old font forever.
     */
    @Test
    public void testChangedFileIsReloaded() throws IOException, FontFormatException {
        File file = fontFile("font.ttf", KARLA);

        Font first = FontCache.getTrueTypeFont(file);
        Files.write(file.toPath(), font(SOURCE_SANS));
        Font second = FontCache.getTrueTypeFont(file);

        assertNotSame(first, second);
        assertEquals("Karla", first.getFamily());
        assertEquals("Source Sans Pro", second.getFamily());
    }

    @Test
    public void testStreamIsKeyedOnSupplierCacheKey() throws IOException, FontFormatException {
        class KarlaSupplier implements FSSupplier<InputStream> {
            private int supplied;

            @Override
            public InputStream supply() {
                supplied++;
                try {
                    return new ByteArrayInputStream(font(KARLA));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public String cacheKey() {
                return "http://example.com/Karla-Bold.ttf";
            }
        }

        KarlaSupplier supplier = new KarlaSupplier();

        assertSame(FontCache.getTrueTypeFont(supplier), FontCache.getTrueTypeFont(supplier));
        assertEquals(1, supplier.supplied);
    }

    /**
     * Fonts from a supplier that can not identify itself - a user provided lambda, for example -
     * still have to work, they just can not be cached.
     */
    @Test
    public void testStreamWithoutCacheKeyIsNotCached() throws IOException, FontFormatException {
        byte[] karla = font(KARLA);
        FSSupplier<InputStream> supplier = () -> new ByteArrayInputStream(karla);

        assertNull(supplier.cacheKey());
        assertNotSame(FontCache.getTrueTypeFont(supplier), FontCache.getTrueTypeFont(supplier));
    }

    @Test
    public void testMissingFontIsNotSupplied() throws IOException, FontFormatException {
        FSSupplier<InputStream> supplier = new FSSupplier<InputStream>() {
            @Override
            public InputStream supply() {
                return null;
            }

            @Override
            public String cacheKey() {
                return "http://example.com/missing.ttf";
            }
        };

        assertNull(FontCache.getTrueTypeFont(supplier));
    }
}
