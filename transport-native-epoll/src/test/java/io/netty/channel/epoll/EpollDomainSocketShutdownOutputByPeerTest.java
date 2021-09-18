/*
 * Copyright 2019 The Netty Project
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

package io.netty.channel.epoll;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.unix.Buffer;
import io.netty.testsuite.transport.TestsuitePermutation.BootstrapFactory;
import io.netty.testsuite.transport.socket.AbstractSocketShutdownOutputByPeerTest;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.List;

public class EpollDomainSocketShutdownOutputByPeerTest extends AbstractSocketShutdownOutputByPeerTest<LinuxSocket> {

    @Override
    protected List<BootstrapFactory<ServerBootstrap>> newFactories() {
        return EpollSocketTestPermutation.INSTANCE.serverDomainSocket();
    }

    @Override
    protected SocketAddress newSocketAddress() {
        return EpollSocketTestPermutation.newDomainSocketAddress();
    }

    @Override
    protected void shutdownOutput(LinuxSocket s) throws IOException {
        s.shutdown(false, true);
    }

    @Override
    protected void connect(LinuxSocket s, SocketAddress address) throws IOException {
        s.connect(address);
    }

    @Override
    protected void close(LinuxSocket s) throws IOException {
        s.close();
    }

    @Override
    protected void write(LinuxSocket s, int data) throws IOException {
        final ByteBuffer buf = Buffer.allocateDirectWithNativeOrder(4);
        buf.putInt(data);
        buf.flip();
        s.write(buf, buf.position(), buf.limit());
        Buffer.free(buf);
    }

    @Override
    protected LinuxSocket newSocket() {
        return LinuxSocket.newSocketDomain();
    }
}
