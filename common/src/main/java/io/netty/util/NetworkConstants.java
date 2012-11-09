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
package io.netty.util;

import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * A class that holds a number of network-related constants.
 */
public final class NetworkConstants {

    /**
     * The {@link InetAddress} representing the host machine
     *
     * We cache this because some machines take almost forever to return from
     * {@link InetAddress}.getLocalHost(). This may be due to incorrect
     * configuration of the hosts and DNS client configuration files.
     */
    public static final InetAddress LOCALHOST;

    /**
     * The loopback {@link NetworkInterface} on the current machine
     */
    public static final NetworkInterface LOOPBACK_IF;

    /**
     * The SOMAXCONN value of the current machine.  If failed to get the value, 3072 is used as a
     * default value.
     */
    public static final int SOMAXCONN;

    /**
     * The logger being used by this class
     */
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(NetworkConstants.class);

    static {

        //Start the process of discovering localhost
        InetAddress localhost;
        try {
            localhost = InetAddress.getLocalHost();
            validateHost(localhost);
        } catch (IOException e) {
            // The default local host names did not work.  Try hard-coded IPv4 address.
            try {
                localhost = InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 });
                validateHost(localhost);
            } catch (IOException e1) {
                // The hard-coded IPv4 address did not work.  Try hard coded IPv6 address.
                try {
                    localhost = InetAddress.getByAddress(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 });
                    validateHost(localhost);
                } catch (IOException e2) {
                    throw new Error("Failed to resolve localhost - incorrect network configuration?", e2);
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
                logger.error("Failed to enumerate network interfaces", e);
            }
        }

        //Set the loopback interface constant
        LOOPBACK_IF = loopbackInterface;

        int somaxconn = 3072;
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader("/proc/sys/net/core/somaxconn"));
            somaxconn = Integer.parseInt(in.readLine());
        } catch (Exception e) {
            // Failed to get SOMAXCONN
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                    // Ignored.
                }
            }
        }

        SOMAXCONN = somaxconn;
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

    /**
     * A constructor to stop this class being constructed.
     */
    private NetworkConstants() {
        // Unused
    }
}
