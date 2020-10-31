package org.cache2k;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
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

import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.io.CacheLoader;
import org.cache2k.io.CacheWriter;
import org.cache2k.io.CacheLoaderException;
import org.cache2k.io.CacheWriterException;
import org.cache2k.processor.EntryProcessingException;
import org.cache2k.processor.EntryProcessor;
import org.cache2k.processor.EntryProcessingResult;
import org.cache2k.processor.MutableCacheEntry;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/* Credits
 *
 * Descriptions derive partly from the java.util.concurrent.ConcurrentMap.
 * Original copyright:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 *
 * Some inspiration is also from the JSR107 Java Caching standard.
 */

/**
 * A cache is similar to a map or a key value store, allowing to retrieve and
 * update values which are associated to keys. In contrast to a {@code HashMap} the
 * cache allows concurrent access and modification to its content and
 * automatically controls the amount of entries in the cache to stay within
 * configured resource limits.
 *
 * <p>A cache can be obtained via a {@link Cache2kBuilder}, for example:
 *
 * <pre>{@code
 *    Cache<Long, List<String>> cache =
 *      new Cache2kBuilder<Long, List<String>>() {}
 *        .name("myCache")
 *        .eternal(true)
 *        .build();
 * }</pre>
 *
 * <p><b>Basic operation:</b> To mutate and retrieve the cache content the operations
 * {@link #put} and {@link #peek} can be used, for example:
 *
 * <pre>{@code
 *    cache.put(1, "one");
 *    cache.put(2, "two");
 *    // might fail:
 *    assertTrue(cache.containsKey(1));
 *    assertEquals("two", cache.peek(2));
 * }</pre>
 *
 * It is important to note that the two assertion in the above example may fail.
 * A cache has not the same guarantees as a data storage, because it needs to remove
 * content automatically as soon as resource limits are reached. This is called <em>eviction</em>.
 *
 * <p><b>Populating:</b> A cache may automatically populate its contents via a {@link CacheLoader}.
 * For typical read mostly caching this has several advantages,
 * for details see {@link CacheLoader}. When using a cache loader the
 * additional methods for mutating the cache directly may not be needed. Some
 * methods, that do not interact with the loader such as {@link #containsKey}
 * may be false friends. To make the code more obvious and protect against
 * the accidental use of methods that do not invoke the loader transparently
 * a subset interface, for example the {@link KeyValueSource} can be used.
 *
 * <p><b>CAS-Operations:</b> The cache has a set of operations that examine an entry
 * and do a mutation in an atomic way, for example {@link #putIfAbsent}, {@link #containsAndRemove}
 * and {@link #replaceIfEquals}. To allow arbitrary semantics that operate atomically on an
 * {@link EntryProcessor} can be implemented and executed via {@link Cache#invoke}.
 *
 * <p><b>Compatibility:</b> Future versions of cache2k may introduce new methods to this interface.
 * To improve upward compatibility applications that need to implement this interface should use
 * {@link AbstractCache} or {@link ForwardingCache}.
 *
 * @param <K> type of the key
 * @param <V> type of the stores values
 * @author Jens Wilke
 * @see Cache2kBuilder to create a cache
 * @see CacheManager to manage and retrieve created caches
 * @see <a href="https://cache2k.org>cache2k homepage</a>
 * @see <a href="https://cache2k.org/docs/latest/user-guide.html">cache2k User Guide</a>
 */
@SuppressWarnings("UnusedDeclaration")
public interface Cache<K, V> extends KeyValueStore<K, V>, Closeable {

  /**
   * A configured or generated name of this cache instance. A cache in close state will still
   * return its name.
   *
   * @see Cache2kBuilder#name(String)
   * @return name of this cache
   */
  String getName();

