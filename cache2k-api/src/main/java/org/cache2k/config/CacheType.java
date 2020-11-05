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

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * A data structure to retain all known type information from the key and value types, including
 * generic parameters within the cache configuration. A caching application typically constructs a
 * type descriptor with the use of a {@link CacheTypeCapture} or {@link org.cache2k.Cache2kBuilder}
 *
 * <p>While the type descriptor contains implementation classes, interface consumers must not rely
 * on the implementation types.
 *
 * <p><b>About types:</b>
 * If no type information is provided it defaults to the Object class. The provided type information
 * is used inside the cache for optimizations and as well as to select appropriate default
 * transformation schemes for copying objects or marshalling. The correct types are not strictly
 * enforced at all levels by the cache for performance reasons. The cache application guarantees
 * that only the specified types will be used. The cache will check the type compatibility at
 * critical points, e.g. when reconnecting to an external storage. Generic types: An application
 * may provide more detailed type information to the cache, which contains also generic type
 * parameters by providing a {@link CacheTypeCapture} where the cache can extract the type
 * information.
 *
 * <p>The cache type is immutable.
 *
 * @see CacheTypeCapture
 * @see <a href="https://github.com/google/guava/wiki/ReflectionExplained">
 *   ReflectionExplained - Google Guava Documentation</a>
 */
public interface CacheType<T> {

  /** The used prefix for the toString() output. */
  String DESCRIPTOR_TO_STRING_PREFIX = "CacheType:";

  static <T> CacheType<T> of(Class<T> t) {
    return (CacheType<T>) of((Type) t);
  }

  @SuppressWarnings("unchecked")
  static CacheType<?> of(Type t) {
    if (t instanceof ParameterizedType) {
      ParameterizedType pt = (ParameterizedType) t;
      Class c = (Class) pt.getRawType();
      CacheType[] ta = new CacheType[pt.getActualTypeArguments().length];
      for (int i = 0; i < ta.length; i++) {
        ta[i] = of(pt.getActualTypeArguments()[i]);
      }
      return new CacheTypeCapture.OfGeneric(c, ta);
    } else if (t instanceof GenericArrayType) {
      GenericArrayType gat = (GenericArrayType) t;
      return new CacheTypeCapture.OfArray(of(gat.getGenericComponentType()));
    }
    if (!(t instanceof Class)) {
      throw new IllegalArgumentException("The run time type is not available, got: " + t);
    }
    Class c = (Class) t;
    if (c.isArray()) {
      return new CacheTypeCapture.OfArray(of(c.getComponentType()));
    }
    return new CacheTypeCapture.OfClass(c);
  }

  /** Class type if not an array. */
  Class<T> getType();

  /**
   * The type has generic type parameters and the concrete types are known.
   * {@link #getTypeArguments()} returns the the arguments.
   */
  boolean hasTypeArguments();

  /**
   * This type is an array. To analyze a multi dimensional array descend to the component,
   * for example {@code getComponentType().isArray()}.
   *
   * @see #getComponentType()
   */
  boolean isArray();

  /** The component type in case of an array */
  CacheType<?> getComponentType();

  /** Known type arguments, if the type is a parametrized type. */
  CacheType<?>[] getTypeArguments();

  /** Java language compatible type name */
  String getTypeName();

}
