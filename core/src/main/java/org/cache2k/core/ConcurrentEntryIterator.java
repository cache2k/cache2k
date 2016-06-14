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

import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Iterator over all cache entries.
 *
 * <p>Hash expansion: During the iteration a hash expansion may happen, which means every
 * entry is rehashed. In this case it is most likely that entries are missed.
 * If an expansion occurred, the iteration will restart from the beginning. To ensure that every
 * entry is only iterated once, the iterator has an internal bookkeeping, what was previously iterated.
 *
 * <p>Clear: A clear operation stops current iterations.
 *
 * <p>Close: A close operation will stop the iteration and yield a {@link CacheClosedException}
 *
 * @author Jens Wilke
 */
public class ConcurrentEntryIterator<K,V> implements Iterator<Entry<K,V>> {

  private HeapCache<K, V> cache;
  private Entry<K, V> lastEntry = null;
  private Entry<K, V> nextEntry = null;
  private long clearCount;
  private Hash2<K,V> hash;
  private Entry<K,V>[] hashArray;
  private HashMap<K,K> seen = new HashMap<K, K>();

  public ConcurrentEntryIterator(HeapCache<K,V> _cache) {
    cache = _cache;
    hash = cache.hash;
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
  public boolean hasBeenIterated(K key, @SuppressWarnings("UnusedParameters") int _hashCode) {
    return seen.containsKey(key);
  }

  /**
   *
   * @param key
   * @param _hashCode corresponding modified hash, unused but we keep it if we want to switch to
   *                  a more efficient hash table
   */
  public void markIterated(K key, @SuppressWarnings("UnusedParameters") int _hashCode) {
    seen.put(key, key);
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
      boolean _notYetIterated = !seen.containsKey(e.key);
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

  /**
   * Check for expansion and abort criteria.
   */
  private Boolean switchCheckAndAbortLocked() {
    if (!hasExpansionOccurred()) {
      return true;
    }
    hashArray = hash.getEntries();
    clearCount = hash.getClearCount();
    boolean _cacheClosed = hashArray == null;
    if (_cacheClosed) {
      return true;
    }
    return false;
  }

  /**
   * True if hash table expanded while iterating. Triggers another
   * scan over the hash tables. True also before first run.
   */
  private boolean hasExpansionOccurred() {
    return hashArray != hash.getEntries();
  }

}
