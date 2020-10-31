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

import java.io.Serializable;
import java.util.Collection;

/**
 * Collection of customizations. The order of inserting is preserved. The first element inserted
 * is returned first by the iteration. Duplicate entries will be rejected.
 *
 * <p>Typically the implementation {@link DefaultCustomizationCollection} will be used.
 *
 * @author Jens Wilke
 * @see DefaultCustomizationCollection
 */
public interface CustomizationCollection<T> extends
  Collection<CustomizationSupplier<T>>, Serializable {

  /**
   * Adds a customization to the collection.
   *
   * @return always {@code true}
   * @throws IllegalArgumentException if the entry is already existing.
   */
  @Override
  boolean add(CustomizationSupplier<T> e);

  /**
   * Adds all customizations to the collection.
   *
   * @return always {@code true}
   * @throws IllegalArgumentException if an entry is already existing.
   */
  @Override
  boolean addAll(Collection<? extends CustomizationSupplier<T>> c);

}
