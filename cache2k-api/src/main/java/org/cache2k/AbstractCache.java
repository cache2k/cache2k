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

import org.cache2k.processor.EntryProcessingResult;
import org.cache2k.processor.EntryProcessor;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

/**
 * Base class for implementations of the cache interface. By default every methods throws
 * {@link UnsupportedOperationException}.
 *
 * @author Jens Wilke
 */
public class AbstractCache<K, V> implements Cache<K, V> {

  @Override
  public String getName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public V get(K key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CacheEntry<K, V> getEntry(K key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public V peek(K key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CacheEntry<K, V> peekEntry(K key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsKey(K key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void put(K key, V value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public V computeIfAbsent(K key, Callable<V> callable) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean putIfAbsent(K key, V value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public V peekAndReplace(K key, V value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean replace(K key, V value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean replaceIfEquals(K key, V oldValue, V newValue) {
    throw new UnsupportedOperationException();
  }

  @Override
  public V peekAndRemove(K key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsAndRemove(K key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void remove(K key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeIfEquals(K key, V expectedValue) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeAll(Iterable<? extends K> keys) {
    throw new UnsupportedOperationException();
  }

  @Override
  public V peekAndPut(K key, V value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void expireAt(K key, long millis) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void loadAll(Iterable<? extends K> keys, CacheOperationCompletionListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void reloadAll(Iterable<? extends K> keys, CacheOperationCompletionListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<Void> loadAll(Iterable<? extends K> keys) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<Void> reloadAll(Iterable<? extends K> keys) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <R> R invoke(K key, EntryProcessor<K, V, R> entryProcessor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <R> Map<K, EntryProcessingResult<R>> invokeAll(
    Iterable<? extends K> keys, EntryProcessor<K, V, R> entryProcessor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<K, V> getAll(Iterable<? extends K> keys) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<K, V> peekAll(Iterable<? extends K> keys) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> valueMap) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Iterable<K> keys() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Iterable<CacheEntry<K, V>> entries() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeAll() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void close() {
    throw new UnsupportedOperationException();
  }

  @Override
  public CacheManager getCacheManager() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isClosed() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ConcurrentMap<K, V> asMap() {
    throw new UnsupportedOperationException();
  }

  @Override
  public <X> X requestInterface(Class<X> type) {
    throw new UnsupportedOperationException();
  }

}
