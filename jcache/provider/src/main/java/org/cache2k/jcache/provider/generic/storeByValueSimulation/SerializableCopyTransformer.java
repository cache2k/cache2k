package org.cache2k.jcache.provider.generic.storeByValueSimulation;

/*
 * #%L
 * cache2k JCache JSR107 implementation
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

import javax.cache.CacheException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Uses serialization to copy the object instances.
 *
 * @author Jens Wilke
 */
public class SerializableCopyTransformer<T> extends CopyTransformer<T> {

  @SuppressWarnings("unchecked")
  @Override
  protected T copy(T o) {
    return (T) copySerializableObject(o);
  }

  static Object copySerializableObject(Object o) {
    if (o == null) {
      return null;
    }
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(bos);
      out.writeObject(o);
      out.close();
      ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
      ObjectInputStream in = new ObjectInputStream(bis);
      return in.readObject();
    } catch (IOException ex) {
      throw new CacheException("Failure to copy object",  ex);
    } catch (ClassNotFoundException ex) {
      throw new CacheException("Failure to copy object",  ex);
    }
  }

}
