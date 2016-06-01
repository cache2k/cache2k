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

import org.cache2k.core.threading.Job;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static org.cache2k.core.util.Util.*;

/**
 * Iterator over all cache entries of two hashes.
 *
 * <p><b>Two hashes:</b>
 * The cache consists of two hashes. The main and the refresh
 * hash. To iterate over all entries we need to iterate over both hashes.
 * Since the iteration runs concurrently entries migrate from one hash
 * to the other. To make sure no entries are lost, two iterations are
 * processed one starting with the main and one with the refresh hash.
 * </p>
 *
 * <p><b>Hash expansion:</b></p>
 * During the iteration a hash expansion may happen, which means every
 * entry is rehashed. In this case it is most likely that entries are missed.
 * If an expansion occurred, there is another iteration over the new
 * hash table contents.
 *
 * <p><b>Clear: </b>
 * A clear operation stops current iterations.
 * </p>
 *
 * <p><b>Close: </b>
 * A close operation will stop the iteration and yield a {@link CacheClosedException}
 * </p>
 *
 * @author Jens Wilke; created: 2013-12-21
 */
public class ConcurrentEntryIterator<K,V> implements Iterator<Entry<K,V>> {

  HeapCache<K, V> cache;
  Entry lastEntry = null;
  Entry nextEntry = null;
  int sequenceCnt = 0;
  int lastSequenceCnt;
  long initialMaxFill;
  long clearCount;
  Hash2<K,V> hash;
  Entry<K,V>[] hashArray;
  Hash<Entry<K, V>> iteratedCtl = new Hash<Entry<K,V>>();
  Entry<K, V>[] iterated;

  public ConcurrentEntryIterator(HeapCache<K,V> _cache) {
    cache = _cache;
    iterated = iteratedCtl.initSingleThreaded((Class<Entry<K, V>>) (Object) Entry.class);
    lastSequenceCnt = 2;
    if (cache.hasBackgroundRefresh()) {
      lastSequenceCnt = 4;
    }
    switchAndCheckAbort();
  }

  @Override
  public boolean hasNext() {
    return (nextEntry = nextEntry()) != null;
  }

  @Override
  public Entry<K,V> next() {
    if (nextEntry != null) {
      Entry<K,V> e = nextEntry;
      nextEntry = null;
      return e;
    }
    Entry<K,V> e = nextEntry();
    if (e == null) {
      throw new NoSuchElementException("not available");
    }
    return e;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  /** Used by the storage code to filter out already iterated keys */
  public boolean hasBeenIterated(Object key, int _hashCode) {
    return Hash.contains(iterated, key, _hashCode);
  }

  public void markIterated(Object key, int _hashCode) {
    Entry _newEntryIterated = new Entry(key, _hashCode);
    iteratedCtl.insert(iterated, _newEntryIterated);
    iterated = iteratedCtl.expand(iterated, _hashCode);
  }

  private Entry nextEntry() {
    Entry e;
    if (hashArray == null) {
      return null;
    }
    if (wasCleared()) {
      return null;
    }
    int idx = 0;
    if (lastEntry != null) {
      e = lastEntry.another;
      if (e != null) {
        e = checkIteratedOrNext(e);
        if (e != null) {
          lastEntry = e;
          return e;
        }
      }
      idx = (lastEntry.hashCode & (hashArray.length - 1) )+ 1;
    }
    for (;;) {
      if (idx >= hashArray.length) {
        if (switchAndCheckAbort()) {
          return null;
        }
        idx = 0;
      }
      e = hashArray[idx];
      if (e != null) {
        e = checkIteratedOrNext(e);
        if (e != null) {
          lastEntry = e;
          return e;
        }
      }
      idx++;
    }
  }

  private boolean wasCleared() {
    return clearCount != hash.getClearCount();
  }

  private Entry<K,V> checkIteratedOrNext(Entry<K,V> e) {
    do {
      boolean _notYetIterated = !Hash.contains(iterated, e.key, e.hashCode);
      if (_notYetIterated) {
        markIterated(e.key, e.hashCode);
        return e;
      }
      e = e.another;
    } while (e != null);
    return null;
  }

  private boolean switchAndCheckAbort() {
    if (Thread.holdsLock(cache.lock)) {
      return switchCheckAndAbortLocked();
    }
    return cache.executeWithGlobalLock(new Job<Boolean>() {
      @Override
      public Boolean call() {
        return switchCheckAndAbortLocked();
      }
    });
  }

  private Boolean switchCheckAndAbortLocked() {
    if (hasExpansionOccurred()) {
      lastSequenceCnt += 2;
    }
    if (lastSequenceCnt == sequenceCnt) {
      hashArray = null;
      return true;
    }
    int _step = sequenceCnt % 6;
    if (_step == 0 || _step == 3 || _step == 4) {
      switchToMainHash();
    }
    if (_step == 1 || _step == 2 || _step == 5) {
    }
    clearCount = hash.getClearCount();
    boolean _cacheClosed = hashArray == null;
    if (_cacheClosed) {
      return true;
    }
    initialMaxFill = hash.getMaxFill();
    sequenceCnt++;
    return false;
  }

  /**
   * True if hash table expanded while iterating. Triggers another
   * scan over the hash tables.
   */
  private boolean hasExpansionOccurred() {
    return hash != null && initialMaxFill != hash.getMaxFill();
  }

  private void switchToMainHash() {
    hash = cache.hash;
    hashArray = hash.getEntries();
  }


}
