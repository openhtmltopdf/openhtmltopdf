package com.openhtmltopdf.util;

import static org.hamcrest.CoreMatchers.notNullValue;

import static org.hamcrest.core.IsEqual.equalTo;

import org.junit.Test;

import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.swing.NaiveUserAgent;

import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class DataUriTest {

    @Test
    public void testGetEmbeddedBase64Image() throws IOException {
        String onePixel = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAACXBIWXMAAC4jAAAuIwF4pT92AAAADElEQVQI12P4//8/AAX+Av7czFnnAAAAAElFTkSuQmCC";

        byte[] result = NaiveUserAgent.getEmbeddedBase64Image(onePixel);

        assertThat(result, notNullValue());
        assertThat(result.length, equalTo(90));
    }

    private String content(String dataUri) throws IOException {
        FSStream stream = new NaiveUserAgent.DataUriFactory().getUrl(dataUri);

        assertThat(stream, notNullValue());

        return new String(OpenUtil.readAll(stream.getStream()), StandardCharsets.UTF_8);
    }

    /**
     * The data of a data URI that is not base 64 encoded is percent encoded (RFC 2397), so it
     * has to be decoded. Inline SVG images rely on this: without it the markup delimiters and
     * a reference such as url(%23gradient) never turn back into characters.
     */
    @Test
    public void testPercentEncodedDataIsDecoded() throws IOException {
        assertThat(content("data:image/svg+xml,%3Csvg%3E%3C/svg%3E"), equalTo("<svg></svg>"));
        assertThat(content("data:text/plain,fill='url(%23gradient)'"), equalTo("fill='url(#gradient)'"));
    }

    /**
     * A percent sign that is not an escape is kept, rather than making the whole URI
     * unusable. Inline SVGs are full of them: width='100%'.
     */
    @Test
    public void testLonePercentSignIsKept() throws IOException {
        assertThat(content("data:text/plain,width='100%'"), equalTo("width='100%'"));
        assertThat(content("data:text/plain,100%25%20and%20100%"), equalTo("100% and 100%"));
        assertThat(content("data:text/plain,trailing%"), equalTo("trailing%"));
        assertThat(content("data:text/plain,%zz"), equalTo("%zz"));
    }

    /**
     * Decoding happens on bytes, so an escaped multi byte character survives, as does an
     * unescaped one next to it.
     */
    @Test
    public void testMultiByteCharacters() throws IOException {
        assertThat(content("data:text/plain,%E2%82%AC"), equalTo("€"));
        assertThat(content("data:text/plain,€%20😀"), equalTo("€ 😀"));
    }

    /**
     * A base 64 data URI is still decoded as base 64, not as percent encoded text.
     */
    @Test
    public void testBase64DataIsNotPercentDecoded() throws IOException {
        assertThat(content("data:text/plain;base64,JTIzJTIz"), equalTo("%23%23"));
    }
}
