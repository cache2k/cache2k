package org.cache2k.configuration;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2017 headissue GmbH, Munich
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
 * Creates a new instance of the customization based on the class name and the class loader
 * in effect by the cache.
 *
 * @author Jens Wilke
 */
public final class CustomizationSupplierByClassName<T> implements CustomizationSupplier<T>, ConfigurationBean {

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
  public CustomizationSupplierByClassName(final String className) {
    if (className == null) {
      throw new NullPointerException("className");
    }
    this.className = className;
  }

  public String getClassName() {
    return className;
  }

  public void setClassName(final String v) {
    className = v;
  }

  @Override
  public T supply(final CacheManager manager) throws Exception {
    return (T) manager.getClassLoader().loadClass(className).newInstance();
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) return true;
    if (!(other instanceof CustomizationSupplierByClassName)) return false;
    CustomizationSupplierByClassName<?> _that = (CustomizationSupplierByClassName<?>) other;
    return className.equals(_that.className);
  }

  @Override
  public int hashCode() {
    return className.hashCode();
  }

}
