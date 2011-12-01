/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.socket.nio;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

/**
 * A UDP (User Datagram Protocol) client for use in testing
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Daniel Bevenius (dbevenius@jboss.com)
 */
public class UdpClient {

    /**
     * The internet address which is the target of this client
     */
    private final InetAddress address;
    
    /**
     * The datagram socket used by this client
     */
    private final DatagramSocket clientSocket;
    
    /**
     * The destination port used by this client
     */
    private final int port;

    /**
     * Creates a new UDP client
     * 
     * @param address The target address to use
     * @param port The target port to use
     */
    public UdpClient(final InetAddress address, final int port)
            throws SocketException {
		//Set the address
        this.address = address;
        //Set the port
        this.port = port;
        //Set up a new datagram socket
        clientSocket = new DatagramSocket();
        //And allow addresses to be reused
        clientSocket.setReuseAddress(true);
    }

    /**
     * Sends a packet
     * 
     * @param payload The payload of bytes to send
     * @return The datagram packet sent
     */
    public DatagramPacket send(final byte[] payload) throws IOException {
        final DatagramPacket dp =
                new DatagramPacket(payload, payload.length, address, port);
        clientSocket.send(dp);
        return dp;
    }

    /**
     * Receives data from a datagram packet
     * 
     * @param dp The datagram packet to use
     * @param timeout The timeout to use
     */
    public void receive(final DatagramPacket dp, final int timeout) throws IOException {
        clientSocket.setSoTimeout(timeout);
        clientSocket.receive(dp);
    }

    /**
     * Close this client and the socket it uses
     */
    public void close() {
        clientSocket.close();
    }
}
