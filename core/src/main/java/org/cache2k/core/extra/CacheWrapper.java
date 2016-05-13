package org.cache2k.core.extra;

/*
 * #%L
 * cache2k core
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

import org.cache2k.Cache;
import org.cache2k.CacheEntry;
import org.cache2k.processor.CacheEntryProcessor;
import org.cache2k.CacheManager;
import org.cache2k.processor.EntryProcessingResult;
import org.cache2k.integration.LoadCompletedListener;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wrap a cache and delegate all calls to it. This can be used to intercept methods calls, e.g. for
 * tracing or additional statistics counting, etc.
 */
@SuppressWarnings({"unused", "deprecation"})
public class CacheWrapper<K,V> implements Cache<K, V> {

  Cache<K, V> cache;

  public CacheWrapper(Cache<K, V> cache) {
    this.cache = cache;
  }

  @Override
  public String getName() {
    return cache.getName();
  }

  @Override
  public void clear() {
    cache.clear();
  }

  @Override
  public V get(K key) {
    return cache.get(key);
  }

  @Override
  public CacheEntry<K, V> getEntry(K key) {
    return cache.getEntry(key);
  }

  @Override
  public void prefetch(K key) {
    cache.prefetch(key);
  }

  @Override
  public void prefetch(Iterable<? extends K> keys) {
    cache.prefetch(keys);
  }

  @Override
  public void prefetchAll(Iterable<? extends K> keys) {
    cache.prefetchAll(keys);
  }

  @Override
  public void prefetch(List<? extends K> keys, int _startIndex, int _afterEndIndex) {
    cache.prefetch(keys, _startIndex, _afterEndIndex);
  }

  @Override
  public void loadAll(final Iterable<? extends K> keys, final LoadCompletedListener l) {
    cache.loadAll(keys, l);
  }

  @Override
  public void reloadAll(final Iterable<? extends K> keys, final LoadCompletedListener l) {
    cache.reloadAll(keys, l);
  }

  @Override
  public V peek(K key) {
    return cache.peek(key);
  }

  @Override
  public CacheEntry<K, V> peekEntry(K key) {
    return cache.peekEntry(key);
  }

  @Override
  public boolean contains(K key) {
    return cache.contains(key);
  }

  @Override
  public boolean containsKey(K key) {
    return cache.containsKey(key);
  }

  @Override
  public void put(K key, V value) {
    cache.put(key, value);
  }

  @Override
  public boolean putIfAbsent(K key, V value) {
    return cache.putIfAbsent(key, value);
  }

  @Override
  public V peekAndReplace(K key, V _value) {
    return cache.peekAndReplace(key, _value);
  }

  @Override
  public boolean replace(K key, V _newValue) {
    return cache.replace(key, _newValue);
  }

  @Override
  public boolean replaceIfEquals(K key, V _oldValue, V _newValue) {
    return cache.replaceIfEquals(key, _oldValue, _newValue);
  }

  @Override
  public V peekAndRemove(K key) {
    return cache.peekAndRemove(key);
  }

  @Override
  public V peekAndPut(K key, V value) {
    return cache.peekAndPut(key, value);
  }

  @Override
  public void remove(K key) {
    cache.remove(key);
  }

  @Override
  public boolean containsAndRemove(K key) { return cache.containsAndRemove(key); }

  @Override
  public boolean removeIfEquals(K key, V value) {
    return cache.removeIfEquals(key, value);
  }

  @Override
  public void removeAllAtOnce(Set<K> key) {
    cache.removeAllAtOnce(key);
  }

  @Override
  public <R> R invoke(K key, CacheEntryProcessor<K, V, R> entryProcessor, Object... args) {
    return cache.invoke(key, entryProcessor, args);
  }

  @Override
  public <R> Map<K, EntryProcessingResult<R>> invokeAll(Iterable<? extends K> keys, CacheEntryProcessor<K, V, R> entryProcessor, Object... objs) {
    return cache.invokeAll(keys, entryProcessor, objs);
  }

  @Override
  public void expire(final K key, final long millis) {
    cache.expire(key, millis);
  }

  @Override
  public Map<K, V> getAll(Iterable<? extends K> keys) {
    return cache.getAll(keys);
  }

  @Override
  public Map<K, V> peekAll(Iterable<? extends K> keys) {
    return cache.peekAll(keys);
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    cache.putAll(m);
  }

  @Override
  public int getTotalEntryCount() {
    return cache.getTotalEntryCount();
  }

  @Override
  public Iterator<CacheEntry<K, V>> iterator() {
    return cache.iterator();
  }

  @Override
  public void removeAll(Iterable<? extends K> keys) {
    cache.removeAll(keys);
  }

  @Override
  public void removeAll() {
    cache.removeAll();
  }

  @Override
  public void purge() {
    cache.purge();
  }

  @Override
  public void flush() {
    cache.flush();
  }

  @Override
  public void destroy() {
    cache.destroy();
  }

  @Override
  public void close() {
    cache.close();
  }

  @Override
  public CacheManager getCacheManager() {
    return cache.getCacheManager();
  }

  @Override
  public boolean isClosed() {
    return cache.isClosed();
  }

  @Override
  public String toString() {
    return cache.toString();
  }

  @Override
  public <X> X requestInterface(Class<X> _type) {
    return cache.requestInterface(_type);
  }

  public Cache<K, V> getWrappedCache() {
    return cache;
  }

}
