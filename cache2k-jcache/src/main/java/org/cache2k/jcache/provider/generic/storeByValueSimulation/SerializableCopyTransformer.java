package org.cache2k.jcache.provider.generic.storeByValueSimulation;

/*
 * #%L
 * cache2k JCache provider
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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

import javax.cache.CacheException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;

/**
 * Uses serialization to copy the object instances.
 *
 * @author Jens Wilke
 */
public class SerializableCopyTransformer<T> extends CopyTransformer<T> {

  private ClassLoader classLoader;

  public SerializableCopyTransformer(final ClassLoader _classLoader) {
    classLoader = _classLoader;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected T copy(T o) {
    return (T) copySerializableObject(o);
  }

  Object copySerializableObject(Object o) {
    if (o == null) {
      return null;
    }
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(bos);
      out.writeObject(o);
      out.close();
      ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
      ObjectInputStream in = new ObjectInputStream(bis) {
        @Override
        protected Class<?> resolveClass(final ObjectStreamClass desc) throws IOException, ClassNotFoundException {
          String name = desc.getName();
          try {
            return Class.forName(name, false, classLoader);
          } catch (ClassNotFoundException ex) {
            return super.resolveClass(desc);
          }
        }
      };
      return in.readObject();
    } catch (IOException ex) {
      throw new CacheException("Failure to copy object",  ex);
    } catch (ClassNotFoundException ex) {
      throw new CacheException("Failure to copy object",  ex);
    }
  }

}
