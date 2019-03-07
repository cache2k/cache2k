package org.cache2k.core.operation;

/*
 * #%L
 * cache2k implementation
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

import org.cache2k.core.ExceptionWrapper;
import org.cache2k.processor.MutableCacheEntry;

import static org.cache2k.expiry.ExpiryTimeValues.NEUTRAL;

/**
 * @author Jens Wilke
 */
class MutableEntryOnProgress<K, V> implements MutableCacheEntry<K, V> {

  private ExaminationEntry<K, V> entry;
  private Progress<K, V, ?> progress;
  private boolean originalExists = false;
  private boolean mutate = false;
  private boolean remove = false;
  private boolean exists = false;
  private V value = null;
  private boolean readThrough;
  private boolean customExpiry = false;
  private long expiryTime = NEUTRAL;
  private long refreshTime = expiryTime;

  /**
   * Sets exists and value together since it must be guaranteed that getValue() returns
   * a value after exists yields true. It is critical that the isPresentOrMiss() check is
   * only done once, since it depends on the current time.
   */
  public MutableEntryOnProgress(final Progress<K, V, ?> _progress, final ExaminationEntry<K, V> _entry, final boolean _readThrough) {
    readThrough = _readThrough;
    entry = _entry;
    progress = _progress;
    if (_progress.isPresentOrMiss()) {
      value = entry.getValueOrException();
      originalExists = exists = true;
    }
  }

  @Override
  public long getCurrentTime() {
    return progress.getCurrentTime();
  }

  @Override
  public boolean exists() {
    return exists;
  }

  @Override
  public MutableCacheEntry<K, V> setValue(final V v) {
    mutate = true;
    exists = true;
    remove = false;
    value = v;
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public MutableCacheEntry<K, V> setException(final Throwable ex) {
    mutate = true;
    exists = true;
    remove = false;
    value = (V) new ExceptionWrapper(progress.getCurrentTime(), ex);
    return this;
  }

  @Override
  public MutableCacheEntry<K, V> setExpiryTime(final long t) {
    customExpiry = true;
    expiryTime = t;
    return this;
  }

  @Override
  public MutableCacheEntry<K, V> remove() {
    if (mutate && !originalExists) {
      mutate = false;
    } else {
      mutate = remove = true;
    }
    exists = false;
    value = null;
    return this;
  }

  @Override
  public K getKey() {
    return entry.getKey();
  }

  @Override
  public V getValue() {
    if (!exists && !mutate && readThrough) {
      throw new Operations.NeedsLoadRestartException();
    }
    if (value instanceof ExceptionWrapper) {
      throw progress.propagateException(entry.getKey(), (ExceptionWrapper) value);
    }
    return value;
  }

  @Override
  public V getOldValue() {
    if (!originalExists || (entry instanceof LoadedEntry)) {
      return null;
    }
    V _value = entry.getValueOrException();
    if (_value instanceof ExceptionWrapper) {
      throw progress.propagateException(entry.getKey(), (ExceptionWrapper) _value);
    }
    return _value;
  }

  @Override
  public boolean wasExisting() {
    return originalExists && !(entry instanceof LoadedEntry);
  }

  @Override
  public Throwable getException() {
    if (!exists && !mutate && readThrough) {
      throw new Operations.NeedsLoadRestartException();
    }
    if (value instanceof ExceptionWrapper) {
      return ((ExceptionWrapper) value).getException();
    }
    return null;
  }

  @Override
  public long getRefreshedTime() {
    if (refreshTime != NEUTRAL) {
      return refreshTime;
    }
    return originalExists ? entry.getRefreshTime() : 0;
  }

  @Override
  public MutableCacheEntry<K, V> setRefreshedTime(final long t) {
    refreshTime = t;
    return this;
  }

  @SuppressWarnings("deprecation")
  @Override
  public long getLastModification() {
    throw new UnsupportedOperationException();
  }

  public void sendMutationCommandIfNeeded() {
    if (mutate) {
      if (remove) {
        progress.remove();
        return;
      }
      if (customExpiry || refreshTime != NEUTRAL) {
        progress.putAndSetExpiry(value, expiryTime, refreshTime);
        return;
      }
      progress.put(value);
      return;
    }
    if (customExpiry) {
      progress.expire(expiryTime);
    }
  }

}
