package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
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

import org.cache2k.MutableCacheEntry;
import org.cache2k.storage.StorageEntry;

/**
 * The cache entry. This is a combined hashtable entry with hashCode and
 * and collision list (other field) and it contains a double linked list
 * (next and previous) for the eviction algorithm.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("unchecked")
public class Entry<E extends Entry, K, T>
  implements MutableCacheEntry<K,T>, StorageEntry {

  static final int FETCHED_STATE = 16;
  static final int REFRESH_STATE = FETCHED_STATE + 1;
  static final int REPUT_STATE = FETCHED_STATE + 3;

  static final int FETCH_IN_PROGRESS_VALID = FETCHED_STATE + 4;

  static final int LOADED_NON_VALID_AND_PUT = 9;

  static final int FETCH_ABORT = 8;

  static final int FETCH_IN_PROGRESS_NON_VALID = 7;

  /** Storage was checked, no data available */
  static final int LOADED_NON_VALID_AND_FETCH = 6;

  /** Storage was checked, no data available */
  static final int LOADED_NON_VALID = 5;

  static final int EXPIRED_STATE = 4;

  /** Logically the same as immediately expired */
  static final int FETCH_NEXT_TIME_STATE = 3;

  static private final int REMOVED_STATE = 2;

  static private final int FETCH_IN_PROGRESS_VIRGIN = 1;

  static final int VIRGIN_STATE = 0;

  static final int EXPIRY_TIME_MIN = 32;

  static private final StaleMarker STALE_MARKER_KEY = new StaleMarker();

  final static InitialValueInEntryNeverReturned INITIAL_VALUE = new InitialValueInEntryNeverReturned();

  public BaseCache.MyTimerTask task;

  /**
   * Time the entry was last updated by put or by fetching it from the cache source.
   * The time is the time in millis times 2. A set bit 1 means the entry is fetched from
   * the storage and not modified since then.
   */
  public long fetchedTime;

  /**
   * Contains the next time a refresh has to occur. Low values have a special meaning, see defined constants.
   * Negative values means the refresh time was expired, and we need to check the time.
   */
  public volatile long nextRefreshTime;

  public K key;

  public volatile T value = (T) INITIAL_VALUE;

  /**
   * Hash implementation: the calculated, modified hash code, retrieved from the key when the entry is
   * inserted in the cache
   *
   * @see BaseCache#modifiedHash(int)
   */
  public int hashCode;

  /**
   * Hash implementation: Link to another entry in the same hash table slot when the hash code collides.
   */
  public Entry<E, K, T> another;

  /** Lru list: pointer to next element or list head */
  public E next;
  /** Lru list: pointer to previous element or list head */
  public E prev;

  public void setLastModification(long t) {
    fetchedTime = t << 1;
  }

  /**
   * Memory entry needs to be send to the storage.
   */
  public boolean isDirty() {
    return (fetchedTime & 1) == 0;
  }

  public void setLastModificationFromStorage(long t) {
    fetchedTime = t << 1 | 1;
  }

  public void resetDirty() {
    fetchedTime = fetchedTime | 1;
  }

  /** Reset next as a marker for {@link #isRemovedFromReplacementList()} */
  public final void removedFromList() {
    next = null;
  }

  /** Check that this entry is removed from the list, may be used in assertions. */
  public boolean isRemovedFromReplacementList() {
    return isStale () || next == null;
  }

  public E shortCircuit() {
    return next = prev = (E) this;
  }

  public final boolean isVirgin() {
    return
      nextRefreshTime == VIRGIN_STATE ||
      nextRefreshTime == FETCH_IN_PROGRESS_VIRGIN;
  }

  public final boolean isFetchNextTimeState() {
    return nextRefreshTime == FETCH_NEXT_TIME_STATE;
  }

  /**
   * The entry value was fetched and is valid, which means it can be
   * returned by the cache. If a valid entry gets removed from the
   * cache the data is still valid. This is because a concurrent get needs to
   * return the data. There is also the chance that an entry is removed by eviction,
   * or is never inserted to the cache, before the get returns it.
   *
   * <p/>Even if this is true, the data may be expired. Use hasFreshData() to
   * make sure to get not expired data.
   */
  public final boolean isDataValidState() {
    return nextRefreshTime >= FETCHED_STATE || nextRefreshTime < 0;
  }

  /**
   * Starts long operation on entry. Pins the entry in the cache.
   */
  public void startFetch() {
    if (isVirgin()) {
      nextRefreshTime = FETCH_IN_PROGRESS_VIRGIN;
    } else {
      nextRefreshTime = FETCH_IN_PROGRESS_NON_VALID;
    }
  }

  public void finishFetch(long _nextRefreshTime) {
    synchronized (Entry.this) {
      nextRefreshTime = _nextRefreshTime;
      notifyAll();
    }
  }

  /**
   * If fetch is not stopped, abort and make entry invalid.
   * This is a safety measure, since during entry processing an
   * exceptions may happen. This can happen regularly e.g. if storage
   * is set to read only and a cache put is made.
   */
  public void ensureFetchAbort(boolean _finished) {
    if (_finished) {
      return;
    }
    if (isFetchInProgress()) {
      synchronized (Entry.this) {
        if (isFetchInProgress()) {
          nextRefreshTime = FETCH_ABORT;
          notifyAll();
        }
      }
    }
  }

  /**
   * Entry is not allowed to be evicted
   */
  public boolean isPinned() {
    return isFetchInProgress();
  }

  public boolean isFetchInProgress() {
    return
      nextRefreshTime == REFRESH_STATE ||
      nextRefreshTime == LOADED_NON_VALID_AND_FETCH ||
      nextRefreshTime == FETCH_IN_PROGRESS_VIRGIN ||
      nextRefreshTime == LOADED_NON_VALID_AND_PUT ||
      nextRefreshTime == FETCH_IN_PROGRESS_NON_VALID ||
      nextRefreshTime == FETCH_IN_PROGRESS_VALID;
  }

  public void waitForFetch() {
    if (!isFetchInProgress()) {
      return;
    }
    try {
      do {
        wait();
      } while (isFetchInProgress());
    } catch (InterruptedException e) {
      throw new CacheInternalError();
    }
  }

  /**
   * Returns true if the entry has a valid value and is fresh / not expired.
   */
  public final boolean hasFreshData() {
    if (nextRefreshTime >= FETCHED_STATE) {
      return true;
    }
    if (needsTimeCheck()) {
      long now = System.currentTimeMillis();
      return now < -nextRefreshTime;
    }
    return false;
  }

  /**
   * Same as {@link #hasFreshData}, optimization if current time is known.
   */
  public final boolean hasFreshData(long now) {
    if (nextRefreshTime >= FETCHED_STATE) {
      return true;
    }
    if (needsTimeCheck()) {
      return now < -nextRefreshTime;
    }
    return false;
  }

  public final boolean hasFreshData(long now, long _nextRefreshTime) {
    if (_nextRefreshTime >= FETCHED_STATE) {
      return true;
    }
    if (_nextRefreshTime < 0) {
      return now < -_nextRefreshTime;
    }
    return false;
  }

  public boolean isLoadedNonValid() {
    return nextRefreshTime == LOADED_NON_VALID;
  }

  public void setLoadedNonValidAndFetch() {
    nextRefreshTime = LOADED_NON_VALID_AND_FETCH;
  }

  public boolean isLoadedNonValidAndFetch() {
    return nextRefreshTime == LOADED_NON_VALID_AND_FETCH;
  }

  /** Entry is kept in the cache but has expired */
  public void setExpiredState() {
    nextRefreshTime = EXPIRED_STATE;
  }

  /**
   * The entry expired, but still in the cache. This may happen if
   * {@link BaseCache#hasKeepAfterExpired()} is true.
   */
  public boolean isExpiredState() {
    return nextRefreshTime == EXPIRED_STATE;
  }

  public void setRemovedState() {
    nextRefreshTime = REMOVED_STATE;
  }

  public boolean isRemovedState() {
    return nextRefreshTime == REMOVED_STATE;
  }

  public void setGettingRefresh() {
    nextRefreshTime = REFRESH_STATE;
  }

  public boolean isGettingRefresh() {
    return nextRefreshTime == REFRESH_STATE;
  }

  public boolean isBeeingReput() {
    return nextRefreshTime == REPUT_STATE;
  }

  public boolean needsTimeCheck() {
    return nextRefreshTime < 0;
  }

  public boolean isStale() {
    return STALE_MARKER_KEY == key;
  }

  public void setStale() {
    key = (K) STALE_MARKER_KEY;
  }

  public boolean hasException() {
    return value instanceof ExceptionWrapper;
  }

  public Throwable getException() {
    if (value instanceof ExceptionWrapper) {
      return ((ExceptionWrapper) value).getException();
    }
    return null;
  }

  public void setException(Throwable exception) {
    value = (T) new ExceptionWrapper(exception);
  }

  public boolean equalsValue(T v) {
    if (value == null) {
      return v == value;
    }
    return value.equals(v);
  }

  public T getValue() {
    if (value instanceof ExceptionWrapper) { return null; }
    return value;
  }


  @Override
  public void setValue(T v) {
    value = v;
  }

  @Override
  public K getKey() {
    return key;
  }

  @Override
  public long getLastModification() {
    return fetchedTime >> 1;
  }

  /**
   * Expiry time or 0.
   */
  public long getValueExpiryTime() {
    if (nextRefreshTime < 0) {
      return -nextRefreshTime;
    } else if (nextRefreshTime > EXPIRY_TIME_MIN) {
      return nextRefreshTime;
    }
    return 0;
  }

  /**
   * Used for the storage interface.
   *
   * @see org.cache2k.storage.StorageEntry
   */
  @Override
  public Object getValueOrException() {
    return value;
  }

  /**
   * Used for the storage interface.
   *
   * @see org.cache2k.storage.StorageEntry
   */
  @Override
  public long getCreatedOrUpdated() {
    return getLastModification();
  }

  /**
   * Used for the storage interface.
   *
   * @see org.cache2k.storage.StorageEntry
   * @deprectated Always returns 0, only to fulfill the {@link org.cache2k.storage.StorageEntry} interface
   */
  @Override
  public long getEntryExpiryTime() {
    return 0;
  }

  @Override
  public String toString() {
    return "Entry{" +
      "createdOrUpdate=" + getCreatedOrUpdated() +
      ", nextRefreshTime=" + nextRefreshTime +
      ", valueExpiryTime=" + getValueExpiryTime() +
      ", entryExpiryTime=" + getEntryExpiryTime() +
      ", key=" + key +
      ", mHC=" + hashCode +
      ", value=" + value +
      ", dirty=" + isDirty() +
      '}';
  }

  /**
   * Cache entries always have the object identity as equals method.
   */
  @Override
  public final boolean equals(Object obj) {
    return this == obj;
  }

  /* check entry states */
  static {
    Entry e = new Entry();
    e.nextRefreshTime = FETCHED_STATE;
    e.setGettingRefresh();
    e = new Entry();
    e.setLoadedNonValidAndFetch();
    e.setExpiredState();
  }

  static class InitialValueInEntryNeverReturned extends Object { }

  static class StaleMarker {
    @Override
    public boolean equals(Object o) { return false; }
  }

}
