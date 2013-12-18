
package org.cache2k;

/*
 * #%L
 * cache2k api only package
 * %%
 * Copyright (C) 2000 - 2013 headissue GmbH, Munich
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
