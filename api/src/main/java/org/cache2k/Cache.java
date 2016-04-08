
package org.cache2k;

/*
 * #%L
 * cache2k API only package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.customization.ExpiryCalculator;
import org.cache2k.integration.CacheLoader;
import org.cache2k.integration.CacheWriter;
import org.cache2k.integration.LoadCompletedListener;
import org.cache2k.integration.CacheLoaderException;
import org.cache2k.processor.CacheEntryProcessor;
import org.cache2k.processor.EntryProcessingResult;

import java.io.Closeable;
import java.util.List;
import java.util.Map;

/*
 * Descriptions derive partly from the java.util.concurrent.ConcurrentMap.
 * Original copyright:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

/**
 * Interface to the cache2k cache implementation. To obtain a cache
 * instance use the {@link CacheBuilder}.
 *
 * @see CacheBuilder to create a new cache
 * @author Jens Wilke
 */
@SuppressWarnings("UnusedDeclaration")
public interface Cache<K, V> extends KeyValueSource<K, V>, Iterable<CacheEntry<K, V>>, Closeable {

  String getName();

  /**
   * Returns object in the cache that is mapped to the key.
   */
  V get(K key);

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
  CacheEntry<K, V> getEntry(K key);

  /**
   * Signals the intent to request a value for the given key in the near future.
   * The method will return immediately and the cache will load the
   * the value asynchronously if not yet present in the cache. The cache may
   * ignore the request, if not enough internal resources are available to
   * load the value in background.
   *
   * @param key the key that should be loaded
   */
  void prefetch(K key);

  /**
   * @deprecated Renamed to {@link #prefetchAll}
   */
  void prefetch(Iterable<? extends K> keys);

  /**
   * Signals the intent to request a value for the given keys in the near future.
   * The method will return immediately and the cache will load the
   * the values asynchronously if not yet present in the cache. The cache may
   * ignore the request, if not enough internal resources are available to
   * load the value in background.
   *
   * @param keys the keys which should be loaded
   */
   void prefetchAll(Iterable<? extends K> keys);

  /**
   * @deprecated use a sublist and {@link #prefetch(Iterable)}
   */
  void prefetch(List<? extends K> keys, int _startIndex, int _afterEndIndex);

  /**
   * Returns the value if it is mapped within the cache and it is not
   * expired, or null.
   *
   * <p>In contrast to {@link #get(Object)} this method solely operates
   * on the cache content and does not invoke the loader.
   *
   * <p>API rationale: Consequently all methods that do not invoke the loader
   * but return a value or a cache entry are named peek within this interface
   * to make the different semantics immediately obvious by the name.
   *
   * @param key key with which the specified value is associated
   * @return the value associated with the specified key, or
   *         {@code null} if there was no mapping for the key.
   *         (A {@code null} return can also indicate that the cache
   *         previously associated {@code null} with the key)
   * @throws ClassCastException if the class of the specified key
   *         prevents it from being stored in this cache
   * @throws NullPointerException if the specified key is null or the
   *         value is null and the cache does not permit null values
   * @throws IllegalArgumentException if some property of the specified key
   *         prevents it from being stored in this cache
   * @throws CacheLoaderException if the loading of the entry produced
   *         an exception, which was not suppressed and is not yet expired
   */
  V peek(K key);

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
   *
   * @throws CacheLoaderException if the loading of the entry produced
   *         an exception, which was not suppressed and is not yet expired
   */
  CacheEntry<K, V> peekEntry(K key);

  /**
   * Returns true, if there is a mapping for the specified key.
   *
   * <p>Statistics: The operation does increase the usage counter, but does
   * not count as read and therefore does not influence miss or hit values.
   *
   * @param key key which association should be checked
   * @return {@code true}, if this cache contains a mapping for the specified
   *         key
   * @throws ClassCastException if the key is of an inappropriate type for
   *         this cache
   * @throws NullPointerException if the specified key is null
   *
   */
  boolean contains(K key);

