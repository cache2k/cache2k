package org.cache2k.core;

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

import org.cache2k.CacheEntry;
import org.cache2k.core.util.InternalClock;
import org.cache2k.core.util.SimpleTask;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.core.operation.ExaminationEntry;
import org.cache2k.core.storageApi.StorageEntry;
import org.cache2k.integration.ExceptionInformation;

import static org.cache2k.core.util.Util.*;

/**
 * Separate with relevant fields for read access only for optimizing the object layout.
 *
 * @author Jens Wilke
 */
class CompactEntry<K,T> {

  private static class InitialValueInEntryNeverReturned { }

  protected final static InitialValueInEntryNeverReturned INITIAL_VALUE = new InitialValueInEntryNeverReturned();

  /**
   * Contains the next time a refresh has to occur, or if no background refresh is configured, when the entry
   * is expired. Low values have a special meaning, see defined constants.
   * Negative values means that we need to check against the wall clock.
   */
  protected volatile long nextRefreshTime;

  private final K key;

  /**
   * Holds the associated entry value or an exception via the {@link ExceptionWrapper}
   */
  @SuppressWarnings("unchecked")
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
    T v = valueOrException;
    if (v instanceof ExceptionWrapper) { return ((ExceptionWrapper) v).getException(); }
    return null;
  }

  public boolean equalsValue(T v) {
    T ve = valueOrException;
    if (ve == null) {
      return v == ve;
    }
    return ve.equals(v);
  }

  @Deprecated
  public T getValue() {
    return valueOrException;
  }

  /**
   * The value of the entry or an {@link ExceptionWrapper}.
   */
  public T getValueOrException() {
    return valueOrException;
  }

  @SuppressWarnings("unchecked")
  public K getKey() {
    if (key == null) {
      return (K) Integer.valueOf(hashCode);
    }
    return key;
  }

  /**
   * Get the raw object reference. Is {@code null} for the {@link IntHeapCache}.
   */
  public K getKeyObj() {
    return key;
  }

}

