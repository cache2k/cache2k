package org.cache2k.core.operation;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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

import org.cache2k.AbstractCacheEntry;
import org.cache2k.CacheEntry;
import org.cache2k.core.Entry;
import org.cache2k.core.ExceptionWrapper;

/**
 *
 *
 * @author Jens Wilke
 */
public class ReadOnlyCacheEntry<K, V> extends AbstractCacheEntry<K,V> implements ResultEntry<K, V> {

  private K key;
  private V valueOrException;
  private long refreshTime;

  @SuppressWarnings("unchecked")
  public static <K,V> ReadOnlyCacheEntry<K,V> of(CacheEntry<K,V> entry) {
    if (entry instanceof ReadOnlyCacheEntry) {
      return (ReadOnlyCacheEntry) entry;
    }
    return new ReadOnlyCacheEntry<K, V>((Entry<K,V>) entry);
  }

  public ReadOnlyCacheEntry(Entry<K,V> entry) {
    setValues(entry);
  }

  private void setValues(final Entry<K, V> entry) {
    setValues(entry.getKey(), entry.getValueOrException(), entry.getRefreshTime());
  }

  private void setValues(final K _key, final V _valueOrException, final long _refreshTime) {
    key = _key;
    refreshTime = _refreshTime;
    valueOrException = _valueOrException;
  }

  @Override
  public Throwable getException() {
    if (valueOrException instanceof ExceptionWrapper) {
      return ((ExceptionWrapper) valueOrException).getException();
    }
    return null;
  }

  @Override
  public K getKey() {
    return key;
  }

  @SuppressWarnings("deprecation")
  @Override
  public long getRefreshTime() {
    return refreshTime;
  }

  @Override
  public V getValue() {
    if (valueOrException instanceof ExceptionWrapper) {
      return null;
    }
    return valueOrException;
  }

  @Override
  public V getValueOrException() {
    return valueOrException;
  }

}