  /**
   * Updates an existing cache entry for the specified key, so it associates
   * the given value, or, insert a new cache entry for this key and value.
   *
   * <p>If an {@link ExpiryCalculator} is specified in the
   * cache configuration it is called and will determine the expiry time.
   * If a {@link CacheWriter} is configured in, then it is called with the
   * new value. If the {@link ExpiryCalculator} or {@link CacheWriter}
   * yield an exception the operation will be aborted and the previous
   * mapping will be preserved.
   *
   * @param key key with which the specified value is associated
   * @param value value to be associated with the specified key
   * @throws ClassCastException if the class of the specified key or value
   *         prevents it from being stored in this cache.
   * @throws NullPointerException if the specified key is null or the
   *         value is null and the cache does not permit null values
   * @throws IllegalArgumentException if some property of the specified key
   *         or value prevents it from being stored in this cache.
   * @throws CacheException if the cache was unable to process the request
   *         completely, for example, if an exceptions was thrown
   *         by a {@link CacheWriter}
   */
  void put(K key, V value);

  /**
   * If the specified key is not already associated
   * with a value, associate it with the given value.
   * This is equivalent to
   *  <pre> {@code
   * if (!cache.containsKey(key)) {
   *   cache.put(key, value);
   *   return true;
   * } else {
   *   return false;
   * }}</pre>
   *
   * except that the action is performed atomically.
   *
   * <p>See {@link #put(Object, Object)} for the effects on the cache writer and
   * expiry calculation.
   *
   * <p>Statistics: If an entry exists this operation counts as a hit, if the entry
   * is missing, a miss and put is counted. This definition is identical to the JSR107
   * statistics semantics. This is not consistent with other operations like
   * {@link #containsAndRemove(Object)} and {@link #contains(Object)} that don't update
   * the hit and miss counter if solely the existence of an entry is tested and not the
   * value itself is requested. This counting is subject to discussion and future change.
   *
   * @param key key with which the specified value is to be associated
   * @param value value to be associated with the specified key
   * @return {@code true}, if no entry was present and the value was associated with the key
   * @throws ClassCastException if the class of the specified key or value
   *         prevents it from being stored in this cache
   * @throws NullPointerException if the specified key is null or the
   *         value is null and the cache does not permit null values
   * @throws IllegalArgumentException if some property of the specified key
   *         or value prevents it from being stored in this cache
   */
  boolean putIfAbsent(K key, V value);

  /**
   * Replaces the entry for a key only if currently mapped to some value.
   * This is equivalent to
   *  <pre> {@code
   * if (cache.contains(key)) {
   *   cache.put(key, value);
   *   return cache.peek(key);
   * } else
   *   return null;
   * }</pre>
   *
   * except that the action is performed atomically.
   *
   * <p>As with {@link #peek(Object)}, no request to the {@link CacheLoader} is made,
   * if no entry is associated to the requested key.
   *
   * @param key key with which the specified value is associated
   * @param value value to be associated with the specified key
   * @return the previous value associated with the specified key, or
   *         {@code null} if there was no mapping for the key.
   *         (A {@code null} return can also indicate that the cache
   *         previously associated {@code null} with the key)
   * @throws ClassCastException if the class of the specified key or value
   *         prevents it from being stored in this cache
   * @throws NullPointerException if the specified key is null or the
   *         value is null and the cache does not permit null values
   * @throws IllegalArgumentException if some property of the specified key
   *         or value prevents it from being stored in this cache
   * @throws CacheLoaderException if the loading of the entry produced
   *         an exception, which was not suppressed and is not yet expired
   */
  V peekAndReplace(K key, V value);

  /**
   * Replaces the entry for a key only if currently mapped to some value.
   * This is equivalent to
   *  <pre> {@code
   * if (cache.contains(key)) {
   *   cache.put(key, value);
   *   return true
   * } else
   *   return false;
   * }</pre>
   *
   * except that the action is performed atomically.
   *
   * <p>Statistics: If an entry exists this operation counts as a hit, if the entry
   * is missing, a miss and put is counted. This definition is identical to the JSR107
   * statistics semantics. This is not consistent with other operations like
   * {@link #containsAndRemove(Object)} and {@link #contains(Object)} that don't update
   * the hit and miss counter if solely the existence of an entry is tested and not the
   * value itself is requested. This counting is subject to discussion and future change.
   *
   * @param key key with which the specified value is associated
   * @param value value to be associated with the specified key
   * @return {@code true} if a mapping is present and the value was replaced.
   *         {@code false} if no entry is present and no action was performed.
   * @throws ClassCastException if the class of the specified key or value
   *         prevents it from being stored in this cache.
   * @throws NullPointerException if the specified key is null or the
   *         value is null and the cache does not permit null values
   * @throws IllegalArgumentException if some property of the specified key
   *         or value prevents it from being stored in this cache.
   */
  boolean replace(K key, V value);

