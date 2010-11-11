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
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Daniel Bevenius (dbevenius@jboss.com)
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class UdpClient {

    private final InetAddress address;
    private final DatagramSocket clientSocket;
    private final int port;

    public UdpClient(final InetAddress address, final int port)
            throws SocketException {
        this.address = address;
        this.port = port;
        clientSocket = new DatagramSocket();
        clientSocket.setReuseAddress(true);
    }

    public DatagramPacket send(final byte[] payload) throws IOException {
        final DatagramPacket dp =
                new DatagramPacket(payload, payload.length, address, port);
        clientSocket.send(dp);
        return dp;
    }

    public void receive(final DatagramPacket dp) throws IOException {
        clientSocket.receive(dp);
    }

    public void close() {
        clientSocket.close();
    }
}
