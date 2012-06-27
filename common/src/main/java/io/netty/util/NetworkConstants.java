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

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
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
     * The logger being used by this class
     */
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(NetworkConstants.class);

    static {

        //Start the process of discovering localhost
        InetAddress localhost = null;

        try {
            //Let's start by getting localhost automatically
            localhost = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            //No? That's okay.
            try {
                //Try to force an IPv4 localhost address
                localhost = InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 });
            } catch (UnknownHostException e1) {
                //No? Okay. You must be using IPv6
                try {
                    //Try to force an IPv6 localhost address
                    localhost = InetAddress.getByAddress(
                            new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 });
                } catch (UnknownHostException e2) {
                    //No? Okay.
                    logger.error("Failed to resolve localhost - Incorrect network configuration?", e2);
                }
            }
        }

        //Set the localhost constant
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
    }

    /**
     * A constructor to stop this class being constructed.
     */
    private NetworkConstants() {
        // Unused
    }
}
