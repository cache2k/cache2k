package org.cache2k.jcache.generic.storeByValueSimulation;

/*
 * #%L
 * cache2k JCache JSR107 implementation
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

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * For immutable objects we just pass the reference through, other objects need to be
 * copied with clone or serialization.
 *
 * @author Jens Wilke
 */
public class SimpleObjectCopyFactory implements ObjectCopyFactory {

  @SuppressWarnings("unchecked")
  @Override
  public <T> ObjectTransformer<T, T> createCopyTransformer(Class<T> clazz) {
    if (isImmutable(clazz)) {
      return ObjectTransformer.IDENT_TRANSFORM;
    }
    Method m = extractPublicClone(clazz);
    if (m != null) {
      return new CloneCopyTransformer<T>(m);
    }
    if (Serializable.class.isAssignableFrom(clazz)) {
      return ObjectTransformer.SERIALIZABLE_COPY_TRANSFORM;
    }

    return null;
  }

  static boolean isImmutable(Class<?> clazz) {
    return
        String.class == clazz ||
        Number.class.isAssignableFrom(clazz);
  }

  static Method extractPublicClone(Class<?> clazz) {
    if (Cloneable.class.isAssignableFrom(clazz)) {
      Method m = null;
      try {
        m = clazz.getMethod("clone");
        if (Modifier.isPublic(m.getModifiers())) {
          return m;
        }
      } catch (NoSuchMethodException e) {
      }
    }
    return null;
  }

}
