package org.cache2k.config;

/*
 * #%L
 * cache2k API
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

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * Default implementation for a customization collection using a list.
 *
 * <p>Rationale: Inserting is a little expansive, since we check whether
 * an entry is already existing. Since there are usually only a few entries
 * the list is sufficient.
 *
 * @author Jens Wilke
 */
public final class DefaultCustomizationCollection<T>
  extends AbstractCollection<CustomizationSupplier<T>>
  implements CustomizationCollection<T> {

  private Collection<CustomizationSupplier<T>> list = new ArrayList<CustomizationSupplier<T>>();

  @Override
  public int size() {
    return list.size();
  }

  @Override
  public Iterator<CustomizationSupplier<T>> iterator() {
    return list.iterator();
  }

  /**
   * Adds a customization to the collection.
   *
   * @return always {@code true}
   * @throws IllegalArgumentException if the entry is already existing.
   */
  @Override
  public boolean add(final CustomizationSupplier<T> entry) {
    if (list.contains(entry)) {
      throw new IllegalArgumentException("duplicate entry");
    }
    return list.add(entry);
  }

  public String toString() {
    return getClass().getSimpleName() + list.toString();
  }

}
