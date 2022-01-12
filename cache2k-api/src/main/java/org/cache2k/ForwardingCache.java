package org.cache2k;

/*-
 * #%L
 * cache2k API
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

import org.cache2k.annotation.NonNull;
import org.cache2k.annotation.Nullable;
import org.cache2k.processor.EntryMutator;
import org.cache2k.processor.EntryProcessingResult;
import org.cache2k.processor.EntryProcessor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Wrapper class that forwards all method calls to a delegate. Can be used to implement extensions
 * that need to intercept calls to the cache.
 *
 * @author Jens Wilke
 */
public abstract class ForwardingCache<K, V> implements Cache<K, V> {

  /**
   * Subclasses need to implement this method which specifies the delegation
   * target.
   */
  protected abstract @NonNull Cache<K, V> delegate();

  @Override
  public String getName() {
    return delegate().getName();
  }

  @Override
  public @Nullable V get(K key) {
    return delegate().get(key);
  }

  @Override
  public @Nullable CacheEntry<K, V> getEntry(K key) {
    return delegate().getEntry(key);
  }

  @Override
  public @Nullable V peek(K key) {
    return delegate().peek(key);
  }

  @Override
  public @Nullable CacheEntry<K, V> peekEntry(K key) {
    return delegate().peekEntry(key);
  }

  @Override
  public boolean containsKey(K key) {
    return delegate().containsKey(key);
  }

  @Override
  public void put(K key, V value) {
    delegate().put(key, value);
  }

  @Override
  public V computeIfAbsent(K key, Function<? super K, ? extends V> function) {
    return delegate().computeIfAbsent(key, function);
  }

  @Override
  public boolean putIfAbsent(K key, V value) {
    return delegate().putIfAbsent(key, value);
  }

  @Override
  public @Nullable V peekAndReplace(K key, V value) {
    return delegate().peekAndReplace(key, value);
  }

  @Override
  public boolean replace(K key, V value) {
    return delegate().replace(key, value);
  }

  @Override
  public boolean replaceIfEquals(K key, V oldValue, V newValue) {
    return delegate().replaceIfEquals(key, oldValue, newValue);
  }

  @Override
  public @Nullable V peekAndRemove(K key) {
    return delegate().peekAndRemove(key);
  }

  @Override
  public boolean containsAndRemove(K key) {
    return delegate().containsAndRemove(key);
  }

  @Override
  public void remove(K key) {
    delegate().remove(key);
  }

  @Override
  public boolean removeIfEquals(K key, V expectedValue) {
    return delegate().removeIfEquals(key, expectedValue);
  }

  @Override
  public void removeAll(Iterable<? extends K> keys) {
    delegate().removeAll(keys);
  }

  @Override
  public @Nullable V peekAndPut(K key, V value) {
    return delegate().peekAndPut(key, value);
  }

  @Override
  public void expireAt(K key, long time) {
    delegate().expireAt(key, time);
  }

  @Override
  public CompletableFuture<Void> loadAll(Iterable<? extends K> keys) {
    return delegate().loadAll(keys);
  }

  @Override
  public CompletableFuture<Void> reloadAll(Iterable<? extends K> keys) {
    return delegate().reloadAll(keys);
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public <@Nullable R> R invoke(K key, EntryProcessor<K, V, R> processor) {
    return delegate().invoke(key, processor);
  }

  @Override
  public void mutate(K key, EntryMutator<K, V> mutator) {
    delegate().mutate(key, mutator);
  }

  @Override
  public <@Nullable R> Map<K, EntryProcessingResult<R>> invokeAll(
    Iterable<? extends K> keys, EntryProcessor<K, V, R> entryProcessor) {
    return delegate().invokeAll(keys, entryProcessor);
  }

  @Override
  public void mutateAll(Iterable<? extends K> keys, EntryMutator<K, V> mutator) {
    delegate().mutateAll(keys, mutator);
  }

  @Override
  public Map<K, V> getAll(Iterable<? extends K> keys) {
    return delegate().getAll(keys);
  }

  @Override
  public Map<K, V> peekAll(Iterable<? extends K> keys) {
    return delegate().peekAll(keys);
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> valueMap) {
    delegate().putAll(valueMap);
  }

  @Override
  public Set<K> keys() {
    return delegate().keys();
  }

  @Override
  public Set<CacheEntry<K, V>> entries() {
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

  @Override
  public <X> X requestInterface(Class<X> type) {
    return delegate().requestInterface(type);
  }

  @Override
  public ConcurrentMap<K, V> asMap() {
    return delegate().asMap();
  }

  /**
   * Forwards to delegate but adds the simple class name to the output.
   */
  @Override
  public String toString() {
    return this.getClass().getSimpleName() + "!" + delegate();
  }

}
