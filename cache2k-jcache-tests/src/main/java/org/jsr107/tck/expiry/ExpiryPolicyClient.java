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

import org.jsr107.tck.support.CacheClient;
import org.jsr107.tck.support.Operation;

import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;

/**
 */
public class ExpiryPolicyClient extends CacheClient implements ExpiryPolicy {

  /**
   * Constructs a {@link ExpiryPolicyClient}.
   *
   * @param address the {@link java.net.InetAddress} on which to connect to the {@link org.jsr107.tck.expiry.ExpiryPolicyServer}
   * @param port    the port to which to connect to the {@link org.jsr107.tck.expiry.ExpiryPolicyServer}
   */
  public ExpiryPolicyClient(InetAddress address, int port) {
    super(address, port);

    this.client = null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Duration getExpiryForCreation() {
    return getClient().invoke(new GetExpiryOperation(ExpiryPolicyServer.EntryOperation.CREATION));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Duration getExpiryForAccess() {
    return getClient().invoke(new GetExpiryOperation(ExpiryPolicyServer.EntryOperation.ACCESSED));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Duration getExpiryForUpdate() {
    return getClient().invoke(new GetExpiryOperation(ExpiryPolicyServer.EntryOperation.UPDATED));
  }

  /**
   * The {@link GetExpiryOperation}.
   */
  private static class GetExpiryOperation implements Operation<Duration> {

    private ExpiryPolicyServer.EntryOperation entryOperation;

    /**
     * Constructs a {@link GetExpiryOperation}.
     */
    public GetExpiryOperation(ExpiryPolicyServer.EntryOperation entryOperation) {
      this.entryOperation = entryOperation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getType() {
      return "getExpiry";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Duration onInvoke(ObjectInputStream ois,
                             ObjectOutputStream oos) throws IOException, ClassNotFoundException {
      oos.writeObject(entryOperation.name());

      Object o = ois.readObject();

      if (o instanceof RuntimeException) {
        throw (RuntimeException) o;
      } else {
        return (Duration) o;
      }
    }
  }
}
