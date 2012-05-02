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
import io.netty.channel.ChannelFuture;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class NioSocketChannel extends AbstractNioChannel implements io.netty.channel.socket.SocketChannel {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioSocketChannel.class);

    private final NioSocketChannelConfig config;
    private final ChannelBufferHolder<?> out = ChannelBufferHolders.byteBuffer(ChannelBuffers.dynamicBuffer());

    private static SocketChannel newSocket() {
        SocketChannel socket;
        try {
            socket = SocketChannel.open();
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }

        boolean success = false;
        try {
            socket.configureBlocking(false);
            success = true;
        } catch (IOException e) {
            throw new ChannelException("Failed to enter non-blocking mode.", e);
        } finally {
            if (!success) {
                try {
                    socket.close();
                } catch (IOException e) {
                    if (logger.isWarnEnabled()) {
                        logger.warn(
                                "Failed to close a partially initialized socket.",
                                e);
                    }

                }
            }
        }

        return socket;
    }

    public NioSocketChannel(Channel parent) {
        this(parent, newSocket());
    }

    public NioSocketChannel(Channel parent, SocketChannel socket) {
        super(parent, socket);
        config = new DefaultNioSocketChannelConfig(socket.socket());
    }

    @Override
    public NioSocketChannelConfig config() {
        return config;
    }

    @Override
    protected SocketChannel javaChannel() {
        return (SocketChannel) super.javaChannel();
    }

    @Override
    public boolean isActive() {
        return javaChannel().isConnected();
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
    protected void doBind(SocketAddress localAddress, ChannelFuture future) {
        try {
            javaChannel().socket().bind(localAddress);
            future.setSuccess();
        } catch (Exception e) {
            future.setFailure(e);
        }
    }

    @Override
    protected void doConnect(SocketAddress remoteAddress,
            SocketAddress localAddress, ChannelFuture future) {
        if (localAddress != null) {
            try {
                javaChannel().socket().bind(localAddress);
            } catch (Exception e) {
                future.setFailure(e);
            }
        }

        try {
            if (javaChannel().connect(remoteAddress)) {
                future.setSuccess();
                pipeline().fireChannelActive();
            }
        } catch (Exception e) {
            future.setFailure(e);
            close(null);
        }
    }

    @Override
    protected void doFinishConnect(ChannelFuture future) {
        try {
            if (javaChannel().finishConnect()) {
                future.setSuccess();
                pipeline().fireChannelActive();
            }
        } catch (Exception e) {
            future.setFailure(e);
            close(null);
        }
    }

    @Override
    protected void doDisconnect(ChannelFuture future) {
        doClose(future);
    }

    @Override
    protected void doClose(ChannelFuture future) {
        try {
            javaChannel().close();
        } catch (Exception e) {
            logger.warn("Failed to close a channel.", e);
        }

        future.setSuccess();
        pipeline().fireChannelInactive();

        if (isRegistered()) {
            deregister(null);
        }
    }

    @Override
    protected void doDeregister(ChannelFuture future) {
        try {
            selectionKey().cancel();
            future.setSuccess();
            pipeline().fireChannelUnregistered();
        } catch (Exception e) {
            future.setFailure(e);
        }
    }

    @Override
    protected int doRead() {
        final SocketChannel ch = javaChannel();

        int ret = 0;
        int readBytes = 0;
        boolean failure = true;

        ChannelBuffer buf = pipeline().nextIn().byteBuffer();
        try {
            while ((ret = buf.writeBytes(ch, buf.writableBytes())) > 0) {
                readBytes += ret;
                if (!buf.writable()) {
                    break;
                }
            }
            failure = false;
        } catch (ClosedChannelException e) {
            // Can happen, and does not need a user attention.
        } catch (Throwable t) {
            pipeline().fireExceptionCaught(t);
        }

        if (readBytes > 0) {
            pipeline().fireInboundBufferUpdated();
        }

        if (ret < 0 || failure) {
            selectionKey().cancel(); // Some JDK implementations run into an infinite loop without this.
            close(null);
            return -1;
        }

        return readBytes;
    }

    @Override
    protected int doFlush(ChannelFuture future) {
        boolean open = true;
        boolean addOpWrite = false;
        boolean removeOpWrite = false;

        final SocketChannel ch = javaChannel();
        final int writeSpinCount = config().getWriteSpinCount();
        final ChannelBuffer buf = unsafe().out().byteBuffer();
        int bytesLeft = buf.readableBytes();
        if (bytesLeft == 0) {
            future.setSuccess();
            return 0;
        }

        int readerIndex = buf.readerIndex();
        int localWrittenBytes = 0;
        int writtenBytes = 0;

        try {
            for (int i = writeSpinCount; i > 0; i --) {
                localWrittenBytes = buf.getBytes(readerIndex, ch, bytesLeft);
                if (localWrittenBytes > 0) {
                    bytesLeft -= localWrittenBytes;
                    if (bytesLeft <= 0) {
                        removeOpWrite = true;
                        future.setSuccess();
                        break;
                    }

                    readerIndex += localWrittenBytes;
                    writtenBytes += localWrittenBytes;
                } else {
                    addOpWrite = true;
                    break;
                }
            }
        } catch (AsynchronousCloseException e) {
            // Doesn't need a user attention - ignore.
        } catch (Throwable t) {
            future.setFailure(t);
            pipeline().fireExceptionCaught(t);
            if (t instanceof IOException) {
                open = false;
                close(null);
            }
        }

        if (open) {
            if (addOpWrite) {
                SelectionKey key = selectionKey();
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            } else if (removeOpWrite) {
                SelectionKey key = selectionKey();
                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
            }
        }

        return writtenBytes;
    }
}
