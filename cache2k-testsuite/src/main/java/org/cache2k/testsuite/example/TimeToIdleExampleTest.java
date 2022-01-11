package org.cache2k.testsuite.example;

/*-
 * #%L
 * cache2k testsuite on public API
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

/**
 * Example how to replicate time to idle semantics by updating the
 * expiry time on each access.
 *
 * @author Jens Wilke
 */
public class TimeToIdleExampleTest {

  /**
   * Store, get, and get a non existing mapping
   */
  @Test
  public void test() {
    WrappedCache<Integer, Integer> wrappedCache =
      new WrappedCache<>(Cache2kBuilder.of(Integer.class, Integer.class).build());
    wrappedCache.get(123);
    wrappedCache.put(123, 123);
    wrappedCache.get(123);
    wrappedCache.close();
  }

  public class WrappedCache<K, V> {
    private final long expireAfterAccess = TimeUnit.MINUTES.toMillis(5);
    private final Cache<K, V> cache;
    public WrappedCache(Cache<K, V> cache) { this.cache = cache; }
    /** Store new value and update expiry time. */
    public void put(K key, V value) {
      cache.mutate(key, entry -> {
        entry.setValue(value);
        entry.setExpiryTime(entry.getStartTime() + expireAfterAccess);
      });
    }
    /**
     * Access mapped value and update expiry time. Updating the expiry time has no
     * effect if no mapping is present.
     */
    public V get(K key) {
      return cache.invoke(key, entry -> {
        entry.setExpiryTime(entry.getStartTime() + expireAfterAccess);
        return entry.getValue();
      });
    }
    public void close() {
      cache.close();
    }
    @Override
    public String toString() {
      return cache.toString();
    }
  }

}
