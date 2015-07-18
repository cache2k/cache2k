
package org.cache2k;

/*
 * #%L
 * cache2k API only package
 * %%
 * Copyright (C) 2000 - 2015 headissue GmbH, Munich
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

import java.io.Closeable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface to the cache2k cache implementation. To obtain a cache
 * instance use the {@link CacheBuilder}.
 *
 * @see CacheBuilder to create a new cache
 * @author Jens Wilke
 */
@SuppressWarnings("UnusedDeclaration")
public interface Cache<K, T> extends KeyValueSource<K,T>, Iterable<CacheEntry<K, T>>, Closeable {

  public abstract String getName();

  /**
   * Clear the cache contents
   */
  public abstract void clear();

  /**
   * Returns object in the cache that is mapped to the key.
   */
  public abstract T get(K key);

  /**
   * Returns a mapped entry from the cache or null. If no entry is present or the entry
   * is expired, null is also returned.
   *
   * <p>If an exception was thrown during fetching the entry via the cache source,
   * method does not follow the same schema of rethrowing the exception like in get()
   * and peek(), instead the exception can be retrieved via,
   * {@link org.cache2k.CacheEntry#getException()}
   *
   * <p>The reason for the existence of this method is, that in the presence of
   * null values it cannot be determined by peek() and get() if there is a mapping
   * or a null value.
   *
   * <p>Multiple calls for the same key may return different instances of the entry
   * object.
   */
  public CacheEntry<K, T> getEntry(K key);

  /**
   * Signals the intent to call a get on the same key in the near future.
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
   * Returns the value if it is mapped within the cache and not expired, or null.
   * No request on the cache source is made.
   *
   * @throws org.cache2k.PropagatedCacheException if an exception happened
   *         when the value was fetched by the cache source
   */
  public abstract T peek(K key);

  /**
   * Returns a mapped entry from the cache or null. If no entry is present or the entry
   * is expired, null is also returned. As with {@link #peek(Object)}, no request to the
   * {@link CacheSource} is made, if no entry is available for the requested key.
   *
   * <p>If an exception was thrown during fetching the entry via the cache source,
   * method does not follow the same schema of rethrowing the exception like in get()
   * and peek(), instead the exception can be retrieved via,
   * {@link org.cache2k.CacheEntry#getException()}
   *
   * <p>The reason for the existence of this method is, that in the presence of
   * null values it cannot be determined by peek() and get() if there is a mapping
   * or a null value.
   *
   * <p>Multiple calls for the same key may return different instances of the entry
   * object.
   */
  public abstract CacheEntry<K, T> peekEntry(K key);

  /**
   * Returns true if the there is a mapping for the specified key. Does not invoke
   * the {@link CacheSource} if no entry exists.
   */
  public abstract boolean contains(K key);

  /**
   * Set object value for the key
   */
  public abstract void put(K key, T value);

  /**
   * Same as put, if there is no value mapped to the key in the cache.
   *
   * <p>If a mapping is present, the cache does not account this call as access.</p>
   */
  public abstract boolean putIfAbsent(K key, T value);

  /**
   * Replace an existing mapping and return the current one.
   */
  public abstract T peekAndReplace(K key, T _value);

  public abstract boolean replace(K key, T _newValue);

  public abstract boolean replace(K key, T _oldValue, T _newValue);

  public abstract T peekAndRemove(K key);

  public abstract T peekAndPut(K key, T value);

  /**
   * Remove the object mapped to key from the cache.
   */
  public abstract void remove(K key);

  /**
   * Remove the mapping if the stored value is the equal to the comparison value.
   *
   * @return true, if mapping was removed
   */
  public boolean remove(K key, T value);

  /**
   * Remove the mappings for the keys atomically. Missing keys
   * will be ignored.
   *
   * <p/>Parallel fetches from the cache source, that are currently
   * ongoing for some of the given keys, will be ignored.
   */
  public abstract void removeAllAtOnce(Set<K> key);

  /**
   * Fetch a set of values from the cache source. This call is modelled after the JSR107
   * method Cache.loadAll.
   *
   * @deprecated May or may not stay in the API, use the more lightweight #prefetch call as alternative
   * @since 0.22
   */
  public void fetchAll(Set<? extends K> keys, boolean replaceExistingValues, FetchCompletedListener l);

