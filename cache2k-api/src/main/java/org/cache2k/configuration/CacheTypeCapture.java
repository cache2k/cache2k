package org.cache2k.configuration;

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

import org.cache2k.Cache2kBuilder;

import java.io.Serializable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

/**
 * Helper class to capture generic types into a type descriptor. This is used to provide
 * the cache with detailed type information of the key and value objects.
 *
 * Example usage with {@link Cache2kBuilder}:<pre>   {@code
 *
 *   CacheBuilder.newCache().valueType(new CacheType<List<String>(){}).build()
 * }</pre>
 *
 * This constructs a cache with the known type {@code List<String>} for its value.
 *
 * @see <a href="https://github.com/google/guava/wiki/ReflectionExplained">Google Guava CacheType explaination</a>
 *
 * @author Jens Wilke
 */
public class CacheTypeCapture<T> implements CacheType<T> {

  @SuppressWarnings("unchecked")
  private final CacheType<T> descriptor =
    of(((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0]);

  protected CacheTypeCapture() { }

  @SuppressWarnings("unchecked")
  public static <T> CacheType<T> of(Class<T> t) {
    return of((Type) t);
  }

  @SuppressWarnings("unchecked")
  public static CacheType of(Type t) {
    if (t instanceof ParameterizedType) {
      ParameterizedType pt = (ParameterizedType) t;
      Class c = (Class) pt.getRawType();
      CacheType[] ta = new CacheType[pt.getActualTypeArguments().length];
      for (int i = 0; i < ta.length; i++) {
        ta[i] = of(pt.getActualTypeArguments()[i]);
      }
      return new OfGeneric(c, ta);
    } else if (t instanceof GenericArrayType) {
      GenericArrayType gat = (GenericArrayType) t;
      return new OfArray(of(gat.getGenericComponentType()));
    }
    if (!(t instanceof Class)) {
      throw new IllegalArgumentException("The run time type is not available, got: " + t);
    }
    Class c = (Class) t;
    if (c.isArray()) {
      return new OfArray(of(c.getComponentType()));
    }
    return new OfClass(c);
  }

  @Override
  public CacheType<T> getBeanRepresentation() {
    return descriptor;
  }

  @Override
  public CacheType<?> getComponentType() {
    return descriptor.getComponentType();
  }

  @Override
  public Class<T> getType() {
    return descriptor.getType();
  }

  @Override
  public CacheType<?>[] getTypeArguments() {
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

  @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
  @Override
  public boolean equals(Object o) {
    return descriptor.equals(o);
  }

  @Override
  public int hashCode() {
    return descriptor.hashCode();
  }

  private static abstract class BaseType implements CacheType, Serializable {

    @Override
    public CacheType getComponentType() {
      return null;
    }

    @Override
    public Class getType() {
      return null;
    }

    @Override
    public CacheType[] getTypeArguments() {
      return null;
    }

    @Override
    public boolean hasTypeArguments() {
      return false;
    }

    @Override
    public boolean isArray() {
      return false;
    }

    @Override
    public CacheType getBeanRepresentation() {
      return this;
    }

    @Override
    public final String toString() {
      return DESCRIPTOR_TO_STRING_PREFIX + getTypeName();
    }

  }

  /**
   * CacheType representing a class.
   */
  public static class OfClass extends BaseType {

    Class<?> type;

    /** Empty constructor for bean compliance. */
    @SuppressWarnings("unused")
    public OfClass() {
    }

    public OfClass(Class<?> type) {
      if (type.isArray()) {
        throw new IllegalArgumentException("array is not a regular class");
      }
      this.type = type;
    }

    @Override
    public Class<?> getType() {
      return type;
    }

    /** Setter for bean compliance */
    public void setType(Class<?> type) {
      this.type = type;
    }

    static String shortenName(String s) {
      final String _LANG_PREFIX = "java.lang.";
      if (s.startsWith(_LANG_PREFIX)) {
        return s.substring(_LANG_PREFIX.length());
      }
      return s;
    }

    @Override
    public String getTypeName() {
      return shortenName(type.getCanonicalName());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      OfClass classType = (OfClass) o;
      return type.equals(classType.type);
    }

    @Override
    public int hashCode() {
      return type.hashCode();
    }

  }

  /**
   * CacheType representing an array.
   */
  public static class OfArray extends BaseType {

    CacheType componentType;

    /** Empty constructor for bean compliance. */
    @SuppressWarnings("unused")
    public OfArray() {
    }

    public OfArray(CacheType componentType) {
      this.componentType = componentType;
    }

    @Override
    public boolean isArray() {
      return true;
    }

    @Override
    public CacheType getComponentType() {
      return componentType;
    }

    @SuppressWarnings("unused")
    public void setComponentType(CacheType componentType) {
      this.componentType = componentType;
    }

    static int countDimensions(CacheType td ) {
      int cnt = 0;
      while (td.isArray()) {
        td = td.getComponentType();
        cnt++;
      }
      return cnt;
    }

    static Class<?> finalPrimitiveType(CacheType td) {
      while (td.isArray()) {
        td = td.getComponentType();
      }
      return td.getType();
    }

    @Override
    public String getTypeName() {
      StringBuilder sb = new StringBuilder();
      int _dimensions = countDimensions(this);
      if (_dimensions > 1) {
        sb.append(finalPrimitiveType(this));
      } else {
        sb.append(getComponentType().getTypeName());
      }
      for (int i = 0; i < _dimensions; i++) {
        sb.append("[]");
      }
      return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) { return true; }
      if (o == null || getClass() != o.getClass()) { return false; }
      OfArray arrayType = (OfArray) o;
      return componentType.equals(arrayType.componentType);
    }

    @Override
    public int hashCode() {
      return componentType.hashCode();
    }
  }

  /**
   * CacheType representing a generic type.
   */
  public static class OfGeneric extends BaseType {

    CacheType[] typeArguments;
    Class<?> type;

    /** Empty constructor for bean compliance. */
    @SuppressWarnings("unused")
    public OfGeneric() {
    }

    public OfGeneric(Class<?> type, CacheType[] typeArguments) {
      this.typeArguments = typeArguments;
      this.type = type;
    }

    @Override
    public Class<?> getType() {
      return type;
    }

    public void setType(Class<?> type) {
      this.type = type;
    }

    @Override
    public boolean hasTypeArguments() {
      return true;
    }

    @Override
    public CacheType[] getTypeArguments() {
      return typeArguments;
    }

    @SuppressWarnings("unused")
    public void setTypeArguments(CacheType[] typeArguments) {
      this.typeArguments = typeArguments;
    }

    @Override
    public String getTypeName() {
      return
        OfClass.shortenName(type.getCanonicalName()) + "<" + arrayToString(typeArguments) + '>';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      OfGeneric that = (OfGeneric) o;
      return Arrays.equals(typeArguments, that.typeArguments) && type.equals(that.type);
    }

    @Override
    public int hashCode() {
      int result = Arrays.hashCode(typeArguments);
      result = 31 * result + type.hashCode();
      return result;
    }

  }

  static String arrayToString(CacheType[] a) {
    if (a.length < 1) {
      throw new IllegalArgumentException();
    }
    StringBuilder sb = new StringBuilder();
    int l = a.length - 1;
    for (int i = 0; ; i++) {
      sb.append(a[i].getTypeName());
      if (i == l)
        return sb.toString();
      sb.append(',');
    }
  }

}
