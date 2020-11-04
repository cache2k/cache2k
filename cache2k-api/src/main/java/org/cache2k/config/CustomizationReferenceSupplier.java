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

/**
 * A reference to the customization to be used is set while building the cache.
 * The reference is returned. The class loader is ignored.
 *
 * @author Jens Wilke
 */
public final class CustomizationReferenceSupplier<T> implements CustomizationSupplier<T> {

  private final T object;

  /**
   * Construct a customization factory that returns always the same object instance.
   *
   * @param obj reference to a customization. Not null.
   */
  public CustomizationReferenceSupplier(T obj) {
    Cache2kConfig.checkNull(obj);
    object = obj;
  }

  @Override
  public T supply(CacheBuildContext ignored) {
    return object;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof CustomizationReferenceSupplier)) {
      return false;
    }
    CustomizationReferenceSupplier<?> obj = (CustomizationReferenceSupplier<?>) other;
    return object.equals(obj.object);
  }

  @Override
  public int hashCode() {
    return object.hashCode();
  }

}
