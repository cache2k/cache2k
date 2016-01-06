package org.cache2k;

/*
 * #%L
 * cache2k API only package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Helper class to capture generic types into a type descriptor. This is used to provide
 * the cache with detailed type information of the key and value objects.
 *
 * Example usage with {@link CacheBuilder}:<pre>   {@code
 *
 *   CacheBuilder.newCache().valueType(new CacheType<List<String>(){}).build()
 * }</pre>
 *
 * This constructs a cache with the known type <code>List&lt;String></code> for its value.
 *
 * @see <a href="https://github.com/google/guava/wiki/ReflectionExplained">Google Guava CacheType explaination</a>
 *
 * @author Jens Wilke
 */
public class CacheType<T> implements CacheTypeDescriptor<T> {

  private CacheTypeDescriptor descriptor;

  protected CacheType() {
    descriptor = fromType(((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0]);
  }

  private static CacheTypeDescriptor fromType(Type t) {
    if (t instanceof ParameterizedType) {
      ParameterizedType pt = (ParameterizedType) t;
      Class c = (Class) pt.getRawType();
      CacheTypeDescriptor[] ta = new CacheTypeDescriptor[pt.getActualTypeArguments().length];
      for (int i = 0; i < ta.length; i++) {
        ta[i] = fromType(pt.getActualTypeArguments()[i]);
      }
      return new CacheTypeDescriptor.OfGeneric(c, ta);
    } else if (t instanceof GenericArrayType) {
      GenericArrayType gat = (GenericArrayType) t;
      return new CacheTypeDescriptor.OfArray(fromType(gat.getGenericComponentType()));
    }
    Class c = (Class) t;
    if (c.isArray()) {
      return new CacheTypeDescriptor.OfArray(fromType(c.getComponentType()));
    }
    return new CacheTypeDescriptor.OfClass(c);
  }

  /**
   * Construct a type descriptor from a regular class or array.
   */
  public static final <T> CacheTypeDescriptor<T> fromClass(Class<T> c) {
    return fromType(c);
  }

  @Override
  public CacheTypeDescriptor getBeanRepresentation() {
    return descriptor;
  }

  @Override
  public CacheTypeDescriptor getComponentType() {
    return descriptor.getComponentType();
  }

  @Override
  public Class<T> getType() {
    return descriptor.getType();
  }

  @Override
  public CacheTypeDescriptor[] getTypeArguments() {
    return descriptor.getTypeArguments();
  }

  @Override
  public String getTypeName() {
    return descriptor.getTypeName();
  }

  @Override
  public boolean hasTypeArguments() {
    return descriptor.hasTypeArguments();
  }

  @Override
  public boolean isArray() {
    return descriptor.isArray();
  }

  @Override
  public boolean equals(Object o) {
    return descriptor.equals(o);
  }

  @Override
  public int hashCode() {
    return descriptor.hashCode();
  }

}
