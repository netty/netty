/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.channel.socket.nio;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

/**
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Daniel Bevenius (dbevenius@jboss.com)
 * @version $Rev$, $Date$
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
