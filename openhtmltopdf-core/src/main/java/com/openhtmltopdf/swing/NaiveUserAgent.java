/*
 * NaiveUserAgent.java
 * Copyright (c) 2004, 2005 Torbjoern Gannholm
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.openhtmltopdf.swing;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.logging.Level;
import java.util.regex.Pattern;

import com.openhtmltopdf.event.DocumentListener;
import com.openhtmltopdf.extend.FSImage;
import com.openhtmltopdf.extend.FSUriResolver;
import com.openhtmltopdf.extend.FSStreamFactory;
import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.extend.SVGDrawer;
import com.openhtmltopdf.extend.UserAgentCallback;
import com.openhtmltopdf.render.FSSVGImage;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceType;
import com.openhtmltopdf.resource.CSSResource;
import com.openhtmltopdf.resource.ImageResource;
import com.openhtmltopdf.resource.XMLResource;
import com.openhtmltopdf.util.LogMessageId;
import com.openhtmltopdf.util.OpenUtil;
import com.openhtmltopdf.util.XRLog;

/**
 * <p>NaiveUserAgent is a simple implementation of {@link UserAgentCallback} which places no restrictions on what
 * XML, CSS or images are loaded.</p>
 *
 * <p>The NaiveUserAgent has a simple per-run cache for images so that the same image is not embedded in a document
 * multiple times.</p>
 *
 * @author Torbjoern Gannholm
 */
public abstract class NaiveUserAgent implements UserAgentCallback, DocumentListener {

  /**
   * a (simple) cache
   * This is only useful for the one run.
   */
  protected final LinkedHashMap<String, ImageResource> _imageCache = new LinkedHashMap<>();
  protected final FSUriResolver DEFAULT_URI_RESOLVER = new DefaultUriResolver();

  protected final Map<ExternalResourceControlPriority, BiPredicate<String, ExternalResourceType>> _accessControllers =
      new EnumMap<>(ExternalResourceControlPriority.class);

  protected FSUriResolver _resolver = DEFAULT_URI_RESOLVER;
  protected String _baseUri;
  protected Map<String, FSStreamFactory> _protocolsStreamFactory = new HashMap<>();
  protected SVGDrawer _svgDrawer;

  public NaiveUserAgent() {
    FSStreamFactory factory = new DefaultHttpStreamFactory();
    this._protocolsStreamFactory.put("http", factory);
    this._protocolsStreamFactory.put("https", factory);
    this._protocolsStreamFactory.put("data", new DataUriFactory());
    this._protocolsStreamFactory.put("classpath", new ClassPathStreamFactory());
  }

  public static class ClassPathStream implements FSStream {

    private final InputStream strm;

    public ClassPathStream(InputStream strm) {
      this.strm = strm;
    }

    @Override
    public InputStream getStream() {
      return strm;
    }

    @Override
    public Reader getReader() {
      InputStream is = getStream();
      if (is == null) {
        return null;
      }
      return new InputStreamReader(is, StandardCharsets.UTF_8);
    }
  }

  public static class ClassPathStreamFactory implements FSStreamFactory {

    private final ClassLoader classLoader;

    public ClassPathStreamFactory() {
      this(null);
    }

    public ClassPathStreamFactory(ClassLoader classLoader) {
      this.classLoader = classLoader != null ? classLoader : getClassLoader();
    }

    @Override
    public FSStream getUrl(String uri) {
      InputStream is = uri != null ? getStream(uri) : null;
      return new ClassPathStream(is);
    }

    private String getPath(String uri) {
      URI fullUri;
      try {
        fullUri = new URI(uri);
      } catch (URISyntaxException e) {
        XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_MALFORMED_URL, uri, e);
        return null;
      }

      String path = fullUri.isOpaque() ? fullUri.getSchemeSpecificPart() : fullUri.getPath();
      if (path.startsWith("/")) {
        path = path.substring(1);
      }
      return path;
    }

