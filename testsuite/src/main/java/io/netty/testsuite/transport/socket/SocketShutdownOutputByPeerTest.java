/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.testsuite.transport.socket;

import io.netty.util.internal.SocketUtils;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;

public class SocketShutdownOutputByPeerTest extends AbstractSocketShutdownOutputByPeerTest<Socket> {

    @Override
    protected void shutdownOutput(Socket s) throws IOException {
        s.shutdownOutput();
    }

    @Override
    protected void connect(Socket s, SocketAddress address) throws IOException {
        SocketUtils.connect(s, address, 10000);
    }

    @Override
    protected void close(Socket s) throws IOException {
        s.close();
    }

    @Override
    protected void write(Socket s, int data) throws IOException {
        s.getOutputStream().write(data);
    }

    @Override
    protected Socket newSocket() {
        return new Socket();
    }

}