  /**
   * Returns a value associated with the given key.  If no value is present or it
   * is expired the cache loader is invoked, if configured, or {@code null} is returned.
   *
   * <p>If the {@link CacheLoader} is invoked, subsequent requests of the same key will block
   * until the loading is completed. Details see {@link CacheLoader}.
   *
   * <p>As an alternative {@link #peek} can be used if the loader should
   * not be invoked.
   *
   * @param key key with which the specified value is associated
   * @return the value associated with the specified key, or
   *         {@code null} if there was no mapping for the key.
   *         (If nulls are permitted a {@code null} can also indicate that the cache
   *         previously associated {@code null} with the key)
   * @throws ClassCastException if the class of the specified key
   *         prevents it from being stored in this cache
   * @throws NullPointerException if the specified key is {@code null}
   * @throws IllegalArgumentException if some property of the specified key
   *         prevents it from being stored in this cache
   * @throws CacheLoaderException if the loading produced an exception .
   */
  @Override
  V get(K key);

  /**
   * Returns an entry that contains the cache value associated with the given key.
   * If no entry is present or the value is expired, either the loader is invoked
   * or {@code null} is returned.
   *
   * <p>If the loader is invoked, subsequent requests of the same key will block
   * until the loading is completed, details see {@link CacheLoader}
   *
   * <p>In case the cache loader yields an exception, the entry object will
   * be returned. The exception can be retrieved via {@link CacheEntry#getException()}.
   *
   * <p>If {@code null} values are present the method can be used to
   * check for an existent mapping and retrieve the value in one API call.
   *
   * <p>The alternative method {@link #peekEntry} can be used if the loader
   * should not be invoked.
   *
   * @param key key to retrieve the associated with the cache entry
   * @throws ClassCastException if the class of the specified key
   *         prevents it from being stored in this cache
   * @throws NullPointerException if the specified key is null
   * @throws IllegalArgumentException if some property of the specified key
   *         prevents it from being stored in this cache
   * @return An entry representing the cache mapping. Multiple calls for the same key may
   *          return different instances of the entry object.
   */
  CacheEntry<K, V> getEntry(K key);

  /**
   * Returns the value associated to the given key.
   *
   * <p>In contrast to {@link #get(Object)} this method solely operates
   * on the cache content and does not invoke the {@linkplain CacheLoader cache loader}.
   *
   * <p>API rationale: Consequently all methods that do not invoke the loader
   * but return a value or a cache entry are prefixed with {@code peek} within this interface
   * to make the different semantics immediately obvious by the name.
   *
   * @param key key with which the specified value is associated
   * @return the value associated with the specified key, or
   *         {@code null} if there was no mapping for the key.
   *         (If nulls are permitted a {@code null} can also indicate that the cache
   *         previously associated {@code null} with the key)
   * @throws ClassCastException if the class of the specified key
   *         prevents it from being stored in this cache
   * @throws NullPointerException if the specified key is null
   * @throws IllegalArgumentException if some property of the specified key
   *         prevents it from being stored in this cache
   * @throws CacheLoaderException if the loading produced an exception .
   */
  V peek(K key);

  /**
   * Returns an entry that contains the cache value associated with the given key.
   * If no entry is present or the value is expired, {@code null} is returned.
   * The {@linkplain CacheLoader cache loader} will not be invoked by this method.
   *
   * <p>In case an exception is present, for example from a load operation carried out
   * previously, the entry object will be returned. The exception can be
   * retrieved via {@link CacheEntry#getException()}.
   *
   * <p>If {@code null} values are present the method can be used to
   * check for an existent mapping and retrieve the value in one API call.
   *
   * @param key key to retrieve the associated with the cache entry
   * @throws ClassCastException if the class of the specified key
   *         prevents it from being stored in this cache
   * @throws NullPointerException if the specified key is null
   * @throws IllegalArgumentException if some property of the specified key
   *         prevents it from being stored in this cache
   * @return An entry representing the cache mapping. Multiple calls for the same key may
   *          return different instances of the entry object.
   */
  CacheEntry<K, V> peekEntry(K key);

  /**
   * Returns {@code true}, if there is a mapping for the specified key.
   *
   * <p>Effect on statistics: The operation does increase the usage counter if a mapping is present,
   * but does not count as read and therefore does not influence miss or hit values.
   *
   * @param key key which association should be checked
   * @return {@code true}, if this cache contains a mapping for the specified
   *         key
   * @throws ClassCastException if the key is of an inappropriate type for
   *         this cache
   * @throws NullPointerException if the specified key is null
   */
  boolean containsKey(K key);

