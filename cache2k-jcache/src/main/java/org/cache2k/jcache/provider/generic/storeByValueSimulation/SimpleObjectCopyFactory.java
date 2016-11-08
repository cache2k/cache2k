package org.cache2k.jcache.provider.generic.storeByValueSimulation;

/*
 * #%L
 * cache2k JCache provider
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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * For immutable objects we just pass the reference through, other objects need to be
 * copied with clone or serialization.
 *
 * @author Jens Wilke
 */
public class SimpleObjectCopyFactory implements ObjectCopyFactory {

  @Override
  public <T> ObjectTransformer<T, T> createCopyTransformer(Class<T> clazz) {
    return createCopyTransformer(clazz, this.getClass().getClassLoader());
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> ObjectTransformer<T, T> createCopyTransformer(Class<T> clazz, ClassLoader classLoader) {
    if (isImmutable(clazz)) {
      return ObjectTransformer.IDENT_TRANSFORM;
    }
    Method m = extractPublicClone(clazz);
    if (m != null) {
      return new CloneCopyTransformer<T>(m);
    }
    if (Serializable.class.isAssignableFrom(clazz)) {
      return new SerializableCopyTransformer(classLoader);
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