  /**
   * Replaces the entry for a key only if currently mapped to a given value.
   * This is equivalent to
   *  <pre> {@code
   * if (cache.contains(key) && Objects.equals(cache.get(key), oldValue)) {
   *   cache.put(key, newValue);
   *   return true;
   * } else
   *   return false;
   * }</pre>
   *
   * except that the action is performed atomically.
   *
   * @param key key with which the specified value is associated
   * @param oldValue value expected to be associated with the specified key
   * @param newValue value to be associated with the specified key
   * @return {@code true} if the value was replaced
   * @throws ClassCastException if the class of a specified key or value
   *         prevents it from being stored in this map
   * @throws NullPointerException if a specified key or value is null,
   *         and this map does not permit null keys or values
   * @throws IllegalArgumentException if some property of a specified key
   *         or value prevents it from being stored in this map
   */
  boolean replaceIfEquals(K key, V oldValue, V newValue);

  /**
   * Removes the mapping for a key from the cache if it is present.
   *
   * <p>Returns the value to which the cache previously associated the key,
   * or {@code null} if the cache contained no mapping for the key.
   *
   * <p>If the cache does permit null values, then a return value of
   * {@code null} does not necessarily indicate that the cache
   * contained no mapping for the key. It is also possible that the cache
   * explicitly associated the key to the value {@code null}.
   *
   * This is equivalent to
   *  <pre> {@code
   *  V tmp = cache.peek(key);
   *  cache.remove(key);
   *  return tmp;
   * }</pre>
   *
   * except that the action is performed atomically.
   *
   * <p>As with {@link #peek(Object)}, no request to the {@link CacheLoader} is made,
   * if no entry is associated to the requested key.
   *
   * @param key key whose mapping is to be removed from the cache
   * @return the previous value associated with <tt>key</tt>, or
   *         <tt>null</tt> if there was no mapping for <tt>key</tt>.
   * @throws NullPointerException if a specified key is null
   * @throws ClassCastException if the key is of an inappropriate type for
   *         the cache. This check is optional depending on the cache
   *         configuration.
   */
  V peekAndRemove(K key);

  /**
   * Removes the mapping for a key from the cache and returns true if it
   * one was present.
   *
   * @param key key whose mapping is to be removed from the cache
   * @return {@code true} if the cache contained a mapping for the specified key
   * @throws NullPointerException if a specified key is null
   * @throws ClassCastException if the key is of an inappropriate type for
   *         the cache. This check is optional depending on the cache
   *         configuration.
   */
  boolean containsAndRemove(K key);

  /**
   * Removes the mapping for a key from the cache if it is present.
   *
   * <p>These alternative versions of the remove operation exist:
   * <ul>
   *   <li>{@link #containsAndRemove(Object)}, returning a success flag</li>
   *   <li>{@link #peekAndRemove(Object)}, returning the removed value</li>
   *   <li>{@link #removeIfEquals(Object, Object)}, conditional removal matching on the current value</li>
   * </ul>
   *
   * @param key key whose mapping is to be removed from the cache
   * @throws NullPointerException if a specified key is null
   * @throws ClassCastException if the key is of an inappropriate type for
   *         this map
   */
  void remove(K key);

  /**
   * Remove the mapping if the stored value is the equal to the comparison value.
   *
   * @param key key whose mapping is to be removed from the cache
   * @param expectedValue value that must match with the existing value in the cache
   * @throws NullPointerException if a specified key is null
   * @throws ClassCastException if the key is of an inappropriate type for
   *         this map
   * @return true, if mapping was removed
   */
  boolean removeIfEquals(K key, V expectedValue);

  /**
   * This was for atomic removal of a bunch of entries. Not supported any more.
   *
   * @deprecated no replacement planed
   * @throws UnsupportedOperationException
   */
  void removeAllAtOnce(java.util.Set<K> key);

  /**
   * Removes a set of keys. This has the same semantics of calling
   * remove to every key, except that the cache is trying to optimize the
   * bulk operation.
   */
  void removeAll(Iterable<? extends K> keys);

