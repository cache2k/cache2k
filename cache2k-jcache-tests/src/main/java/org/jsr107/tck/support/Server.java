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
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A rudimentary multi-threaded {@link Socket}-based {@link Server} that can
 * handle, using {@link OperationHandler}s, {@link Operation}s invoked by
 * {@link Client}s.
 *
 * @author Brian Oliver
 * @see Client
 * @see Operation
 * @see OperationHandler
 */
public class Server implements AutoCloseable {

    /**
     * Logger
     */
    public static final  Logger LOG = Logger.getLogger(Server.class.getName());


    /**
     * The port on which the {@link Server} will accept {@link Client} connections.
     */
    private int port;

    /**
     * The {@link OperationHandler}s by operation.
     */
    private ConcurrentHashMap<String, OperationHandler> operationHandlers;

    /**
     * The {@link ServerSocket} that will be used to accept {@link Client}
     * connections and requests.
     * <p>
     * When this is <code>null</code> the {@link Server} is not running.
     */
    private ServerSocket serverSocket;

    /**
     * The {@link Thread} that will manage accepting {@link Client} connections.
     * <p>
     * When this is <code>null</code> the {@link Server} is not running.
     */
    private Thread serverThread;

    /**
     * A map of {@link ClientConnection} by connection number.
     */
    private ConcurrentHashMap<Integer, ClientConnection> clientConnections;


    /**
     * Should the running {@link Server} terminate as soon as possible?
     */
    private AtomicBoolean isTerminating;

    /**
     * Construct a {@link Server} that will accept {@link Client} connections
     * and requests on the specified port.
     *
     * @param port the port on which to accept {@link Client} connections and requests
     */
    public Server(int port) {
        this.port = port;
        this.operationHandlers = new ConcurrentHashMap<String, OperationHandler>();
        this.serverSocket = null;
        this.serverThread = null;
        this.clientConnections = new ConcurrentHashMap<Integer, ClientConnection>();
        this.isTerminating = new AtomicBoolean(false);
    }

    /**
     * Registers the specified {@link OperationHandler} for an operation.
     *
     * @param handler the {@link OperationHandler}
     */
    public void addOperationHandler(OperationHandler handler) {
        this.operationHandlers.put(handler.getType(), handler);
    }

