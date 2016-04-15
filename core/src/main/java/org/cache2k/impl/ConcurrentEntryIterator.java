package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
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

import java.util.Iterator;
import java.util.NoSuchElementException;

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

  BaseCache<K, V> cache;
  Entry lastEntry = null;
  Entry nextEntry = null;
  int sequenceCnt = 0;
  int lastSequenceCnt;
  int initialHashSize;
  Hash<Entry<K, V>> hashCtl;
  Entry<K, V>[] hash;
  Hash<Entry<K, V>> iteratedCtl = new Hash<Entry<K,V>>();
  Entry<K, V>[] iterated;

  public ConcurrentEntryIterator(BaseCache<K,V> _cache) {
    cache = _cache;
    iterated = iteratedCtl.init((Class<Entry<K, V>>) (Object) Entry.class);
    lastSequenceCnt = 2;
    if (cache.hasBackgroundRefresh()) {
      lastSequenceCnt = 4;
    }
    switchAndCheckAbort();
  }

  private Entry nextEntry() {
    Entry e;
    if (hash == null) {
      return null;
    }
    if (hashCtl.shouldAbort()) {
      if (hashCtl.isCleared()) {
        return null;
      }
      if (hashCtl.isClosed()) {
        throw new CacheClosedException();
      }
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
      idx = Hash.index(hash, lastEntry.hashCode) + 1;
    }
    for (;;) {
      if (idx >= hash.length) {
        if (switchAndCheckAbort()) {
          return null;
        }
        idx = 0;
      }
      e = hash[idx];
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

  protected Entry<K,V> checkIteratedOrNext(Entry<K,V> e) {
    do {
      boolean _notYetIterated = !Hash.contains(iterated, e.key, e.hashCode);
      if (_notYetIterated) {
        Entry _newEntryIterated = new Entry();
        _newEntryIterated.key = e.key;
        _newEntryIterated.hashCode = e.hashCode;
        iterated = iteratedCtl.insert(iterated, _newEntryIterated);
        return e;
      }
      e = e.another;
    } while (e != null);
    return null;
  }

  protected boolean switchAndCheckAbort() {
    synchronized (cache.lock) {
      if (hasExpansionOccurred()) {
        lastSequenceCnt += 2;
      }
      if (lastSequenceCnt == sequenceCnt) {
        hash = null;
        return true;
      }
      int _step = sequenceCnt % 6;
      if (_step == 0 || _step == 3 || _step == 4) {
        switchToMainHash();
      }
      if (_step == 1 || _step == 2 || _step == 5) {
        switchToRefreshHash();
      }
      boolean _cacheClosed = hash == null;
      if (_cacheClosed) {
        return true;
      }
      initialHashSize = hashCtl.size;
      sequenceCnt++;
    }
    return false;
  }

  /**
   * True if hash table expanded while iterating. Triggers another
   * scan over the hash tables.
   */
  private boolean hasExpansionOccurred() {
    return hashCtl != null && initialHashSize != hashCtl.size;
  }

  private void switchToMainHash() {
    hash = cache.mainHash;
    hashCtl = cache.mainHashCtrl;
  }

  private void switchToRefreshHash() {
    hash = cache.refreshHash;
    hashCtl = cache.refreshHashCtrl;
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
    return Hash.contains(iterated,key,  _hashCode);
  }

}
