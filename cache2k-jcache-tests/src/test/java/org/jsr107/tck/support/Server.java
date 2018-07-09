/**
 *  Copyright 2011-2013 Terracotta, Inc.
 *  Copyright 2011-2013 Oracle, Inc.
 *  Copyright 2016 headissue GmbH
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

import java.io.EOFException;
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
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A rudimentary multi-threaded {@link Socket}-based {@link Server} that can
 * handle, using {@link OperationHandler}s, {@link Operation}s invoked by
 * {@link Client}s.
 *
 * @author Brian Oliver
 * @author Jens Wilke
 * @see Client
 * @see Operation
 * @see OperationHandler
 */
public class Server implements AutoCloseable {

    /**
     * If false, always communicate over TCP and don't try to use direct method in the local VM
     */
    private static final boolean ALLOW_DIRECT_CALLS =
      System.getProperty("org.jsr107.tck.support.server.alwaysUseTcp") == null;

    /**
     * If a server and socket is unused, put it in a queue and reuse it.
     * This way we can save the TCP connection overhead and thread start if
     * it is possible to communicate directly within the local machine.
     */
    private static final Queue<SocketThread> SOCKET_THREADS = new LinkedBlockingDeque<>(10);

    /**
     * Contains the servers that run on the respective port. The client can lookup its server
     * in the hash table. If it is present, it runs in the local VM. If it is not present
     * the client must connect via TCP.
     */
    private static final ConcurrentHashMap<Integer, Server> PORT_2_SERVER = new ConcurrentHashMap<>();

    /**
     * Returns the server for this port, if running in the local machine.
     */
    public static Server lookupServerAtLocalMachine(int port) {
        return PORT_2_SERVER.get(port);
    }

    /**
     * Logger
     */
    public static final  Logger LOG = Logger.getLogger(Server.class.getName());

    /**
     * Special operation to signal the server that the client has been closed.
￼    */
   public static final Operation<Void> CLOSE_OPERATION = new Operation<Void>() {

        public String getType() {
            return "CLOSE";
        }

       /**
￼       * The connections are closed by the server upon receiving the closed operation.
￼       * We need to block in the client until the server performed the close, to make
￼       * sure the server has removed the client from the client collection map.
￼       *
￼       * <p>This is executed in the client after the close command is sent.
￼       *
￼       * @see Client#invoke(Operation)
￼       */
        public Void onInvoke(final ObjectInputStream ois, final ObjectOutputStream oos) throws IOException {
            try {
                ois.readByte();
                throw new IOException("Unexpected data received from the server after close.");
            } catch (EOFException expected) {
                // connection successfully closed by the server
            }
            return null;
        }
    };

    /**
     * The {@link OperationHandler}s by operation.
     */
    private ConcurrentHashMap<String, OperationHandler> operationHandlers;

    /**
     * The {@link Thread} that will manage accepting {@link Client} connections.
     * <p>
     * When this is <code>null</code> the {@link Server} is not running.
     */
    private SocketThread serverThread;

    /**
     * Should the running {@link Server} terminate as soon as possible?
     */
    private AtomicBoolean isTerminating = new AtomicBoolean(false);

    private ServerSocket serverSocket;

    /**
     * A map of {@link ClientConnection} by connection number.
     */
    private ConcurrentHashMap<Integer, ClientConnection> clientConnections;

    private int serverDirectUsage = 0;


    /**
     * Construct a {@link Server} that will accept {@link Client} connections
     * and requests on the specified port.
     *
     * @param port the port on which to accept {@link Client} connections and requests
     */
    public Server(int port) {
        this.operationHandlers = new ConcurrentHashMap<String, OperationHandler>();
        this.clientConnections = new ConcurrentHashMap<Integer, ClientConnection>();
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
        if (serverThread == null) {
            if (ALLOW_DIRECT_CALLS) {
                serverThread = SOCKET_THREADS.poll();
            }
            if (serverThread == null) {
                serverSocket = createServerSocket();
                serverThread = new SocketThread();
                serverThread.serverSocket = serverSocket;
                serverThread.setBoundServer(this);
                serverThread.start();
            } else {
                serverSocket = serverThread.serverSocket;
                serverThread.setBoundServer(this);
                assert !serverThread.wasUsed();
            }
            if (ALLOW_DIRECT_CALLS) {
                PORT_2_SERVER.put(serverSocket.getLocalPort(), this);
            }
        }
        return getInetAddress();
    }