  /**
   * Updates an existing cache entry for the specified key, so it associates
   * the given value, or, insert a new cache entry for this key and value. The previous
   * value will returned, or null if none was available.
   *
   * <p>Returns the value to which the cache previously associated the key,
   * or {@code null} if the cache contained no mapping for the key.
   *
   * <p>If the cache does permit null values, then a return value of
   * {@code null} does not necessarily indicate that the cache
   * contained no mapping for the key. It is also possible that the cache
   * explicitly associated the key to the value {@code null}.
   *
   * This is equivalent to
   *  <pre> {@code
   *  V tmp = cache.peek(key);
   *  cache.put(key, value);
   *  return tmp;
   * }</pre>
   *
   * except that the action is performed atomically.
   *
   * <p>As with {@link #peek(Object)}, no request to the {@link CacheLoader} is made,
   * if no entry is associated to the requested key.
   *
   * <p>See {@link #put(Object, Object)} for the effects on the cache writer and
   * expiry calculation.
   *
   * @param key key with which the specified value is associated
   * @param value value to be associated with the specified key
   * @return {@code true} if a mapping is present and the value was replaced.
   *         {@code false} if no entry is present and no action was performed.
   * @throws ClassCastException if the class of the specified key or value
   *         prevents it from being stored in this cache.
   * @throws NullPointerException if the specified key is null or the
   *         value is null and the cache does not permit null values
   * @throws IllegalArgumentException if some property of the specified key
   *         or value prevents it from being stored in this cache.
   */
  V peekAndPut(K key, V value);

  /**
   * Asynchronously loads the given set of keys into the cache. Only missing or expired
   * values will be loaded.
   *
   * <p>The cache uses multiple threads to load the values in parallel. If there
   * are not sufficient threads available, the load tasks will be queued and executed
   * in a first come first serve manner. The loader thread pool will be also
   * used for refresh operation after an entry is expired, which means, heavy load
   * operation may delay a refresh of an entry.
   *
   * <p>If no loader is defined, the method will throw an immediate exception.
   *
   * <p>After the load is completed, the completion listener will be called, if provided.
   *
   * @param keys The keys to be loaded
   * @param l Listener interface that is invoked upon completion. May be null if no
   *          completion notification is needed.
   * @throws UnsupportedOperationException if no loader is defined
   */
  void loadAll(Iterable<? extends K> keys, LoadCompletedListener l);

  /**
   * Asynchronously loads the given set of keys into the cache. Invokes load for all keys
   * and replaces values already in the cache.
   *
   * <p>The cache uses multiple threads to load the values in parallel. If there
   * are not sufficient threads available, the load tasks will be queued and executed
   * in a first come first serve manner. The loader thread pool will be also
   * used for refresh operation after an entry is expired, which means, heavy load
   * operation may delay a refresh of an entry.
   *
   * <p>If no loader is defined, the method will throw an immediate exception.
   *
   * <p>After the load is completed, the completion listener will be called, if provided.
   *
   * @param keys The keys to be loaded
   * @param l Listener interface that is invoked upon completion. May be null if no
   *          completion notification is needed.
   * @throws UnsupportedOperationException if no loader is defined
   */
  void reloadAll(Iterable<? extends K> keys, LoadCompletedListener l);

  <R> R invoke(K key, CacheEntryProcessor<K, V, R> entryProcessor, Object... args);

  <R> Map<K, EntryProcessingResult<R>> invokeAll(
    Iterable<? extends K> keys, CacheEntryProcessor<K , V, R> entryProcessor, Object... objs);

