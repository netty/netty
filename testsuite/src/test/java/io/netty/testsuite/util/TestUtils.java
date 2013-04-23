/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.testsuite.util;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.Channel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public final class TestUtils {

    private static final int START_PORT = 32768;
    private static final int END_PORT = 65536;
    private static final int NUM_CANDIDATES = END_PORT - START_PORT;

    private static final List<Integer> PORTS = new ArrayList<Integer>();
    private static Iterator<Integer> portIterator;

    /**
     * The logger being used by this class
     */
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(TestUtils.class);

    /**
     * The {@link InetAddress} representing the host machine
     * <p/>
     * We cache this because some machines take almost forever to return from
     * {@link InetAddress}.getLocalHost(). This may be due to incorrect
     * configuration of the hosts and DNS client configuration files.
     */
    public static final InetAddress LOCALHOST;

    /**
     * The loopback {@link NetworkInterface} on the current machine
     */
    public static final NetworkInterface LOOPBACK_IF;

    static {
        for (int i = START_PORT; i < END_PORT; i ++) {
            PORTS.add(i);
        }
        Collections.shuffle(PORTS);
    }

    static {
        //Start the process of discovering localhost
        InetAddress localhost;
        try {
            localhost = InetAddress.getLocalHost();
            validateHost(localhost);
        } catch (IOException e0) {
            // The default local host names did not work.  Try hard-coded IPv4 address.
            try {
                localhost = InetAddress.getByAddress(new byte[]{ 127, 0, 0, 1 });
                validateHost(localhost);
            } catch (IOException e1) {
                // The hard-coded IPv4 address did not work.  Try hard coded IPv6 address.
                try {
                    localhost = InetAddress.getByAddress(new byte[]{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 });
                    validateHost(localhost);
                } catch (IOException e2) {
                    // Log all exceptions we caught so far for easier diagnosis.
                    logger.warn("Failed to resolve localhost with InetAddress.getLocalHost():", e0);
                    logger.warn("Failed to resolve localhost with InetAddress.getByAddress(127.0.0.1):", e1);
                    logger.warn("Failed to resolve localhost with InetAddress.getByAddress(::1)", e2);
                    throw new Error("failed to resolve localhost; incorrect network configuration?");
                }
            }
        }

        LOCALHOST = localhost;

        //Prepare to get the local NetworkInterface
        NetworkInterface loopbackInterface;

        try {
            //Automatically get the loopback interface
            loopbackInterface = NetworkInterface.getByInetAddress(LOCALHOST);
        } catch (SocketException e) {
            //No? Alright. There is a backup!
            loopbackInterface = null;
        }

        //Check to see if a network interface was not found
        if (loopbackInterface == null) {
            try {
                //Start iterating over all network interfaces
                for (Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                     interfaces.hasMoreElements();) {
                    //Get the "next" interface
                    NetworkInterface networkInterface = interfaces.nextElement();

                    //Check to see if the interface is a loopback interface
                    if (networkInterface.isLoopback()) {
                        //Phew! The loopback interface was found.
                        loopbackInterface = networkInterface;
                        //No need to keep iterating
                        break;
                    }
                }
            } catch (SocketException e) {
                //Nope. Can't do anything else, sorry!
                logger.warn("Failed to enumerate network interfaces", e);
            }
        }

        //Set the loopback interface constant
        LOOPBACK_IF = loopbackInterface;
    }

    private static void validateHost(InetAddress host) throws IOException {
        ServerSocket ss = null;
        Socket s1 = null;
        Socket s2 = null;
        try {
            ss = new ServerSocket();
            ss.setReuseAddress(false);
            ss.bind(new InetSocketAddress(host, 0));
            s1 = new Socket(host, ss.getLocalPort());
            s2 = ss.accept();
        } finally {
            if (s2 != null) {
                try {
                    s2.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
            if (s1 != null) {
                try {
                    s1.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
            if (ss != null) {
                try {
                    ss.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    private static int nextCandidatePort() {
        if (portIterator == null || !portIterator.hasNext()) {
            portIterator = PORTS.iterator();
        }
        return portIterator.next();
    }

    /**
     * Return a free port which can be used to bind to
     *
     * @return port
     */
    public static int getFreePort() {
        for (int i = 0; i < NUM_CANDIDATES; i ++) {
            int port = nextCandidatePort();
            try {
                // Ensure it is possible to bind on both wildcard and loopback.
                ServerSocket ss;
                ss = new ServerSocket();
                ss.setReuseAddress(false);
                ss.bind(new InetSocketAddress(port));
                ss.close();

                ss = new ServerSocket();
                ss.setReuseAddress(false);
                ss.bind(new InetSocketAddress(LOCALHOST, port));
                ss.close();

                return port;
            } catch (IOException e) {
                // ignore
            }
        }

        throw new RuntimeException("unable to find a free port");
    }

    /**
     * Return {@code true} if SCTP is supported by the running os.
     *
     */
    public static boolean isSctpSupported() {
        String os = System.getProperty("os.name").toLowerCase(Locale.UK);
        if ("unix".equals(os) || "linux".equals(os) || "sun".equals(os) || "solaris".equals(os)) {
            try {
                // Try to open a SCTP Channel, by using reflection to make it compile also on
                // operation systems that not support SCTP like OSX and Windows
                Class<?> sctpChannelClass = Class.forName("com.sun.nio.sctp.SctpChannel");
                Channel channel = (Channel) sctpChannelClass.getMethod("open").invoke(null);
                try {
                    channel.close();
                } catch (IOException e) {
                    // ignore
                }
            } catch (UnsupportedOperationException e) {
                // This exception may get thrown if the OS does not have
                // the shared libs installed.
                System.out.print("Not supported: " + e.getMessage());
                return false;
            } catch (Throwable t) {
                if (!(t instanceof IOException)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private TestUtils() { }
}
