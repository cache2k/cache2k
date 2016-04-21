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

import java.io.Serializable;
import java.util.Arrays;

/**
 * A data structure to retain all known type information from the key and value types, including generic
 * parameters within the cache configuration. A caching application typically constructs a type descriptor
 * with the use of a {@link CacheType}.
 *
 * <p>While the type descriptor contains implementation classes, interface consumers must not rely on the
 * implementation types.</p>
 *
 * @see CacheType
 */
public interface CacheTypeDescriptor<T> {

  /** The used prefix for the toString() output. */
  String DESCRIPTOR_TO_STRING_PREFIX = "CacheTypeDescriptor#";

  /** Class type if not an array. */
  Class<T> getType();

  /**
   * This ia a parameterized type and the concrete types are known.
   * {@link #getTypeArguments()} returns the the arguments.
   */
  boolean hasTypeArguments();

  /* This type is an array. */
  boolean isArray();

  /** The component type in case of an array */
  CacheTypeDescriptor getComponentType();

  /** Known type arguments, if the type is a parameterized type. */
  CacheTypeDescriptor[] getTypeArguments();

  /** Java language compatible type name */
  String getTypeName();

  /**
   * Return a serializable version of this type descriptor.
   */
  CacheTypeDescriptor getBeanRepresentation();

  abstract class BaseType implements CacheTypeDescriptor, Serializable {

    @Override
    public CacheTypeDescriptor getComponentType() {
      return null;
    }

    @Override
    public Class<?> getType() {
      return null;
    }

    @Override
    public CacheTypeDescriptor[] getTypeArguments() {
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
    public CacheTypeDescriptor getBeanRepresentation() {
      return this;
    }

    @Override
    public final String toString() {
      return DESCRIPTOR_TO_STRING_PREFIX + getTypeName();
    }

  }

  class OfClass extends BaseType {

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

  class OfArray extends BaseType {

    CacheTypeDescriptor componentType;

    /** Empty constructor for bean compliance. */
    @SuppressWarnings("unused")
    public OfArray() {
    }

    public OfArray(CacheTypeDescriptor componentType) {
      this.componentType = componentType;
    }

    @Override
    public boolean isArray() {
      return true;
    }

    @Override
    public CacheTypeDescriptor getComponentType() {
      return componentType;
    }

    @SuppressWarnings("unused")
    public void setComponentType(CacheTypeDescriptor componentType) {
      this.componentType = componentType;
    }

    static int countDimensions(CacheTypeDescriptor td ) {
      int cnt = 0;
      while (td.isArray()) {
        td = td.getComponentType();
        cnt++;
      }
      return cnt;
    }

    static Class<?> finalPrimitiveType(CacheTypeDescriptor td) {
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

  class OfGeneric extends BaseType {

    CacheTypeDescriptor[] typeArguments;
    Class<?> type;

    /** Empty constructor for bean compliance. */
    @SuppressWarnings("unused")
    public OfGeneric() {
    }

    public OfGeneric(Class<?> type, CacheTypeDescriptor[] typeArguments) {
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
    public CacheTypeDescriptor[] getTypeArguments() {
      return typeArguments;
    }

    @SuppressWarnings("unused")
    public void setTypeArguments(CacheTypeDescriptor[] typeArguments) {
      this.typeArguments = typeArguments;
    }

    static String arrayToString(CacheTypeDescriptor[] a) {
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

}
