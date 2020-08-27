/*
 * Copyright 2020 The Netty Project
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
package io.netty.channel.uring;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.DuplexChannel;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;
import java.io.IOException;
import java.util.concurrent.Executor;

abstract class AbstractIOUringStreamChannel extends AbstractIOUringChannel implements DuplexChannel {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractIOUringStreamChannel.class);

    AbstractIOUringStreamChannel(Channel parent, LinuxSocket socket) {
        super(parent, socket);
    }

    protected AbstractIOUringStreamChannel(Channel parent, LinuxSocket socket, boolean active) {
        super(parent, socket, active);
    }

    AbstractIOUringStreamChannel(Channel parent, LinuxSocket fd, SocketAddress remote) {
        super(parent, fd, remote);
        // Add EPOLLRDHUP so we are notified once the remote peer close the connection.
    }

    @Override
    public ChannelFuture shutdown() {
        System.out.println("AbstractStreamChannel shutdown");
        return shutdown(newPromise());
    }

    @Override
    public ChannelFuture shutdown(final ChannelPromise promise) {

        ChannelFuture shutdownOutputFuture = shutdownOutput();
        if (shutdownOutputFuture.isDone()) {
            shutdownOutputDone(shutdownOutputFuture, promise);
        } else {
            shutdownOutputFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture shutdownOutputFuture) throws Exception {
                    shutdownOutputDone(shutdownOutputFuture, promise);
                }
            });
        }
        return promise;
    }

    @UnstableApi
    @Override
    protected final void doShutdownOutput() throws Exception {
        socket.shutdown(false, true);
    }

    private void shutdownInput0(final ChannelPromise promise) {
        try {
            socket.shutdown(true, false);
            promise.setSuccess();
        } catch (Throwable cause) {
            promise.setFailure(cause);
        }
    }

    @Override
    public boolean isOutputShutdown() {
        return socket.isOutputShutdown();
    }

    @Override
    public boolean isInputShutdown() {
        return socket.isInputShutdown();
    }

    @Override
    public boolean isShutdown() {
        return socket.isShutdown();
    }

    @Override
    public ChannelFuture shutdownOutput() {
        return shutdownOutput(newPromise());
    }

    @Override
    public ChannelFuture shutdownOutput(final ChannelPromise promise) {
        EventLoop loop = eventLoop();
        if (loop.inEventLoop()) {
            ((AbstractUnsafe) unsafe()).shutdownOutput(promise);
        } else {
            loop.execute(new Runnable() {
                @Override
                public void run() {
                    ((AbstractUnsafe) unsafe()).shutdownOutput(promise);
                }
            });
        }

        return promise;
    }

    @Override
    public ChannelFuture shutdownInput() {
        return shutdownInput(newPromise());
    }

    @Override
    public ChannelFuture shutdownInput(final ChannelPromise promise) {
        Executor closeExecutor = ((IOUringStreamUnsafe) unsafe()).prepareToClose();
        if (closeExecutor != null) {
            closeExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    shutdownInput0(promise);
                }
            });
        } else {
            EventLoop loop = eventLoop();
            if (loop.inEventLoop()) {
                shutdownInput0(promise);
            } else {
                loop.execute(new Runnable() {
                    @Override
                    public void run() {
                        shutdownInput0(promise);
                    }
                });
            }
        }
        return promise;
    }

    private void shutdownOutputDone(final ChannelFuture shutdownOutputFuture, final ChannelPromise promise) {
        ChannelFuture shutdownInputFuture = shutdownInput();
        if (shutdownInputFuture.isDone()) {
            shutdownDone(shutdownOutputFuture, shutdownInputFuture, promise);
        } else {
            shutdownInputFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture shutdownInputFuture) throws Exception {
                    shutdownDone(shutdownOutputFuture, shutdownInputFuture, promise);
                }
            });
        }
    }

    private static void shutdownDone(ChannelFuture shutdownOutputFuture,
                                     ChannelFuture shutdownInputFuture,
                                     ChannelPromise promise) {
        System.out.println("AbstractStreamChannel ShutdownDone");
        Throwable shutdownOutputCause = shutdownOutputFuture.cause();
        Throwable shutdownInputCause = shutdownInputFuture.cause();
        if (shutdownOutputCause != null) {
            if (shutdownInputCause != null) {
                logger.info("Exception suppressed because a previous exception occurred.",
                             shutdownInputCause);
            }
            promise.setFailure(shutdownOutputCause);
        } else if (shutdownInputCause != null) {
            promise.setFailure(shutdownInputCause);
        } else {
            promise.setSuccess();
        }
    }

    class IOUringStreamUnsafe extends AbstractUringUnsafe {

        // Overridden here just to be able to access this method from AbstractEpollStreamChannel
        @Override
        protected Executor prepareToClose() {
            return super.prepareToClose();
        }

        @Override
        void pollIn(int res) {
            final ChannelConfig config = config();

            final ByteBufAllocator allocator = config.getAllocator();
            final RecvByteBufAllocator.Handle allocHandle = recvBufAllocHandle();
            allocHandle.reset(config);

            ByteBuf byteBuf = allocHandle.allocate(allocator);
            doReadBytes(byteBuf);
        }

        private ByteBuf readBuffer;

        public void doReadBytes(ByteBuf byteBuf) {
            assert readBuffer == null;
            IOUringEventLoop ioUringEventLoop = (IOUringEventLoop) eventLoop();
            IOUringSubmissionQueue submissionQueue = ioUringEventLoop.getRingBuffer().getIoUringSubmissionQueue();

            unsafe().recvBufAllocHandle().attemptedBytesRead(byteBuf.writableBytes());

            if (byteBuf.hasMemoryAddress()) {
                readBuffer = byteBuf;
                submissionQueue.addRead(socket.intValue(), byteBuf.memoryAddress(),
                        byteBuf.writerIndex(), byteBuf.capacity());
                submissionQueue.submit();
            }
        }

        void readComplete0(int localReadAmount) {
            boolean close = false;
            ByteBuf byteBuf = null;
            final IOUringRecvByteAllocatorHandle allocHandle =
                    (IOUringRecvByteAllocatorHandle) unsafe()
                            .recvBufAllocHandle();
            final ChannelPipeline pipeline = pipeline();
            try {
                logger.trace("EventLoop Read Res: {}", localReadAmount);
                logger.trace("EventLoop Fd: {}", fd().intValue());
                byteBuf = this.readBuffer;
                this.readBuffer = null;

                if (localReadAmount > 0) {
                    byteBuf.writerIndex(byteBuf.writerIndex() + localReadAmount);
                }

                allocHandle.lastBytesRead(localReadAmount);
                if (allocHandle.lastBytesRead() <= 0) {
                    // nothing was read, release the buffer.
                    byteBuf.release();
                    byteBuf = null;
                    close = allocHandle.lastBytesRead() < 0;
                    if (close) {
                        // There is nothing left to read as we received an EOF.
                        shutdownInput(false);
                    }
                    allocHandle.readComplete();
                    pipeline.fireChannelReadComplete();
                    return;
                }

                allocHandle.incMessagesRead(1);
                pipeline.fireChannelRead(byteBuf);
                byteBuf = null;
                allocHandle.readComplete();
                pipeline.fireChannelReadComplete();
            } catch (Throwable t) {
                handleReadException(pipeline, byteBuf, t, close, allocHandle);
            }
        }

        private void handleReadException(ChannelPipeline pipeline, ByteBuf byteBuf,
                                         Throwable cause, boolean close,
                                         IOUringRecvByteAllocatorHandle allocHandle) {
            if (byteBuf != null) {
                if (byteBuf.isReadable()) {
                    pipeline.fireChannelRead(byteBuf);
                } else {
                    byteBuf.release();
                }
            }
            allocHandle.readComplete();
            pipeline.fireChannelReadComplete();
            pipeline.fireExceptionCaught(cause);
            if (close || cause instanceof IOException) {
                shutdownInput(false);
            }
        }
    }
}
