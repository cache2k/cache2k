package org.cache2k.core;

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

import org.cache2k.CacheEntry;
import org.cache2k.core.util.InternalClock;
import org.cache2k.core.timing.TimerTask;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.core.operation.ExaminationEntry;
import org.cache2k.core.storageApi.StorageEntry;
import org.cache2k.integration.ExceptionInformation;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import static org.cache2k.core.util.Util.*;

/**
 * Separate with relevant fields for read access only for optimizing the object layout.
 *
 * @author Jens Wilke
 */
class CompactEntry<K, V> {

  private static class InitialValueInEntryNeverReturned { }

  protected static final InitialValueInEntryNeverReturned INITIAL_VALUE =
    new InitialValueInEntryNeverReturned();

  /**
   * Contains the next time a refresh has to occur, or if no background refresh is configured,
   * when the entry is expired. Low values have a special meaning, see defined constants.
   * Negative values means that we need to check against the wall clock.
   */
  protected volatile long nextRefreshTime;

  private final K key;

  /**
   * Holds the associated entry value or an exception via the {@link ExceptionWrapper}
   */
  @SuppressWarnings("unchecked")
  private volatile V valueOrException = (V) INITIAL_VALUE;

  /**
   * Hash implementation: the calculated, modified hash code, retrieved from the key when
   * the entry is inserted in the cache
   *
   * @see HeapCache#modifiedHash(int)
   */
  public final int hashCode;

  /**
   * Hash implementation: Link to another entry in the same hash table slot when the hash
   * code collides.
   */
  public Entry<K, V> another;

  /**
   * Hit counter. Modified directly by heap cache and eviction algorithm.
   *
   * @see HeapCache#recordHit(Entry)
   */
  public long hitCnt;

  CompactEntry(K key, int hashCode) {
    this.key = key;
    this.hashCode = hashCode;
  }

  public void setValueOrException(V valueOrException) {
    this.valueOrException = valueOrException;
  }

  public Throwable getException() {
    V v = valueOrException;
    if (v instanceof ExceptionWrapper) { return ((ExceptionWrapper) v).getException(); }
    return null;
  }

  public boolean equalsValue(V v) {
    V ve = valueOrException;
    if (ve == null) {
      return v == ve;
    }
    return ve.equals(v);
  }

  /**
   * Should never be called under normal circumstances. For efficiency reasons the entry is
   * handed to the expiry policy directly, before it is screened for an exception.
   *
   * @Deprecated May only be called as CacheEntry before screening for exceptions.
   */
  @Deprecated
  public V getValue() {
    return valueOrException;
  }

