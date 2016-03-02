package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.CacheEntry;
import org.cache2k.impl.operation.ExaminationEntry;
import org.cache2k.storage.StorageEntry;

/**
 * The cache entry. This is a combined hashtable entry with hashCode and
 * and collision list (other field) and it contains a double linked list
 * (next and previous) for the eviction algorithm.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("unchecked")
public class Entry<K, T>
  implements CacheEntry<K,T>, StorageEntry, ExaminationEntry<K, T> {

  static final int FETCHED_STATE = 16;
  static final int REFRESH_STATE = FETCHED_STATE + 1;
  static final int REPUT_STATE = FETCHED_STATE + 3;

  static final int FETCH_IN_PROGRESS_VALID = FETCHED_STATE + 4;

  /**
   * Cache.remove() operation received. Needs to be send to storage.
   */
  static final int REMOVE_PENDING = 11;

  /**
   * Entry was created for locking purposes of an atomic operation.
   */
  static final int ATOMIC_OP_NON_VALID = 10;

  static final int LOADED_NON_VALID_AND_PUT = 9;

  static final int FETCH_ABORT = 8;

  static final int FETCH_IN_PROGRESS_NON_VALID = 7;

  /** Storage was checked, no data available */
  static final int LOADED_NON_VALID_AND_FETCH = 6;

  /** Storage was checked, no data available */
  static final int READ_NON_VALID = 5;

  static final int EXPIRED_STATE = 4;

  /** Logically the same as immediately expired */
  static final int FETCH_NEXT_TIME_STATE = 3;

  static private final int GONE_STATE = 2;

  static private final int FETCH_IN_PROGRESS_VIRGIN = 1;

  static final int VIRGIN_STATE = 0;

  static final int EXPIRY_TIME_MIN = 32;

  static private final StaleMarker STALE_MARKER_KEY = new StaleMarker();

  final static InitialValueInEntryNeverReturned INITIAL_VALUE = new InitialValueInEntryNeverReturned();

  public BaseCache.MyTimerTask task;

  /**
   * Hit counter for clock pro. Not used by every eviction algorithm.
   */
  long hitCnt;

  /**
   * Time the entry was last updated by put or by fetching it from the cache source.
   * The time is the time in millis times 2. A set bit 1 means the entry is fetched from
   * the storage and not modified since then.
   */
  private volatile long fetchedTime;

  /**
   * Contains the next time a refresh has to occur, or if no background refresh is configured, when the entry
   * is expired. Low values have a special meaning, see defined constants.
   * Negative values means that we need to check against the wall clock.
   *
   * Whenever processing is done on the entry, e.g. a refresh or update,  the field is used to reflect
   * the processing state. This means that, during processing the expiry time is lost. This has no negative
   * impact on the visibility of the entry. For example if the entry is refreshed, it is expired, but since
   * background refresh is enabled, the expired entry is still returned by the cache.
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
  public Entry<K, T> another;

  /** Lru list: pointer to next element or list head */
  public Entry next;
  /** Lru list: pointer to previous element or list head */
  public Entry prev;

  private static final int MODIFICATION_TIME_BITS = 44;
  private static final long MODIFICATION_TIME_BASE = 0;
  private static final int MODIFICATION_TIME_SHIFT = 1;
  /** including dirty */
  private static final long MODIFICATION_TIME_MASK = (1L << MODIFICATION_TIME_BITS) - 1;

  private static final int DIRTY = 0;
  private static final int CLEAN = 1;

  /**
   * Set modification time and marks entry. We use {@value MODIFICATION_TIME_BITS} bit to store
   * the time, including 1 bit for the dirty state. Since everything is stored in a long field,
   * we have bits left that we can use for other purposes.
   *
   * @param _clean 1 means not modified
   */
  void setLastModification(long t, int _clean) {
    fetchedTime =
      fetchedTime & ~MODIFICATION_TIME_MASK |
        ((((t - MODIFICATION_TIME_BASE) << MODIFICATION_TIME_SHIFT) + _clean) & MODIFICATION_TIME_MASK);
  }

  /**
   * Set modification time and marks entry as dirty.
   */
  public void setLastModification(long t) {
    setLastModification(t, DIRTY);
  }

  /**
   * Memory entry needs to be send to the storage.
   */
  public boolean isDirty() {
    return (fetchedTime & MODIFICATION_TIME_SHIFT) == DIRTY;
  }

  public void setLastModificationFromStorage(long t) {
    setLastModification(t, CLEAN);
  }

  public void resetDirty() {
    fetchedTime = fetchedTime | 1;
  }

  @Override
  public long getLastModification() {
    return (fetchedTime & MODIFICATION_TIME_MASK) >> MODIFICATION_TIME_SHIFT;
  }

  /**
   * Different possible processing states. The code only uses fetch now, rest is preparation.
   */
  enum ProcessingState {
    DONE,
    READ,
    READ_COMPLETE,
    MUTATE,
    LOAD,
    LOAD_COMPLETE,
    FETCH,
    REFRESH,
    EXPIRY,
    EXPIRY_COMPLETE,
    WRITE,
    WRITE_COMPLETE,
    STORE,
    STORE_COMPLETE,
    NOTIFY,
    PINNED,
    EVICT,
    LAST
  }

  private static final int PS_BITS = 5;
  private static final int PS_MASK = (1 << PS_BITS) - 1;
  private static final int PS_POS = MODIFICATION_TIME_BITS;

  public ProcessingState getProcessingState() {
    return ProcessingState.values()[(int) ((fetchedTime >> PS_POS) & PS_MASK)];
  }

  public void setProcessingState(ProcessingState ps) {
    fetchedTime = fetchedTime & ~((long) PS_MASK << PS_POS) | ((long) ps.ordinal() << PS_POS);
  }

  /**
   * Starts long operation on entry. Pins the entry in the cache.
   */
  public long startFetch() {
    setProcessingState(ProcessingState.FETCH);
    return nextRefreshTime;
  }

  public void startFetch(ProcessingState ps) {
    setProcessingState(ps);
  }

  public void nextProcessingStep(ProcessingState ps) {
    setProcessingState(ps);
  }

  public void processingDone() {
    setProcessingState(ProcessingState.DONE);
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
    synchronized (Entry.this) {
      if (isFetchInProgress()) {
        notifyAll();
      }
    }
  }

  public void ensureFetchAbort(boolean _finished, long _previousNextRefreshTime) {
    if (_finished) {
      return;
    }
    synchronized (Entry.this) {
      if (isVirgin()) {
        nextRefreshTime = FETCH_ABORT;
      }
      if (isFetchInProgress()) {
        setProcessingState(ProcessingState.DONE);
        notifyAll();
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
    return getProcessingState() != ProcessingState.DONE;
  }

  public void waitForFetch() {
    if (!isFetchInProgress()) {
      return;
    }
    boolean _interrupt = false;
    do {
      try {
        wait();
      } catch (InterruptedException ignore) {
        _interrupt = true;
      }
    } while (isFetchInProgress());
    if (_interrupt) {
      Thread.currentThread().interrupt();
    }
  }

  public void setGettingRefresh() {
    setProcessingState(ProcessingState.REFRESH);
  }

  public boolean isGettingRefresh() {
    return getProcessingState() == ProcessingState.REFRESH;
  }

  /** Reset next as a marker for {@link #isRemovedFromReplacementList()} */
  public final void removedFromList() {
    next = null;
  }

  /** Check that this entry is removed from the list, may be used in assertions. */
  public boolean isRemovedFromReplacementList() {
    return isStale () || next == null;
  }

  public Entry shortCircuit() {
    return next = prev = this;
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
    return isDataValidState(nextRefreshTime);
  }

  public static boolean isDataValidState(long _nextRefreshTime) {
    return _nextRefreshTime >= FETCHED_STATE || _nextRefreshTime < 0;
  }

  public boolean hasData() {
    return !isVirgin() && !isGone();
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
    return nextRefreshTime == READ_NON_VALID;
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
    return isExpiredState(nextRefreshTime);
  }

  public static boolean isExpiredState(long _nextRefreshTime) {
    return _nextRefreshTime == EXPIRED_STATE;
  }

  public void setGone() {
    nextRefreshTime = GONE_STATE;
  }

  /**
   * The entry is not present in the heap any more and was evicted, expired or removed.
   * Usually we should never grab an entry from the hash table that has this state, but,
   * after the synchronize goes through somebody else might have evicted it.
   */
  public boolean isGone() {
    return nextRefreshTime == GONE_STATE;
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
  public K getKey() {
    return key;
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
  public T getValueOrException() {
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
      "key=" + key +
      ", lock=" + getProcessingState() +
      ", createdOrUpdate=" + getCreatedOrUpdated() +
      ", nextRefreshTime=" + nextRefreshTime +
      ", valueExpiryTime=" + getValueExpiryTime() +
      ", entryExpiryTime=" + getEntryExpiryTime() +
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
    synchronized (e) {
      e.setGettingRefresh();
      e = new Entry();
      e.setLoadedNonValidAndFetch();
      e.setExpiredState();
    }
  }

  @Override
  public int hashCode() {
    int result = (int) (this.hitCnt ^ this.hitCnt >>> 32);
    result = 31 * result + (int) (this.fetchedTime ^ this.fetchedTime >>> 32);
    result = 31 * result + (int) (this.nextRefreshTime ^ this.nextRefreshTime >>> 32);
    result = 31 * result + (this.key != null ? this.key.hashCode() : 0);
    result = 31 * result + (this.value != null ? this.value.hashCode() : 0);
    result = 31 * result + this.hashCode;
    result = 31 * result + (this.another != null ? this.another.hashCode() : 0);
    result = 31 * result + (this.next != null ? this.next.hashCode() : 0);
    result = 31 * result + (this.prev != null ? this.prev.hashCode() : 0);
    return result;
  }

  static class InitialValueInEntryNeverReturned extends Object { }

  static class StaleMarker {
    @Override
    public boolean equals(Object o) { return false; }
  }

}
