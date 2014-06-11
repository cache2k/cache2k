package org.cache2k.storage;

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

import java.util.Comparator;
import java.util.TreeSet;

/**
 * Holds areas of free space (slots).
 *
 * @author Jens Wilke; created: 2014-04-25
 */
public class FreeSpaceMap {

  long freeSpace;
  TreeSet<Slot> freeSet;
  TreeSet<Slot> pos2slot;

  public void init() {
    freeSpace = 0;
    freeSet = new TreeSet<>();
    pos2slot = new TreeSet<>(new PositionOrder());
  }

  public synchronized void put(Slot s) {
    freeSpace += s.size;
    freeSet.add(s);
    pos2slot.add(s);
  }

  /**
   * Get the slot that ends at the position exclusive or: start + size = pos
   */
  public synchronized Slot reserveSlotEndingAt(long pos) {
    reusedFreeSlotUnderLock.position = pos;
    Slot s = pos2slot.floor(reusedFreeSlotUnderLock);
    if (s != null && s.getNextPosition() == pos) {
      freeSet.remove(s);
      pos2slot.remove(s);
      freeSpace -= s.size;
      return s;
    }
    return null;
  }

  final Slot reusedFreeSlotUnderLock = new Slot(0, 0);

  /**
   * Find a slot which size is greater then the needed size and
   * remove it from the free space map.
   */
  public Slot findFree(int size) {
    reusedFreeSlotUnderLock.size = size;
    Slot s = freeSet.ceiling(reusedFreeSlotUnderLock);
    if (s != null) {
      freeSet.remove(s);
      pos2slot.remove(s);
      freeSpace -= s.size;
      return s;
    }
    return null;
  }

  /**
   * Used to rebuild the free space map. Entry has already got a position.
   * The used space will be removed of the free space map.
   */
  public void allocateSpace(long _position, int _size) {
    freeSpace -= _size;
    reusedFreeSlotUnderLock.position = _position;
    Slot s = pos2slot.floor(reusedFreeSlotUnderLock);
    pos2slot.remove(s);
    freeSet.remove(s);
    if (s.position < _position) {
      Slot _preceding = new Slot();
      _preceding.position = s.position;
      _preceding.size = (int) (_position - s.position);
      pos2slot.add(_preceding);
      freeSet.add(_preceding);
      s.size -= _preceding.size;
    }
    if (s.size > _size) {
      s.position = _size + _position;
      s.size -= _size;
      pos2slot.add(s);
      freeSet.add(s);
    }
  }

  public void freeSpace(Slot s) {
    freeSpace(s.position, s.size);
  }

  /**
   * Free the space. Not just put the slot in but try to merge it.
   */
  public void freeSpace(long _position, int _size) {
    freeSpace += _size;
    reusedFreeSlotUnderLock.position = (_size + _position);
    Slot s = pos2slot.ceiling(reusedFreeSlotUnderLock);
    if (s != null && s.position == reusedFreeSlotUnderLock.position) {
      pos2slot.remove(s);
      freeSet.remove(s);
      s.position = _position;
      s.size += _size;
    } else {
      s = new Slot(_position, _size);
    }
    reusedFreeSlotUnderLock.position = _position;
    Slot s2 = pos2slot.lower(reusedFreeSlotUnderLock);
    if (s2 != null && s2.getNextPosition() == _position) {
      pos2slot.remove(s2);
      freeSet.remove(s2);
      s2.size += s.size;
      s = s2;
    }
    pos2slot.add(s);
    freeSet.add(s);
  }

  public long getFreeSpace() { return freeSpace; }

  /**
   * Calculate the free space for an integrity check.
   */
  public long calculateFreeSpace() {
    long s = 0;
    Slot _prev = null;
    for (Slot fs : pos2slot) {
      s += fs.size;
      _prev = fs;
    }
    return s;
  }

  public int getSlotCount() {
    return freeSet.size();
  }

  static class PositionOrder implements Comparator<Slot> {
    @Override
    public int compare(Slot o1, Slot o2) {
      if (o1.position < o2.position) {
        return -1;
      }
      if (o1.position > o2.position) {
        return 1;
      }
      return 0;
    }
  }

  public long getSizeOfLargestSlot() {
    if (freeSet.size() == 0) {
      return 0;
    }
    return freeSet.last().size;
  }

  public long getSizeOfSmallestSlot() {
    if (freeSet.size() == 0) {
      return 0;
    }
    return freeSet.first().size;
  }

  /**
   * Describes an area of free space in the storage. The comparison is by size.
   */
  public static class Slot implements Comparable<Slot> {

    long position;
    int size;

    public Slot() { }

    public Slot(long _position, int _size) {
      this.position = _position;
      this.size = _size;
    }

    public long getNextPosition() {
      return position + size;
    }

    @Override
    public int compareTo(Slot o) {
      int d = size - o.size;
      if (d != 0) {
        return d;
      }
      return (position < o.position) ? -1 : ((position == o.position) ? 0 : 1);
    }

    @Override
    public String toString() {
      return "FreeSlot{" +
        "position=" + position +
        ", size=" + size +
        '}';
    }

  }

}
