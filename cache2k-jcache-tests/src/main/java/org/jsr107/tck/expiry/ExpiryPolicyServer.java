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

package org.jsr107.tck.expiry;

import org.jsr107.tck.support.OperationHandler;
import org.jsr107.tck.support.Server;

import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 *
 */
public class ExpiryPolicyServer extends Server {

  /**
   * The type of operation that was performed on a Cache.Entry
   */
  public enum EntryOperation {
    /**
     * An entry was created.
     */
    CREATION,

    /**
     * An entry was accessed.
     */
    ACCESSED,

    /**
     * An entry was updated.
     */
    UPDATED;
  }

  /**
   * The underlying {@link javax.cache.integration.CacheLoader} that will be used to
   * load entries requested by the {@link org.jsr107.tck.integration.CacheLoaderClient}s.
   */
  private ExpiryPolicy expiryPolicy;

  /**
   * Constructs an {@link ExpiryPolicyServer} (without a {@link ExpiryPolicy} to
   * which client requests will be delegated).
   *
   * @param port the port on which to accept {@link ExpiryPolicyClient} requests
   */
  public ExpiryPolicyServer(int port) {
    this(port, null);
  }

  /**
   * Constructs an ExpiryPolicyServer.
   *
   * @param port         the port on which to accept {@link ExpiryPolicyClient} requests
   * @param expiryPolicy (optional) the {@link ExpiryPolicy} that will be used to handle
   *                     client requests
   */
  public ExpiryPolicyServer(int port, ExpiryPolicy expiryPolicy) {
    super(port);

    // establish the client-server operation handlers
    addOperationHandler(new GetExpiryOperationHandler());

    this.expiryPolicy = expiryPolicy;
  }

  /**
   * Set the {@link ExpiryPolicy} the {@link ExpiryPolicyServer} should use
   * from now on.
   *
   * @param expiryPolicy the {@link ExpiryPolicy}
   */
  public void setExpiryPolicy(ExpiryPolicy expiryPolicy) {
    this.expiryPolicy = expiryPolicy;
  }

  /**
   * The {@link OperationHandler} for a {@link ExpiryPolicy} operation.
   */
  public class GetExpiryOperationHandler implements OperationHandler {
    @Override
    public String getType() {
      return "getExpiry";
    }

    @Override
    public void onProcess(ObjectInputStream ois,
                          ObjectOutputStream oos) throws IOException, ClassNotFoundException {

      if (expiryPolicy == null) {
        throw new NullPointerException("The ExpiryPolicy for the ExpiryPolicyServer has not be set");
      } else {
        EntryOperation entryOperation = EntryOperation.valueOf((String)ois.readObject());

        try {
          Duration duration;
          switch (entryOperation) {
            case CREATION:
              duration = expiryPolicy.getExpiryForCreation();
              break;
            case ACCESSED:
              duration = expiryPolicy.getExpiryForAccess();
              break;
            case UPDATED:
              duration = expiryPolicy.getExpiryForUpdate();
              break;
            default:
              duration = null;
              break;
          }
          oos.writeObject(duration);
        } catch (Exception e) {
          oos.writeObject(e);
        }
      }
    }
  }
}
