package org.cache2k.configuration;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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
public class DefaultCustomizationCollection<T> implements CustomizationCollection<T> {

  private Collection<CustomizationSupplier<T>> list = new ArrayList<CustomizationSupplier<T>>();

  @Override
  public int size() {
    return list.size();
  }

  @Override
  public boolean isEmpty() {
    return list.isEmpty();
  }

  @Override
  public boolean contains(final Object o) {
    return list.contains(o);
  }

  @Override
  public Iterator<CustomizationSupplier<T>> iterator() {
    return list.iterator();
  }

  @Override
  public Object[] toArray() {
    return list.toArray();
  }

  @Override
  public <T> T[] toArray(final T[] a) {
    return list.toArray(a);
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

  @Override
  public boolean remove(final Object o) {
    return list.remove(o);
  }

  @Override
  public boolean containsAll(final Collection<?> c) {
    return list.containsAll(c);
  }

  @Override
  public boolean addAll(final Collection<? extends CustomizationSupplier<T>> c) {
    for (CustomizationSupplier<T> e : c) {
      add(e);
    }
    return true;
  }

  @Override
  public boolean removeAll(final Collection<?> c) {
    return list.removeAll(c);
  }

  @Override
  public boolean retainAll(final Collection<?> c) {
    return list.retainAll(c);
  }

  @Override
  public void clear() {
    list.clear();
  }

}
