package com.openhtmltopdf.outputdevice.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.Font;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

import com.openhtmltopdf.extend.FSSupplier;

/**
 * Tests the caching behavior of {@link FontCache}. Uses logical fonts rather than fonts created
 * with {@code Font.createFont} as those would be leaked by the JDK, which is what this cache is
 * there to prevent in the first place.
 */
public class FontCacheTest {
    private final AtomicInteger created = new AtomicInteger();

    private final FSSupplier<Font> supplier = () -> {
        created.incrementAndGet();
        return new Font(Font.SANS_SERIF, Font.PLAIN, 1);
    };

    @Before
    public void emptyCache() {
        FontCache.invalidateAll();
    }

    @Test
    public void testFontIsCreatedOncePerKey() {
        Font first = FontCache.get("font-1", 0, supplier);
        Font second = FontCache.get("font-1", 0, supplier);

        assertSame(first, second);
        assertEquals(1, created.get());
    }

    @Test
    public void testDifferentKeysGetDifferentFonts() {
        Font first = FontCache.get("font-1", 0, supplier);
        Font second = FontCache.get("font-2", 0, supplier);

        assertNotSame(first, second);
        assertEquals(2, created.get());
    }

    @Test
    public void testFontWithoutKeyIsNotCached() {
        Font first = FontCache.get(null, 0, supplier);
        Font second = FontCache.get(null, 0, supplier);

        assertNotSame(first, second);
        assertEquals(2, created.get());
        assertEquals(0, FontCache.size());
    }

    @Test
    public void testFontIsRecreatedWhenResourceChanged() {
        Font first = FontCache.get("font-1", 1000, supplier);
        Font second = FontCache.get("font-1", 1000, supplier);
        Font third = FontCache.get("font-1", 2000, supplier);

        assertSame(first, second);
        assertNotSame(second, third);
        assertEquals(2, created.get());

        // The stale font is replaced rather than kept beside the new one.
        assertEquals(1, FontCache.size());
    }

    @Test
    public void testFailureToCreateFontIsNotCached() {
        Font first = FontCache.get("font-1", 0, () -> null);

        assertNull(first);
        assertEquals(0, FontCache.size());

        // So that a font that was temporarily unavailable can still be loaded later.
        assertSame(FontCache.get("font-1", 0, supplier), FontCache.get("font-1", 0, supplier));
        assertEquals(1, created.get());
    }

    @Test
    public void testInvalidateAllDropsCachedFonts() {
        Font first = FontCache.get("font-1", 0, supplier);
        FontCache.invalidateAll();
        Font second = FontCache.get("font-1", 0, supplier);

        assertNotSame(first, second);
        assertEquals(2, created.get());
    }

    @Test
    public void testKeyAndLastModifiedAreTakenFromSupplier() {
        class KeyedSupplier implements FSSupplier<Font> {
            private long lastModified;

            @Override
            public Font supply() {
                return supplier.supply();
            }

            @Override
            public String cacheKey() {
                return "font-1";
            }

            @Override
            public long lastModified() {
                return lastModified;
            }
        }

        KeyedSupplier keyed = new KeyedSupplier();

        Font first = FontCache.get(keyed);
        Font second = FontCache.get(keyed);

        assertSame(first, second);
        assertEquals(1, created.get());

        keyed.lastModified = 1000;

        assertNotSame(second, FontCache.get(keyed));
        assertEquals(2, created.get());
    }

    @Test
    public void testSupplierIsNotCacheableByDefault() {
        FSSupplier<InputStream> plain = () -> null;

        assertNull(plain.cacheKey());
        assertEquals(FSSupplier.UNKNOWN_LAST_MODIFIED, plain.lastModified());
    }

    @Test
    public void testContentCacheKeyFollowsContent() {
        byte[] content = "a font".getBytes(StandardCharsets.UTF_8);

        assertEquals(FontCache.contentCacheKey(content), FontCache.contentCacheKey(content.clone()));
        assertNotEquals(FontCache.contentCacheKey(content),
                FontCache.contentCacheKey("another font".getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * The point of the cache: concurrent renders of the same document must not each register
     * their own copy of the font with the JDK.
     */
    @Test
    public void testFontIsCreatedOnceWhenThreadsRace() throws InterruptedException {
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        try {
            for (int i = 0; i < threads; i++) {
                executor.execute(() -> {
                    try {
                        start.await();
                        FontCache.get("font-1", 0, supplier);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, created.get());
    }
}
