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
 * Separate with relevant fields for read access only for optimizing the object layout.
 *
 * @author Jens Wilke
 */
class CompactEntry<K,T> {

  private static class InitialValueInEntryNeverReturned extends Object { }

  protected final static InitialValueInEntryNeverReturned INITIAL_VALUE = new InitialValueInEntryNeverReturned();

  /**
   * Contains the next time a refresh has to occur, or if no background refresh is configured, when the entry
   * is expired. Low values have a special meaning, see defined constants.
   * Negative values means that we need to check against the wall clock.
   */
  protected volatile long nextRefreshTime;

  public final K key;

  /**
   * Holds the associated entry value or an exception via the {@link ExceptionWrapper}
   */
  private volatile T valueOrException = (T) INITIAL_VALUE;

  /**
   * Hash implementation: the calculated, modified hash code, retrieved from the key when the entry is
   * inserted in the cache
   *
   * @see HeapCache#modifiedHash(int)
   */
  public final int hashCode;

  /**
   * Hash implementation: Link to another entry in the same hash table slot when the hash code collides.
   */
  public Entry<K, T> another;

  /**
   * Hit counter. Modified directly by heap cache and eviction algorithm.
   *
   * @see HeapCache#recordHit(Entry)
   */
  public long hitCnt;

  public CompactEntry(final K _key, final int _hashCode) {
    key = _key;
    hashCode = _hashCode;
  }

  public void setValueOrException(T _valueOrException) {
    valueOrException = _valueOrException;
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

  /**
   * Used for the storage interface.
   *
   * @see StorageEntry
   */
  public T getValueOrException() {
    return valueOrException;
  }

}

/**
 * The cache entry. This is a combined hashtable entry with hashCode and
 * and collision list (other field) and it contains a double linked list
 * (next and previous) for the eviction algorithm.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("unchecked")
public class Entry<K, T> extends CompactEntry<K,T>
  implements CacheEntry<K,T>, StorageEntry, ExaminationEntry<K, T> {

  /**
   * A value greater as means it is a time value.
   */
  public static final int EXPIRY_TIME_MIN = 32;

  /**
   * bigger or equal means entry has / contains valid data
   */
  static final int DATA_VALID = 16;

  /** @see #isVirgin() */
  static final int VIRGIN = 0;

  /** @see #isReadNonValid() */
  static final int READ_NON_VALID = 1;

  /**
   * Cache.remove() operation received. Needs to be send to storage.
   */
  static final int REMOVE_PENDING = 2;

  static final int ABORTED = 3;

  /** @see #isExpired() */
  static final int EXPIRED = 4;

  /** Expired, but protect entry from remval, since refresh is started. */
  static final int EXPIRED_REFRESH_PENDING = 5;

  static final int EXPIRED_REFRESHED = 6;

  /** @see #isGone() */
  static final int GONE = 8;
  static final int GONE_OTHER = 15;

  /**
   * Usually this contains the reference to the timer task. In some cases, like when exceptions
   * happen we will link to the PiggyBack object to add more information as needed.
   */
  private Object misc;

  /**
   * Time the entry was last updated by put or by fetching it from the cache source.
   * The time is the time in millis times 2. A set bit 1 means the entry is fetched from
   * the storage and not modified since then.
   */
  private volatile long fetchedTime;

  /** Lru list: pointer to next element or list head */
  public Entry next;

  /** Lru list: pointer to previous element or list head */
  public Entry prev;

  /** Marker for Clock-PRO clock */
  private boolean hot;

  public Entry(final K _key, final int _hashCode) {
    super(_key, _hashCode);
  }

  public Entry() { this(null, 0); }

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
   * We don't use enums, since enums produce object garbage.
   */
  static class ProcessingState {
    static final int DONE = 0;
    static final int READ = 1;
    static final int READ_COMPLETE = 2;
    static final int MUTATE = 3;
    static final int LOAD = 4;
    static final int LOAD_COMPLETE = 5;
    static final int FETCH = 6;
    static final int REFRESH = 7;
    static final int EXPIRY = 8;
    static final int EXPIRY_COMPLETE = 9;
    static final int WRITE = 10;
    static final int WRITE_COMPLETE = 11;
    static final int STORE = 12;
    static final int STORE_COMPLETE = 13;
    static final int NOTIFY = 14;
    static final int PINNED = 15;
    static final int EVICT = 16;
    static final int LAST = 17;
  }

