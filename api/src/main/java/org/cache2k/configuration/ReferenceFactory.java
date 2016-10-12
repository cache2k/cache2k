package org.cache2k.configuration;

/*
 * #%L
 * cache2k API
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

import org.cache2k.CacheManager;

/**
 * A reference to the customization to be used is set while building the cache.
 * The reference is returned. The class loader can be ignored.
 *
 * @author Jens Wilke
 */
public final class ReferenceFactory<T> implements CustomizationFactory<T> {

  private T object;

  /**
   * Construct a customization factory that returns always the same object instance.
   *
   * @param obj reference to a customization. Not null.
   */
  public ReferenceFactory(final T obj) {
    if (obj == null) {
      throw new NullPointerException("object reference");
    }
    object = obj;
  }

  @Override
  public T create(final CacheManager ignored) {
    return object;
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) return true;
    if (!(other instanceof ReferenceFactory)) return false;
    ReferenceFactory<?> _that = (ReferenceFactory<?>) other;
    return object.equals(_that.object);
  }

  @Override
  public int hashCode() {
    return object.hashCode();
  }

}
