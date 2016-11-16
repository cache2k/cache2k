package org.jsr107.tck.integration;

import org.junit.Assert;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.fail;

/**
 * Functional Tests for the {@link CacheLoaderClient} and {@link CacheLoaderServer}
 * classes.
 *
 * @author Brian Oliver
 */
public class CacheLoaderClientServerTest {

  /**
   * Ensure that values can be loaded from the {@link CacheLoaderClient} via
   * the {@link CacheLoaderServer}.
   */
  @Test
  public void shouldLoadFromServerWithClient() {

    RecordingCacheLoader<String> recordingCacheLoader = new RecordingCacheLoader<String>();


    CacheLoaderServer<String, String> serverCacheLoader = new CacheLoaderServer<String, String>(10000, recordingCacheLoader);

    try {
      serverCacheLoader.open();

      CacheLoaderClient<String, String> clientCacheLoader = new CacheLoaderClient<>(serverCacheLoader.getInetAddress(), serverCacheLoader.getPort());

      String value = clientCacheLoader.load("gudday");

      Assert.assertThat(value, is(notNullValue()));
      Assert.assertThat(value, is("gudday"));
      Assert.assertThat(recordingCacheLoader.hasLoaded("gudday"), is(true));
    } catch (Exception e) {

    } finally {
      serverCacheLoader.close();
    }
  }

  /**
   * Ensure that exceptions thrown by an underlying cache loader are re-thrown.
   */
  @Test
  public void shouldRethrowExceptions() {

    FailingCacheLoader<String, String> failingCacheLoader = new FailingCacheLoader<>();


    CacheLoaderServer<String, String> serverCacheLoader = new CacheLoaderServer<String, String>(10000, failingCacheLoader);

    try {
      serverCacheLoader.open();

      CacheLoaderClient<String, String> clientCacheLoader = new CacheLoaderClient<>(serverCacheLoader.getInetAddress(), serverCacheLoader.getPort());

      String value = clientCacheLoader.load("gudday");

      fail("An UnsupportedOperationException should have been thrown");
    } catch (Exception e) {

    } finally {
      serverCacheLoader.close();
    }
  }

  /**
   * Ensure that <code>null</code> entries can be passed from the
   * {@link CacheLoaderServer} back to the {@link CacheLoaderClient}.
   */
  @Test
  public void shouldLoadNullValuesFromServerWithClient() {

    NullValueCacheLoader<String, String> nullCacheLoader = new NullValueCacheLoader<>();

    CacheLoaderServer<String, String> serverCacheLoader = new CacheLoaderServer<String, String>(10000, nullCacheLoader);

    try {
      serverCacheLoader.open();

      CacheLoaderClient<String, String> clientCacheLoader = new CacheLoaderClient<>(serverCacheLoader.getInetAddress(), serverCacheLoader.getPort());

      String value = clientCacheLoader.load("gudday");

      Assert.assertThat(value, is(nullValue()));
    } catch (Exception e) {

    } finally {
      serverCacheLoader.close();
    }
  }

}
