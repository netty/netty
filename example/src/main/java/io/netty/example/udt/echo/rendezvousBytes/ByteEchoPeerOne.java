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
package io.netty.example.udt.echo.rendezvousBytes;

import io.netty.example.udt.echo.rendezvous.Config;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.logging.Logger;

/**
 * UDT Byte Stream Peer
 * <p/>
 * Sends one message when a connection is open and echoes back any received data
 * to the server. Simply put, the echo client initiates the ping-pong traffic
 * between the echo client and server by sending the first message to the
 * server.
 * <p/>
 */
public class ByteEchoPeerOne extends ByteEchoPeerBase {
    private static final Logger log = Logger.getLogger(ByteEchoPeerOne.class.getName());

    public ByteEchoPeerOne(int messageSize, SocketAddress myAddress, SocketAddress peerAddress) {
        super(messageSize, myAddress, peerAddress);
    }

    public static void main(String[] args) throws Exception {
        log.info("init");
        final int messageSize = 64 * 1024;
        final InetSocketAddress myAddress = new InetSocketAddress(
                Config.hostOne, Config.portOne);
        final InetSocketAddress peerAddress = new InetSocketAddress(
                Config.hostTwo, Config.portTwo);
        new ByteEchoPeerOne(messageSize, myAddress, peerAddress).run();
    }
}
