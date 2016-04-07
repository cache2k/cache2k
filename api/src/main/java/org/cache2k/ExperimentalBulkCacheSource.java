
package org.cache2k;

/*
 * #%L
 * cache2k API only package
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

import java.util.BitSet;

/**
 * Interface for requesting a bulk of data. The idea of this interface is
 * that no object allocations need to be done in the callee to suport the interface
 * and that it is possible to retrieve a subset of entries. The current interface
 * works, but is quite difficult to implement. Will be changed for 1.0.
 *
 * @author Jens Wilke
 */
@Deprecated
public interface ExperimentalBulkCacheSource<K, T> {

  /**
   * Retrieves the objects associated with the given keys in the result array.
   * The two arrays must be of identical size.
   * 
   * @param keys keys of the elements
   * @param result result objects, a non-null value in the result array means
   *        that this object may not be retrieved.
   * @param s start index
   * @param e end index, exclusive
   */
  void getBulk(K[] keys, T[] result, BitSet _fetched, int s, int e);

}
