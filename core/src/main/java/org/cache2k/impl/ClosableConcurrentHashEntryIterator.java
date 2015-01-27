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

import org.cache2k.ClosableIterator;

/**
 * Iterator over all hash table entries of two hashes.
 *
 * <p>The hash has a usage/reference counter for iterations
 * to suspend expand until the iteration finished. This is needed
 * for correctness of the iteration, if an expand is done during
 * the iteration process, the iterations returns duplicate
 * entries or not all entries.
 *
 * <p>Failing to operate the increment/decrement in balance will
 * mean that the hash table expands are blocked forever, which is a
 * serious error condition. Typical problems arise by thrown
 * exceptions during an iteration. Since there is a measure for the
 * hash quality, a problem like this may be detected.
 *
 * <p>Rationale: We need to keep track of the entries/keys iterated.
 * The cache may remove and insert entries from hash to refreshHash,
 * also the application may do a remove and insert. A removed entry
 * has always e.another kept intact, so traversal of the collision list
 * will always work. If a iteration is going on, this means a removed
 * entry needs to be cloned to reassign the e.another pointer.
 *
 * <p>Rationale: The iteration just works on the hash data structure.
 * However, it needs to be checked, whether the cache is closed
 * meanwhile. To signal this, the hash is closed also. This is
 * a little complex, but safes the dependency on the cache here.
 *
 * @author Jens Wilke; created: 2013-12-21
 */
public class ClosableConcurrentHashEntryIterator<E extends Entry>
  implements ClosableIterator<E> {

  int iteratedCountLastRun;
  Entry lastEntry = null;
  Hash<E> hashCtl;
  Hash<E> hashCtl2;
  Entry[] hash;
  Entry[] hash2;
  Hash<E> hashCtlCopy;
  Hash<E> hashCtl2Copy;
  Entry[] hashCopy;
  Entry[] hash2Copy;
  Hash<Entry> iteratedCtl = new Hash<Entry>();
  Entry[] iterated;
  boolean keepIterated = false;
  boolean stopOnClear = true;

  public ClosableConcurrentHashEntryIterator(
    Hash<E> _hashCtl, E[] _hash,
    Hash<E> _hashCtl2, E[] _hash2) {
    hashCopy = hash = _hash;
    hash2Copy = hash2 = _hash2;
    hashCtlCopy = hashCtl = _hashCtl;
    hashCtl2Copy = hashCtl2 = _hashCtl2;
    _hashCtl.incrementSuppressExpandCount();
    iterated = iteratedCtl.init(Entry.class);
  }

  private Entry nextEntry() {
    Entry e;
    if (hash == null) {
      return null;
    }
    if (hashCtl.shouldAbort()) {
      if (checkForClearAndAbort()) {
        return null;
      }
      if (stopOnClear && hashCtl.isCleared()) {
        throw new CacheClosedException();
      }
    }
    int idx = 0;
    if (lastEntry != null) {
      e = lastEntry.another;
      if (e != null) {
        e = checkIteratedOrNext((E) e);
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
        e = checkIteratedOrNext((E) e);
        if (e != null) {
          lastEntry = e;
          return e;
        }
      }
      idx++;
    }
  }

  protected E checkIteratedOrNext(E e) {
    do {
      boolean _notYetIterated = !Hash.contains(iterated, e.key, e.hashCode);
      if (_notYetIterated) {
        Entry _newEntryIterated = new Entry();
        _newEntryIterated.key = e.key;
        _newEntryIterated.hashCode = e.hashCode;
        iterated = iteratedCtl.insert(iterated, _newEntryIterated);
        return e;
      }
      e = (E) e.another;
    } while (e != null);
    return null;
  }

  protected boolean switchAndCheckAbort() {
    hashCtl.decrementSuppressExpandCount();
    hash = hash2;
    hashCtl = hashCtl2;
    hash2 = null;
    hashCtl2 = null;
    if (hash == null) {
      lastEntry = null;
      if (iteratedCountLastRun == iteratedCtl.size) {
        close();
        return true;
      }
      iteratedCountLastRun = iteratedCtl.size;
      hash = hashCopy;
      hash2 = hash2Copy;
      hashCtl = hashCtlCopy;
      hashCtl2 = hashCtl2Copy;
    }
    hashCtl.incrementSuppressExpandCount();
    return false;
  }

  /**
   * Check if hash was cleared and we need to abort. Returns true
   * if we should abort.
   */
  protected boolean checkForClearAndAbort() {
    if (hashCtl.isCleared()) {
      close();
      return true;
    }
    return false;
  }

  @Override
  public boolean hasNext() {
    return nextEntry() != null;
  }

  @Override
  public E next() {
    return (E) lastEntry;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void close() {
    if (hashCtl != null) {
      hashCtl.decrementSuppressExpandCount();
      hashCtl = hashCtl2 = null;
      hash = hash2 = null;
      hashCtlCopy = hashCtl2Copy = null;
      hashCopy = hash2Copy = null;
      lastEntry = null;
      if (!keepIterated) {
        iterated = null;
        iteratedCtl = null;
      }
    }
  }

  @Override
  protected void finalize() throws Throwable {
    super.finalize();
    close();
  }

  /**
   * Keep hash of iterated items, needed for storage iteration.
   */
  public void setKeepIterated(boolean keepIterated) {
    this.keepIterated = keepIterated;
  }

  /**
   * Iterations stops when storage is cleared, default is true.
   */
  public void setStopOnClear(boolean stopOnClear) {
    this.stopOnClear = stopOnClear;
  }
}
