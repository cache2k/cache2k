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
 * Creates a new instance of the customization based on the class name and the class loader
 * in effect by the cache.
 *
 * @author Jens Wilke
 */
public final class CustomizationSupplierByClassName<T>
  implements CustomizationSupplier<T>, ValidatingConfigBean {

  private String className;

  /**
   * Default constructor for beans.
   */
  public CustomizationSupplierByClassName() { }

  /**
   * Construct a customization factory based on the class name.
   *
   * @param className Fully qualified class name, used to create the class instance
   *                  via a {@link ClassLoader#loadClass(String)}. The class must have
   *                  a default constructor. Not null.
   */
  public CustomizationSupplierByClassName(String className) {
    if (className == null) {
      throw new NullPointerException("className");
    }
    this.className = className;
  }

  public String getClassName() {
    return className;
  }

  public void setClassName(String v) {
    className = v;
  }

  @Override
  public void validate() {
    if (className == null) {
      throw new IllegalArgumentException("className not set");
    }
  }

  @Override
  public ConfigBuilder builder() {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("unchecked")
  @Override
  public T supply(CacheBuildContext<?, ?> ctx) {
    try {
      return (T) ctx.getCacheManager().getClassLoader()
        .loadClass(className).getConstructor().newInstance();
    } catch (Exception e) {
      throw new LinkageError("error loading customization class", e);
    }
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof CustomizationSupplierByClassName)) return false;
    CustomizationSupplierByClassName<?> that = (CustomizationSupplierByClassName<?>) other;
    return className.equals(that.className);
  }

  @Override
  public int hashCode() {
    if (className != null) {
      return className.hashCode();
    }
    return 0;
  }

}