  String num2processingStateText(int ps) {
    switch (ps) {
      case ProcessingState.DONE: return "DONE";
      case ProcessingState.READ: return "READ";
      case ProcessingState.READ_COMPLETE: return "READ_COMPLETE";
      case ProcessingState.MUTATE: return "MUTATE";
      case ProcessingState.LOAD: return "LOAD";
      case ProcessingState.LOAD_COMPLETE: return "LOAD_COMPLETE";
      case ProcessingState.FETCH: return "FETCH";
      case ProcessingState.REFRESH: return "REFRESH";
      case ProcessingState.EXPIRY: return "EXPIRY";
      case ProcessingState.EXPIRY_COMPLETE: return "EXPIRY_COMPLETE";
      case ProcessingState.WRITE: return "WRITE";
      case ProcessingState.WRITE_COMPLETE: return "WRITE_COMPLETE";
      case ProcessingState.STORE: return "STORE";
      case ProcessingState.STORE_COMPLETE: return "STORE_COMPLETE";
      case ProcessingState.NOTIFY: return "NOTIFY";
      case ProcessingState.PINNED: return "PINNED";
      case ProcessingState.EVICT: return "EVICT";
      case ProcessingState.LAST: return "LAST";
    }
    return "UNKNOWN";
  }

  private static final int PS_BITS = 5;
  private static final int PS_MASK = (1 << PS_BITS) - 1;
  private static final int PS_POS = MODIFICATION_TIME_BITS;

  public int getProcessingState() {
    return (int) ((fetchedTime >> PS_POS) & PS_MASK);
  }

  private void setProcessingState(int v) {
    fetchedTime = fetchedTime & ~((long) PS_MASK << PS_POS) | (((long) v) << PS_POS);
  }

  /**
   * Starts long operation on entry. Pins the entry in the cache.
   */
  public void startProcessing() {
    setProcessingState(ProcessingState.FETCH);
  }

  public void startProcessing(int ps) {
    setProcessingState(ps);
  }

  /**
   * Switch to another processing state that is other then done.
   */
  public void nextProcessingStep(int ps) {
    setProcessingState(ps);
  }

  /**
   * Set processing state to done and notify all that wait for
   * processing this entry.
   */
  public void processingDone() {
    notifyAll();
    setProcessingState(ProcessingState.DONE);
  }

  public long getNextRefreshTime() {
    return nextRefreshTime;
  }

  public void setNextRefreshTime(long _nextRefreshTime) {
    nextRefreshTime = _nextRefreshTime;
  }

  /**
   * Make sure entry processing is properly finished, otherwise threads waiting for
   * an entry get stuck.
   *
   * <p>Usually no exceptions happens, but the CacheClosedException is happening out of order
   * and stops processing to properly finish.
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
        processingDone();
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

  private static final Entry LIST_REMOVED_MARKER = new Entry();

  /** Reset next as a marker for {@link #isRemovedFromReplacementList()} */
  public final void removedFromList() {
    next = LIST_REMOVED_MARKER;
    prev = null;
  }

  /** Check that this entry is removed from the list, may be used in assertions. */
  public boolean isRemovedFromReplacementList() {
    return next == LIST_REMOVED_MARKER;
  }

