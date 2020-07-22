package org.jsr107.tck.event;

import org.jsr107.tck.testutil.CacheTestSupport;
import org.junit.Assert;
import org.junit.Test;

import javax.cache.event.EventType;

import java.util.ArrayList;

import static org.hamcrest.CoreMatchers.is;

/**
 * Functional Tests for the {@link org.jsr107.tck.event.CacheEntryListenerClient} and
 * {@link org.jsr107.tck.event.CacheEntryListenerServer}
 * classes.
 *
 * <p>Original TCK test code does not test anything, because an exception
 * happens within the catch block. Disabled. (jw).
 *
 * @author Joe Fialli
 */
public class CacheEntryListenerClientServerTest {

  /**
   * Ensure that values can be loaded from the {@link org.jsr107.tck.event.CacheEntryListenerClient} via
   * the {@link org.jsr107.tck.event.CacheEntryListenerServer}.
   */
  public void shouldHandleCacheEntryEventFromServerWithClient() throws Exception {

    CacheTestSupport.MyCacheEntryListener<String, String> listener = new CacheTestSupport.MyCacheEntryListener<>();


    CacheEntryListenerServer<String, String> serverListener =
      new CacheEntryListenerServer<>(10011, String.class, String.class);
    serverListener.addCacheEventListener(listener);

    try {
      serverListener.open();

      CacheEntryListenerClient<String, String> clientListener =
        new CacheEntryListenerClient<>(serverListener.getInetAddress(), serverListener.getPort());

      TestCacheEntryEvent<String, String> event = new TestCacheEntryEvent(null, EventType.CREATED);
      event.setKey("key");
      event.setValue("value");
      event.setOldValueAvailable(false);
      ArrayList events = new ArrayList();
      events.add(event);

      clientListener.onCreated(events);
      Assert.assertThat(listener.getCreated(), is(1));

      clientListener.onRemoved(events);
      Assert.assertThat(listener.getRemoved(), is(0));

      event = new TestCacheEntryEvent(null, EventType.UPDATED);
      event.setKey("key");
      event.setValue("value");
      event.setOldValue("oldValue");
      event.setOldValueAvailable(true);

      events.clear();
      events.add(event);
      clientListener.onUpdated(events);
      Assert.assertThat(listener.getUpdated(), is(1));
      Assert.assertThat(listener.getCreated(), is(1));

    } finally {
      serverListener.close();
    }
  }

  public void testMultipleTimes() throws Exception {
    for (int i = 0; i < 10 ; i++ ) {
      shouldHandleCacheEntryEventFromServerWithClient();
    }
  }

}
