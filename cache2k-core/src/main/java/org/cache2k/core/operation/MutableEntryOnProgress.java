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

  private K key;

  /**
   * Current content of the cache, or loaded, or {@code null} if data was not
   * yet requested.
   */
  private ExaminationEntry<K, V> entry;
  private Progress<K, V, ?> progress;
  private boolean originalExists = false;
  private boolean mutate = false;
  private boolean remove = false;
  private boolean exists = false;
  private V value = null;
  private boolean customExpiry = false;
  private long expiryTime = NEUTRAL;
  private long refreshTime = expiryTime;

  /**
   * Sets exists and value together since it must be guaranteed that getValue() returns
   * a value after exists yields true. It is critical that the isPresentOrMiss() check is
   * only done once, since it depends on the current time.
   */
  public MutableEntryOnProgress(final K key, final Progress<K, V, ?> progress, final ExaminationEntry<K, V> entry) {
    this.entry = entry;
    this.progress = progress;
    this.key = key;
    if (entry != null && progress.isPresentOrMiss()) {
      value = this.entry.getValueOrException();
      originalExists = exists = true;
    }
  }

  @Override
  public long getCurrentTime() {
    return progress.getCurrentTime();
  }

  @Override
  public boolean exists() {
    triggerInstallationRead();
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
    return key;
  }

  @Override
  public V getValue() {
    triggerLoadOrInstallationRead();
    checkAndThrowException(value);
    return value;
  }

  private void triggerLoadOrInstallationRead() {
    triggerInstallationRead();
    if (!exists && !mutate && progress.isLoaderPresent()) {
      throw new Operations.NeedsLoadRestartException();
    }
  }

  private void triggerInstallationRead() {
    if (!mutate && entry == null) {
      throw new Operations.WantsDataRestartException();
    }
  }

  @Override
  public V getOldValue() {
    triggerLoadOrInstallationRead();
    if (!originalExists || (entry instanceof LoadedEntry)) {
      return null;
    }
    V value = entry.getValueOrException();
    checkAndThrowException(value);
    return value;
  }

  private void checkAndThrowException(final V value) {
    if (value instanceof ExceptionWrapper) {
      throw progress.propagateException(key, (ExceptionWrapper) value);
    }
  }

  @Override
  public boolean wasExisting() {
    triggerInstallationRead();
    checkAndThrowException(value);
    return originalExists && !(entry instanceof LoadedEntry);
  }

  @Override
  public Throwable getException() {
    triggerLoadOrInstallationRead();
    if (value instanceof ExceptionWrapper) {
      return ((ExceptionWrapper) value).getException();
    }
    return null;
  }

  @Override
  public long getRefreshedTime() {
    triggerInstallationRead();
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

  public boolean isMutationNeeded() {
    return mutate || customExpiry;
  }

  public void sendMutationCommand() {
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
      return;
    }
    progress.noMutation();
  }

}