  /**
   * The value of the entry or an {@link ExceptionWrapper}.
   */
  public V getValueOrException() {
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
public class Entry<K, V> extends CompactEntry<K, V>
  implements CacheEntry<K, V>, StorageEntry, ExaminationEntry<K, V> {

  /**
   * A value greater as means it is a time value.
   */
  public static final int EXPIRY_TIME_MIN = 32;

  /**
   * bigger or equal means entry has / contains valid data
   */
  public static final int DATA_VALID = 16;

  /** @see #isVirgin() */
  public static final int VIRGIN = 0;

  public static final int READ_NON_VALID = 1;

  /**
   * Cache.remove() operation received. Needs to be send to storage.
   */
  public static final int REMOVE_PENDING = 2;

  public static final int ABORTED = 3;

  /** @see #isExpiredState() */
  public static final int EXPIRED = 4;

  /** Expired, but protect entry from removal, since refresh is started. */
  public static final int EXPIRED_REFRESH_PENDING = 5;

  /** Entry is in refresh probation phase */
  public static final int EXPIRED_REFRESHED = 6;

  /** @see #isGone() */
  public static final int GONE = 8;
  public static final int GONE_OTHER = 15;

  /**
   * Usually this contains the reference to the timer task. In some cases, like when exceptions
   * happen we will link to the PiggyBack object to add more information as needed.
   */
  private Object misc;

  /**
   * Time the entry was last updated by put or by fetching it from the cache loader.
   * The time is the time in millis times 2. A set bit 1 means the entry is fetched from
   * the storage and not modified since then.
   */
  private volatile long refreshTimeAndState;
  private static final AtomicLongFieldUpdater<Entry> STATE_UPDATER =
    AtomicLongFieldUpdater.newUpdater(Entry.class, "refreshTimeAndState");

  /** Lru list: pointer to next element or list head */
  public Entry next;

  /** Lru list: pointer to previous element or list head */
  public Entry prev;

  /** Marker for Clock-PRO clock */
  private int hotAndWeight;

  public Entry(K key, int hashCode) {
    super(key, hashCode);
  }

  public Entry() { this(null, 0); }

  private static final int MODIFICATION_TIME_BITS = 44;
  private static final long MODIFICATION_TIME_BASE = 0;
  private static final int MODIFICATION_TIME_SHIFT = 1;
  /** including dirty */
  private static final long MODIFICATION_TIME_MASK = (1L << MODIFICATION_TIME_BITS) - 1;

  /**
   * A cache entry which is used for a call to the expiry or refresh policies.
   */
  public CacheEntry<K, V> getTempCacheEntry() {
    Object obj = getValueOrException();
    if (obj instanceof ExceptionWrapper) { return (CacheEntry) obj; }
    return this;
  }

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
    public static final int LOAD_ASYNC = 17;
    public static final int WRITE_ASYNC = 18;
    public static final int EXPIRE = 19;
    public static final int LAST = 20;
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
      case ProcessingState.LOAD_ASYNC: return "LOAD_ASYNC";
      case ProcessingState.WRITE_ASYNC: return "WRITE_ASYNC";
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
    refreshTimeAndState = withState(refreshTimeAndState, v);
  }

  private long withState(long refreshTimeAndState, long state) {
    return refreshTimeAndState & ~((long) PS_MASK << PS_POS) | (state << PS_POS);
  }

  /**
   * Check and switch the processing state atomically.
   */
  public boolean checkAndSwitchProcessingState(int ps0, int ps) {
    long rt = refreshTimeAndState;
    long expect = withState(rt, ps0);
    long update = withState(rt, ps);
    return STATE_UPDATER.compareAndSet(this, expect, update);
  }

  /**
   * Starts long operation on entry. Pins the entry in the cache.
   */
  public void startProcessing(int ps, EntryAction action) {
    setProcessingState(ps);
    if (action != null) {
      setEntryAction(action);
    }
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
  public void processingDone(EntryAction action) {
    processingDone();
  }

  public void processingDone() {
    notifyAll();
    setProcessingState(ProcessingState.DONE);
    resetEntryAction();
  }

  public long getNextRefreshTime() {
    return nextRefreshTime;
  }

  public void setNextRefreshTime(long nextRefreshTime) {
    this.nextRefreshTime = nextRefreshTime;
  }

  /**
   * Make sure entry processing is properly finished, otherwise threads waiting for
   * an entry get stuck.
   *
   * <p>Usually no exceptions happens, but the CacheClosedException is happening out of order
   * and stops processing to properly finish.
   */
  public void ensureAbort(boolean finished) {
    if (finished) {
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
    boolean interrupt = false;
    do {
      try {
        wait();
      } catch (InterruptedException ignore) {
        interrupt = true;
      }
    } while (isProcessing());
    if (interrupt) {
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
  public final boolean isDataAvailable() {
    return nextRefreshTime >= DATA_VALID || nextRefreshTime < 0;
  }

  public final boolean isDataAvailableOrProbation() {
    return isDataAvailable() || nextRefreshTime == Entry.EXPIRED_REFRESHED;
  }

  /**
   * Entry has a valid value and is fresh / not expired. This predicate may depend on
   * the clock, if the expiry of an entry was not yet detected by the cache, e.g.
   * the internal expiry event is not yet delivered.
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
   * if {@link HeapCache#isKeepAfterExpired()} is false. {@code true} means that
   * the expiry was detected by the cache. To find out whether the entry is valid
   * and not expired {@link #hasFreshData(InternalClock)} needs to be used.
   */
  public boolean isExpiredState() {
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
  public final boolean isGone() {
    long nrt = nextRefreshTime;
    return nrt >= GONE && nrt <= GONE_OTHER;
  }

  public final boolean needsTimeCheck() {
    return nextRefreshTime < 0;
  }


  public boolean isHot() { return hotAndWeight < 0; }

  public void setHot(boolean f) {
    if (f) {
      hotAndWeight = hotAndWeight | 0x80000000;
    } else {
      hotAndWeight = hotAndWeight & 0x7fffffff;
    }
  }

  /**
   * Store weight as 16 bit floating point number.
   */
  public void setCompressedWeight(int v) {
    hotAndWeight = hotAndWeight & 0x80000000 | v;
  }

  public int getCompressedWeight() {
    return hotAndWeight & 0x7fffffff;
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
    Object key = getKeyObj();
    if (key == null) {
      sb.append(hashCode);
    } else {
      sb.append(key);
      if (c != null && (HeapCache.modifiedHash(key.hashCode()) != hashCode)) {
        sb.append(", keyMutation=true");
      }
    }
    Object valueOrException = getValueOrException();
    if (valueOrException instanceof ExceptionWrapper) {
      sb.append(", exception=")
        .append((((ExceptionWrapper) valueOrException).getException().getClass().getSimpleName()));
    } else if (valueOrException != null) {
      sb.append(", valueId=").append(System.identityHashCode(valueOrException));
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
      } else {
        sb.append(", noTimer");
      }
    } else {
      sb.append(", timerState=?");
    }
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
    TaskPiggyBack pb = getPiggyBack(TaskPiggyBack.class);
    if (pb != null) {
      return pb.task;
    }
    return null;
  }

  public <X> X getPiggyBack(Class<X> type) {
    Object obj = misc;
    if (!(obj instanceof PiggyBack)) {
      return null;
    }
    PiggyBack pb = (PiggyBack) obj;
    do {
      if (pb.getClass() == type) {
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
    TaskPiggyBack pb = getPiggyBack(TaskPiggyBack.class);
    if (pb != null) {
      pb.task = v;
      return;
    }
    misc = new TaskPiggyBack(v, (PiggyBack) misc);
  }

  public void setEntryAction(EntryAction action) {
    action.next = existingPiggyBackForInserting();
    misc = action;
  }

  public EntryAction getEntryAction() {
    if (!(misc instanceof PiggyBack)) {
      return null;
    }
    PiggyBack at = ((PiggyBack) misc);
    while (at != null) {
      if (at instanceof EntryAction) {
        return (EntryAction) at;
      }
      at = at.next;
    }
    return null;
  }

  public void resetEntryAction() {
    if (!(misc instanceof PiggyBack)) {
      return;
    }
    if (misc instanceof EntryAction) {
      misc = ((PiggyBack) misc).next;
      return;
    }
    PiggyBack at = ((PiggyBack) misc);
    while (at != null) {
      PiggyBack next = at.next;
      if (next instanceof EntryAction) {
        at.next = next.next;
        return;
      }
      at = next;
    }
  }

  /**
   * We want to add a new piggy back. Check for timer task and convert it to
   * piggy back.
   */
  private PiggyBack existingPiggyBackForInserting() {
    Object misc = this.misc;
    if (misc instanceof TimerTask) {
      return new TaskPiggyBack((TimerTask) misc, null);
    }
    return (PiggyBack) misc;
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
    PiggyBack next;

    PiggyBack(PiggyBack next) {
      this.next = next;
    }
  }

  static class TaskPiggyBack extends PiggyBack {
    TimerTask task;

    TaskPiggyBack(TimerTask task, PiggyBack next) {
      super(next);
      this.task = task;
    }
  }

  static class LoadExceptionPiggyBack extends PiggyBack {
    ExceptionInformation info;

    LoadExceptionPiggyBack(ExceptionInformation info, PiggyBack next) {
      super(next);
      this.info = info;
    }
  }

  static class RefreshProbationPiggyBack extends PiggyBack {
    long nextRefreshTime;

    RefreshProbationPiggyBack(long nextRefreshTime, PiggyBack next) {
      super(next);
      this.nextRefreshTime = nextRefreshTime;
    }
  }

  /*
   * **************************************** LRU list operation ********************************
   */

  public static void removeFromList(Entry e) {
    e.prev.next = e.next;
    e.next.prev = e.prev;
    e.removedFromList();
  }

  public static void insertInList(Entry head, Entry e) {
    e.prev = head;
    e.next = head.next;
    e.next.prev = e;
    head.next = e;
  }


  public static <E extends Entry> E insertIntoTailCyclicList(E head, E e) {
    if (head == null) {
      return (E) e.shortCircuit();
    }
    e.next = head;
    e.prev = head.prev;
    head.prev = e;
    e.prev.next = e;
    return head;
  }


  public static <E extends Entry> E removeFromCyclicList(E head, E e) {
    if (e.next == e) {
      e.removedFromList();
      return null;
    }
    Entry eNext = e.next;
    e.prev.next = eNext;
    e.next.prev = e.prev;
    e.removedFromList();
    return e == head ? (E) eNext : head;
  }

  public static Entry removeFromCyclicList(Entry e) {
    Entry eNext = e.next;
    e.prev.next = eNext;
    e.next.prev = e.prev;
    e.removedFromList();
    return eNext == e ? null : eNext;
  }

  public static int getCyclicListEntryCount(Entry e) {
    if (e == null) { return 0; }
    Entry head = e;
    int cnt = 0;
    do {
      cnt++;
      e = e.next;
      if (e == null) {
        return -cnt;
      }
    } while (e != head);
    return cnt;
  }

  public static boolean checkCyclicListIntegrity(Entry e) {
    if (e == null) { return true; }
    Entry head = e;
    do {
      if (e.next == null) {
        return false;
      }
      if (e.next.prev != e) {
        return false;
      }
      e = e.next;
    } while (e != head);
    return true;
  }

}
