package com.openhtmltopdf.swing;

import static org.hamcrest.core.IsEqual.equalTo;

import org.junit.Ignore;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;

import com.openhtmltopdf.extend.FSUriResolver;

public class DefaultUriResolverTest {
    
    private FSUriResolver resolver = new NaiveUserAgent.DefaultUriResolver();
    
    @Test
    public void testJarHttpUrlResolve() {
        assertThat(resolver.resolveURI("jar:http://www.foo.com/bar/baz.jar!/", "logo.png"), equalTo("jar:http://www.foo.com/bar/baz.jar!/logo.png"));
    }

    @Test
    public void testJarFileUrlResolve() {
        assertThat(resolver.resolveURI("jar:file:/E:/example/target/server-1.0.0.war!/WEB-INF/classes!/export/", "logo.jpg"), equalTo("jar:file:/E:/example/target/server-1.0.0.war!/WEB-INF/classes!/export/logo.jpg"));
    }
    
    @Test
    public void testJarUrlResolveFromEntryInSubDirectory() {
        assertThat(resolver.resolveURI("jar:file:/E:/example/x.jar!/dir/base.html", "logo.png"), equalTo("jar:file:/E:/example/x.jar!/dir/logo.png"));
    }

    @Test
    public void testJarUrlResolveWithParentDirectory() {
        assertThat(resolver.resolveURI("jar:file:/E:/example/x.jar!/dir/base.html", "../logo.png"), equalTo("jar:file:/E:/example/x.jar!/logo.png"));
    }

    @Test
    public void testJarUrlResolveWithCurrentDirectory() {
        assertThat(resolver.resolveURI("jar:file:/E:/example/x.jar!/dir/base.html", "./sub/logo.png"), equalTo("jar:file:/E:/example/x.jar!/dir/sub/logo.png"));
    }

    /**
     * An absolute path in the relative part is resolved against the root of the
     * archive, not against the root of the url containing the archive.
     */
    @Test
    public void testJarUrlResolveWithAbsolutePath() {
        assertThat(resolver.resolveURI("jar:file:/E:/example/x.jar!/dir/base.html", "/logo.png"), equalTo("jar:file:/E:/example/x.jar!/logo.png"));
        assertThat(resolver.resolveURI("jar:http://www.foo.com/bar/baz.jar!/dir/base.html", "/logo.png"), equalTo("jar:http://www.foo.com/bar/baz.jar!/logo.png"));
    }

    @Test
    public void testJarUrlResolveKeepsQueryAndFragment() {
        assertThat(resolver.resolveURI("jar:file:/E:/example/x.jar!/dir/base.html", "logo.png?q=1#f"), equalTo("jar:file:/E:/example/x.jar!/dir/logo.png?q=1#f"));
    }

    @Test
    public void testJarUrlResolveWithPercentEncodedArchivePath() {
        assertThat(resolver.resolveURI("jar:file:/E:/exa%20mple/x.jar!/dir/base.html", "logo.png"), equalTo("jar:file:/E:/exa%20mple/x.jar!/dir/logo.png"));
    }

    @Test
    public void testJarUrlResolveWithExclamationMarkInArchiveName() {
        assertThat(resolver.resolveURI("jar:file:/E:/example/x!bang.jar!/dir/base.html", "logo.png"), equalTo("jar:file:/E:/example/x!bang.jar!/dir/logo.png"));
    }

    /**
     * The jar url handler rejects base urls without an entry separator, so do we.
     */
    @Test
    public void testJarUrlWithoutEntrySeparator() {
        assertThat(resolver.resolveURI("jar:file:/E:/example/x.jar", "logo.png"), equalTo(null));
    }

    @Test
    public void testAbsoluteJarFileAsRelativePart() {
        assertThat(resolver.resolveURI("http://example.com/", "jar:file:/E:/example/target/server-1.0.0.war!/WEB-INF/classes!/export/logo.jpg"), equalTo("jar:file:/E:/example/target/server-1.0.0.war!/WEB-INF/classes!/export/logo.jpg"));
    }
    
    @Test
    @Ignore("Base URIs not working with out a trailing slash")
    public void testNonJarUrlResolveWithoutTrailingSlash() {
    	assertThat(resolver.resolveURI("http://example.com", "logo.png"), equalTo("http://example.com/logo.png"));
    }
 
    @Test
    public void testNonJarUrlResolveWithTrailingSlash() {
        assertThat(resolver.resolveURI("http://example.com/", "logo.png"), equalTo("http://example.com/logo.png"));
    }
    
    @Test
    public void testNonJarWithPath() {
        assertThat(resolver.resolveURI("http://example.com/path/", "logo.png"), equalTo("http://example.com/path/logo.png"));
    }
    
    @Test
    public void testAbsoluteAsPartialUrl() {
        assertThat(resolver.resolveURI("http://example.com/", "http://sample.com/logo.png"), equalTo("http://sample.com/logo.png"));
    }
    
    @Test
    @Ignore("Should empty urls should resolve to the base rather than null?")
    public void testEmptyUrl() {
        assertThat(resolver.resolveURI("http://example.com/", ""), equalTo("http://example.com/"));
    }
    
    
}
