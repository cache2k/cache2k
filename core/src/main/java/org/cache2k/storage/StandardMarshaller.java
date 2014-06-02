package org.cache2k.storage;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2014 headissue GmbH, Munich
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * A standard marshaller implementation, based on serialization.
 *
 * @author Jens Wilke; created: 2014-03-31
 */
public class StandardMarshaller implements Marshaller {

  @Override
  public byte[] marshall(Object o) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(bos);
    oos.writeObject(o);
    oos.close();
    return bos.toByteArray();
  }

  @Override
  public Object unmarshall(byte[] ba) throws IOException, ClassNotFoundException {
    ByteArrayInputStream bis = new ByteArrayInputStream(ba);
    return returnObject(bis);
  }

  @Override
  public Object unmarshall(ByteBuffer b) throws IOException, ClassNotFoundException {
    ByteBufferInputStream bis = new ByteBufferInputStream(b);
    return returnObject(bis);
  }

  private Object returnObject(InputStream bis) throws IOException, ClassNotFoundException {
    ObjectInputStream ois = new ObjectInputStream(bis);
    try {
      return ois.readObject();
    } finally {
      ois.close();
    }
  }

  @Override
  public boolean supports(Object o) {
    return true;
  }

  @Override
  public ObjectOutput startOutput(OutputStream out) throws IOException {
    return new ObjectOutputStream(out);
  }

  @Override
  public ObjectInput startInput(InputStream in) throws IOException {
    return new ObjectInputStream(in);
  }

  @Override
  public MarshallerFactory.Parameters getFactoryParameters() {
    return new MarshallerFactory.Parameters(this.getClass());
  }

}
