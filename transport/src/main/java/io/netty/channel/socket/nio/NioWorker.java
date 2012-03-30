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

import static io.netty.channel.Channels.fireChannelBound;
import static io.netty.channel.Channels.fireChannelConnected;
import static io.netty.channel.Channels.fireExceptionCaught;
import static io.netty.channel.Channels.fireMessageReceived;
import static io.netty.channel.Channels.succeededFuture;
import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBufferFactory;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ReceiveBufferSizePredictor;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;

public class NioWorker extends AbstractNioWorker {

    private final SocketReceiveBufferPool recvBufferPool = new SocketReceiveBufferPool();

    public NioWorker(Executor executor) {
        super(executor);
    }
    
    public NioWorker(Executor executor, boolean allowShutdownOnIdle) {
        super(executor, allowShutdownOnIdle);
    }


    @Override
    protected boolean read(SelectionKey k) {
        final SocketChannel ch = (SocketChannel) k.channel();
        final NioSocketChannel channel = (NioSocketChannel) k.attachment();

        final ReceiveBufferSizePredictor predictor =
            channel.getConfig().getReceiveBufferSizePredictor();
        final int predictedRecvBufSize = predictor.nextReceiveBufferSize();

        int ret = 0;
        int readBytes = 0;
        boolean failure = true;

        ByteBuffer bb = recvBufferPool.acquire(predictedRecvBufSize);
        try {
            while ((ret = ch.read(bb)) > 0) {
                readBytes += ret;
                if (!bb.hasRemaining()) {
                    break;
                }
            }
            failure = false;
        } catch (ClosedChannelException e) {
            // Can happen, and does not need a user attention.
        } catch (Throwable t) {
            fireExceptionCaught(channel, t);
        }

        if (readBytes > 0) {
            bb.flip();

            final ChannelBufferFactory bufferFactory =
                channel.getConfig().getBufferFactory();
            final ChannelBuffer buffer = bufferFactory.getBuffer(readBytes);
            buffer.setBytes(0, bb);
            buffer.writerIndex(readBytes);

            recvBufferPool.release(bb);

            // Update the predictor.
            predictor.previousReceiveBufferSize(readBytes);

            // Fire the event.
            fireMessageReceived(channel, buffer);
        } else {
            recvBufferPool.release(bb);
        }

        if (ret < 0 || failure) {
            k.cancel(); // Some JDK implementations run into an infinite loop without this.
            close(channel, succeededFuture(channel));
            return false;
        }

        return true;
    }


    @Override
    protected void registerTask(AbstractNioChannel channel, ChannelFuture future) {
        boolean server = !(channel instanceof NioClientSocketChannel);
        SocketAddress localAddress = channel.getLocalAddress();
        SocketAddress remoteAddress = channel.getRemoteAddress();

        if (localAddress == null || remoteAddress == null) {
            if (future != null) {
                future.setFailure(new ClosedChannelException());
            }
            close(channel, succeededFuture(channel));
            return;
        }

        try {
            if (server) {
                channel.getJdkChannel().configureBlocking(false);
            }
            
            boolean registered = channel.getJdkChannel().isRegistered();
            if (!registered) {
                synchronized (channel.interestOpsLock) {
                    channel.getJdkChannel().register(
                            selector, channel.getRawInterestOps(), channel);
                }
                
            } else {
                setInterestOps(channel, succeededFuture(channel), channel.getRawInterestOps());
            }
            if (future != null) {

                ((NioSocketChannel) channel).setConnected();
                future.setSuccess();
            }
            
        } catch (IOException e) {
            if (future != null) {
                future.setFailure(e);
            }
            close(channel, succeededFuture(channel));
            if (!(e instanceof ClosedChannelException)) {
                throw new ChannelException(
                        "Failed to register a socket to the selector.", e);
            }
        }

        if (server || !((NioClientSocketChannel) channel).boundManually) {
            fireChannelBound(channel, localAddress);
        }
        fireChannelConnected(channel, remoteAddress);
    }

}
