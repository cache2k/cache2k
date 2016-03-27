package org.cache2k.extra;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.Cache;
import org.cache2k.CacheEntry;
import org.cache2k.processor.CacheEntryProcessor;
import org.cache2k.CacheManager;
import org.cache2k.ClosableIterator;
import org.cache2k.processor.EntryProcessingResult;
import org.cache2k.integration.LoadCompletedListener;

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
  public <R> Map<K, EntryProcessingResult<R>> invokeAll(Set<? extends K> keys, CacheEntryProcessor<K, V, R> entryProcessor, Object... objs) {
    return cache.invokeAll(keys, entryProcessor, objs);
  }

  @Override
  public Map<K, V> getAll(Iterable<? extends K> keys) {
    return cache.getAll(keys);
  }

  @Override
  public Map<K, V> peekAll(Set<? extends K> keys) {
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
  public ClosableIterator<CacheEntry<K, V>> iterator() {
    return cache.iterator();
  }

  @Override
  public void removeAll(Set<? extends K> keys) {
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
