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

/**
 * Iterator over all hash table entries of two hashes.
 *
 * @author Jens Wilke; created: 2013-12-21
 */
public class HashEntryIterator {

  BaseCache.Entry lastEntry = null;
  BaseCache.Entry[] hash;
  BaseCache.Entry[] hash2;

  public HashEntryIterator(BaseCache.Entry[] hash, BaseCache.Entry[] hash2) {
    this.hash = hash;
    this.hash2 = hash2;
  }

  final BaseCache.Entry nextEntry() {
    int idx = 0;
    if (lastEntry != null) {
      if (lastEntry.another != null) {
        return lastEntry = lastEntry.another;
      }
      idx = BaseCache.Hash.index(hash, lastEntry.hashCode);
    }
    BaseCache.Entry e;
    do {
      idx++;
      if (idx >= hash.length) {
        idx = 0;
        hash = hash2;
        if (hash == null) {
          return null;
        }
      }
      e = hash[idx];
    } while (e == null);
    return lastEntry = e;
  }

}