  /**
   * Entry was not inserted into the replacement list AND not removed from replacement list.
   * In practise it may happen that an entry gets removed before even entering the replacement list,
   * because of clear.
   */
  public boolean isNotYetInsertedInReplacementList() {
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
   * The entry expired, still in the cache and subject to removal from the cache
   * if {@link HeapCache#hasKeepAfterExpired()} is false.
   */
  public boolean isExpired() {
    return nextRefreshTime == EXPIRED;
  }

  public void setGone() {
    long nrt = nextRefreshTime;
    if (nrt >= 0 && nrt < GONE) {
      nextRefreshTime = GONE + nrt;
      return;
    }
    nextRefreshTime = GONE_OTHER;
  }

  /**
   * The entry is not present in the heap any more and was evicted, expired or removed.
   */
  public boolean isGone() {
    long nrt = nextRefreshTime;
    return nrt >= GONE && nrt <= GONE_OTHER;
  }

  public boolean needsTimeCheck() {
    return nextRefreshTime < 0;
  }


  public boolean isHot() { return hot; }

  public void setHot(boolean f) {
    hot = f;
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
    sb.append("id=").append(System.identityHashCode(this));
    sb.append(", lock=").append(num2processingStateText(getProcessingState()));
    sb.append(", key=");
    if (key == null) {
      sb.append("null");
    } else {
      sb.append(key);
      if (c != null && (c.modifiedHash(key.hashCode()) != hashCode)) {
        sb.append(", keyMutation=true");
      }
    }
    Object _valueOrException = getValueOrException();
    if (_valueOrException != null) {
      sb.append(", valueId=").append(System.identityHashCode(_valueOrException));
    } else {
      sb.append(", value=null");
    }
    sb.append(", modified=").append(formatMillis(getLastModification()));
    long nrt = nextRefreshTime;
    if (nrt < 0) {
      sb.append(", nextRefreshTime(sharp)=").append(formatMillis(-nrt));
    } else if (nrt == ExpiryPolicy.ETERNAL) {
      sb.append(", nextRefreshTime=ETERNAL");
    } else if (nrt >= EXPIRY_TIME_MIN) {
      sb.append(", nextRefreshTime(timer)=").append(formatMillis(nrt));
    } else {
      sb.append(", state=").append(nrt);
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

  /**
   * We want to add a new piggy back. Check for timer task and convert it to
   * piggy back.
   */
  private PiggyBack existingPiggyBackForInserting() {
    Object _misc = misc;
    if (_misc instanceof TimerTask) {
      return new TimerTaskPiggyBack((TimerTask) _misc, null);
    }
    return (PiggyBack) _misc;
  }

  public void setSuppressedLoadExceptionInformation(ExceptionInformation w) {
    LoadExceptionPiggyBack inf = getPiggyBack(LoadExceptionPiggyBack.class);
    if (inf != null) {
      inf.info = w;
      return;
    }
    misc = new LoadExceptionPiggyBack(w, existingPiggyBackForInserting());
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

  public void setRefreshProbationNextRefreshTime(long nrt) {
    RefreshProbationPiggyBack inf = getPiggyBack(RefreshProbationPiggyBack.class);
    if (inf != null) {
      inf.nextRefreshTime = nrt;
      return;
    }
    misc = new RefreshProbationPiggyBack(nrt, existingPiggyBackForInserting());
  }

  public long getRefreshProbationNextRefreshTime() {
    RefreshProbationPiggyBack inf = getPiggyBack(RefreshProbationPiggyBack.class);
    return inf != null ? inf.nextRefreshTime : 0;
  }

  static class PiggyBack {
    final PiggyBack next;

    public PiggyBack(final PiggyBack _next) {
      next = _next;
    }
  }

  static class TimerTaskPiggyBack extends PiggyBack {
    TimerTask task;

    public TimerTaskPiggyBack(final TimerTask _task, final PiggyBack _next) {
      super(_next);
      task = _task;
    }
  }

  static class LoadExceptionPiggyBack extends PiggyBack {
    ExceptionInformation info;

    public LoadExceptionPiggyBack(final ExceptionInformation _info, final PiggyBack _next) {
      super(_next);
      info = _info;
    }
  }

  static class RefreshProbationPiggyBack extends PiggyBack {
    long nextRefreshTime;

    public RefreshProbationPiggyBack(long _nextRefreshTime, final PiggyBack _next) {
      super(_next);
      nextRefreshTime = _nextRefreshTime;
    }
  }

  /*
   * **************************************** LRU list operation *******************************************
   */

  public static void removeFromList(final Entry e) {
    e.prev.next = e.next;
    e.next.prev = e.prev;
    e.removedFromList();
  }

  public static void insertInList(final Entry _head, final Entry e) {
    e.prev = _head;
    e.next = _head.next;
    e.next.prev = e;
    _head.next = e;
  }

  public static final int getListEntryCount(final Entry _head) {
    Entry e = _head.next;
    int cnt = 0;
    while (e != _head) {
      cnt++;
      if (e == null) {
        return -cnt;
      }
      e = e.next;
    }
    return cnt;
  }

  public static final <E extends Entry> void moveToFront(final E _head, final E e) {
    removeFromList(e);
    insertInList(_head, e);
  }

  public static final <E extends Entry> E insertIntoTailCyclicList(final E _head, final E e) {
    if (_head == null) {
      return (E) e.shortCircuit();
    }
    e.next = _head;
    e.prev = _head.prev;
    _head.prev = e;
    e.prev.next = e;
    return _head;
  }

  /**
   * Insert X into A B C, yields: A X B C.
   */
  public static final <E extends Entry> E insertAfterHeadCyclicList(final E _head, final E e) {
    if (_head == null) {
      return (E) e.shortCircuit();
    }
    e.prev = _head;
    e.next = _head.next;
    _head.next.prev = e;
    _head.next = e;
    return _head;
  }

  /** Insert element at the head of the list */
  public static final <E extends Entry> E insertIntoHeadCyclicList(final E _head, final E e) {
    if (_head == null) {
      return (E) e.shortCircuit();
    }
    e.next = _head;
    e.prev = _head.prev;
    _head.prev.next = e;
    _head.prev = e;
    return e;
  }

  public static <E extends Entry> E removeFromCyclicList(final E _head, E e) {
    if (e.next == e) {
      e.removedFromList();
      return null;
    }
    Entry _eNext = e.next;
    e.prev.next = _eNext;
    e.next.prev = e.prev;
    e.removedFromList();
    return e == _head ? (E) _eNext : _head;
  }

  public static Entry removeFromCyclicList(final Entry e) {
    Entry _eNext = e.next;
    e.prev.next = _eNext;
    e.next.prev = e.prev;
    e.removedFromList();
    return _eNext == e ? null : _eNext;
  }

  public static int getCyclicListEntryCount(Entry e) {
    if (e == null) { return 0; }
    final Entry _head = e;
    int cnt = 0;
    do {
      cnt++;
      e = e.next;
      if (e == null) {
        return -cnt;
      }
    } while (e != _head);
    return cnt;
  }

  public static boolean checkCyclicListIntegrity(Entry e) {
    if (e == null) { return true; }
    Entry _head = e;
    do {
      if (e.next == null) {
        return false;
      }
      if (e.next.prev == null) {
        return false;
      }
      if (e.next.prev != e) {
        return false;
      }
      e = e.next;
    } while (e != _head);
    return true;
  }

}