/**
 * The cache entry. This is a combined hash table entry with hashCode and
 * and collision list (other field) and it contains a double linked list
 * (next and previous) for the eviction algorithm.
 *
 * <p>When an entry is present and its value is updated, the entry objects
 * stays the same. The entry object is used for locking and coordination of
 * all concurrent accesses to it.
 *
 * <p>The entry is only used as {@link CacheEntry} directly and handed to the
 * application when it is locked and the values are stable, that is for example
 * when the loader is called.
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

  static final int READ_NON_VALID = 1;

  /**
   * Cache.remove() operation received. Needs to be send to storage.
   */
  static final int REMOVE_PENDING = 2;

  static final int ABORTED = 3;

  /** @see #isExpired() */
  static final int EXPIRED = 4;

  /** Expired, but protect entry from removal, since refresh is started. */
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
  private volatile long refreshTimeAndState;

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

  /**
   * Set modification time and marks entry as dirty. Bit 0 was used as dirty bit for the
   * storage attachment. Not used at the moment.
   */
  public void setRefreshTime(long t) {
    refreshTimeAndState =
      refreshTimeAndState & ~MODIFICATION_TIME_MASK |
        ((((t - MODIFICATION_TIME_BASE) << MODIFICATION_TIME_SHIFT)) & MODIFICATION_TIME_MASK);
  }

  @Override
  public long getRefreshTime() {
    return (refreshTimeAndState & MODIFICATION_TIME_MASK) >> MODIFICATION_TIME_SHIFT;
  }

  @SuppressWarnings("deprecation")
  @Override @Deprecated
  public long getLastModification() {
    throw new UnsupportedOperationException();
  }

  /**
   * Different possible processing states. The code only uses fetch now, rest is preparation.
   * We don't use enums, since enums produce object garbage.
   */
  public static class ProcessingState {
    public static final int DONE = 0;
    public static final int READ = 1;
    public static final int READ_COMPLETE = 2;
    public static final int MUTATE = 3;
    public static final int LOAD = 4;
    public static final int LOAD_COMPLETE = 5;
    public static final int COMPUTE = 6;
    public static final int REFRESH = 7;
    public static final int EXPIRY = 8;
    public static final int EXPIRY_COMPLETE = 9;
    public static final int WRITE = 10;
    public static final int WRITE_COMPLETE = 11;
    public static final int STORE = 12;
    public static final int STORE_COMPLETE = 13;
    public static final int NOTIFY = 14;
    public static final int PINNED = 15;
    public static final int EVICT = 16;
    public static final int LAST = 17;
  }

  public static String num2processingStateText(int ps) {
    switch (ps) {
      case ProcessingState.DONE: return "DONE";
      case ProcessingState.READ: return "READ";
      case ProcessingState.READ_COMPLETE: return "READ_COMPLETE";
      case ProcessingState.MUTATE: return "MUTATE";
      case ProcessingState.LOAD: return "LOAD";
      case ProcessingState.LOAD_COMPLETE: return "LOAD_COMPLETE";
      case ProcessingState.COMPUTE: return "COMPUTE";
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
    return (int) ((refreshTimeAndState >> PS_POS) & PS_MASK);
  }

  private void setProcessingState(int v) {
    refreshTimeAndState = refreshTimeAndState & ~((long) PS_MASK << PS_POS) | (((long) v) << PS_POS);
  }

  /**
   * Starts long operation on entry. Pins the entry in the cache.
   */
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

  public boolean isStable() { return isProcessing(); }

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
   * <p>Even if this is true, the data may be expired. Use hasFreshData() to
   * make sure to get not expired data.
   */
  public final boolean isDataValid() {
    return nextRefreshTime >= DATA_VALID || nextRefreshTime < 0;
  }

  /**
   * Returns true if the entry has a valid value and is fresh / not expired.
   */
  public final boolean hasFreshData(InternalClock t) {
    if (nextRefreshTime >= DATA_VALID) {
      return true;
    }
    if (needsTimeCheck()) {
      return t.millis() < -nextRefreshTime;
    }
    return false;
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
    return getRefreshTime();
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
    Object _key = getKeyObj();
    if (_key == null) {
      sb.append(hashCode);
    } else {
      sb.append(_key);
      if (c != null && (c.modifiedHash(_key.hashCode()) != hashCode)) {
        sb.append(", keyMutation=true");
      }
    }
    Object _valueOrException = getValueOrException();
    if (_valueOrException instanceof ExceptionWrapper) {
      sb.append(", exception=").append((((ExceptionWrapper) _valueOrException).getException().getClass().getSimpleName()));
    } else if (_valueOrException != null) {
      sb.append(", valueId=").append(System.identityHashCode(_valueOrException));
    } else {
      sb.append(", value=null");
    }
    long t = getRefreshTime();
    if (t > 0) {
      sb.append(", refresh=").append(formatMillis(t));
    }
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
        sb.append(", timerState=").append(getTask());
      }
    } else {
      sb.append(", timerState=skipped/notLocked");
    }
    sb.append("}");
    return sb.toString();
  }

  @Override
  public String toString() {
    return toString(null);
  }

  public SimpleTask getTask() {
    if (misc instanceof SimpleTask) {
      return (SimpleTask) misc;
    }
    TaskPiggyBack pb = getPiggyBack(TaskPiggyBack.class);
    if (pb != null) {
      return pb.task;
    }
    return null;
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

  public void setTask(SimpleTask v) {
    if (misc == null || misc instanceof SimpleTask) {
      misc = v;
      return;
    }
    TaskPiggyBack pb = getPiggyBack(TaskPiggyBack.class);
    if (pb != null) {
      pb.task = v;
      return;
    }
    misc = new TaskPiggyBack(v, (PiggyBack) misc);
  }

  /**
   * We want to add a new piggy back. Check for timer task and convert it to
   * piggy back.
   */
  private PiggyBack existingPiggyBackForInserting() {
    Object _misc = misc;
    if (_misc instanceof SimpleTask) {
      return new TaskPiggyBack((SimpleTask) _misc, null);
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

  static class TaskPiggyBack extends PiggyBack {
    SimpleTask task;

    public TaskPiggyBack(final SimpleTask _task, final PiggyBack _next) {
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


  public static <E extends Entry> E insertIntoTailCyclicList(final E _head, final E e) {
    if (_head == null) {
      return (E) e.shortCircuit();
    }
    e.next = _head;
    e.prev = _head.prev;
    _head.prev = e;
    e.prev.next = e;
    return _head;
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
      if (e.next.prev != e) {
        return false;
      }
      e = e.next;
    } while (e != _head);
    return true;
  }

}
