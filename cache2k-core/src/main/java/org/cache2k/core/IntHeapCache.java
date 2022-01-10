package org.cache2k.core;

/*-
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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

import org.cache2k.core.api.InternalCacheBuildContext;

/**
 * Overwrite methods so the integer value gets stored directly in the
 * {@code Entry.hashCode} field and {@code Entry.value} is set to null.
 *
 * @author Jens Wilke
 */
public class IntHeapCache<V> extends HeapCache<Integer, V> {

  public IntHeapCache(InternalCacheBuildContext<Integer, V> ctx) {
    super(ctx);
  }

  @Override
  public Integer toEntryKey(Integer key) {
    return null;
  }

  @Override
  public int toStoredHashCodeOrKey(Integer key, int hc) {
    return key;
  }

  @Override
  public int spreadHashFromEntry(Entry e) {
    return spreadHash(e.hashCode);
  }

  @Override
  public Integer keyObjFromEntry(Entry<Integer, V> e) {
    return e.hashCode;
  }

  /**
   * Modified hash table implementation. Rehash needs to calculate the correct hash code again.
   */
  @Override
  public StampedHash<Integer, V> createHashTable() {
    return new StampedHash<Integer, V>(this) {
      @Override
      protected int spreadHashFromEntry(int hc) {
        return spreadHash(hc);
      }

      @Override
      protected boolean keyObjIsEqual(Integer key, Entry e) {
        return true;
      }
    };
  }

}
