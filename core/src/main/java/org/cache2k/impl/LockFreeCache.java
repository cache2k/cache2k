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

/**
 * @author Jens Wilke; created: 2013-12-22
 */
public abstract class LockFreeCache<E extends Entry, K, T>
  extends BaseCache<E, K, T> {

  /**
   * First lookup in the hash unsynchronized, if missed, do synchronize and
   * try again.
   */
  @Override
  protected final E lookupOrNewEntrySynchronized(K key) {
    int hc = modifiedHash(key.hashCode());
    E e = Hash.lookup(mainHash, key, hc);
    if (e != null) {
      recordHit(e);
      return e;
    }
    synchronized (lock) {
      e = lookupEntry(key, hc);
      if (e == null) {
        e = newEntry(key, hc);
        return e;
      }
      return e;
    }
  }

  @Override
  protected final E lookupEntryUnsynchronized(K key, int hc) {
    E e = Hash.lookup(mainHash, key, hc);
    if (e != null) {
      recordHit(e);
      return e;
    }
    return null;
  }

}
