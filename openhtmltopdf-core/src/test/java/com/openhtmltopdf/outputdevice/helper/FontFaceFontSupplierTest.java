package com.openhtmltopdf.outputdevice.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import com.openhtmltopdf.layout.SharedContext;
import com.openhtmltopdf.resource.ImageResource;
import com.openhtmltopdf.swing.NaiveUserAgent;

public class FontFaceFontSupplierTest {
    private static SharedContext context(String baseUri) {
        NaiveUserAgent uac = new NaiveUserAgent() {
            @Override
            public ImageResource getImageResource(String uri, ExternalResourceType type) {
                throw new UnsupportedOperationException();
            }
        };
        uac.setBaseURL(baseUri);

        SharedContext ctx = new SharedContext();
        ctx.setUserAgentCallback(uac);

        return ctx;
    }

    private static String cacheKey(String baseUri, String src) {
        return new FontFaceFontSupplier(context(baseUri), src).cacheKey();
    }

    /**
     * The same relative src in two documents usually refers to two different fonts.
     */
    @Test
    public void testRelativeSrcIsResolvedAgainstBaseUri() {
        String one = cacheKey("file:/documents/one/index.html", "font.ttf");
        String two = cacheKey("file:/documents/two/index.html", "font.ttf");

        assertEquals("file:/documents/one/font.ttf", one);
        assertEquals("file:/documents/two/font.ttf", two);
        assertNotEquals(one, two);
    }

    @Test
    public void testAbsoluteSrcIsUsedAsIs() {
        assertEquals("http://example.com/font.ttf",
                cacheKey("file:/documents/one/index.html", "http://example.com/font.ttf"));
    }

    /**
     * Without a base URI we can not tell which font a relative src refers to, so it must not be
     * cached at all.
     */
    @Test
    public void testUnresolvableSrcIsNotCacheable() {
        assertNull(cacheKey(null, "font.ttf"));
    }

    /**
     * A data URI already identifies the font by its content, but is far too long to hold on to.
     */
    @Test
    public void testDataSrcIsKeyedOnItsHash() {
        String src = "data:font/ttf;base64,AAEAAAALAIAAAwAwT1MvMg8SBfoAAAC8AAAAYGNtYXA";
        String key = cacheKey("file:/documents/one/index.html", src);

        assertEquals(FontCache.contentCacheKey(src.getBytes(StandardCharsets.UTF_8)), key);
        assertNotEquals(key, cacheKey("file:/documents/one/index.html", src + "XYZ"));
    }
}