    private InputStream getStream(String uri) {
      if (uri == null) {
        return null;
      }

      String path = getPath(uri);
      if (path == null) {
        return null;
      }

      InputStream is;
      if (classLoader != null) {
        is = classLoader.getResourceAsStream(path);
      }
      else {
        is = ClassLoader.getSystemResourceAsStream(path);
      }
      if (is == null) {
        XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_ITEM_AT_URI_NOT_FOUND, uri);
      }
      return is;
    }

    /**
     * Return the ClassLoader to use: typically the thread context ClassLoader, if available;
     * the ClassLoader that loaded this class will be used as fallback.
     *
     * @return the ClassLoader (only {@code null} if even the system ClassLoader isn't accessible)
     * @see Thread#getContextClassLoader()
     * @see ClassLoader#getSystemClassLoader()
     */
    private static ClassLoader getClassLoader() {
      ClassLoader cl = null;
      try {
        cl = Thread.currentThread().getContextClassLoader();
      }
      catch (Throwable ex) {
        // Cannot access thread context ClassLoader - falling back...
      }
      if (cl == null) {
        // No thread context class loader -> use class loader of this class.
        cl = ClassPathStream.class.getClassLoader();
        if (cl == null) {
          // getClassLoader() returning null indicates the bootstrap ClassLoader
          try {
            cl = ClassLoader.getSystemClassLoader();
          }
          catch (Throwable ex) {
            // Cannot access system ClassLoader - oh well, maybe the caller can live with null...
          }
        }
      }
      return cl;
    }
  }

  /**
   * Get the binary content of an embedded base 64 image.
   *
   * @param imageDataUri URI of the embedded image
   * @return The binary content
   */
  public static byte[] getEmbeddedBase64Image(String imageDataUri) {
    int b64Index = imageDataUri.indexOf("base64,");
    if (b64Index != -1) {
      String b64encoded = imageDataUri.substring(b64Index + "base64,".length());
      return DataUriFactory.fromBase64Encoded(b64encoded);
    } else {
      XRLog.log(Level.SEVERE, LogMessageId.LogMessageId0Param.LOAD_EMBEDDED_DATA_URI_MUST_BE_ENCODED_IN_BASE64);
    }
    return null;
  }

  public void setProtocolsStreamFactory(Map<String, FSStreamFactory> protocolsStreamFactory) {
    this._protocolsStreamFactory = protocolsStreamFactory;
  }

  public void setUriResolver(FSUriResolver resolver) {
    this._resolver = resolver;
  }

  /**
   * Sets the drawer used for SVG images that arrive through the image pipeline rather than
   * as a replaced element, ie. SVGs used as CSS images such as <code>background-image</code>.
   * Without one, such images can not be drawn.
   */
  public void setSVGDrawer(SVGDrawer svgDrawer) {
    this._svgDrawer = svgDrawer;
  }

  /**
   * Builds an image for SVG content, to be drawn by the SVG drawer rather than decoded as a
   * bitmap.
   *
   * @param uri the resolved URI, for logging
   * @param content the SVG document
   * @param dotsPerPixel dots per CSS pixel of the output
   *
   * @return the image, or null if there is no SVG drawer or the content could not be used.
   */
  protected FSImage buildSVGImage(String uri, byte[] content, double dotsPerPixel) {
    if (this._svgDrawer == null) {
      XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.LOAD_SVG_IMAGE_WITHOUT_SVG_DRAWER, uri);
      return null;
    }

    FSImage image = null;

    try (InputStream svgStream = new ByteArrayInputStream(content)) {
      XMLResource xml = XMLResource.load(svgStream);

      if (xml != null && xml.getDocument() != null) {
        image = FSSVGImage.create(
            xml.getDocument().getDocumentElement(), this._svgDrawer, dotsPerPixel, uri);
      }
    } catch (IOException | RuntimeException e) {
      // XMLResource::load throws an unchecked XRRuntimeException for malformed content.
      XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_CANT_READ_IMAGE_FILE_FOR_URI, uri, e);
      return null;
    }

    // A null image has already been reported, either by the drawer or by FSSVGImage.
    return image;
  }

  public FSUriResolver getDefaultUriResolver() {
    return DEFAULT_URI_RESOLVER;
  }

  /**
   * Empties the image cache entirely.
   */
  @Deprecated
  public void clearImageCache() {
    _imageCache.clear();
  }

  protected FSStreamFactory getProtocolFactory(String protocol) {
    return _protocolsStreamFactory.get(protocol);
  }

  protected boolean hasProtocolFactory(String protocol) {
    return _protocolsStreamFactory.containsKey(protocol);
  }

  protected String extractProtocol(String uri) throws URISyntaxException {
    int idxSeparator;
    if (uri != null && (idxSeparator = uri.indexOf(':')) > 0) {
      return uri.substring(0, idxSeparator);
    } else {
      throw new URISyntaxException(uri, "missing protocol for URI");
    }
  }

  /**
   * Gets a InputStream for the resource identified by a resolved URI.
   */
  protected InputStream openStream(String uri) {
    java.io.InputStream is = null;

    try {
      String protocol = extractProtocol(uri);

      if (hasProtocolFactory(protocol)) {
        return getProtocolFactory(protocol).getUrl(uri).getStream();
      } else {
        try {
          is = new URI(uri).toURL().openStream();
        } catch (java.net.MalformedURLException e) {
          XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_MALFORMED_URL, uri, e);
        } catch (java.io.FileNotFoundException e) {
          XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_ITEM_AT_URI_NOT_FOUND, uri, e);
        } catch (java.io.IOException e) {
          XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_IO_PROBLEM_FOR_URI, uri, e);
        }
      }
    } catch (URISyntaxException e1) {
      XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_MALFORMED_URL, uri, e1);
    }

    return is;
  }

  /**
   * Gets a reader for the identified resource by a resolved URI.
   */
  protected Reader openReader(String uri) {
    InputStream is = null;

    try {
      String protocol = extractProtocol(uri);

      if (hasProtocolFactory(protocol)) {
        return getProtocolFactory(protocol).getUrl(uri).getReader();
      } else {
        try {
          is = new URI(uri).toURL().openStream();
        } catch (java.net.MalformedURLException e) {
          XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_MALFORMED_URL, uri, e);
        } catch (java.io.FileNotFoundException e) {
          XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_ITEM_AT_URI_NOT_FOUND, uri, e);
        } catch (java.io.IOException e) {
          XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_IO_PROBLEM_FOR_URI, uri, e);
        }
      }
    } catch (URISyntaxException e1) {
      XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_MALFORMED_URL, uri, e1);
    }

    return is == null ? null : new InputStreamReader(is, StandardCharsets.UTF_8);
  }

  protected String readAll(Reader reader) throws IOException {
    return OpenUtil.readAll(reader);
  }

  /**
   * Retrieves the CSS located at the given URI.  It's assumed the URI does point to a CSS file--the URI will
   * be resolved, accessed (using the set FSStreamFactory or URL::openStream), opened, read and then passed into the CSS parser.
   * The result is packed up into an CSSResource for later consumption.
   *
   * @param uri Location of the CSS source.
   * @return A CSSResource containing the CSS reader or null if not available.
   */
  @SuppressWarnings("resource")
  @Override
  public CSSResource getCSSResource(String uri, ExternalResourceType type) {
    if (!checkAccessAllowed(uri, type, ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI)) {
      return null;
    }

    String resolved = _resolver.resolveURI(this._baseUri, uri);
    if (!checkAccessAllowed(resolved, type, ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI)) {
      return null;
    }

    if (resolved == null) {
      XRLog.log(Level.INFO, LogMessageId.LogMessageId2Param.LOAD_URI_RESOLVER_REJECTED_LOADING_AT_URI, "CSS resource", uri);
      return null;
    }

    // Ownership is transferred to CSSResource which implements Closeable.
    return new CSSResource(openReader(resolved));
  }

  public abstract ImageResource getImageResource(String uri, ExternalResourceType type);

  /**
   * Retrieves the XML located at the given URI. It's assumed the URI does point to a XML--the URI will
   * be accessed (using the set HttpStreamFactory or URL::openStream), opened, read and then passed into the XML parser (XMLReader)
   * configured for Flying Saucer. The result is packed up into an XMLResource for later consumption.
   *
   * @param uri Location of the XML source.
   * @return An XMLResource containing the image.
   */
  @Override
  public XMLResource getXMLResource(String uri, ExternalResourceType type) {
    if (!checkAccessAllowed(uri, type, ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI)) {
      return null;
    }
    String resolved = _resolver.resolveURI(this._baseUri, uri);
    if (!checkAccessAllowed(resolved, type, ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI)) {
      return null;
    }

    if (resolved == null) {
      XRLog.log(Level.INFO, LogMessageId.LogMessageId2Param.LOAD_URI_RESOLVER_REJECTED_LOADING_AT_URI, "XML resource", uri);
      return null;
    }

    try (Reader inputReader = openReader(resolved)) {
      return inputReader == null ? null :
          XMLResource.load(inputReader);
    } catch (IOException e) {
      // On auto close, swallow.
      return null;
    }
  }

  @Override
  public byte[] getBinaryResource(String uri, ExternalResourceType type) {
    if (!checkAccessAllowed(uri, type, ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI)) {
      return null;
    }
    String resolved = _resolver.resolveURI(this._baseUri, uri);
    if (!checkAccessAllowed(resolved, type, ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI)) {
      return null;
    }

    if (resolved == null) {
      XRLog.log(Level.INFO, LogMessageId.LogMessageId2Param.LOAD_URI_RESOLVER_REJECTED_LOADING_AT_URI, "binary resource", uri);
      return null;
    }

    try (InputStream is = openStream(resolved)) {
      if (is == null) {
        return null;
      }

      return OpenUtil.readAll(is);
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Returns true if the given URI was visited, meaning it was requested at some point since initialization.
   *
   * @param uri A URI which might have been visited.
   * @return Always false; visits are not tracked in the NaiveUserAgent.
   */
  @Override
  public boolean isVisited(String uri) {
    return false;
  }

  public void setAccessController(
      ExternalResourceControlPriority prio,
      BiPredicate<String, ExternalResourceType> controller) {
    this._accessControllers.put(prio, controller);
  }

  public boolean checkAccessAllowed(
      String uriOrResolved,
      ExternalResourceType type,
      ExternalResourceControlPriority priority) {
    BiPredicate<String, ExternalResourceType> controller = this._accessControllers.get(priority);

    if (uriOrResolved == null) {
      return false;
    }

    if (controller == null) {
      return true;
    }

    boolean passed = controller.test(uriOrResolved, type);

    if (!passed) {
      XRLog.log(Level.WARNING, LogMessageId.LogMessageId2Param.LOAD_RESOURCE_ACCESS_REJECTED, uriOrResolved, type);
    }

    return passed;
  }

  /**
   * Returns the current baseUrl for this class.
   */
  @Override
  public String getBaseURL() {
    return _baseUri;
  }

  /**
   * URL relative to which URIs are resolved.
   *
   * @param uri A URI which anchors other, possibly relative URIs.
   */
  @Override
  public void setBaseURL(String uri) {
    _baseUri = uri;
  }

  @Override
  @Deprecated
  public void documentStarted() {
    clearImageCache();
  }

  @Override
  @Deprecated
  public void documentLoaded() { /* ignore*/ }

  @Override
  @Deprecated
  public void onLayoutException(Throwable t) { /* ignore*/ }

  @Override
  @Deprecated
  public void onRenderException(Throwable t) { /* ignore*/ }

  @Override
  public String resolveURI(String uri) {
    return _resolver.resolveURI(getBaseURL(), uri);
  }

  @Override
  public String resolveUri(String baseUri, String uri) {
    return _resolver.resolveURI(baseUri, uri);
  }

  public static class DefaultHttpStream implements FSStream {
    private InputStream strm;

    public DefaultHttpStream(InputStream strm) {
      this.strm = strm;
    }

    @Override
    public InputStream getStream() {
      return this.strm;
    }

    @Override
    public Reader getReader() {
      if (this.strm != null) {
        return new InputStreamReader(this.strm, StandardCharsets.UTF_8);
      }
      return null;
    }
  }

  public static class DefaultHttpStreamFactory implements FSStreamFactory {
    final static int CONNECTION_TIMEOUT = 10_000;
    final static int READ_TIMEOUT = 30_000;

    final int connectTimeout;
    final int readTimeout;

    /**
     * Create a FSStreamFactory for http, https with specified timeouts.
     * Uses URLConnection to perform requests.
     * Zero value for timeout specifies no timeout.
     */
    public DefaultHttpStreamFactory(int connectTimeout, int readTimeout) {
      this.connectTimeout = connectTimeout;
      this.readTimeout = readTimeout;
    }

    /**
     * Create a FSStreamFactory with 10 second connect timeout and
     * 30 second read timeout.
     */
    public DefaultHttpStreamFactory() {
      this(CONNECTION_TIMEOUT, READ_TIMEOUT);
    }

    @SuppressWarnings("resource")
    @Override
    public FSStream getUrl(String uri) {
      InputStream is = null;

      try {
        URLConnection conn = new URI(uri).toURL().openConnection();
        conn.setConnectTimeout(this.connectTimeout);
        conn.setReadTimeout(this.readTimeout);
        conn.connect();

        is = conn.getInputStream();
      } catch (java.net.MalformedURLException | URISyntaxException e) {
        XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_MALFORMED_URL, uri, e);
      } catch (java.io.FileNotFoundException e) {
        XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_ITEM_AT_URI_NOT_FOUND, uri, e);
      } catch (java.io.IOException e) {
        XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_IO_PROBLEM_FOR_URI, uri, e);
      }

      // Ownership is transferred to DefaultHttpStream which implements Closeable.
      return new DefaultHttpStream(is);
    }
  }

  private static class ByteStream implements FSStream {

    private final byte[] input;

    ByteStream(byte[] input) {
      this.input = input;
    }

    @Override
    public InputStream getStream() {
      return new ByteArrayInputStream(input);
    }

    @Override
    public Reader getReader() {
      return new InputStreamReader(getStream(), StandardCharsets.UTF_8);
    }
  }

  public static class DataUriFactory implements FSStreamFactory {

    static final Pattern WHITE_SPACE = Pattern.compile("\\s+");

    static byte[] fromBase64Encoded(String b64encoded) {
      return Base64.getMimeDecoder().decode(WHITE_SPACE.matcher(b64encoded).replaceAll(""));
    }

    /**
     * Decodes the data part of a data URI that is not base64 encoded. Such data is
     * URL encoded (RFC 2397), so <code>%3Csvg%3E</code> has to become
     * <code>&lt;svg&gt;</code> and <code>%23gradient</code> has to become
     * <code>#gradient</code>.
     *
     * <p>Decoding is lenient: a percent sign that is not followed by two hex digits
     * is kept as-is rather than treated as an error. Inline SVG images are full of
     * unencoded percent signs (<code>width='100%'</code>) and browsers accept them,
     * so rejecting them would be unhelpful.</p>
     */
    static byte[] fromPercentEncoded(String data) {
      ByteArrayOutputStream out = new ByteArrayOutputStream(data.length());
      StringBuilder literal = new StringBuilder(data.length());

      for (int i = 0; i < data.length(); i++) {
        char c = data.charAt(i);
        int hi;
        int lo;

        if (c == '%' &&
            i + 2 < data.length() &&
            (hi = Character.digit(data.charAt(i + 1), 16)) != -1 &&
            (lo = Character.digit(data.charAt(i + 2), 16)) != -1) {

          // Flush the literal characters seen so far, so that we never split a
          // surrogate pair or a multi byte character while encoding them.
          appendUtf8(out, literal);
          out.write((hi << 4) + lo);
          i += 2;
        } else {
          literal.append(c);
        }
      }

      appendUtf8(out, literal);
      return out.toByteArray();
    }

    private static void appendUtf8(ByteArrayOutputStream out, StringBuilder literal) {
      if (literal.length() != 0) {
        byte[] bytes = literal.toString().getBytes(StandardCharsets.UTF_8);
        out.write(bytes, 0, bytes.length);
        literal.setLength(0);
      }
    }

    @Override
    public FSStream getUrl(String url) {
      int idxSeparator;
      if (url != null && url.startsWith("data:") && (idxSeparator = url.indexOf(',')) > 0) {
        String data = url.substring(idxSeparator + 1);
        byte[] res;
        if (url.indexOf("base64,") == idxSeparator - 6 /* 6 = "base64,".length */) {
          res = fromBase64Encoded(data);
        } else {
          res = fromPercentEncoded(data);
        }
        return new ByteStream(res);
      }
      return null;
    }
  }

  public static class DefaultAccessController
      implements BiPredicate<String, ExternalResourceType> {

    public boolean test(String uri, ExternalResourceType resourceType) {
      if (resourceType == null) {
        return false;
      }

      switch (resourceType) {
        case BINARY:
        case CSS:
        case FONT:
        case IMAGE_RASTER:
        case XML_XHTML:
        case XML_SVG:
        case PDF:
        case SVG_BINARY:
          return true;
        case FILE_EMBED:
          return false;
      }

      return false;
    }
  }

  public static class DefaultUriResolver implements FSUriResolver {
    /**
     * Resolves the URI; if absolute, leaves as is, if relative, returns an
     * absolute URI based on the baseUrl for the agent.
     *
     * @param uri A URI, possibly relative.
     * @return A URI as String, resolved, or null if there was an exception
     * (for example if the URI is malformed).
     */
    @Override
    public String resolveURI(String baseUri, String uri) {
      if (uri == null || uri.isEmpty()) {
        return null;
      }

      if (uri.startsWith("data:")) {
        return uri; //bypass URI "formatting" check for data uri, as we may have whitespace in the base64 encoded data
      }

      try {
        URI possiblyRelative = new URI(uri);

        if (possiblyRelative.isAbsolute()) {
          return possiblyRelative.toString();
        } else {
          if (baseUri == null) {
            // If user hasn't provided base URI, just reject resolving relative URIs.
            XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.LOAD_COULD_NOT_RESOLVE_RELATIVE_URI_BECAUSE_NO_BASE_URI_WAS_PROVIDED,
                uri);
            return null;
          } else if (baseUri.startsWith("jar")) {
            // Fix for OpenHTMLtoPDF issue-#125, jar: scheme urls are opaque URIs, so calling
            // base.resolve(relative) on them returns only the relative part. Instead, we resolve
            // against the entry path inside the archive (which is hierarchical) and re-attach the
            // jar part afterwards. This mirrors what java.net.URL's jar handler does, without
            // using the URL constructors, which are deprecated since Java 20.
            int entryStart = baseUri.indexOf("!/");

            if (entryStart < 0) {
              // Same as the URL jar handler, which rejects such base urls with
              // MalformedURLException("no !/ in spec").
              XRLog.log(Level.WARNING, LogMessageId.LogMessageId3Param.EXCEPTION_URI_WITH_BASE_URI_INVALID, uri, "jar scheme", baseUri);
              return null;
            }

            URI baseEntry = new URI(baseUri.substring(entryStart + 1));
            return baseUri.substring(0, entryStart + 1) + baseEntry.resolve(possiblyRelative);
          } else {
            URI base = new URI(baseUri);
            URI absolute = base.resolve(uri);
            return absolute.toString();
          }
        }
      } catch (URISyntaxException e) {
        XRLog.log(Level.WARNING, LogMessageId.LogMessageId3Param.EXCEPTION_URI_WITH_BASE_URI_INVALID, uri, "", baseUri, e);
        return null;
      }
    }
  }
}
