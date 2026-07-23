package com.openhtmltopdf.outputdevice.helper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.layout.SharedContext;
import com.openhtmltopdf.util.LogMessageId;
import com.openhtmltopdf.util.XRLog;

public class FontFaceFontSupplier implements FSSupplier<InputStream> {
    private final String src;
    private final SharedContext ctx;
    
    public FontFaceFontSupplier(SharedContext ctx, String src) {
        this.src = src;
        this.ctx = ctx;
    }
    
    @Override
    public InputStream supply() {
        byte[] font1 = ctx.getUserAgentCallback().getBinaryResource(src, ExternalResourceType.FONT);
        
        if (font1 == null) {
            XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_COULD_NOT_LOAD_FONT_FACE, src);
            return null;
        }
        
        return new ByteArrayInputStream(font1);
    }

    /**
     * The URI of the font resolved against the base URI of the document, so that the same
     * relative src - {@code src: url(font.ttf)} - in two documents does not share a cache entry.
     */
    @Override
    public String cacheKey() {
        String resolved = ctx.getUserAgentCallback().resolveURI(src);

        if (resolved == null) {
            // A relative URI without a base URI, so we can not tell which font this is.
            return null;
        }

        // A data URI carries the font itself, so key on its content instead of holding on to a
        // string that can be megabytes long.
        return resolved.startsWith("data:") ?
                FontCache.contentCacheKey(resolved.getBytes(StandardCharsets.UTF_8)) :
                resolved;
    }
}