  public <R> R invoke(K key, CacheEntryProcessor<K, T, R> entryProcessor, Object... _args);

  public <R> Map<K, EntryProcessingResult<R>> invokeAll(
      Set<? extends K> keys, CacheEntryProcessor<K ,T, R> p, Object... _objs);

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
   * duplicate fetches from the cache source. To avoid deadlocks there is a
   * fallback non-bulk operation if a fetch is ongoing and the keys overlap.
   *
   * <p/>In contrast to JSR107 the following guarantees are met
   * if the operation returns without exception: map.size() == keys.size()
   *
   * <p/>Exception handling: The method may terminate normal, even if a cache
   * fetch via cache source failed. In this case the exception will be thrown
   * when the value is requested from the map.
   *
   * @exception PropagatedCacheException may be thrown if the fetch fails.
   */
  public Map<K, T> getAll(Set<? extends K> keys);

  /**
   * Bulk counterpart for {@link #peek(Object)}
   */
  public Map<K, T> peekAll(Set<? extends K> keys);

  /**
   * Put all elements of the map into the cache.
   */
  public void putAll(Map<? extends K, ? extends T> m);

  /**
   * Number of entries the cache holds in total. When iterating the entries
   * the cache will always return less or an identical number of entries.
   * The reason for this is, that duplicate entries may exist in different
   * storage layers (typically in heap and in persistent storage), or may be
   * expired already.
   *
   * <p>The method has more statistical value and the result depends on the
   * actual configuration of the cache.
   *
   * <p>TODO-API: Keep this for the final API?
   */
  public abstract int getTotalEntryCount();

  /**
   * Iterate all entries in the cache.
   *
   * <p>Contract: All entries present in the cache by the call of the method call will
   * be iterated if not removed during the iteration goes on. The iteration may or may not
   * iterate entries inserted during the iteration is in progress. The iteration never
   * iterates duplicate entries.
   *
   * <p>The iteration is usable concurrently. Concurrent operations will not be
   * influenced. Mutations of the cache, like remove or put, will not stop the iteration.
   *
   * <p>The iterator itself is not thread safe. Calls to one iterator instance from
   * different threads need to be properly synchronized.
   *
   * <p>The iterator holds resources. If an iteration is aborted, the resources should
   * be freed by calling {@link org.cache2k.ClosableIterator#close}, e.g. with a
   * try with resources pattern.
   */
  @Override
  ClosableIterator<CacheEntry<K, T>> iterator();

  public abstract void removeAll();

  /**
   * Remove persistent entries, that are not longer needed. Only has an effect
   * if a storage is defined.
   */
  public abstract void purge();

  /**
   * Ensure that any transient data is stored in the persistence storage.
   * Nothing will be done if no persistent storage is configured.
   */
  public abstract void flush();

  /**
   * @deprecated use {@link #close()}
   */
  public abstract void destroy();

  /**
   * Free all resources and remove the cache from the CacheManager.
   * If persistence support is enabled, the cache may flush unwritten data. Depending on the
   * configuration of the persistence, this method only returns after all unwritten data is
   * flushed to disk.
   *
   * <p>The method is designed to free resources and finish operations as gracefully and fast
   * as possible. Some cache operations take an unpredictable long time such as the call of
   * the {@link CacheSource#get(Object)}, so it may happen that the cache still has threads
   * in use when this method returns.
   *
   * <p>After close, subsequent cache operations will throw a {@link org.cache2k.CacheException}.
   * Cache operations currently in progress, may or may not terminated with an exception.
   *
   * <p>If all caches need to be closed it is more effective to use {@link CacheManager#close()}
   */
  public abstract void close();

  /**
   * Return the cache manager for this cache instance.
   */
  public CacheManager getCacheManager();

  /**
   * True if cache was closed or closing is in progress.
   */
  public abstract boolean isClosed();

  /**
   * Returns information about the caches internal information. Calling {@link #toString}
   * on the cache object is an expensive operation, since internal statistics are
   * collected and other thread users need to be locked out, to have a consistent
   * view.
   */
  public String toString();

}