    /**
     * Obtains the {@link InetAddress} on which the {@link Server} is listening.
     *
     * @return the {@link InetAddress}
     */
    public synchronized InetAddress getInetAddress() {
        if (serverThread != null) {
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
        if (serverThread != null) {
            return serverSocket.getLocalPort();
        } else {
            throw new IllegalStateException("Server is not open");
        }
    }

    public synchronized void clientConnectedDirectly() {
        serverDirectUsage++;
    }

    public synchronized void closeWasCalledOnClient() {
        if (serverDirectUsage == 0) {
            throw new IllegalStateException(
              "customization already closed, class: "
              + this.getClass().getSimpleName());
        }
        serverDirectUsage--;
    }

    /**
     * Stops the {@link Server}.
     * <p>
     * Does nothing if the {@link Server} is already stopped.
     */
    public synchronized void close() {
        if (serverThread != null) {
            PORT_2_SERVER.remove(getPort());

            if (ALLOW_DIRECT_CALLS) {
                if (serverDirectUsage > 0) {
                    throw new IllegalStateException(
                      "The close() method was not called. " +
                        "Customizations implementing Closeable need to be closed. " +
                        "See https://github.com/jsr107/jsr107tck/issues/100" +
                        ", server type " + this.getClass().getSimpleName()
                    );
                }
            }

            if (!serverThread.wasUsed()) {
                SOCKET_THREADS.offer(serverThread);
            } else {
                //we're now terminating
                isTerminating.set(true);

                if (clientConnections.size() > 0) {
                    LOG.warning("Open client connections: " + clientConnections);
                    throw new IllegalStateException(
                      "Excepting no open client connections. " +
                        "Customizations implementing Closeable need to be closed. " +
                        "See https://github.com/jsr107/jsr107tck/issues/100" +
                        ", server type " + this.getClass().getSimpleName()
                    );
                }

                //stop the server socket
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    //failed to close the server socket - but we don't care
                }
                serverSocket = null;

                //interrupt the server thread
                serverThread.interrupt();
            }
            serverThread = null;

            //stop the clients
            for (ClientConnection clientConnection : clientConnections.values()) {
                clientConnection.close();
            }
            this.clientConnections = new ConcurrentHashMap<Integer, ClientConnection>();
        }
    }

    /**
     * Asynchronously handles {@link Client} requests via a {@link Socket} using the
     * defined {@link OperationHandler}s.
     */
    private static class ClientConnection extends Thread implements AutoCloseable {

        private Server server;

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
        public ClientConnection(Server server, int identity, Socket socket) {
            this.server = server;
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
                        if (CLOSE_OPERATION.getType().equals(operation)) {
                            // regular close, remove before closing
                            server.clientConnections.remove(identity);
                            // connection close means we acknowledge to the client and the client may
                            // complete the close operation.
                            socket.close();
                            socket = null;
                            break;
                        }
                        server.executeOperation(operation, ois, oos);
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
                server.clientConnections.remove(identity);
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

    public void executeOperation(String operation, ObjectInputStream ois, ObjectOutputStream oos) throws ClassNotFoundException, IOException {
        OperationHandler handler = operationHandlers.get(operation);
        if (handler != null) {
            handler.onProcess(ois, oos);
        } else {
            throw new IllegalArgumentException("no handler for: " + operation);
        }
    }

    private static InetAddress serverSocketAddress = null;

    private ServerSocket createServerSocket() throws IOException {
        final int ephemeralPort = 0;
        // JW: change from original TCK: always use ephemeral port!
        ServerSocket result = new ServerSocket(ephemeralPort, 50, getServerInetAddress());
        LOG.log(Level.INFO, "Starting " + this.getClass().getCanonicalName() +
                " server at address:" + getServerInetAddress() + " port:" + result.getLocalPort());
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
    private static InetAddress getServerInetAddress() throws SocketException, UnknownHostException {
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

    static AtomicInteger connectionId = new AtomicInteger();


    static class SocketThread extends Thread {

        private AtomicBoolean wasUsed = new AtomicBoolean();

        ServerSocket serverSocket;

        volatile Server boundServer;

        public void setBoundServer(final Server _boundServer) {
            boundServer = _boundServer;
        }

        public boolean wasUsed() {
            return wasUsed.get();
        }

        @Override
        public void run() {
            try {
                while (!boundServer.isTerminating.get()) {
                    Socket socket = serverSocket.accept();
                    wasUsed.set(true);
                    ClientConnection clientConnection = new ClientConnection(boundServer, connectionId.incrementAndGet(), socket);
                    boundServer.clientConnections.put(clientConnection.getIdentity(), clientConnection);
                    clientConnection.start();
                }
            } catch (IOException e) {
                // ignore
            } finally {
                wasUsed.set(true);
            }
        }

    }


}
