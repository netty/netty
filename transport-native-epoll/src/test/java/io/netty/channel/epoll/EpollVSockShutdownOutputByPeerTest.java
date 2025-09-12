/*
 * Copyright 2025 The Netty Project
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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.unix.Buffer;
import io.netty.testsuite.transport.TestsuitePermutation.BootstrapFactory;
import io.netty.testsuite.transport.socket.AbstractSocketShutdownOutputByPeerTest;
import io.netty.util.internal.CleanableDirectBuffer;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class EpollVSockShutdownOutputByPeerTest extends AbstractSocketShutdownOutputByPeerTest<LinuxSocket> {
    private Bootstrap bootstrap;
    private EpollVSockChannel vSockChannel;
    private static final ChannelHandler NOOP_HANDLER = new ChannelHandler() {

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            // NOOP
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            // NOOP
        }
    };

    @Override
    protected List<BootstrapFactory<ServerBootstrap>> newFactories() {
        return EpollSocketTestPermutation.INSTANCE.serverVSocket();
    }

    @Override
    protected SocketAddress newSocketAddress() {
        return EpollSocketTestPermutation.newVSockAddress();
    }

    @Override
    protected void shutdownOutput(LinuxSocket s) throws IOException {
        s.shutdown(false, true);
    }

    @Override
    protected void connect(LinuxSocket s, SocketAddress address) throws IOException {
        bootstrap.handler(NOOP_HANDLER).connect(address).syncUninterruptibly().channel();
    }

    @Override
    protected void close(LinuxSocket s) throws IOException {
        s.close();
        vSockChannel.close().syncUninterruptibly().channel();
    }

    @Override
    protected void write(LinuxSocket s, int data) throws IOException {
        final CleanableDirectBuffer buf = Buffer.allocateDirectBufferWithNativeOrder(4);
        buf.buffer().putInt(data);
        buf.buffer().flip();
        s.send(buf.buffer(), buf.buffer().position(), buf.buffer().limit());
        buf.clean();
    }

    @Override
    protected LinuxSocket newSocket() {
        final LinuxSocket socket = LinuxSocket.newSocketVSock();
        vSockChannel = new EpollVSockChannel(null, socket);
        bootstrap = EpollSocketTestPermutation.INSTANCE.clientVSocket(vSockChannel)
                .get(0)
                .newInstance();
        return socket;
    }
}
