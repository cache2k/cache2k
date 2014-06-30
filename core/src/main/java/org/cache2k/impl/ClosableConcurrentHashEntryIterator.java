package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2014 headissue GmbH, Munich
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
public class ClosableConcurrentHashEntryIterator<E extends BaseCache.Entry>
  implements ClosableIterator<E> {

  BaseCache.Entry lastEntry = null;
  BaseCache.Hash<E> hashCtl;
  BaseCache.Hash<E> hashCtl2;
  BaseCache.Hash<BaseCache.Entry> iteratedCtl = new BaseCache.Hash<>();
  BaseCache.Entry[] iterated;
  BaseCache.Entry[] hash;
  BaseCache.Entry[] hash2;
  boolean keepIterated = false;
  boolean stopOnClear = true;

  public ClosableConcurrentHashEntryIterator(
    BaseCache.Hash<E> hashCtl, E[] hash,
    BaseCache.Hash<E> hashCtl2, E[] hash2) {
    this.hash = hash;
    this.hash2 = hash2;
    this.hashCtl = hashCtl;
    this.hashCtl2 = hashCtl2;
    hashCtl.incrementSuppressExpandCount();
    iterated = iteratedCtl.init(BaseCache.Entry.class);
  }

  private BaseCache.Entry nextEntry() {
    BaseCache.Entry e;
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
        lastEntry = e = checkIteratedOrNext((E) e);
        if (e != null) {
          return e;
        }
      }
      idx = BaseCache.Hash.index(hash, lastEntry.hashCode) + 1;
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
        lastEntry = e = checkIteratedOrNext((E) e);
        if (e != null) {
          return e;
        }
      }
      idx++;
    }
  }

  protected E checkIteratedOrNext(E e) {
    do {
      boolean _notYetIterated = !BaseCache.Hash.contains(iterated, e.key, e.hashCode);
      if (_notYetIterated) {
        BaseCache.Entry _newEntryIterated = new BaseCache.Entry();
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
      close();
      return true;
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
      hashCtl = null;
      hash = null;
      hashCtl2 = null;
      hash2 = null;
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