  /**
   * Inserts a new value associated with the given key or updates an
   * existing association of the same key with the new value.
   *
   * <p>If an {@link ExpiryPolicy} is specified in the
   * cache configuration it is called and will determine the expiry time.
   * If a {@link CacheWriter} is registered, then it is called with the
   * new value. If the {@link ExpiryPolicy} or {@link CacheWriter}
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
  @Override
  void put(K key, V value);

  /**
   * If the specified key is not already associated with a value (or exception),
   * call the provided task and associate it with the returned value. This is equivalent to
   *
   *  <pre> {@code
   * if (!cache.containsKey(key)) {
   *   V value = callable.call();
   *   cache.put(key, value);
   *   return value;
   * } else {
   *   return cache.peek(key);
   * }}</pre>
   *
   * except that the action is performed atomically.
   *
   * <p>See {@link #put(Object, Object)} for the effects on the cache writer and
   * expiry calculation.
   *
   * <p>Statistics: If an entry exists this operation counts as a hit, if the entry
   * is missing, a miss and put is counted.
   *
   * <p>Exceptions: If call throws an exception the cache contents will
   * not be modified and the exception is propagated. The customized exception propagator is not
   * used for this method.
   *
   * <p>Rationale: The {@code Function} interface that {@code Map.computeIfAbsent} uses is only
   * available in Java 8. {@code Callable} is a useful fallback and we can use it directly
   * for the Spring integration. A mismatch is that {@code Callable.call()} declares a checked
   * exception but the cache access method do not.
   *
   * @param key key with which the specified value is to be associated
   * @param callable task that computes the value
   * @return the cached value or the result of the compute operation if no mapping is present
   * @throws CacheLoaderException if a checked exception is thrown it is wrapped into a
   *         {@code CacheLoaderException}
   * @throws RuntimeException in case {@link Callable#call} yields a runtime exception,
   *         this is thrown directly
   * @throws ClassCastException if the class of the specified key or value
   *         prevents it from being stored in this cache
   * @throws NullPointerException if the specified key is {@code null} or the
   *         value is {@code null} and the cache does not permit {@code null} values
   * @throws IllegalArgumentException if some property of the specified key
   *         or value prevents it from being stored in this cache
   */
  V computeIfAbsent(K key, Callable<V> callable);

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
   * {@link #containsAndRemove(Object)} and {@link #containsKey(Object)} that don't update
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
   * if (cache.containsKey(key)) {
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
   * if (cache.containsKey(key)) {
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
   * {@link #containsAndRemove(Object)} and {@link #containsKey(Object)} that don't update
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
   * if (cache.containsKey(key) && Objects.equals(cache.get(key), oldValue)) {
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
   * @return the previous value associated with the specified key, or
   *         {@code null} if there was no mapping for the key.
   *         (A {@code null} can also indicate that the cache
   *         previously associated the value {@code null} with the key)
   * @throws NullPointerException if a specified key is null
   * @throws ClassCastException if the key is of an inappropriate type for
   *         the cache. This check is optional depending on the cache
   *         configuration.
   */
  V peekAndRemove(K key);

  /**
   * Removes the mapping for a key from the cache and returns {@code true} if it
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
   * <p>If a writer is registered {@link CacheWriter#delete(Object)} will get called.
   *
   * <p>These alternative versions of the remove operation exist:
   * <ul>
   *   <li>{@link #containsAndRemove(Object)}, returning a success flag</li>
   *   <li>{@link #peekAndRemove(Object)}, returning the removed value</li>
   *   <li>{@link #removeIfEquals(Object, Object)}, conditional removal matching on the current
   *   value</li>
   * </ul>
   *
   * <p>See {@link KeyValueStore#remove(Object)}, for an explanation why no flag or object is
   * returned.
   *
   * @param key key which mapping is to be removed from the cache, not null
   * @throws NullPointerException if a specified key is null
   * @throws ClassCastException if the key is of an inappropriate type for
   *         this map
   * @throws CacheWriterException if the writer call failed
   */
  @Override
  void remove(K key);

  /**
   * Remove the mapping if the stored value is equal to the comparison value.
   *
   * <p>If no mapping exists, this method will do nothing and return {@code false}, even
   * if the tested value is {@code null}.
   *
   * @param key key whose mapping is to be removed from the cache
   * @param expectedValue value that must match with the existing value in the cache.
   *                      It is also possible to check whether the value is {@code null}.
   * @throws NullPointerException if a specified key is {@code null}
   * @throws ClassCastException if the key is of an inappropriate type for
   *         this map
   * @return {@code true}, if mapping was removed
   */
  boolean removeIfEquals(K key, V expectedValue);

  /**
   * Removes a set of keys. This has the same semantics of calling
   * remove to every key, except that the cache is trying to optimize the
   * bulk operation.
   *
   * @param keys a set of keys to remove
   * @throws NullPointerException if a specified key is null
   */
  @Override
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
   * @return the previous value associated with the specified key, or
   *         {@code null} if there was no mapping for the key.
   *         (A {@code null} return can also indicate that the cache
   *         previously associated {@code null} with the key)
   * @throws ClassCastException if the class of the specified key or value
   *         prevents it from being stored in this cache.
   * @throws NullPointerException if the specified key is null or the
   *         value is null and the cache does not permit null values
   * @throws IllegalArgumentException if some property of the specified key
   *         or value prevents it from being stored in this cache.
   */
  V peekAndPut(K key, V value);

  /**
   * Updates an existing not expired mapping to expire at the given point in time.
   * If there is no mapping associated with the key or it is already expired, this
   * operation has no effect. The special values {@link org.cache2k.expiry.Expiry#NOW} and
   * {@link org.cache2k.expiry.Expiry#REFRESH} also effect an entry that was just
   * refreshed.
   *
   * <p>If the expiry time is in the past, the entry will expire immediately and
   * refresh ahead is triggered, if enabled.
   *
   * <p>Although the special time value {@link org.cache2k.expiry.Expiry#NOW} will lead
   * to an effective removal of the cache entry, the writer is not called, since the
   * method is for cache control only.
   *
   * <p>The cache must be configured with a {@link ExpiryPolicy} or
   * {@link Cache2kBuilder#expireAfterWrite(long, TimeUnit)} otherwise expiry
   * timing is not available and this method will throw an exception. An immediate expire
   * via {@link org.cache2k.expiry.Expiry#NOW} is always working.
   *
   * @param key key with which the specified value is associated
   * @param millis Time in milliseconds since epoch when the entry should expire.
   *               Also see {@link ExpiryTimeValues}
   * @throws IllegalArgumentException if no expiry was enabled during cache setup.
   * @deprecated To be removed in 2.2. Use {@link #invoke(Object, EntryProcessor)}
   */
  @Deprecated
  void expireAt(K key, long millis);

  /**
   * Asynchronously loads the given set of keys into the cache. Only missing or expired
   * values will be loaded.
   *
   * <p>The cache uses multiple threads to load the values in parallel. If thread resources
   * are not sufficient, meaning the used executor is throwing
   * {@link java.util.concurrent.RejectedExecutionException} the calling thread is used to produce
   * back pressure.
   *
   * <p>If no loader is defined, the method will throw an immediate exception.
   *
   * <p>After the load is completed, the completion listener will be called, if provided.
   *
   * @param keys The keys to be loaded
   * @param listener Listener interface that is invoked upon completion. May be {@code null} if no
   *          completion notification is needed.
   * @throws UnsupportedOperationException if no loader is defined
   * @deprecated to be removed in 2.2
   */
  @Deprecated
  void loadAll(Iterable<? extends K> keys, CacheOperationCompletionListener listener);

  /**
   * Asynchronously loads the given set of keys into the cache. Always invokes load for all keys
   * and replaces values already in the cache.
   *
   * <p>The cache uses multiple threads to load the values in parallel. If thread resources
   * are not sufficient, meaning if the used executor is throwing
   * {@link java.util.concurrent.RejectedExecutionException} the calling thread is used to produce
   * back pressure.
   *
   * <p>If no loader is defined, the method will throw an immediate exception.
   *
   * <p>After the load is completed, the completion listener will be called, if provided.
   *
   * @param keys The keys to be loaded
   * @param listener Listener interface that is invoked upon completion. May be {@code null} if no
   *          completion notification is needed.
   * @throws UnsupportedOperationException if no loader is defined
   * @deprecated to be removed in 2.2
   */
  @Deprecated
  void reloadAll(Iterable<? extends K> keys, CacheOperationCompletionListener listener);

  /**
   * Asynchronously loads the given set of keys into the cache. Only missing or expired
   * values will be loaded.
   *
   * <p>The cache uses multiple threads to load the values in parallel. If thread resources
   * are not sufficient, meaning the used executor is throwing
   * {@link java.util.concurrent.RejectedExecutionException} the calling thread is used to produce
   * back pressure.
   *
   * <p>If no loader is defined, the method will throw an immediate exception.
   *
   * <p>After the load is completed, the completion listener will be called, if provided.
   *
   * @param keys The keys to be loaded
   * @return future getting notified on completion
   * @throws UnsupportedOperationException if no loader is defined
   */
  CompletableFuture<Void> loadAll(Iterable<? extends K> keys);

  /**
   * Asynchronously loads the given set of keys into the cache. Always invokes load for all keys
   * and replaces values already in the cache.
   *
   * <p>The cache uses multiple threads to load the values in parallel. If thread resources
   * are not sufficient, meaning the used executor is throwing
   * {@link java.util.concurrent.RejectedExecutionException} the calling thread is used to produce
   * back pressure.
   *
   * <p>If no loader is defined, the method will throw an immediate exception.
   *
   * <p>After the load is completed, the completion listener will be called, if provided.
   *
   * @param keys The keys to be loaded
   * @return future getting notified on completion
   * @throws UnsupportedOperationException if no loader is defined
   */
  CompletableFuture<Void> reloadAll(Iterable<? extends K> keys);

  /**
   * Invoke a user defined function on a cache entry.
   *
   * For examples and further details consult the documentation of {@link EntryProcessor}
   * and {@link org.cache2k.processor.MutableCacheEntry}.
   *
   * @param key the key of the cache entry that should be processed
   * @param entryProcessor processor instance to be invoked
   * @param <R> type of the result
   * @throws EntryProcessingException if an exception happened inside
   *         {@link EntryProcessor#process(MutableCacheEntry)}
   * @return result provided by the entry processor
   * @see EntryProcessor
   * @see org.cache2k.processor.MutableCacheEntry
   */
  <R> R invoke(K key, EntryProcessor<K, V, R> entryProcessor);

  /**
   * Invoke a user defined function on multiple cache entries specified by the
   * {@code keys} parameter.
   *
   * <p>The order of the invocation is unspecified. To speed up processing the cache
   * may invoke the entry processor in parallel.
   *
   * For examples and further details consult the documentation of {@link EntryProcessor}
   * and {@link org.cache2k.processor.MutableCacheEntry}.
   *
   * @param keys the keys of the cache entries that should be processed
   * @param entryProcessor processor instance to be invoked
   * @param <R> type of the result
   * @return map containing the invocation results for every cache key
   * @see EntryProcessor
   * @see org.cache2k.processor.MutableCacheEntry
   */
  <R> Map<K, EntryProcessingResult<R>> invokeAll(
    Iterable<? extends K> keys, EntryProcessor<K, V, R> entryProcessor);

  /**
   * Retrieve values from the cache associated with the provided keys. If the
   * value is not yet in the cache, the loader is invoked.
   *
   * <p>Executing the request, the cache may do optimizations like
   * utilizing multiple threads for invoking the loader or using the bulk
   * methods on the loader. This is not yet fully exploited and will improve
   * with further cache2k releases.
   *
   * <p>Exception handling: The method may terminate normal, even if the cache
   * loader failed to provide values for some keys. The cache will generally
   * do everything to delay the propagation of the exception until the key is requested,
   * to be most specific. If the loader has permanent failures this method may
   * throw an exception immediately.
   *
   * <p>The operation is not performed atomically.
   *
   * @throws NullPointerException if one of the specified keys is null
   * @throws CacheLoaderException in case the loader has permanent failures.
   *            Otherwise the exception is thrown when the key is requested.
   */
  @Override
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
   * <p>The operation is not performed atomically. Mutations of the cache during
   * this operation may or may not affect the result.
   *
   * @throws NullPointerException if one of the specified keys is null
   * @throws IllegalArgumentException if some property of the specified key
   *         prevents it from being stored in this cache
   */
  Map<K, V> peekAll(Iterable<? extends K> keys);

  /**
   * Insert all elements of the map into the cache.
   *
   * <p>See {@link Cache#put(Object, Object)} for information about the
   * interaction with the {@link CacheWriter} and {@link ExpiryPolicy}
   *
   * @param valueMap Map of keys with associated values to be inserted in the cache
   * @throws NullPointerException if one of the specified keys is null
   */
  @Override
   void putAll(Map<? extends K, ? extends V> valueMap);

  /**
   * Iterate all keys in the cache.
   *
   * <p>Contract: The iteration is usable while concurrent operations happen on the cache.
   * All entry keys will be iterated when present in the cache at the moment
   * of the call to {@link Iterable#iterator()}. An expiration or mutation
   * happening during the iteration, may or may not be reflected. Separate calls to
   * {@link Iterable#iterator()} to the identical {@code Iterable} instance start
   * a separate iteration. It is ensured that every key is only iterated once.
   *
   * <p>The iterator itself is not thread safe. Calls to one iterator instance from
   * different threads are illegal or need proper synchronization.
   *
   * <p><b>Statistics:</b> Iteration is neutral to the cache statistics.
   *
   * <p><b>Efficiency:</b> Iterating keys is faster as iterating complete entries.
   */
  Iterable<K> keys();

  /**
   * Iterate all entries in the cache.
   *
   * <p>See {@link #keys()} for the general iterator contract.
   *
   * <p><b>Efficiency:</b> Iterating entries is less efficient then just iterating keys. The cache
   * needs to create a new entry object and employ some sort of synchronisation to supply a
   * consistent and immutable entry.
   *
   * @see #keys()
   */
  Iterable<CacheEntry<K, V>> entries();

  /**
   * Removes all cache contents. This has the same semantics of calling
   * remove to every key, except that the cache is trying to optimize the
   * bulk operation. Same as {@code clear} but listeners will be called.
   */
  void removeAll();

  /**
   * Clear the cache in a fast way, causing minimal disruption. Not calling the listeners.
   */
  void clear();

  /**
   * Release resources in the local VM and remove the cache from the CacheManager.
   *
   * <p>The method is designed to free resources and finish operations as gracefully and fast
   * as possible. Some cache operations take an unpredictable long time such as the call of
   * the {@link CacheLoader}, so it may happen that the cache still has threads
   * in use when this method returns.
   *
   * <p>After close, subsequent cache operations will throw a {@link IllegalStateException}.
   * Cache operations currently in progress, may or may not be terminated with an exception.
   * A subsequent call to close will not throw an exception.
   *
   * <p>If all caches need to be closed it is more effective to use {@link CacheManager#close()}
   */
  void close();

  /**
   * Return the cache manager for this cache instance.
   */
  CacheManager getCacheManager();

  /**
   * Returns {@code true} if cache was closed or closing is in progress.
   */
  boolean isClosed();

  /**
   * Returns internal information. This is an expensive operation, since internal statistics are
   * collected. During the call, concurrent operations on the cache may be blocked. This method will
   * not throw the {@link IllegalStateException} in case the cache is closed, but return the
   * solely the cache name and no statistics.
   */
  String toString();

  /**
   * Request an alternative interface for this cache instance.
   */
  <T> T requestInterface(Class<T> type);

  /**
   * Returns a map interface for operating with this cache. Operations on the map
   * affect the cache directly, as well as modifications on the cache will affect the map.
   *
   * <p>The returned map supports {@code null} values if enabled via
   * {@link Cache2kBuilder#permitNullValues(boolean)}.
   *
   * <p>The {@code equals} and {@code hashCode} methods of the {@code Map} are forwarded to the
   * cache. A map is considered identical when from the same cache instance. This is not compatible
   * to the general {@code Map} contract.
   *
   * <p>Operations on the map do not invoke the loader.
   *
   * <p>Multiple calls to this method return a new object instance which is a wrapper of the cache
   * instance. Calling this method is a cheap operation.
   *
   * @return {@code ConcurrentMap} wrapper for this cache instance
   */
  ConcurrentMap<K, V> asMap();

}
