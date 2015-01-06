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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Random;

/**
 * @author Jens Wilke; created: 2013-07-18
 */
public class CacheSizeEstimator {

  final static Log log = LogFactory.getLog(CacheSizeEstimator.class);

  final int MIN_ENTRY_COUNT = 3;
  final int ADDED_ENTRY_COUNT = 27;
  final int DEPTH_COUNT = 12;

  int accuracy;
  Random random;
  BaseCache.Entry lastEntry;
  BaseCache.Entry[] hash1;
  BaseCache.Entry[] hash2;

  final void switchHash() {
    BaseCache.Entry[] tmp = hash1;
    hash1 = hash2;
    hash2 = tmp;
  }

  final BaseCache.Entry nextEntry() {
    if (lastEntry.another != null) {
      return lastEntry = lastEntry.another;
    }
    int idx = BaseCache.Hash.index(hash1, lastEntry.hashCode);
    BaseCache.Entry e;
    do {
      idx++;
      if (idx >= hash1.length) {
        idx = 0;

      }
      e = hash1[idx];
    } while (e == null);
    return lastEntry = e;
  }

  final void findAnyEntry() {
    int idx = 0;
    while (hash1[idx] != null) {
      idx++;
      if (idx >= hash1.length) {
        idx = 0;
        switchHash();
      }
    }
    lastEntry = hash1[idx];
  }

  final void randomizeStartEntry() {
    findAnyEntry();
    int _forward = random.nextInt(hash1.length + hash2.length);
    for (;_forward != 0; _forward--) {
      nextEntry();
    }
  }

  final void addEntries(DepthSearchAndSizeCounter _counter, int _count) {
    for (int i = 0; i < _count; i++) {
      _counter.insert(nextEntry().value);
      _counter.insert(nextEntry().key);
    }
  }

  final int getSizeEstimationForAnEntry() {
    try {
      DepthSearchAndSizeCounter cse = new DepthSearchAndSizeCounter();
      addEntries(cse, ADDED_ENTRY_COUNT);
      for (int i = 0; (i < DEPTH_COUNT) && cse.hasNext(); i++) {
        cse.descend();
        if (log.isDebugEnabled()) {
          log.debug("CSE: depth=" + i + ", counter=" + cse.getCounter() + ", objectCount=" + cse.getObjectCount() + ", memUsage=" + cse.getByteCount() + ", nextCnt=" + cse.next.size());
        }
      }
      accuracy =
        (cse.hasCircles() ? 1 : 0) +
        (cse.hasCommonObjects() ? 2 : 0) + // this may apply quite often, e.g. for strings, locale etc.
        (cse.hasNext() ? 4 : 0);
      return cse.getByteCount() / ADDED_ENTRY_COUNT;

    } catch (DepthSearchAndSizeCounter.EstimationException ex) {
      StringBuilder sb = new StringBuilder();
      sb.append("Problems descending object tree for size estimation, path: ");
      for (Class<?> c : ex.getPath()) {
        sb.append(" -> ");
        sb.append(c.getSimpleName());
      }
      log.warn(sb.toString(), ex.getCause());
      accuracy = 8;
      return 0;
    }
  }

  final int getAccuracy() {
    return accuracy;
  }

}
