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
import java.net.InetAddress;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A rudimentary {@link Client} that is used to invoke {@link Operation}s, those
 * of which will be handled by a {@link Server}.
 * <p>
 * Note: Only a single-thread should access individual {@link Client} instances
 * at any point in time.   Should multiple-threaded access be required,
 * additional {@link Client} instances should be created, one per thread.
 *
 * @author Brian Oliver
 * @see Server
 * @see Operation
 * @see OperationHandler
 */
public class Client implements AutoCloseable {

  /**
   * The port on which the {@link Server} is running.
   */
  private int port;

  /**
   * The {@link Socket} connecting the {@link Client} to the {@link Server}.
   * <p>
   * When this is <code>null</code> the {@link Client} is not connected.
   * </p>
   */
  private Socket socket;

  /**
   * The {@link ObjectOutputStream} to the {@link Server}.
   * <p>
   * When this is <code>null</code> the {@link Client} is not connected.
   * </p>
   */
  private ObjectOutputStream oos;

  /**
   * The {@link ObjectInputStream} from the {@link Server}.
   * <p>
   * When this is <code>null</code> the {@link Client} is not connected.
   * </p>
   */
  private ObjectInputStream ois;

  /**
   * Constructs a {@link Client} that will auto connect to a {@link Server}
   * on the specified port.
   *
   * @param address the {@link InetAddress} on which the {@link Server}
   *                is accepting requests
   * @param port    the port on which the {@link Server} is
   *                is accepting requests
   * @throws IOException when the {@link Client} can't connect to the
   *                     {@link Server}
   */
  public Client(InetAddress address, int port) throws IOException {
    Logger logger = Logger.getLogger(this.getClass().getName());
    this.port = port;
    try {
        logger.log(Level.INFO, "Starting " + this.getClass().getCanonicalName() +
                " client connecting to server at address:" + address + " port:" + port);
        this.socket = new Socket(address, port);
    } catch (IOException ioe) {
        throw new IOException("Client failed to connect to server at " + address + ":" + port, ioe);
    }
    this.oos = new ObjectOutputStream(socket.getOutputStream());
    this.ois = new ObjectInputStream(socket.getInputStream());
  }

  /**
   * Invokes the specified {@link Operation} on the {@link Server}.
   *
   * @param operation the {@link Operation} to be performed
   * @param <T>       the type of the result
   * @return the result of the {@link Operation}
   */
  public synchronized <T> T invoke(Operation<T> operation) {
    if (socket == null) {
      throw new IllegalStateException("Can't execute an operation as the Client is disconnected");
    } else {
      try {
        oos.writeObject(operation.getType());
        return operation.onInvoke(ois, oos);
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException("Failed to perform operation " + operation.getType(), e);
      }
    }
  }

  /**
   * Closes the {@link Client} connection.  If not connected or already closed,
   * nothing will happen.
   */
  public synchronized void close() {
    if (socket != null) {
      try {
        oos.close();
      } catch (IOException e) {
        //failed to close the stream - but we don't care
      } finally {
        oos = null;
      }


      try {
        ois.close();
      } catch (IOException e) {
        //failed to close the stream - but we don't care
      } finally {
        ois = null;
      }

      try {
        socket.close();
      } catch (IOException e) {
        //failed to close the socket - but we don't care
      } finally {
        socket = null;
      }
    }
  }
}
