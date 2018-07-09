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

import java.io.Closeable;
import java.io.Serializable;
import java.net.InetAddress;

/**
 * A client-side base class for delegating requests to a server.
 *
 * @author Brian Oliver
 * @author Joe Fialli
 */
public class CacheClient implements Closeable, Serializable {
    /**
     * The {@link java.net.InetAddress} on which to connect to the {@link org.jsr107.tck.integration.CacheLoaderServer}.
     */
    protected InetAddress address;

    /**
     * The port on which to connect to the {@link org.jsr107.tck.integration.CacheLoaderServer}.
     */
    protected int port;

    /**
     * The {@link org.jsr107.tck.support.Client} connection to the {@link org.jsr107.tck.integration.CacheLoaderServer}.
     */
    protected transient Client client;

    private transient boolean checkedForDirectCallsPossible;
    private transient boolean useDirectCalls;

    protected transient Server directServer;

    protected CacheClient(InetAddress address, int port) {
        this.address = address;
        this.port = port;
        this.client = null;
    }

    /**
     * Obtains the internal {@link Client} used to communicate with the
     * {@link org.jsr107.tck.integration.CacheLoaderServer}.  If the {@link Client} is not connected, a
     * connection will be attempted.
     *
     * @return the {@link Client}
     */
    protected synchronized Client getClient() {
        if (client == null) {
            try {
                client = new Client(address, port);
            } catch (Exception e) {
                throw new RuntimeException("Failed to acquire Client address:" + address + ":" + port, e);
            }
        }

        return client;
    }

    protected synchronized boolean isDirectCallable() {
        if (!checkedForDirectCallsPossible) {
            checkDirectCallsPossible();
            useDirectCalls = directServer != null;
            checkedForDirectCallsPossible = true;
        }
        return useDirectCalls;
    }

    protected void checkDirectCallsPossible() {
        directServer = Server.lookupServerAtLocalMachine(port);
        if (directServer != null) {
            directServer.clientConnectedDirectly();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void close() {
        if (directServer != null) {
            directServer.closeWasCalledOnClient();
        }
        if (client != null) {
            try {
                client.invoke(Server.CLOSE_OPERATION);
                client.close();
            } finally {
                client = null;
            }
        }
    }
}
