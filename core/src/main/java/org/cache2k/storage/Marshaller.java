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

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * @author Jens Wilke; created: 2014-03-27
 */
public interface Marshaller {

  byte[] marshall(Object o) throws IOException;

  Object unmarshall(byte[] ba) throws IOException, ClassNotFoundException;

  Object unmarshall(ByteBuffer b) throws IOException, ClassNotFoundException;

  /**
   * True if the marshaller is able to marshall this object. A specialized
   * marshaller is allowed to return false. In this case a fallback is done
   * to a default marshaller, which supports every object type.
   */
  boolean supports(Object o);

  ObjectOutput startOutput(OutputStream out) throws IOException;

  ObjectInput startInput(InputStream in) throws IOException;

  /**
   *
   *
   * @return parameters for the factory or null if the marshaller type is sufficient.
   */
  MarshallerFactory.Parameters getFactoryParameters();

}
