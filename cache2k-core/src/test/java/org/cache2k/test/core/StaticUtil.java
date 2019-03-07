package org.cache2k.test.core;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.CacheManager;
import org.cache2k.event.CacheEntryRemovedListener;
import org.cache2k.core.InternalCache;
import org.cache2k.core.InternalCacheInfo;
import org.cache2k.CacheOperationCompletionListener;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;

/**
 * @author Jens Wilke
 */
public class StaticUtil {

  public static <T> Iterable<T> toIterable(T... keys) {
    return Arrays.asList(keys);
  }

  public static InternalCacheInfo latestInfo(Cache c) {
    return ((InternalCache) c).getLatestInfo();
  }

  /**
   * Enforce the usage of wired cache by registering a dummy listener.
   */
  @SuppressWarnings("unchecked")
  public static <K,V> Cache2kBuilder<K,V> enforceWiredCache(Cache2kBuilder<K,V> b) {
    return b.addListener(new CacheEntryRemovedListener() {
      @Override
      public void onEntryRemoved(final Cache c, final CacheEntry entry) {

      }
    });
  }

  public static void allCachesClosed() {
    assertFalse("all caches closed",
      CacheManager.getInstance().getActiveCaches().iterator().hasNext());
  }

  public static int count(Iterable<?> _things) {
    int _counter = 0;
    for (Object o : _things) {
      _counter++;
    }
    return _counter;
  }

  public static Throwable loadAndWait(LoaderRunner x) {
    CacheLoaderTest.CompletionWaiter w = new CacheLoaderTest.CompletionWaiter();
    x.run(w);
    w.awaitCompletion();
    return w.getException();
  }

  public static <K,V> void load(Cache<K,V> c, K ...keys) {
    CacheLoaderTest.CompletionWaiter w = new CacheLoaderTest.CompletionWaiter();
    c.loadAll(toIterable(keys), w);
    w.awaitCompletion();
  }

  public static <K,V> void reload(Cache<K,V> c, K ...keys) {
    CacheLoaderTest.CompletionWaiter w = new CacheLoaderTest.CompletionWaiter();
    c.reloadAll(toIterable(keys), w);
    w.awaitCompletion();
  }

  public interface LoaderRunner {
    void run(CacheOperationCompletionListener l);
  }

}
