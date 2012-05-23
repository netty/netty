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
import static io.netty.channel.Channels.fireExceptionCaughtLater;
import static io.netty.channel.Channels.fireMessageReceived;
import static io.netty.channel.Channels.succeededFuture;
import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBufferFactory;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ReceiveBufferSizePredictor;
import io.netty.channel.socket.SocketChannels;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;

public class NioWorker extends AbstractNioWorker {

    protected final ReceiveBufferPool recvBufferPool = new ReceiveBufferPool();

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
                    channel.getJdkChannel().register(selector, channel.getRawInterestOps(), channel);
                }
                
            } else {
                setInterestOps(channel, succeededFuture(channel), channel.getRawInterestOps());
            }
            if (future != null) {
                if (channel instanceof NioSocketChannel) {
                    ((NioSocketChannel) channel).setConnected();
                }
                future.setSuccess();
            }
            if (server || !((NioClientSocketChannel) channel).boundManually) {
                fireChannelBound(channel, localAddress);
            }
            fireChannelConnected(channel, remoteAddress);
            
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
    }
    
    void closeInput(NioSocketChannel channel, ChannelFuture future) {
        boolean isIoThread = isIoThread();
        
        boolean bound = channel.isBound();
        try {
            
            if (channel.getJdkChannel().isOpen()) {
                channel.getJdkChannel().getChannel().socket().shutdownInput();
                
                // remove the read OP
                int ops = channel.getRawInterestOps();
                ops &= ~SelectionKey.OP_READ;
                
                setInterestOps(channel, succeededFuture(channel), ops);
                
                if (selector != null) {
                    selector.wakeup();
                }
            }

            if (channel.setClosedInput()) {
                future.setSuccess();
                if (bound) {
                    if (isIoThread) {
                        SocketChannels.fireChannelInputClosed(channel);
                    } else {
                        SocketChannels.fireChannelInputClosedLater(channel);
                    }
                }
            } else {
                future.setSuccess();
            }
            
        } catch (Throwable t) {
            future.setFailure(t);
            if (isIoThread) {
                fireExceptionCaught(channel, t);
            } else {
                fireExceptionCaughtLater(channel, t);
                
            }
        }
    }

    @Override
    public void setInterestOps(AbstractNioChannel channel, ChannelFuture future, int interestOps) {
        if (channel instanceof NioSocketChannel) {
           
            NioSocketChannel ch = (NioSocketChannel) channel;
            if (!ch.isInputOpen()) {
                // remove the OP_READ from the channel if the input was closed
                interestOps &= ~SelectionKey.OP_READ;
            }
        }

        super.setInterestOps(channel, future, interestOps);
    }

    void closeOutput(NioSocketChannel channel, ChannelFuture future) {
        boolean isIoThread = isIoThread();
        
        boolean bound = channel.isBound();
        try {

            if (channel.getJdkChannel().isOpen()) {
                channel.getJdkChannel().getChannel().socket().shutdownOutput();
                
                // remove the read OP
                int ops = channel.getRawInterestOps();
                ops &= ~SelectionKey.OP_READ;
                setInterestOps(channel, future, ops);
                
                if (selector != null) {
                    selector.wakeup();
                }
            }

            if (channel.setClosedOutput()) {
                future.setSuccess();
                if (bound) {
                    if (isIoThread) {
                        SocketChannels.fireChannelOutputClosed(channel);
                    } else {
                        SocketChannels.fireChannelOutputClosedLater(channel);
                    }
                }
            } else {
                future.setSuccess();
            }
            
        } catch (Throwable t) {
            future.setFailure(t);
            if (isIoThread) {
                fireExceptionCaught(channel, t);
            } else {
                fireExceptionCaughtLater(channel, t);
                
            }
        }
    }
    

}
