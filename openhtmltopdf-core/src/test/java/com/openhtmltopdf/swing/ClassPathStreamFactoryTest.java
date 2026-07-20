package com.openhtmltopdf.swing;

import java.io.InputStream;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class ClassPathStreamFactoryTest {
  private final NaiveUserAgent.ClassPathStreamFactory classPathStreamFactory = new NaiveUserAgent.ClassPathStreamFactory();

  @Test
  public void testGetUrl() {
    InputStream is = classPathStreamFactory.getUrl("classpath:/com/openhtmltopdf/swing/classPathStreamFactoryTest.txt").getStream();
    assertThat(is, notNullValue());
  }

  @Test
  public void testGetUrlWithoutLeadingSlash() {
    // Without a leading slash the URI is opaque and URI.getPath() returns null (issue #137).
    InputStream is = classPathStreamFactory.getUrl("classpath:com/openhtmltopdf/swing/classPathStreamFactoryTest.txt").getStream();
    assertThat(is, notNullValue());
  }
}
