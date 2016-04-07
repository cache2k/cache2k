package org.cache2k.storage;

/*
 * #%L
 * cache2k core package
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