    /**
     * Opens and starts the {@link Server}.
     * <p>
     * Does nothing if the {@link Server} is already open.
     *
     * @return the {@link InetAddress} on which the {@link Server}
     * is accepting requests from {@link Client}s.
     * @throws IOException if not able to create ServerSocket
     */
    public synchronized InetAddress open() throws IOException {
        if (serverSocket == null) {
            serverSocket = createServerSocket();
            serverThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        int connectionId = 0;

                        while (!isTerminating.get()) {
                            Socket socket = serverSocket.accept();

                            ClientConnection clientConnection = new ClientConnection(connectionId++, socket);
                            clientConnections.put(clientConnection.getIdentity(), clientConnection);
                            clientConnection.start();
                        }
                    } catch (NullPointerException e) {
                        isTerminating.compareAndSet(false, true);
                    } catch (IOException e) {
                        isTerminating.compareAndSet(false, true);
                    }
                }
            });

            serverThread.start();
        }

        return getInetAddress();
    }

    /**
     * Obtains the {@link InetAddress} on which the {@link Server} is listening.
     *
     * @return the {@link InetAddress}
     */
    public synchronized InetAddress getInetAddress() {
        if (serverSocket != null) {
            try {
                return getServerInetAddress();
            } catch (SocketException e) {
                return serverSocket.getInetAddress();
            } catch (UnknownHostException e) {
                return serverSocket.getInetAddress();
            }
        } else {
            throw new IllegalStateException("Server is not open");
        }
    }

    /**
     * Obtains the port on which the {@link Server} is listening.
     *
     * @return the port
     */
    public synchronized int getPort() {
        if (serverSocket != null) {
            return port;
        } else {
            throw new IllegalStateException("Server is not open");
        }
    }

    /**
     * Stops the {@link Server}.
     * <p>
     * Does nothing if the {@link Server} is already stopped.
     */
    public synchronized void close() {
        if (serverSocket != null) {
            //we're now terminating
            isTerminating.set(true);

            //stop the server socket
            try {
                serverSocket.close();
            } catch (IOException e) {
                //failed to close the server socket - but we don't care
            }
            serverSocket = null;

            //interrupt the server thread
            serverThread.interrupt();
            serverThread = null;

            //stop the clients
            for (ClientConnection clientConnection : clientConnections.values()) {
                clientConnection.close();
            }
            this.clientConnections = new ConcurrentHashMap<Integer, ClientConnection>();

            isTerminating.set(false);
        }
    }

    /**
     * Asynchronously handles {@link Client} requests via a {@link Socket} using the
     * defined {@link OperationHandler}s.
     */
    private class ClientConnection extends Thread implements AutoCloseable {

        /**
         * The {@link ClientConnection} identity.
         */
        private int identity;

        /**
         * The {@link Socket} to the {@link Client}.
         */
        private Socket socket;

        /**
         * Constructs a {@link ClientConnection}.
         *
         * @param identity the identity for the {@link ClientConnection}
         * @param socket   the {@link Socket} on which to receive and respond to
         *                 {@link Client} requests
         */
        public ClientConnection(int identity, Socket socket) {
            this.identity = identity;
            this.socket = socket;
        }

        /**
         * Obtains the identity for the {@link ClientConnection}.
         *
         * @return the identity
         */
        public int getIdentity() {
            return this.identity;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {

            try {
                ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());

                while (true) {
                    try {
                        String operation = (String) ois.readObject();
                        OperationHandler handler = Server.this.operationHandlers.get(operation);

                        if (handler != null) {
                            handler.onProcess(ois, oos);
                        }
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                //any error closes the connection

            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        //failed to close the socket - but we don't care
                    }
                }

                //remove this from the server
                Server.this.clientConnections.remove(identity);
            }
        }

        /**
         * {@inheritDoc}
         */
        public void close() {
            try {
                socket.close();
            } catch (IOException e) {
                //failed to close the socket - but we don't care
            } finally {
                socket = null;
            }
        }
    }

    private static InetAddress serverSocketAddress = null;

    private ServerSocket createServerSocket() throws IOException {

        final int ephemeralPort = 0;
        ServerSocket result = null;
        try {
            result = new ServerSocket(port, 50, getServerInetAddress());
        } catch (IOException e) {

            // requested port may still be in use due to linger on close on some OSs,
            // use ephemeral port for server socket
            result = new ServerSocket(ephemeralPort, 50, getServerInetAddress());
            LOG.warning("createServerSocket: unable to use requested port " + port +
                    "; using ephemeral port " + result.getLocalPort());
            this.port = result.getLocalPort();
        }
        LOG.log(Level.INFO, "Starting " + this.getClass().getCanonicalName() +
                " server at address:" + getServerInetAddress() + " port:" + port);
        return result;
    }

    /**
     * To support distributed testing, return a non-loopback address if available.
     * <p>
     * By default, on a machine with multiple network interfaces, the appropriate
     * ip address is selected based on values of java network system properties java.net.preferIPv4Stack
     * and java.net.preferIPv6Addresses.
     * <p>
     * A user can override the automated selection by specifying a network interface with
     * system property org.jsr107.tck.support.server.networkinterface set to the
     * display name of the network interface or explicitly setting the server address with
     * system property org.jsr107.tck.support.server.address.  The address value may be either
     * ipv4 or ipv6, the value should be consistent with the values of the java network properties                                  q
     * mentioned above.
     * <p>
     * @return remote addressable inet address
     * @throws SocketException
     * @throws UnknownHostException
     */
    private InetAddress getServerInetAddress() throws SocketException, UnknownHostException {
        if (serverSocketAddress == null) {
            boolean preferIPV4Stack = Boolean.getBoolean("java.net.preferIPv4Stack");
            boolean preferIPV6Addresses = Boolean.getBoolean("java.net.preferIPv6Addresses") && !preferIPV4Stack;

            String serverAddress = System.getProperty("org.jsr107.tck.support.server.address");
            if (serverAddress != null) {
                try {
                    serverSocketAddress = InetAddress.getByName(serverAddress);
                } catch (UnknownHostException e) {
                    // ignore
                     LOG.log(Level.WARNING,
                             "ignoring system property org.jsr107.tck.support.server.address due to exception", e);
                }
            }

            NetworkInterface serverSocketNetworkInterface = null;
            if (serverSocketAddress == null)  {
                String niName = System.getProperty("org.jsr107.tck.support.server.networkinterface");
                try {
                    serverSocketNetworkInterface = niName == null ? null : NetworkInterface.getByName(niName);
                    if (serverSocketNetworkInterface != null) {
                        serverSocketAddress =
                                getFirstNonLoopbackAddress(serverSocketNetworkInterface, preferIPV4Stack, preferIPV6Addresses);
                    }
                    if (serverSocketAddress == null)  {
                        LOG.log(Level.WARNING,
                            "ignoring system property org.jsr107.tck.support.server.networkinterface with value:"
                                + niName);
                    }
                } catch (SocketException e) {
                    LOG.log(Level.WARNING,
                                "ignoring system property org.jsr107.tck.support.server.networkinterface due to exception", e);
                }
            }

            if (serverSocketAddress == null) {
                serverSocketAddress = getFirstNonLoopbackAddress(preferIPV4Stack, preferIPV6Addresses);
            }

            if (serverSocketAddress == null) {
                LOG.warning("no remote ip address available so only possible to test using loopback address.");
                serverSocketAddress = InetAddress.getLocalHost();
            }
        }
        return serverSocketAddress;
    }

    /**
     * Get non-loopback address.  InetAddress.getLocalHost() does not work on machines without static ip address.
     *
     * @param preferIPv4 true iff require IPv4 addresses only
     * @param preferIPv6 true iff prefer IPv6 addresses
     * @return nonLoopback {@link InetAddress}
     * @throws SocketException
     */
    private static InetAddress getFirstNonLoopbackAddress(boolean preferIPv4, boolean preferIPv6) throws SocketException {
        InetAddress result = null;
        Enumeration en = NetworkInterface.getNetworkInterfaces();
        while (result == null && en.hasMoreElements()) {
            result = getFirstNonLoopbackAddress((NetworkInterface) en.nextElement(), preferIPv4, preferIPv6);
        }
        return result;
    }

   /**
    * Get non-loopback address.  InetAddress.getLocalHost() does not work on machines without static ip address.
    *
    * @param ni target network interface
    * @param preferIPv4 true iff require IPv4 addresses only
    * @param preferIPv6 true iff prefer IPv6 addresses
    * @return nonLoopback {@link InetAddress}
    * @throws SocketException
    */
    private static InetAddress getFirstNonLoopbackAddress(NetworkInterface ni,
                                                          boolean preferIPv4,
                                                          boolean preferIPv6) throws SocketException {
        InetAddress result = null;

        // skip virtual interface name, PTP and non-running interface.
        if (ni.isVirtual() || ni.isPointToPoint() || ! ni.isUp()) {
            return result;
        }
        LOG.info("Interface name is: " + ni.getDisplayName());
        for (Enumeration en2 = ni.getInetAddresses(); en2.hasMoreElements(); ) {
            InetAddress addr = (InetAddress) en2.nextElement();
            if (!addr.isLoopbackAddress()) {
                if (addr instanceof Inet4Address) {
                    if (preferIPv6) {
                        continue;
                    }
                    result = addr;
                    break;
                }

                if (addr instanceof Inet6Address) {
                    if (preferIPv4) {
                        continue;
                    }
                    result = addr;
                    break;
                }
            }
        }
        return result;
    }
}
