
package org.cache2k;

/*
 * #%L
 * cache2k API only package
 * %%
 * Copyright (C) 2000 - 2014 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface to the cache2k cache implementation. To obtain a cache
 * instance use the {@link CacheBuilder}
 *
 * @see CacheBuilder to create a new cache
 * @author Jens Wilke
 */
public interface Cache<K, T> extends KeyValueSource<K,T> {

  public abstract String getName();

  /**
   * Clear the cache contents
   */
  public abstract void clear();

  /**
   * Returns object mapped to key
   */
  public abstract T get(K key);

  /**
   * Signals the intend to call a get on the same key in the near future.
   *
   * <p/>Triggers a prefetch and returns immediately. If the entry is in
   * the cache already and still fresh nothing will happen. If the entry
   * is not in the cache or expired a fetch will be triggered. The fetch
   * will take place with the same thread pool then the one used
   * for background refresh.
   */
  public abstract void prefetch(K key);

  /**
   * Signals the intend to call get on the set of keys in the near future.
   *
   * <p/>Without threads: Issues a bulk fetch on the set of keys not in
   * the cache.
   *
   * <p/>With threads: If a thread is available than start the
   * fetch operation on the missing key mappings and return after the
   * keys are locked for data fetch within the fetch. A sequential get
   * on a key will stall until the value is loaded.
   */
  public abstract void prefetch(Set<K> keys);

  public abstract void prefetch(List<K> keys, int _startIndex, int _afterEndIndex);

  /**
   * Returns the value if it is mapped within the cache.
   * No request on the cache source is made.
   */
  public abstract T peek(K key);

  /**
   * Set object value for the key
   */
  public abstract void put(K key, T value);

  /**
   * Remove the object mapped to key from the cache.
   */
  public abstract void remove(K key);

  /**
   * Remove the mappings for the keys atomically. Missing keys
   * will be ignored.
   *
   * <p/>Fetches in flight, aka gets started before this method
   * call but not yet finished, will be ignored. This is important
   * since this call is used for transaction commits and the key
   * may be locked within the database.
   */
  public abstract void removeAllAtOnce(Set<K> key);

  /**
   * Disclaimer: This method is here to be able to support known coding similar
   * to JSR107. Do not use it. Just use prefetch() and the normal Cache.get().
   * Since Cache.get() is almost as fast as a HashMap there is no need to
   * build up mini-caches. The caller code will also look much cleaner.
   *
   * <p/>Bulk get: gets all values associated with the keys. If an exception
   * happened during the fetch of any key, this exception will be thrown wrapped
   * into a {@link PropagatedCacheException}. If more exceptions exist, the
   * selection is arbitrary.
   *
   * <p/>The cache source does not need to support the bulk operation. It is
   * neither guaranteed that the bulk get is called on the cache source if it
   * exists.
   *
   * <p/>The operation may be split into chunks and not performed atomically.
   * The entries that are processed within a chunk will be locked, to avoid
   * multiple fetches from the cache source. In this operation there is the
   * potential for deadlocks, so deadlocks need to be detected and avoided.
   * One possible solution to avoid deadlocks, is to fall back to single
   * get operations.
   *
   * <p/>In contrast to JSR107 the following guarantees are met
   * if the operation returns without exception: map.size() == keys.size()
   *
   * @exception PropagatedCacheException if there an exception happened
   *            during fetch of any key.
   */
  public Map<K, T> getAll(Set<? extends K> keys);

  /**
   * Free all resources and remove the cache from the CacheManager.
   */
  public abstract void destroy();

  /**
   * Returns information about the caches internal information. Calling toString()
   * on the cache object is an expansive operation, since internal statistics are
   * collected and other thread users need to be locked out, to have a consistent
   * view.
   */
  public String toString();

}
