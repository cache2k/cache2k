package org.cache2k.core;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
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

import org.cache2k.IntCache;

/**
 * Overwrite methods so the integer value gets stored directly in the
 * {@link Entry#hashCode} field and {@link Entry#key} is set to null.
 * Defines methods from {@link IntCache} so no autoboxing needs to be done
 * in the access path.
 *
 * @author Jens Wilke
 */
public class IntHeapCache<V> extends HeapCache<Integer, V> implements IntCache<V> {

  @Override
  public Integer extractIntKeyObj(final Integer key) {
    return null;
  }

  @Override
  public int extractIntKeyValue(final Integer key, final int hc) {
    return key;
  }

  @Override
  public int extractModifiedHash(final Entry e) {
    return modifiedHash(e.hashCode);
  }

  @Override
  public Integer extractKeyObj(final Entry<Integer, V> e) {
    return e.hashCode;
  }

  /**
   * Modified hash table implementation. Rehash needs to calculate the correct hash code again.
   */
  @Override
  public Hash2<Integer, V> createHashTable() {
    return new Hash2<Integer, V>(this) {
      @Override
      protected int modifiedHashCode(final int hc) {
        return IntHeapCache.this.modifiedHash(hc);
      }

      @Override
      protected boolean keyObjIsEqual(final Integer key, final Entry e) {
        return true;
      }
    };
  }

  @Override
  public V peek(final int key) {
    Entry<Integer, V> e = peekEntryInternal(null, modifiedHash(key), key);
    if (e != null) {
      return returnValue(e);
    }
    return null;
  }

  @Override
  public boolean containsKey(final int key) {
    Entry e = lookupEntry(null, modifiedHash(key), key);
    if (e != null) {
      metrics.heapHitButNoRead();
      return e.hasFreshData(clock);
    }
    return false;
  }

  @Override
  public void put(final int key, final V value) {
    for (;;) {
      Entry e = lookupOrNewEntry(null, modifiedHash(key), key);
      synchronized (e) {
        e.waitForProcessing();
        if (e.isGone()) {
          metrics.goneSpin();
          continue;
        }
        if (!e.isVirgin()) {
          metrics.heapHitButNoRead();
        }
        putValue(e, value);
      }
      return;
    }
  }

  @Override
  public V get(final int key) {
    Entry<Integer, V> e = getEntryInternal(null, modifiedHash(key), key);
    if (e == null) {
      return null;
    }
    return returnValue(e);
  }

  @Override
  public void remove(final int key) {
    super.remove(key);
  }

}
