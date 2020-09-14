package org.cache2k.core.operation;

/*
 * #%L
 * cache2k implementation
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

import org.cache2k.core.ExceptionWrapper;
import org.cache2k.processor.MutableCacheEntry;

import static org.cache2k.expiry.ExpiryTimeValues.NEUTRAL;

/**
 * @author Jens Wilke
 */
class MutableEntryOnProgress<K, V> implements MutableCacheEntry<K, V> {

  private final K key;

  /**
   * Current content of the cache, or loaded, or {@code null} if data was not
   * yet requested.
   */
  private final ExaminationEntry<K, V> entry;
  private final Progress<K, V, ?> progress;
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
  MutableEntryOnProgress(K key, Progress<K, V, ?> progress, ExaminationEntry<K, V> entry) {
    this.entry = entry;
    this.progress = progress;
    this.key = key;
    if (entry != null && progress.isDataFreshOrMiss()) {
      value = this.entry.getValueOrException();
      originalExists = exists = true;
    }
  }

  @Override
  public long getCurrentTime() {
    return progress.getMutationStartTime();
  }

  @Override
  public long getStartTime() {
    return progress.getMutationStartTime();
  }

  @Override
  public boolean exists() {
    triggerInstallationRead(false);
    return exists;
  }

  @Override
  public MutableCacheEntry<K, V> setValue(V v) {
    mutate = true;
    exists = true;
    remove = false;
    value = v;
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public MutableCacheEntry<K, V> setException(Throwable ex) {
    mutate = true;
    exists = true;
    remove = false;
    value = (V) new ExceptionWrapper(key,
      progress.getMutationStartTime(), ex,
      progress.getExceptionPropagator());
    return this;
  }

  @Override
  public MutableCacheEntry<K, V> setExpiryTime(long t) {
    customExpiry = true;
    expiryTime = t;
    return this;
  }

  /**
   * Reset mutate if nothing exists in the cache
   */
  @Override
  public MutableCacheEntry<K, V> remove() {
    if (mutate) {
      triggerInstallationRead(true);
    }
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
    triggerLoadOrInstallationRead(false);
    checkAndThrowException(value);
    return value;
  }

  @Override
  public MutableCacheEntry<K, V> reload() {
    if (!progress.isLoaderPresent()) {
      throw new UnsupportedOperationException("Loader is not configured");
    }
    if (!progress.wasLoaded()) {
      triggerLoadOrInstallationRead(false);
      throw new Operations.NeedsLoadRestartException();
    }
    return this;
  }

  private void triggerLoadOrInstallationRead(boolean ignoreMutate) {
    triggerInstallationRead(ignoreMutate);
    if (!exists && (ignoreMutate || !mutate) && progress.isLoaderPresent()) {
      throw new Operations.NeedsLoadRestartException();
    }
  }

  private void triggerInstallationRead(boolean ignoreMutate) {
    if ((ignoreMutate || !mutate) && entry == null) {
      throw new Operations.WantsDataRestartException();
    }
  }

  @Override
  public V getOldValue() {
    triggerLoadOrInstallationRead(true);
    if (!originalExists || (entry instanceof LoadedEntry)) {
      return null;
    }
    V value = entry.getValueOrException();
    checkAndThrowException(value);
    return value;
  }

  @SuppressWarnings("unchecked")
  private void checkAndThrowException(V value) {
    if (value instanceof ExceptionWrapper) {
      ((ExceptionWrapper<K>) value).propagateException();
    }
  }

  @Override
  public boolean wasExisting() {
    triggerInstallationRead(true);
    checkAndThrowException(value);
    return originalExists && !(entry instanceof LoadedEntry);
  }

  @Override
  public Throwable getException() {
    triggerLoadOrInstallationRead(false);
    if (value instanceof ExceptionWrapper) {
      return ((ExceptionWrapper) value).getException();
    }
    return null;
  }

  @Override
  public long getRefreshedTime() {
    triggerInstallationRead(false);
    if (refreshTime != NEUTRAL) {
      return refreshTime;
    }
    return originalExists ? entry.getRefreshTime() : 0;
  }

  @Override
  public MutableCacheEntry<K, V> setRefreshedTime(long t) {
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
    progress.expire(expiryTime);
  }

}
