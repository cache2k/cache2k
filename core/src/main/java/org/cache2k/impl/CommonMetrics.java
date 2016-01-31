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
 * @author Jens Wilke
 */
public interface CommonMetrics {

  long getPutNewEntryCount();
  long getPutHitCount();
  long getCasOperationCount();
  long getPutNoReadHitCount();

  /**
   * Correction counter for read usage
   */
  long getContainsButHitCount();

  /**
   * A valid entry was removed from the cache.
   */
  long getRemoveCount();

  long getHeapHitButNoReadCount();

  interface Updater extends CommonMetrics {

    void putNewEntry();
    void putNewEntry(long cnt);

    void putHit();
    void putHit(long cnt);

    void putNoReadHit();
    void putNoReadHit(long cnt);

    void casOperation();
    void casOperation(long cnt);

    void containsButHit();
    void containsButHit(long cnt);

    void remove();
    void remove(long cnt);

    void heapHitButNoRead();
    void heapHitButNoRead(long cnt);

  }

}
