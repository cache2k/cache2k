package org.cache2k.jcache;

/*
 * #%L
 * cache2k JCache JSR107 implementation
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

import org.cache2k.Cache;
import org.cache2k.impl.BaseCache;

import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author Jens Wilke; created: 2015-03-28
 */
public class Cache2kCacheAdapter<K, V> implements javax.cache.Cache<K, V> {

  Cache2kManagerAdapter manager;
  Cache<K, V> cache;

  public Cache2kCacheAdapter(Cache2kManagerAdapter manager, Cache<K, V> cache) {
    this.manager = manager;
    this.cache = cache;
  }

  @Override
  public V get(K k) {
    return cache.get(k);
  }

  @Override
  public Map<K, V> getAll(Set<? extends K> keys) {
    return cache.getAll(keys);
  }

  @Override
  public boolean containsKey(K key) {
    return cache.contains(key);
  }

  @Override
  public void loadAll(Set<? extends K> keys, boolean replaceExistingValues, CompletionListener completionListener) {
    throw new UnsupportedOperationException("jsr107 loadAll() not supported");
  }

  @Override
  public void put(K k, V v) {
    cache.put(k, v);
  }

  @Override
  public V getAndPut(K key, V value) {
    throw new UnsupportedOperationException("jsr107 getAndPut() not supported");
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> map) {
    throw new UnsupportedOperationException("jsr107 putAll() not supported");
  }

  @Override
  public boolean putIfAbsent(K key, V value) {
    return cache.putIfAbsent(key, value);
  }

  @Override
  public boolean remove(K key) {
    return ((BaseCache) cache).removeWithFlag(key);
  }

  @Override
  public boolean remove(K key, V oldValue) {
    throw new UnsupportedOperationException("jsr107 remove(key, old) not supported");
  }

  @Override
  public V getAndRemove(K key) {
    throw new UnsupportedOperationException("jsr107 getAndRemove(key) not supported");
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    throw new UnsupportedOperationException("jsr107 replace(k, old, new) not supported");
  }

  @Override
  public boolean replace(K key, V value) {
    throw new UnsupportedOperationException("jsr107 replace(k, v) not supported");
  }

  @Override
  public V getAndReplace(K key, V value) {
    throw new UnsupportedOperationException("jsr107 getAndReplace() not supported");
  }

  @Override
  public void removeAll(Set<? extends K> keys) {
    throw new UnsupportedOperationException("jsr107 removeAll(keys) not supported");
  }

  @Override
  public void removeAll() {
    throw new UnsupportedOperationException("jsr107 removeAll() not supported");
  }

  @Override
  public void clear() {
    cache.clear();
  }

  @Override
  public <C extends Configuration<K, V>> C getConfiguration(Class<C> clazz) {
    throw new UnsupportedOperationException("jsr107 getConfiguration() not supported");
  }

  @Override
  public <T> T invoke(K key, EntryProcessor<K, V, T> entryProcessor, Object... arguments) throws EntryProcessorException {
    throw new UnsupportedOperationException("jsr107 invoke() not supported");
  }

  @Override
  public <T> Map<K, EntryProcessorResult<T>> invokeAll(Set<? extends K> keys, EntryProcessor<K, V, T> entryProcessor, Object... arguments) {
    throw new UnsupportedOperationException("jsr107 invokeAll() not supported");
  }

  @Override
  public String getName() {
    return cache.getName();
  }

  @Override
  public CacheManager getCacheManager() {
    return manager;
  }

  @Override
  public void close() {
    cache.close();
  }

  @Override
  public boolean isClosed() {
    throw new UnsupportedOperationException("jsr107 isClosed() not supported");
  }

  @Override
  public <T> T unwrap(Class<T> clazz) {
    throw new UnsupportedOperationException("jsr107 unwrap() not supported");
  }

  @Override
  public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
    throw new UnsupportedOperationException("jsr107 registerCacheEntryListener not supported");
  }

  @Override
  public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
    throw new UnsupportedOperationException("jsr107 deregisterCacheEntryListener not supported");
  }

  @Override
  public Iterator<Entry<K, V>> iterator() {
    throw new UnsupportedOperationException("jsr107 iterator not supported");
  }

}
