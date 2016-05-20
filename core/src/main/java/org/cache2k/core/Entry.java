package org.cache2k.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.CacheEntry;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.core.operation.ExaminationEntry;
import org.cache2k.core.storageApi.StorageEntry;
import org.cache2k.integration.ExceptionInformation;

import static org.cache2k.core.util.Util.*;

import java.lang.reflect.Field;
import java.util.TimerTask;

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

  /**
   * A value greater as means it is a time value.
   */
  public static final int EXPIRY_TIME_MIN = 32;

  /**
   * bigger or equal means entry has / contains valid data
   */
  static final int DATA_VALID = 16;

  /**
   * Cache.remove() operation received. Needs to be send to storage.
   */
  static final int REMOVE_PENDING = 11;

  static final int ABORTED = 8;

  /** @see #isReadNonValid() */
  static final int READ_NON_VALID = 5;

  /** @see #isExpired() */
  static final int EXPIRED = 4;

  /** @see #isGone() */
  static private final int GONE = 2;

  /** @see #isVirgin() */
  static final int VIRGIN = 0;

  final static InitialValueInEntryNeverReturned INITIAL_VALUE = new InitialValueInEntryNeverReturned();

  /**
   * Usually this contains the reference to the timer task. In some cases, like when exceptions
   * happen we will link to the PiggyBack object to add more information as needed.
   */
  private Object misc;

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
  private volatile long nextRefreshTime;

  public K key;

  private volatile T valueOrException = (T) INITIAL_VALUE;

  /**
   * Hash implementation: the calculated, modified hash code, retrieved from the key when the entry is
   * inserted in the cache
   *
   * @see HeapCache#modifiedHash(int)
   */
  public int hashCode;

  /**
   * Hash implementation: Link to another entry in the same hash table slot when the hash code collides.
   */
  public volatile Entry<K, T> another;

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

  public long getNextRefreshTime() {
    return nextRefreshTime;
  }

  public void setNextRefreshTime(long _nextRefreshTime) {
    nextRefreshTime = _nextRefreshTime;
  }

  public void setValueOrException(T _valueOrException) {
    valueOrException = _valueOrException;
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
  public void startProcessing() {
    setProcessingState(ProcessingState.FETCH);
  }

  public void startProcessing(ProcessingState ps) {
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
  public void ensureAbort(boolean _finished) {
    if (_finished) {
      return;
    }
    synchronized (this) {
      if (isVirgin()) {
        nextRefreshTime = ABORTED;
      }
      if (isProcessing()) {
        setProcessingState(ProcessingState.DONE);
        notifyAll();
      }
    }
  }

  public boolean isProcessing() {
    return getProcessingState() != ProcessingState.DONE;
  }

  public void waitForProcessing() {
    if (!isProcessing()) {
      return;
    }
    boolean _interrupt = false;
    do {
      try {
        wait();
      } catch (InterruptedException ignore) {
        _interrupt = true;
      }
    } while (isProcessing());
    if (_interrupt) {
      Thread.currentThread().interrupt();
    }
  }

  public boolean isGettingRefresh() {
    return getProcessingState() == ProcessingState.REFRESH;
  }

  /** Reset next as a marker for {@link #isRemovedFromReplacementList()} */
  public final void removedFromList() {
    next = null;
    prev = null;
  }

  /** Check that this entry is removed from the list, may be used in assertions. */
  public boolean isRemovedFromReplacementList() {
    return next == null;
  }

  public Entry shortCircuit() {
    return next = prev = this;
  }

  /**
   * Initial state of an entry.
   */
  public final boolean isVirgin() {
    return nextRefreshTime == VIRGIN;
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
  public final boolean isDataValid() {
    return nextRefreshTime >= DATA_VALID || nextRefreshTime < 0;
  }

  /**
   * Returns true if the entry has a valid value and is fresh / not expired.
   */
  public final boolean hasFreshData() {
    if (nextRefreshTime >= DATA_VALID) {
      return true;
    }
    if (needsTimeCheck()) {
      return System.currentTimeMillis() < -nextRefreshTime;
    }
    return false;
  }

  public final boolean hasFreshData(long now, long _nextRefreshTime) {
    if (_nextRefreshTime >= DATA_VALID) {
      return true;
    }
    if (_nextRefreshTime < 0) {
      return now < -_nextRefreshTime;
    }
    return false;
  }

  /** Storage was checked, no data available */
  public boolean isReadNonValid() {
    return nextRefreshTime == READ_NON_VALID;
  }

  /** Entry is kept in the cache but has expired */
  public void setExpiredState() {
    nextRefreshTime = EXPIRED;
  }

  /**
   * The entry expired, but still in the cache. This may happen if
   * {@link HeapCache#hasKeepAfterExpired()} is true.
   */
  public boolean isExpired() {
    return nextRefreshTime == EXPIRED;
  }

  public void setGone() {
    nextRefreshTime = GONE;
  }

  /**
   * The entry is not present in the heap any more and was evicted, expired or removed.
   * Usually we should never grab an entry from the hash table that has this state, but,
   * after the synchronize goes through somebody else might have evicted it.
   */
  public boolean isGone() {
    return nextRefreshTime == GONE;
  }

  public boolean needsTimeCheck() {
    return nextRefreshTime < 0;
  }

  private static final int HOT_BITS = 1;
  private static final int HOT_POS = MODIFICATION_TIME_BITS + PS_BITS;
  private static final long HOT_DIRECT_MASK = 1L << HOT_POS;

  public boolean isHot() {
    return (fetchedTime & HOT_DIRECT_MASK) != 0;
  }

  public void setHot(boolean f) {
    if (f) {
      fetchedTime |=  HOT_DIRECT_MASK;
    } else {
      fetchedTime &= ~HOT_DIRECT_MASK;
    }
  }

  public Throwable getException() {
    if (valueOrException instanceof ExceptionWrapper) {
      return ((ExceptionWrapper) valueOrException).getException();
    }
    return null;
  }

  public boolean equalsValue(T v) {
    if (valueOrException == null) {
      return v == valueOrException;
    }
    return valueOrException.equals(v);
  }

  public T getValue() {
    if (valueOrException instanceof ExceptionWrapper) { return null; }
    return valueOrException;
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
   * @see StorageEntry
   */
  @Override
  public T getValueOrException() {
    return valueOrException;
  }

  /**
   * Used for the storage interface.
   *
   * @see StorageEntry
   */
  @Override
  public long getCreatedOrUpdated() {
    return getLastModification();
  }

  /**
   * Used for the storage interface.
   *
   * @see StorageEntry
   * @deprecated  Always returns 0, only to fulfill the {@link StorageEntry} interface
   */
  @Override
  public long getEntryExpiryTime() {
    return 0;
  }

  public String toString(HeapCache c) {
    StringBuilder sb = new StringBuilder();
    sb.append("Entry{");
    sb.append("objId=").append(System.identityHashCode(this));
    sb.append(", lock=").append(getProcessingState());
    sb.append(", key=");
    if (key == null) {
      sb.append("null");
    } else {
      sb.append(key);
      if (c != null && (c.modifiedHash(key.hashCode()) != hashCode)) {
        sb.append(", keyMutation=true");
      }
    }
    if (valueOrException != null) {
      sb.append(", valueIdentityHashCode=").append(System.identityHashCode(valueOrException));
    } else {
      sb.append(", value=null");
    }
    sb.append(", modified=").append(formatMillis(getLastModification()));
    if (nextRefreshTime < 0) {
      sb.append(", nextRefreshTime(sharp)=").append(formatMillis(-nextRefreshTime));
    } else if (nextRefreshTime == ExpiryPolicy.ETERNAL) {
      sb.append(", nextRefreshTime=ETERNAL");
    } else if (nextRefreshTime >= EXPIRY_TIME_MIN) {
      sb.append(", nextRefreshTime(timer)=").append(formatMillis(nextRefreshTime));
    } else {
      sb.append(", state=").append(nextRefreshTime);
    }
    if (Thread.holdsLock(this)) {
      if (getTask() != null) {
        String _timerState = "<unavailable>";
        try {
          Field f = TimerTask.class.getDeclaredField("state");
          f.setAccessible(true);
          int _state = f.getInt(getTask());
          _timerState = String.valueOf(_state);
        } catch (Exception x) {
          _timerState = x.toString();
        }
        sb.append(", timerState=").append(_timerState);
      }
    } else {
      sb.append(", timerState=skipped/notLocked");
    }

    sb.append(", dirty=").append(isDirty());
    sb.append("}");
    return sb.toString();
  }

  @Override
  public String toString() {
    return toString(null);
  }

  static class InitialValueInEntryNeverReturned extends Object { }

  public TimerTask getTask() {
    if (misc instanceof TimerTask) {
      return (TimerTask) misc;
    }
    TimerTaskPiggyBack pb = getPiggyBack(TimerTaskPiggyBack.class);
    if (pb != null) {
      return pb.task;
    }
    return null;
  }

  public boolean cancelTimerTask() {
    Object o = misc;
    if (o instanceof TimerTask) {
      misc = null;
      return ((TimerTask) o).cancel();
    }
    TimerTaskPiggyBack pb = getPiggyBack(TimerTaskPiggyBack.class);
    if (pb != null) {
      TimerTask tt = pb.task;
      if (tt != null) {
        pb.task = null;
        return tt.cancel();
      }
    }
    return false;
  }

  public <X> X getPiggyBack(Class<X> _class) {
    Object obj = misc;
    if (!(obj instanceof PiggyBack)) {
      return null;
    }
    PiggyBack pb = (PiggyBack) obj;
    do {
      if (pb.getClass() == _class) {
        return (X) pb;
      }
      pb = pb.next;
    } while (pb != null);
    return null;
  }

  public void setTask(TimerTask v) {
    if (misc == null || misc instanceof TimerTask) {
      misc = v;
      return;
    }
    TimerTaskPiggyBack pb = getPiggyBack(TimerTaskPiggyBack.class);
    if (pb != null) {
      pb.task = v;
      return;
    }
    misc = new TimerTaskPiggyBack(v, (PiggyBack) misc);
  }

  public void setSuppressedLoadExceptionInformation(ExceptionInformation w) {
    Object _misc = misc;
    if (_misc instanceof TimerTask) {
      misc = new TimerTaskPiggyBack((TimerTask) _misc, new LoadExceptionPiggyBack(w, null));
      return;
    }
    LoadExceptionPiggyBack inf = getPiggyBack(LoadExceptionPiggyBack.class);
    if (inf != null) {
      inf.info = w;
      return;
    }
    misc = new LoadExceptionPiggyBack(w, (PiggyBack) _misc);
  }

  /**
   * If the entry carries information about a suppressed exception, clear it.
   */
  public void resetSuppressedLoadExceptionInformation() {
    LoadExceptionPiggyBack inf = getPiggyBack(LoadExceptionPiggyBack.class);
    if (inf != null) {
      inf.info = null;
    }
  }

  public ExceptionInformation getSuppressedLoadExceptionInformation() {
    LoadExceptionPiggyBack inf = getPiggyBack(LoadExceptionPiggyBack.class);
    return inf != null ? inf.info : null;
  }

  static class PiggyBack {
    PiggyBack next;
  }

  static class TimerTaskPiggyBack extends PiggyBack {
    TimerTask task;

    public TimerTaskPiggyBack(final TimerTask _task, final PiggyBack _next) {
      task = _task;
      next = _next;
    }
  }

  static class LoadExceptionPiggyBack extends PiggyBack {
    ExceptionInformation info;

    public LoadExceptionPiggyBack(final ExceptionInformation _info, final PiggyBack _next) {
      info = _info;
      next = _next;
    }
  }

}
