/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.nio;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.Channel;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelException;
import io.netty.channel.socket.DefaultSocketChannelConfig;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class NioSocketChannel extends AbstractNioChannel implements io.netty.channel.socket.SocketChannel {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioSocketChannel.class);

    private final SocketChannelConfig config;
    private final ChannelBufferHolder<?> out = ChannelBufferHolders.byteBuffer(ChannelBuffers.dynamicBuffer());

    private static SocketChannel newSocket() {
        try {
            return SocketChannel.open();
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }

    public NioSocketChannel() {
        this(null, null, newSocket());
    }

    public NioSocketChannel(Channel parent, Integer id, SocketChannel socket) {
        super(parent, id, socket);
        try {
            socket.configureBlocking(false);
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException e2) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                            "Failed to close a partially initialized socket.", e2);
                }

            }

            throw new ChannelException("Failed to enter non-blocking mode.", e);
        }

        config = new DefaultSocketChannelConfig(socket.socket());
    }

    @Override
    public SocketChannelConfig config() {
        return config;
    }

    @Override
    protected SocketChannel javaChannel() {
        return (SocketChannel) super.javaChannel();
    }

    @Override
    public boolean isActive() {
        SocketChannel ch = javaChannel();
        return ch.isOpen() && ch.isConnected();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ChannelBufferHolder<Object> firstOut() {
        return (ChannelBufferHolder<Object>) out;
    }

    @Override
    protected SocketAddress localAddress0() {
        return javaChannel().socket().getLocalSocketAddress();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return javaChannel().socket().getRemoteSocketAddress();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        javaChannel().socket().bind(localAddress);
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (localAddress != null) {
            javaChannel().socket().bind(localAddress);
        }

        boolean success = false;
        try {
            boolean connected = javaChannel().connect(remoteAddress);
            if (connected) {
                selectionKey().interestOps(SelectionKey.OP_READ);
            } else {
                selectionKey().interestOps(SelectionKey.OP_CONNECT);
            }
            success = true;
            return connected;
        } finally {
            if (!success) {
                doClose();
            }
        }
    }

    @Override
    protected void doFinishConnect() throws Exception {
        if (!javaChannel().finishConnect()) {
            throw new Error();
        }
        selectionKey().interestOps(SelectionKey.OP_READ);
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doClose() throws Exception {
        javaChannel().close();
    }

    @Override
    protected void doDeregister() throws Exception {
        selectionKey().cancel();
        eventLoop().cancelledKeys ++;
    }

    @Override
    protected int doRead() throws Exception {
        ChannelBuffer buf = pipeline().nextIn().byteBuffer();
        return buf.writeBytes(javaChannel(), buf.writableBytes());
    }

    @Override
    protected int doFlush(boolean lastSpin) throws Exception {
        final ChannelBuffer buf = unsafe().out().byteBuffer();
        final int expectedWrittenBytes = buf.readableBytes();
        if (expectedWrittenBytes == 0) {
            return 0;
        }

        final int writtenBytes = buf.readBytes(javaChannel(), expectedWrittenBytes);

        final SelectionKey key = selectionKey();
        final int interestOps = key.interestOps();
        if (writtenBytes >= expectedWrittenBytes) {
            // Wrote the outbound buffer completely - clear OP_WRITE.
            if ((interestOps & SelectionKey.OP_WRITE) != 0) {
                key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
            }
        } else {
            // Wrote something or nothing.
            // a) If wrote something, the caller will not retry.
            //    - Set OP_WRITE so that the event loop calls flushForcibly() later.
            // b) If wrote nothing:
            //    1) If 'lastSpin' is false, the caller will call this method again real soon.
            //       - Do not update OP_WRITE.
            //    a) If 'lastSpin' is true, the caller will not retry.
            //       - Set OP_WRITE so that the event loop calls flushForcibly() later.
            if (writtenBytes > 0 || lastSpin) {
                if ((interestOps & SelectionKey.OP_WRITE) == 0) {
                    key.interestOps(interestOps | SelectionKey.OP_WRITE);
                }
            }
        }

        return writtenBytes;
    }
}
