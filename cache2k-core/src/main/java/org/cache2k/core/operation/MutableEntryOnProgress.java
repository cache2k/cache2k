package org.cache2k.core.operation;

/*
 * #%L
 * cache2k core implementation
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
  private long refreshTime = NEUTRAL;
  private boolean mutationRequested;

  /**
   * Sets exists and value together since it must be guaranteed that getValue() returns
   * a value after exists yields true. It is critical that the isPresentOrMiss() check is
   * only done once, since it depends on the current time.
   */
  MutableEntryOnProgress(K key, Progress<K, V, ?> progress, ExaminationEntry<K, V> entry,
                         boolean mutationRequested) {
    this.entry = entry;
    this.progress = progress;
    this.key = key;
    if (entry != null && (progress.isDataFreshOrMiss() || progress.wasLoaded())) {
      value = entry.getValueOrException();
      originalExists = exists = true;
    }
    this.mutationRequested = mutationRequested;
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
    if (entry != null) { lock(); }
    mutate = true;
    exists = true;
    remove = false;
    value = v;
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public MutableCacheEntry<K, V> setException(Throwable ex) {
    if (entry != null) { lock(); }
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
    if (entry != null) { lock(); }
    customExpiry = true;
    expiryTime = t;
    return this;
  }

  /**
   * Reset mutate if nothing exists in the cache
   */
  @Override
  public MutableCacheEntry<K, V> remove() {
    triggerInstallationRead(true);
    if (mutate && !originalExists) {
      mutate = false;
    } else {
      if (entry != null) { lock(); }
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

  /**
   * Locks the entry.
   *
   * @throws UnsupportedOperationException if lock is not supported
   */
  private void lock() {
    if (mutationRequested) { return; }
    throw new Operations.NeedsLockRestartException();
  }

  @SuppressWarnings("unchecked")
  private void checkAndThrowException(V value) {
    if (value instanceof ExceptionWrapper) {
      throw ((ExceptionWrapper<K>) value).generateExceptionToPropagate();
    }
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
  public long getModificationTime() {
    triggerInstallationRead(false);
    if (refreshTime != NEUTRAL) {
      return refreshTime;
    }
    return originalExists ? entry.getRefreshTime() : 0;
  }

  @Override
  public MutableCacheEntry<K, V> setModificationTime(long t) {
    refreshTime = t;
    return this;
  }

  public boolean isMutationNeeded() {
    return mutate || customExpiry;
  }

  public void sendMutationCommand() {
    if (!isMutationNeeded()) {
      progress.noMutation();
      return;
    }
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
