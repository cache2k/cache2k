package org.jsr107.tck.event;

import org.jsr107.tck.testutil.ExcludeListExcluder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryListener;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;
import javax.cache.processor.EntryProcessorException;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests listener exceptions are propagated. Not present in original TCK.
 *
 * @author Jens Wilke
 * @since cache2k
 */
public class ListenerExceptionsTest {

  @Rule
  public MethodRule rule = new ExcludeListExcluder(this.getClass());

  static final MutableCacheEntryListenerConfiguration<Object, Object> SYNC_LISTENER =
    new MutableCacheEntryListenerConfiguration<>(null, null, true, true);

  private Cache<Object, Object> cache;

  @Test
  public void removedListenerException() {
    CacheEntryRemovedListener<Object, Object> listener = cacheEntryEvents -> {
      throw new TriggerException();
    };
    MutableConfiguration<Object, Object> cfg = new MutableConfiguration<>();
    cfg.addCacheEntryListenerConfiguration(new MutableCacheEntryListenerConfiguration<>(SYNC_LISTENER)
      .setCacheEntryListenerFactory(new FactoryBuilder.SingletonFactory(listener)));
    cache =
      getCacheManager().createCache(getTestCacheName(), cfg);
    cache.put(1, 1);
    expectException(() -> cache.remove(1));
    assertFalse(cache.containsKey(1));
    cache.put(1, 1);
    expectException(() -> cache.getAndRemove(1));
    cache.put(1, 1);
    cache.remove(1, 2);
    expectException(() -> cache.remove(1, 1));
    cache.put(1, 1);
    expectException(() -> cache.removeAll());
    cache.put(1, 1);
    Set<Object> keys = new HashSet<>();
    keys.add(1);
    expectException(() -> cache.removeAll(keys));
    cache.put(1, 1);
    expectEntryProcessorException(() ->
      cache.invoke(1, (entry, arguments) -> { entry.remove(); return null; }));
    cache.put(1, 1);
    expectEntryProcessorException(
      () -> cache.invokeAll(keys, (entry, arguments) -> { entry.remove(); return null; })
        .get(1).get());
    cache.close();
  }

  @Test
  public void updatedListenerException() {
    CacheEntryUpdatedListener<Object, Object> listener = cacheEntryEvents -> {
      throw new TriggerException();
    };
    MutableConfiguration<Object, Object> cfg = new MutableConfiguration<>();
    cfg.addCacheEntryListenerConfiguration(new MutableCacheEntryListenerConfiguration<>(SYNC_LISTENER)
      .setCacheEntryListenerFactory(new FactoryBuilder.SingletonFactory(listener)));
    cache =
      getCacheManager().createCache(getTestCacheName(), cfg);
    cache.put(1, 1);
    expectException(() -> cache.put(1, 1));
    expectException(() -> cache.replace(1, 1));
    expectException(() -> cache.getAndReplace(1, 1));
    cache.replace(1, 2, 2);
    expectException(() -> cache.replace(1, 1, 2));
    cache.close();
  }

  @Test
  public void createdListenerException() {
    CacheEntryCreatedListener<Object, Object> listener = cacheEntryEvents -> {
      throw new TriggerException();
    };
    MutableConfiguration<Object, Object> cfg = new MutableConfiguration<>();
    cfg.addCacheEntryListenerConfiguration(new MutableCacheEntryListenerConfiguration<>(SYNC_LISTENER)
      .setCacheEntryListenerFactory(new FactoryBuilder.SingletonFactory(listener)));
    cache =
      getCacheManager().createCache(getTestCacheName(), cfg);
    expectException(() -> cache.putIfAbsent(1, 1));
    cache.removeAll();
    expectException(() -> cache.put(1, 1));
    cache.close();
  }

  private void expectException(Runnable r) {
    try {
      r.run();
      fail("exception expected");
    } catch (CacheEntryListenerException ex) {
      assertEquals(TriggerException.class, rootCause(ex).getClass());
    }
  }

  private void expectEntryProcessorException(Runnable r) {
    try {
      r.run();
      fail("exception expected");
    } catch (EntryProcessorException ex) {
      assertEquals(TriggerException.class, rootCause(ex).getClass());
    }
  }


  private Throwable rootCause(Throwable ex) {
    if (ex.getCause() == null) {
      return ex;
    }
    return rootCause(ex.getCause());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void registerListener(CacheEntryListener<Object, Object> listener) {
    cache.registerCacheEntryListener(
      new MutableCacheEntryListenerConfiguration<>(SYNC_LISTENER)
        .setCacheEntryListenerFactory(new FactoryBuilder.SingletonFactory(listener)));
  }


  protected CacheManager getCacheManager() {
    return Caching.getCachingProvider().getCacheManager();
  }

  protected String getTestCacheName() {
    return getClass().getName();
  }

  static class TriggerException extends RuntimeException { }

}
