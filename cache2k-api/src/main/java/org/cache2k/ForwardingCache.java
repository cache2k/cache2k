package org.cache2k;

/*
 * #%L
 * cache2k API
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

import org.cache2k.jmx.CacheInfoMXBean;
import org.cache2k.processor.EntryProcessingResult;
import org.cache2k.processor.EntryProcessor;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;

/**
 * Wrapper class that forwards all method calls to a delegate. Can be used to implement extensions that
 * need to intercept calls to the cache.
 *
 * @author Jens Wilke
 */
public abstract class ForwardingCache<K, V> implements Cache<K, V> {

  /**
   * Subclasses need to implement this method which specifies the delegation
   * target.
   */
  protected abstract Cache<K, V> delegate();

  @Override
  public String getName() {
    return delegate().getName();
  }

  @Override
  public V get(final K key) {
    return delegate().get(key);
  }

  @Override
  public CacheEntry<K, V> getEntry(final K key) {
    return delegate().getEntry(key);
  }

  @Override
  public void prefetch(final K key) {
    delegate().prefetch(key);
  }

  @Override
  public void prefetchAll(final Iterable<? extends K> keys, final CacheOperationCompletionListener listener) {
    delegate().prefetchAll(keys, listener);
  }

  @Override
  public V peek(final K key) {
    return delegate().peek(key);
  }

  @Override
  public CacheEntry<K, V> peekEntry(final K key) {
    return delegate().peekEntry(key);
  }

  @Override
  public boolean containsKey(final K key) {
    return delegate().containsKey(key);
  }

  @Override
  public void put(final K key, final V value) {
    delegate().put(key, value);
  }

  @Override
  public V computeIfAbsent(final K key, final Callable<V> callable) {
    return delegate().computeIfAbsent(key, callable);
  }

  @Override
  public boolean putIfAbsent(final K key, final V value) {
    return delegate().putIfAbsent(key, value);
  }

  @Override
  public V peekAndReplace(final K key, final V value) {
    return delegate().peekAndReplace(key, value);
  }

  @Override
  public boolean replace(final K key, final V value) {
    return delegate().replace(key, value);
  }

  @Override
  public boolean replaceIfEquals(final K key, final V oldValue, final V newValue) {
    return delegate().replaceIfEquals(key, oldValue, newValue);
  }

  @Override
  public V peekAndRemove(final K key) {
    return delegate().peekAndRemove(key);
  }

  @Override
  public boolean containsAndRemove(final K key) {
    return delegate().containsAndRemove(key);
  }

  @Override
  public void remove(final K key) {
    delegate().remove(key);
  }

  @Override
  public boolean removeIfEquals(final K key, final V expectedValue) {
    return delegate().removeIfEquals(key, expectedValue);
  }

  @Override
  public void removeAll(final Iterable<? extends K> keys) {
    delegate().removeAll(keys);
  }

  @Override
  public V peekAndPut(final K key, final V value) {
    return delegate().peekAndPut(key, value);
  }

  @Override
  public void expireAt(final K key, final long millis) {
    delegate().expireAt(key, millis);
  }

  @Override
  public void loadAll(final Iterable<? extends K> keys, final CacheOperationCompletionListener listener) {
    delegate().loadAll(keys, listener);
  }

  @Override
  public void reloadAll(final Iterable<? extends K> keys, final CacheOperationCompletionListener listener) {
    delegate().reloadAll(keys, listener);
  }

  @Override
  public <R> R invoke(final K key, final EntryProcessor<K, V, R> entryProcessor) {
    return delegate().invoke(key, entryProcessor);
  }

  @Override
  public <R> Map<K, EntryProcessingResult<R>> invokeAll(final Iterable<? extends K> keys, final EntryProcessor<K, V, R> entryProcessor) {
    return delegate().invokeAll(keys, entryProcessor);
  }

  @Override
  public Map<K, V> getAll(final Iterable<? extends K> keys) {
    return delegate().getAll(keys);
  }

  @Override
  public Map<K, V> peekAll(final Iterable<? extends K> keys) {
    return delegate().peekAll(keys);
  }

  @Override
  public void putAll(final Map<? extends K, ? extends V> valueMap) {
    delegate().putAll(valueMap);
  }

  @Override
  public Iterable<K> keys() {
    return delegate().keys();
  }

  @Override
  public Iterable<CacheEntry<K, V>> entries() {
    return delegate().entries();
  }

  @Override
  public void removeAll() {
    delegate().removeAll();
  }

  @Override
  public void clear() {
    delegate().clear();
  }

  @Override
  public void clearAndClose() {
    delegate().clearAndClose();
  }

  @Override
  public void close() {
    delegate().close();
  }

  @Override
  public CacheManager getCacheManager() {
    return delegate().getCacheManager();
  }

  @Override
  public boolean isClosed() {
    return delegate().isClosed();
  }

  /**
   * Forwards to delegate but adds the simple class name to the output.
   */
  @Override
  public String toString() {
    return this.getClass().getSimpleName() + "!" + delegate().toString();
  }

  @Override
  public <X> X requestInterface(final Class<X> _type) {
    return delegate().requestInterface(_type);
  }

  @Override
  public ConcurrentMap<K, V> asMap() {
    return delegate().asMap();
  }

  @Override
  public CacheInfoMXBean getStatistics() {
    return delegate().getStatistics();
  }

}
