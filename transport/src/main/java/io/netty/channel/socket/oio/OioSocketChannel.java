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
package io.netty.channel.socket.oio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ChannelBufType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.socket.DefaultSocketChannelConfig;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.NotYetConnectedException;

public class OioSocketChannel extends AbstractOioByteChannel
                              implements SocketChannel {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(OioSocketChannel.class);

    private static final ChannelMetadata METADATA = new ChannelMetadata(ChannelBufType.BYTE, false);

    private final Socket socket;
    private final SocketChannelConfig config;
    private InputStream is;
    private OutputStream os;
    private volatile boolean suspendRead;

    public OioSocketChannel() {
        this(new Socket());
    }

    public OioSocketChannel(Socket socket) {
        this(null, null, socket);
    }

    public OioSocketChannel(Channel parent, Integer id, Socket socket) {
        super(parent, id);
        this.socket = socket;
        config = new DefaultSocketChannelConfig(socket);

        boolean success = false;
        try {
            if (socket.isConnected()) {
                is = socket.getInputStream();
                os = socket.getOutputStream();
            }
            socket.setSoTimeout(SO_TIMEOUT);
            success = true;
        } catch (Exception e) {
            throw new ChannelException("failed to initialize a socket", e);
        } finally {
            if (!success) {
                try {
                    socket.close();
                } catch (IOException e) {
                    logger.warn("Failed to close a socket.", e);
                }
            }
        }
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public SocketChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return !socket.isClosed();
    }

    @Override
    public boolean isActive() {
        return !socket.isClosed() && socket.isConnected();
    }

    @Override
    protected SocketAddress localAddress0() {
        return socket.getLocalSocketAddress();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return socket.getRemoteSocketAddress();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        socket.bind(localAddress);
    }

    @Override
    protected void doConnect(SocketAddress remoteAddress,
            SocketAddress localAddress) throws Exception {
        if (localAddress != null) {
            socket.bind(localAddress);
        }

        boolean success = false;
        try {
            socket.connect(remoteAddress, config().getConnectTimeoutMillis());
            is = socket.getInputStream();
            os = socket.getOutputStream();
            success = true;
        } finally {
            if (!success) {
                doClose();
            }
        }
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doClose() throws Exception {
        socket.close();
    }

    @Override
    protected int available() {
        try {
            return is.available();
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    protected int doReadBytes(ByteBuf buf) throws Exception {
        if (socket.isClosed()) {
            return -1;
        }

        if (suspendRead) {
            try {
                Thread.sleep(SO_TIMEOUT);
            } catch (InterruptedException e) {
                // ignore
            }
            return 0;
        }

        try {
            int read = buf.writeBytes(is, buf.writableBytes());
            if (read > 0 && !suspendRead) {
                return read;
            } else {
                // so the read bytes were 0 or the read was suspend
                return 0;
            }
        } catch (SocketTimeoutException e) {
            return 0;
        }
    }

    @Override
    protected void doWriteBytes(ByteBuf buf) throws Exception {
        OutputStream os = this.os;
        if (os == null) {
            throw new NotYetConnectedException();
        }
        buf.readBytes(os, buf.readableBytes());
    }


    @Override
    protected OioByteUnsafe newUnsafe() {
        return new OioSocketChannelUnsafe();
    }

    private final class OioSocketChannelUnsafe extends OioByteUnsafe {

        @Override
        public void suspendRead() {
            suspendRead = true;
        }

        @Override
        public void resumeRead() {
            suspendRead = false;
        }
    }
}
