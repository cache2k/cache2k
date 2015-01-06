package org.cache2k.storage;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2015 headissue GmbH, Munich
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
