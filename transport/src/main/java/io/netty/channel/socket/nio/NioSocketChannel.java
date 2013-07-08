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
package io.netty.channel.socket.nio;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.FileRegion;
import io.netty.channel.nio.AbstractNioByteChannel;
import io.netty.channel.socket.DefaultSocketChannelConfig;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.util.ReferenceCounted;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * {@link io.netty.channel.socket.SocketChannel} which uses NIO selector based implementation.
 */
public class NioSocketChannel extends AbstractNioByteChannel implements io.netty.channel.socket.SocketChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioSocketChannel.class);

    // Buffers to use for Gathering writes
    private static final ThreadLocal<ByteBuffer[]> BUFFERS = new ThreadLocal<ByteBuffer[]>() {
        @Override
        protected ByteBuffer[] initialValue() {
            return new ByteBuffer[128];
        }
    };

    private static ByteBuffer[] getNioBufferArray() {
        return BUFFERS.get();
    }

    private static ByteBuffer[] doubleNioBufferArray(ByteBuffer[] array, int size) {
        ByteBuffer[] newArray = new ByteBuffer[array.length << 1];
        System.arraycopy(array, 0, newArray, 0, size);
        BUFFERS.set(newArray);
        return newArray;
    }

    private static SocketChannel newSocket() {
        try {
            return SocketChannel.open();
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }

    private final SocketChannelConfig config;

    /**
     * Create a new instance
     */
    public NioSocketChannel() {
        this(newSocket());
    }

    /**
     * Create a new instance using the given {@link SocketChannel}.
     */
    public NioSocketChannel(SocketChannel socket) {
        this(null, socket);
    }

    /**
     * Create a new instance
     *
     * @param parent    the {@link Channel} which created this instance or {@code null} if it was created by the user
     * @param socket    the {@link SocketChannel} which will be used
     */
    public NioSocketChannel(Channel parent, SocketChannel socket) {
        super(parent, socket);
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

        config = new DefaultSocketChannelConfig(this, socket.socket());
    }

    @Override
    public ServerSocketChannel parent() {
        return (ServerSocketChannel) super.parent();
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
    protected SocketChannel javaChannel() {
        return (SocketChannel) super.javaChannel();
    }

    @Override
    public boolean isActive() {
        SocketChannel ch = javaChannel();
        return ch.isOpen() && ch.isConnected();
    }

    @Override
    public boolean isInputShutdown() {
        return super.isInputShutdown();
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    public boolean isOutputShutdown() {
        return javaChannel().socket().isOutputShutdown() || !isActive();
    }

    @Override
    public ChannelFuture shutdownOutput() {
        return shutdownOutput(newPromise());
    }

    @Override
    public ChannelFuture shutdownOutput(final ChannelPromise promise) {
        EventLoop loop = eventLoop();
        if (loop.inEventLoop()) {
            try {
                javaChannel().socket().shutdownOutput();
                promise.setSuccess();
            } catch (Throwable t) {
                promise.setFailure(t);
            }
        } else {
            loop.execute(new Runnable() {
                @Override
                public void run() {
                    shutdownOutput(promise);
                }
            });
        }
        return promise;
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
            if (!connected) {
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
    protected int doReadBytes(ByteBuf byteBuf) throws Exception {
        return byteBuf.writeBytes(javaChannel(), byteBuf.writableBytes());
    }

    @Override
    protected int doWriteBytes(ByteBuf buf, boolean lastSpin) throws Exception {
        final int expectedWrittenBytes = buf.readableBytes();
        final int writtenBytes = buf.readBytes(javaChannel(), expectedWrittenBytes);
        updateOpWrite(expectedWrittenBytes, writtenBytes, lastSpin);
        return writtenBytes;
    }

    @Override
    protected long doWriteFileRegion(FileRegion region, boolean lastSpin) throws Exception {
        final long position = region.transfered();
        final long expectedWrittenBytes = region.count() - position;
        final long writtenBytes = region.transferTo(javaChannel(), position);
        updateOpWrite(expectedWrittenBytes, writtenBytes, lastSpin);
        return writtenBytes;
    }

    @Override
    protected int doWrite(Object[] msgs, int msgsLength, final int startIndex) throws Exception {
        // Do non-gathering write for a single buffer case.
        if (msgsLength <= 1) {
            return super.doWrite(msgs, msgsLength, startIndex);
        }

        ByteBuffer[] nioBuffers = getNioBufferArray();
        int nioBufferCnt = 0;
        long expectedWrittenBytes = 0;
        for (int i = startIndex; i < msgsLength; i++) {
            Object m = msgs[i];
            if (!(m instanceof ByteBuf)) {
                return super.doWrite(msgs, msgsLength, startIndex);
            }

            ByteBuf buf = (ByteBuf) m;

            int readerIndex = buf.readerIndex();
            int readableBytes = buf.readableBytes();
            expectedWrittenBytes += readableBytes;

            if (buf.isDirect()) {
                int count = buf.nioBufferCount();
                if (count == 1) {
                    if (nioBufferCnt == nioBuffers.length) {
                        nioBuffers = doubleNioBufferArray(nioBuffers, nioBufferCnt);
                    }
                    nioBuffers[nioBufferCnt ++] = buf.internalNioBuffer(readerIndex, readableBytes);
                } else {
                    ByteBuffer[] nioBufs = buf.nioBuffers();
                    if (nioBufferCnt + nioBufs.length == nioBuffers.length + 1) {
                        nioBuffers = doubleNioBufferArray(nioBuffers, nioBufferCnt);
                    }
                    for (ByteBuffer nioBuf: nioBufs) {
                        if (nioBuf == null) {
                            break;
                        }
                        nioBuffers[nioBufferCnt ++] = nioBuf;
                    }
                }
            } else {
                ByteBuf directBuf = alloc().directBuffer(readableBytes);
                directBuf.writeBytes(buf, readerIndex, readableBytes);
                buf.release();
                msgs[i] = directBuf;
                if (nioBufferCnt == nioBuffers.length) {
                    nioBuffers = doubleNioBufferArray(nioBuffers, nioBufferCnt);
                }
                nioBuffers[nioBufferCnt ++] = directBuf.internalNioBuffer(0, readableBytes);
            }
        }

        final SocketChannel ch = javaChannel();
        long writtenBytes = 0;
        boolean done = false;
        for (int i = config().getWriteSpinCount() - 1; i >= 0; i --) {
            final long localWrittenBytes = ch.write(nioBuffers, 0, nioBufferCnt);
            updateOpWrite(expectedWrittenBytes, localWrittenBytes, i == 0);
            if (localWrittenBytes == 0) {
                break;
            }
            expectedWrittenBytes -= localWrittenBytes;
            writtenBytes += localWrittenBytes;
            if (expectedWrittenBytes == 0) {
                done = true;
                break;
            }
        }

        if (done) {
            // release buffers
            for (int i = startIndex; i < msgsLength; i++) {
                ((ReferenceCounted) msgs[i]).release();
            }
            return msgsLength - startIndex;
        } else {
            // Did not write all buffers completely.
            // Release the fully written buffers and update the indexes of the partially written buffer.
            int writtenBufs = 0;
            for (int i = startIndex; i < msgsLength; i++) {
                final ByteBuf buf = (ByteBuf) msgs[i];
                final int readerIndex = buf.readerIndex();
                final int readableBytes = buf.writerIndex() - readerIndex;

                if (readableBytes < writtenBytes) {
                    writtenBufs ++;
                    buf.release();
                    writtenBytes -= readableBytes;
                } else if (readableBytes > writtenBytes) {
                    buf.readerIndex(readerIndex + (int) writtenBytes);
                    break;
                } else { // readable == writtenBytes
                    writtenBufs ++;
                    buf.release();
                    break;
                }
            }
            return writtenBufs;
        }
    }
}
