package org.cache2k.jcache.provider.generic.storeByValueSimulation;

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