  /**
   * Disclaimer: This method is here to be able to support known coding similar
   * to JSR107. Do not use it. Just use prefetch() and the normal Cache.get().
   * Since Cache.get() is almost as fast as a HashMap there is no need to
   * build up mini-caches. The caller code will also look much cleaner.
   *
   * <p/>Bulk get: gets all values associated with the keys. If an exception
   * happened during the fetch of any key, this exception will be thrown wrapped
   * into a {@link CacheLoaderException}. If more exceptions exist, the
   * selection is arbitrary.
   *
   * <p/>The cache source does not need to support the bulk operation. It is
   * not guaranteed that the bulk get is called on the cache source if it
   * exists.
   *
   * <p/>The operation may be split into chunks and not performed atomically.
   * The entries that are processed within a chunk will be locked, to avoid
   * duplicate fetches from the cache source. To avoid deadlocks there is a
   * fallback non-bulk operation if a concurrent bulk get is ongoing and
   * the keys overlap.
   *
   * <p/>In contrast to JSR107 the following guarantees are met
   * if the operation returns without exception: map.size() == keys.size().
   * TODO: only if null values are permitted.
   *
   * <p/>Exception handling: The method may terminate normal, even if a cache
   * fetch via cache source failed. In this case the exception will be thrown
   * when the value is requested from the map.
   *
   * @exception CacheLoaderException may be thrown if the fetch fails.
   */
  Map<K, V> getAll(Iterable<? extends K> keys);

  /**
   * Bulk version for {@link #peek(Object)}
   *
   * <p>If the cache permits null values, the map will contain entries
   * mapped to a null value.
   *
   * <p>If the loading of an entry produced an exception, which was not
   * suppressed and is not yet expired. This exception will be thrown
   * as {@link CacheLoaderException} when the entry is accessed
   * via the map interface.
   *
   * @throws ClassCastException if the class of the specified key
   *         prevents it from being stored in this cache
   * @throws NullPointerException if the specified key is null or the
   *         value is null and the cache does not permit null values
   * @throws IllegalArgumentException if some property of the specified key
   *         prevents it from being stored in this cache
   */
  Map<K, V> peekAll(Iterable<? extends K> keys);

  /**
   * Put all elements of the map into the cache.
   */
   void putAll(Map<? extends K, ? extends V> valueMap);

  /**
   * Number of entries the cache stores in total. When iterating the entries
   * the cache will always return less or an identical number of entries.
   *
   * <p>Expired entries may stay in the cache {@link CacheBuilder#keepDataAfterExpired(boolean)}.
   * These entries will be counted, but will not be returned by the iterator.
   *
   * <p>With the upcoming storage extension the heap cache may also store an entry
   * for a not existing mapping in order to not recheck the storage all the time.
   *
   * <p>The method has more statistical value and the result depends on the
   * actual configuration of the cache.
   */
  int getTotalEntryCount();

  /**
   * Iterate all entries in the cache.
   *
   * <p>Contract: All entries present in the cache by the call of the method call will
   * be iterated if not removed during the iteration goes on. The iteration may or may not
   * iterate entries inserted while the iteration is in progress. The iteration never
   * iterates duplicate entries.
   *
   * <p>The iteration is usable concurrently. Concurrent operations will not be
   * influenced. Mutations of the cache, like remove or put, will not stop the iteration.
   *
   * <p>The iterator itself is not thread safe. Calls to one iterator instance from
   * different threads are illegal or need proper synchronization.
   *
   * <p>The iterator holds heap resources to keep track of entries already iterated.
   * If an iteration is aborted, the resources should be freed by calling
   * {@link org.cache2k.ClosableIterator#close}, for example in a try with resources clause.
   *
   * <p>Statistics: Iteration is neutral to the cache statistics. Counting hits for iterated
   * entries would effectively render the hitrate metric meaningless if iterations are used.
   *
   * <p>In case a storage (off heap or persistence) is attached the iterated entries are
   * always inserted into the heap cache. This will affect statistics.
   */
  @Override
  ClosableIterator<CacheEntry<K, V>> iterator();

  void removeAll();

  /**
   * Clear the cache contents
   */
  void clear();

  /**
   * Remove persistent entries, that are not longer needed. Only has an effect
   * if a storage is defined.
   */
  void purge();

  /**
   * Ensure that any transient data is stored in the persistence storage.
   * Nothing will be done if no persistent storage is configured.
   */
  void flush();

  /**
   * @deprecated use {@link #close()}
   */
  void destroy();

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
  void close();

  /**
   * Return the cache manager for this cache instance.
   */
  CacheManager getCacheManager();

  /**
   * True if cache was closed or closing is in progress.
   */
  boolean isClosed();

  /**
   * Returns internal information. This is an expensive operation, since internal statistics are
   * collected. During the call, concurrent operations on the cache may be blocked, to check
   * consistency.
   */
  String toString();

  /**
   * Request an alternative interface for the cache.
   */
  <X> X requestInterface(Class<X> _type);

}
