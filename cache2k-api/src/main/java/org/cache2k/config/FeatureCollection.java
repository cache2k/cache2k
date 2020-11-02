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
 * A collection of features.
 *
 * @author Jens Wilke
 */
public final class FeatureCollection<K, V>
  extends AbstractCollection<Feature<K, V>> {

  private Collection<Feature<K, V>> list = new ArrayList<>();

  @Override
  public int size() {
    return list.size();
  }

  @Override
  public Iterator<Feature<K, V>> iterator() {
    return list.iterator();
  }

  /**
   * Adds a customization to the collection.
   *
   * @return always {@code true}
   * @throws IllegalArgumentException if the entry is already existing.
   */
  @Override
  public boolean add(Feature<K, V> entry) {
    if (list.contains(entry)) {
      throw new IllegalArgumentException("duplicate entry");
    }
    return list.add(entry);
  }

  public String toString() {
    return getClass().getSimpleName() + list.toString();
  }

}
