/**
 *  Copyright 2011-2013 Terracotta, Inc.
 *  Copyright 2011-2013 Oracle, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jsr107.tck.support;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * An {@link OperationHandler} is responsible for processing an {@link Operation}
 * invoked by a {@link Client}.
 *
 * @author Brian Oliver
 * @see Operation
 * @see Client
 * @see Server
 */
public interface OperationHandler {

  /**
   * The type of the operation.
   *
   * @return the type of operation
   */
  String getType();

  /**
   * Perform an {@link Operation} initiated by a {@link Client}.
   *
   * @param ois the {@link ObjectInputStream} to read information from the
   *            {@link Client}, typically parameters from an {@link Operation}
   * @param oos the {@link ObjectOutputStream} to write information to the
   *            {@link Client}, typically the result of an {@link Operation}
   * @throws IOException  if either of the streams can't be read/written to
   * @throws ClassNotFoundException  if a requested class can't be loaded
   */
  void onProcess(ObjectInputStream ois, ObjectOutputStream oos)
      throws IOException, ClassNotFoundException;
}
