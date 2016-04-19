package org.cache2k.core;

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

/**
 * Implementation of the CAR replacement algorithm. This implementation is for evaluation only and
 * not yet tuned for production.
 *
 * @author Jens Wilke
 */
public class CarCache<K, V> extends ConcurrentEvictionCache<K, V> {

  int size;
  int arcP = 0;

  Hash<Entry> b1HashCtrl;
  Entry[] b1Hash;

  Hash<Entry> b2HashCtrl;
  Entry[] b2Hash;

  int t1Size;
  int t2Size;
  Entry<K, V> t2Head;
  Entry<K, V> t1Head;
  Entry<K, V> b1Head;
  Entry<K, V> b2Head;

  boolean b2HitPreferenceForEviction;

  long hits;

  protected void initializeHeapCache() {
    super.initializeHeapCache();
    t1Size = 0;
    t2Size = 0;
    t1Head = null;
    t2Head = null;
    b1Head = new Entry<K, V>();
    b2Head = new Entry<K, V>();
    b1Head = new Entry<K, V>().shortCircuit();
    b2Head = new Entry<K, V>().shortCircuit();
    b1HashCtrl = new Hash<Entry>();
    b2HashCtrl = new Hash<Entry>();
    b1Hash = b1HashCtrl.init(Entry.class);
    b2Hash = b2HashCtrl.init(Entry.class);
  }

  @Override
  protected void recordHit(Entry e) {
    e.hitCnt++;
  }

  @Override
  protected void insertIntoReplacementList(Entry e) {
    size++;
    t1Size++;
    t1Head = insertIntoTailCyclicList(t1Head, e);
  }

  @Override
  protected Entry newEntry() {
    return new Entry();
  }

  @Override
  public long getHitCnt() {
    return hits + sumUpListHits(t1Head) + sumUpListHits(t2Head);
  }

  private int sumUpListHits(Entry e) {
    if (e == null) { return 0; }
    int cnt = 0;
    Entry _head = e;
    do {
      cnt += e.hitCnt;
      e = (Entry) e.prev;
    } while (e != _head);
    return cnt;
  }

  @Override
  protected Entry checkForGhost(K key, int hc) {
    Entry e = b1HashCtrl.remove(b1Hash, key, hc);
    if (e != null) {
      removeFromList(e);
      b1HitAdaption();
      insertT2(e);
      return e;
    }
    e = b2HashCtrl.remove(b2Hash, key, hc);
    if (e != null) {
      removeFromList(e);
      b2HitAdaption();
      insertT2(e);
      return e;
    }
    return null;
  }

  private void b1HitAdaption() {
    int _b1Size = b1HashCtrl.size + 1;
    int _b2Size = b2HashCtrl.size;
    int _delta = _b1Size >= _b2Size ? 1 : _b2Size / _b1Size;
    arcP = Math.min(arcP + _delta, maxSize);
    b2HitPreferenceForEviction = false;
  }

  private void b2HitAdaption() {
    int _b1Size = b1HashCtrl.size;
    int _b2Size = b2HashCtrl.size + 1;
    int _delta = _b2Size >= _b1Size ? 1 : _b1Size / _b2Size;
    arcP = Math.max(arcP - _delta, 0);
    b2HitPreferenceForEviction = true;
  }

  @Override
  protected Entry findEvictionCandidate() {
    return replace();
  }

  private Entry<K, V> replace() {
    Entry<K, V> e = null;
    for (;;) {
      if (t1Size >= Math.max(1, arcP) || t2Size == 0) {
        e = t1Head;
        if (e.hitCnt == 0) {
          t1Head = e.next;
          break;
        }
        hits += e.hitCnt;
        e.hitCnt = 0;
        t1Head = removeFromCyclicList(t1Head, e);
        t1Size--;
        t2Head = insertIntoTailCyclicList(t2Head, e);
        t2Size++;
      } else {
        e = t2Head;
        if (e.hitCnt == 0) {
          t2Head = e.next;
          break;
        }
        hits += e.hitCnt;
        e.hitCnt = 0;
        t2Head = e.next;
      }
    }
    evictGhosts();
    return e;
  }

  private void evictGhosts() {
    int _b1Size = b1HashCtrl.size;
    if (t1Size + _b1Size == maxSize && _b1Size > 0) {
      Entry e = b1Head.prev;
      removeFromList(e);
      boolean f = b1HashCtrl.remove(b1Hash, e);
      return;
    }
    int _b2Size = b2HashCtrl.size;
    if (t1Size + t2Size + _b1Size + _b2Size > 2 * maxSize && _b2Size > 0) {
      Entry e = b2Head.prev;
      removeFromList(e);
      boolean f = b2HashCtrl.remove(b2Hash, e);
    }
  }

  private void insertT2(Entry<K, V> e) {
    t2Size++;
    t2Head = insertIntoTailCyclicList(t2Head, e);
  }

  private int getListSize() {
    return t1Size + t2Size;
  }

  private void insertCopyIntoB1(Entry<K, V> e) {
    Entry<K, V> e2 = copyEntryForGhost(e);
    b1Hash = b1HashCtrl.insert(b1Hash, e2);
    insertInList(b1Head, e2);
  }

  private void insertCopyIntoB2(Entry<K, V> e) {
    Entry<K, V> e2 = copyEntryForGhost(e);
    b2Hash = b2HashCtrl.insert(b2Hash, e2);
    insertInList(b2Head, e2);
  }

  private Entry<K, V> copyEntryForGhost(Entry<K, V> e) {
    Entry<K, V> e2;
    e2 = new Entry<K, V>();
    e2.key = (K) e.key;
    e2.hashCode = e.hashCode;
    return e2;
  }

  @Override
  protected void removeEntryFromReplacementList(Entry e) {
    boolean _t1Hit = t1Head != null && t1Head.prev == e;
    boolean _t2Hit = t2Head != null && t2Head.prev == e;
    if (!_t1Hit || !_t2Hit) {
      if (t1Size < t2Size) {
        _t1Hit = false; _t2Hit = true;
        Entry<K, V> x = t1Head;
        if (x != null) {
          do {
            if (x == e) {
              _t1Hit = true;
              _t2Hit = false;
              break;
            }
            x = x.next;
          } while (x != t1Head);
        }
      } else {
        _t1Hit = true; _t2Hit = false;
        Entry<K, V> x = t2Head;
        if (x != null) {
          do {
            if (x == e) {
              _t1Hit = false;
              _t2Hit = true;
              break;
            }
            x = x.next;
          } while (x != t2Head);
        }
      }
    }
    if (_t1Hit) {
      insertCopyIntoB1(e);
      t1Head = removeFromCyclicList(t1Head, e);
      t1Size--;
    } else {
      insertCopyIntoB2(e);
      t2Head = removeFromCyclicList(t2Head, e);
      t2Size--;
    }
  }

}
