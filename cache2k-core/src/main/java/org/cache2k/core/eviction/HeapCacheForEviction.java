package org.cache2k.core.eviction;

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

import org.cache2k.core.Entry;

/**
 * Interface for the eviction to the heap cache hash. By separating this, the
 * eviction code has no direct dependency on the heap cache and we can
 * unit test the eviction without a complete cache.
 *
 * @author Jens Wilke
 */
public interface HeapCacheForEviction<K, V> {

  /**
   * Hash table entry array, used reaa only by random eviction.
   */
  Entry<K, V>[] getHashEntries();

  /**
   * After removing the entry from the eviction data structure,
   * remove it from the hash table.
   */
  void removeEntryForEviction(Entry<K, V> e);

}
