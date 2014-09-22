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

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Helper to estimate the size of object within a cache. Usage: Insert all objects to
 * analyze with insert(), than call descend() to descend one level of the object graph.
 * The end is reached when hasNext() yields false. The The result can be obtained via
 * getBytes().
 *
 * <p>The basic problem when we want to estimate the size is that it is never known
 * whether an object is exclusive for our cache structure, or it is also referenced by
 * some other classes. To do a "perfect" size measurement the whole java heap must be
 * analyzed and it must be decided which object should be counted where. So, anyway,
 * let's try a good guess, and see whether it is of some use.
 * </p>
 *
 * <p>Heuristics: Top-Level objects are always counted at least once by instance. An identical
 * object that is found via different ways when descending the graph is not counted at all.
 * Object reached via a non-counted object are not counted.</p>
 *
 * <p>Caveats: If only one object contains a reference to a bigger static data structure
 * that belongs to the system, the size could be totally wrong. Countermeasures:
 * we do measure the size only if more then x objects are in the cache and we limit the
 * depth. If objects have a reference e.g. to some system objects, or maybe the cache, these
 * objects will not be counted, if the reference is appearing more than once. So an
 * problem may still arise if there is one object within the cache that holds such
 * a reference and the others not. Current countermeasures for this: Estimate size only
 * when sufficient objects are in the cache; limit the descending depth.</p>
 *
 * @author Jens Wilke; created: 2013-06-25
 */
@SuppressWarnings("unused")
public class DepthSearchAndSizeCounter {

  int bytes;
  int objectCount;
  int counter;

  HashMap<Integer, SeenEntry> seen = new HashMap<Integer, SeenEntry>();
  Set<SeenEntry> next = new HashSet<SeenEntry>();

  /**
   * Array of hash entries seen twice or more times. Top level objects will always be
   * counted once. Secondary entries, will never be counted, since we cannot know
   * if they are exclusive to the hash or not.
   * */
  Set<SeenEntry> eleminate = new HashSet<SeenEntry>();

  boolean commonObjects = false;
  boolean circles = false;

  public void insert(Object o) {
    counter++;
    nextLevel(null, o);
  }

  public int getNextCount() {
    return next.size();
  }

  public boolean hasNext() {
    return !next.isEmpty();
  }

  public void descend() throws EstimationException {
    SeenEntry e = null;
    try {
      Iterator<SeenEntry> it = next.iterator();
      next = new HashSet<SeenEntry>();
      eleminate = new HashSet<SeenEntry>();
      while (it.hasNext()) {
        descend(e = it.next());
      }
    } catch (Exception ex) {
      List<Class<?>> _path = new ArrayList<Class<?>>();
      while (e != null) {
        _path.add(0, e.object.getClass());
        e = e.via;
      }
      throw new EstimationException(ex, _path);
    }
    eleminateSeenEntries();
  }

  /**
   * Reset our counters but keep the lookup set of the objects seen so far.
   */
  public void resetCounter() {
    objectCount = 0;
    bytes = 0;
    counter = 0;
  }

  /** go down the subtree and make sure that we don't descend it further */
  void eleminateDescendantsFromNext(SeenEntry e) {
    next.remove(e);
    for (SeenEntry e2 : e.transitive) {
      eleminateDescendantsFromNext(e2);
    }
  }

  void eleminateSeenEntries() {
    for (SeenEntry e : eleminate) {
      eleminateDescendantsFromNext(e);
      bytes -= e.getTotalBytes();
      objectCount -= e.getObjectCount();
    }
  }

  void descend(SeenEntry e) throws IllegalAccessException {
    Object o = e.object;

    Class<?> c = o.getClass();
    if (c.isArray()) {
      recurseArray(e, c, o);
    } else {
      recurseFields(e, c, o);
    }
  }

  void recurseArray(SeenEntry e, Class<?> c, Object o) throws IllegalAccessException {
    Class<?> t = c.getComponentType();
    if (t.isPrimitive()) {
      if (t == long.class || t == double.class) {
        e.bytes += 8 * Array.getLength(o);
      } else {
        e.bytes += 4 * Array.getLength(o);
      }
    } else {
      Object[] oa = (Object[]) o;
      e.bytes += 8 * Array.getLength(o);
      for (Object oi : oa) {
        nextLevel(e, oi);
      }
    }
    e.bytes += 24; // array overhead
    bytes += e.bytes;
    objectCount++;
  }

  void recurseFields(SeenEntry e, Class<?> c, Object o) throws IllegalAccessException {
    if (c == null) { return; }
    recurseFields(e, c.getSuperclass(), o);
    Field[] fa = c.getDeclaredFields();
    for (Field f : fa) {
      if (Modifier.isStatic(f.getModifiers())) {
        continue;
      }
      Class<?> t = f.getType();
      if (t.isPrimitive()) {
        if (t == long.class || t == double.class) {
          e.bytes += 8;
        } else {
          e.bytes += 4;
        }
        continue;
      }
      e.bytes += 8;
      f.setAccessible(true);
      Object o2 = f.get(o);
      nextLevel(e, o2);
    }
    e.bytes += 20; // object overhead
    objectCount++;
    bytes += e.bytes;
  }

  void nextLevel(SeenEntry _parent, Object o) {
    if (o == null) {
      return;
    }
    SeenEntry e = seen.get(System.identityHashCode(o));
    if (e != null) {
      if (_parent != null) { checkObjectNavigationPath(_parent, e); }
      e.referenceCount++;
      if (!e.eleminated) {
        eleminate.add(e);
        e.eleminated = true;
      }
    } else {
      e = new SeenEntry(o);
      if (_parent != null) {
        _parent.transitive.add(e);
        e.via = _parent;
      }
      seen.put(System.identityHashCode(o), e);
      next.add(e);
    }
  }

  /**
   * to know the accuracy we check the navigation path that has lead us to an object.
   */
  final void checkObjectNavigationPath(SeenEntry _parent, SeenEntry e) {
    if (!circles || !commonObjects) {
      SeenEntry r0 = _parent.getRoot();
      SeenEntry r1 = e.getRoot();
      if (r0 == r1) {
        circles = true;
      } else {
        commonObjects = true;
      }
    }
  }

  public int getByteCount() {
    return bytes;
  }

  public int getObjectCount() {
    return objectCount;
  }

  public int getCounter() {
    return counter;
  }

  public boolean hasCommonObjects() {
    return commonObjects;
  }

  public boolean hasCircles() {
    return circles;
  }

  static class SeenEntry {

    SeenEntry via;
    Set<SeenEntry> transitive = new HashSet<SeenEntry>();
    Object object;
    int referenceCount = 1;
    int bytes;
    boolean eleminated;

    SeenEntry(Object object) {
      this.object = object;
    }

    public final SeenEntry getRoot() {
      return via == null ? this : via.getRoot();
    }

    public int getTotalBytes() {
      int v = bytes;
      for (SeenEntry e : transitive) {
        if (!e.eleminated) {
          v += e.getTotalBytes();
        }
      }
      return v;
    }

    /** Count of object seen below this one, including itself */
    public int getObjectCount() {
      int v = 1;
      for (SeenEntry e : transitive) {
        if (!e.eleminated && e.bytes > 0) {
          v++;
          v += e.getObjectCount();
        }
      }
      return v;
    }

  }

  public static class EstimationException extends Exception {

    List<Class<?>> path;

    EstimationException(Throwable cause, List<Class<?>> path) {
      super(cause);
      this.path = path;
    }

    public List<Class<?>> getPath() {
      return path;
    }
  }

}
