package org.cache2k.core.operation;

/*
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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
import org.cache2k.io.LoadExceptionInfo;
import org.cache2k.processor.MutableCacheEntry;

import static org.cache2k.expiry.ExpiryTimeValues.NEUTRAL;

/**
 * Mutable entry on top of the progress interface.
 *
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
  private final boolean mutationRequested;
  private final boolean originalExists;
  private boolean mutate = false;
  private boolean remove = false;
  private V value = null;
  private boolean customExpiry = false;
  private long expiryTime = NEUTRAL;
  private long refreshTime = NEUTRAL;
  private V originalOrLoadedValue = null;
  private boolean dataRead = false;

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
    originalExists = entry != null && progress.isDataFreshOrMiss();
    if (isEntryValid()) {
      originalOrLoadedValue = entry.getValueOrException();
    }
    this.mutationRequested = mutationRequested;
  }

  @Override
  public long getStartTime() {
    return progress.getMutationStartTime();
  }

  @Override
  public boolean exists() {
    triggerInstallationRead();
    return originalExists;
  }

  private void dataAccess() {
    triggerInstallationRead();
    dataRead = true;
  }

  @Override
  public MutableCacheEntry<K, V> setValue(V v) {
    if (entry != null) { lock(); }
    mutate = true;
    remove = false;
    value = v;
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public MutableCacheEntry<K, V> setException(Throwable ex) {
    if (entry != null) { lock(); }
    mutate = true;
    remove = false;
    value = (V) new ExceptionWrapper<K, V>(
      key, progress.getMutationStartTime(), ex, progress.getExceptionPropagator());
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
    triggerInstallationRead();
    if (mutate && !originalExists) {
      mutate = false;
    } else {
      if (entry != null) { lock(); }
      mutate = remove = true;
    }
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
    dataRead = true;
    checkAndThrowException(originalOrLoadedValue);
    return originalOrLoadedValue;
  }

  @Override
  public MutableCacheEntry<K, V> load() {
    if (dataRead) {
      throw new IllegalStateException(
        "getValue()/getException()/getModificationTime() called before load");
    }
    if (!progress.isLoaderPresent()) {
      throw new UnsupportedOperationException("Loader is not configured");
    }
    if (!progress.wasLoaded()) {
      triggerLoadOrInstallationRead();
      throw new Operations.NeedsLoadRestartException();
    }
    return this;
  }

  private void triggerLoadOrInstallationRead() {
    triggerInstallationRead();
    if (!originalExists && !progress.wasLoaded() && progress.isLoaderPresent()) {
      if (mutationRequested) {
        throw new IllegalStateException("getValue() after mutation not supported");
      }
      throw new Operations.NeedsLoadRestartException();
    }
  }

  private void triggerInstallationRead() {
    if (entry == null) {
      throw new Operations.WantsDataRestartException();
    }
  }

  /**
   * Locks the entry.
  ``       *
   * @throws UnsupportedOperationException if lock is not supported
   */
  public MutableCacheEntry<K, V> lock() {
    if (mutationRequested) { return this; }
    throw new Operations.NeedsLockRestartException();
  }

  @SuppressWarnings("unchecked")
  private void checkAndThrowException(V value) {
    if (value instanceof ExceptionWrapper) {
      throw ((ExceptionWrapper<K, V>) value).generateExceptionToPropagate();
    }
  }

  @Override
  public Throwable getException() {
    LoadExceptionInfo<K, V> info = getExceptionInfo();
    return info != null ? info.getException() : null;
  }

  @SuppressWarnings("unchecked")
  @Override
  public LoadExceptionInfo<K, V> getExceptionInfo() {
    triggerLoadOrInstallationRead();
    dataRead = true;
    if (originalOrLoadedValue instanceof ExceptionWrapper) {
      return (ExceptionWrapper<K, V>) originalOrLoadedValue;
    }
    return null;
  }

  @Override
  public long getExpiryTime() {
    dataAccess();
    return isEntryValid() ? entry.getExpiryTime() : 0;
  }

  private boolean isEntryValid() {
    return originalExists || progress.wasLoaded();
  }

  @Override
  public long getModificationTime() {
    dataAccess();
    return isEntryValid() ? entry.getModificationTime() : 0;
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
