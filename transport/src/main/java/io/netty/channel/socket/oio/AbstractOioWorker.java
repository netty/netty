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
package io.netty.channel.socket.oio;

import static io.netty.channel.Channels.fireChannelClosed;
import static io.netty.channel.Channels.fireChannelDisconnected;
import static io.netty.channel.Channels.fireChannelInterestChanged;
import static io.netty.channel.Channels.fireChannelUnbound;
import static io.netty.channel.Channels.fireExceptionCaught;
import static io.netty.channel.Channels.succeededFuture;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.Channels;

import java.io.IOException;

/**
 * Abstract base class for Oio-Worker implementations
 *
 * @param <C> {@link AbstractOioChannel}
 */
abstract class AbstractOioWorker<C extends AbstractOioChannel> implements Runnable {

    protected final C channel;

    public AbstractOioWorker(C channel) {
        this.channel = channel;
    }

    @Override
    public void run() {
        channel.workerThread = Thread.currentThread();

        while (channel.isOpen()) {
            synchronized (channel.interestOpsLock) {
                while (!channel.isReadable()) {
                    try {
                        // notify() is not called at all.
                        // close() and setInterestOps() calls Thread.interrupt()
                        channel.interestOpsLock.wait();
                    } catch (InterruptedException e) {
                        if (!channel.isOpen()) {
                            break;
                        }
                    }
                }
            }
            
            try {
               if (!process()) {
                   break;
               }
            } catch (Throwable t) {
                if (!channel.isSocketClosed()) {
                    fireExceptionCaught(channel, t);
                }
                break;
            }
        }

        // Setting the workerThread to null will prevent any channel
        // operations from interrupting this thread from now on.
        channel.workerThread = null;

        // Clean up.
        close(channel, succeededFuture(channel));
    }
    
    /**
     * Process the incoming messages and also is responsible for call {@link Channels#fireMessageReceived(Channel, Object)} once a message
     * was processed without errors. 
     * 
     * @return continue returns <code>true</code> as long as this worker should continue to try processing incoming messages
     * @throws IOException
     */
    abstract boolean process() throws IOException;
    
    static void setInterestOps(
            AbstractOioChannel channel, ChannelFuture future, int interestOps) {

        // Override OP_WRITE flag - a user cannot change this flag.
        interestOps &= ~Channel.OP_WRITE;
        interestOps |= channel.getInterestOps() & Channel.OP_WRITE;

        boolean changed = false;
        try {
            if (channel.getInterestOps() != interestOps) {
                if ((interestOps & Channel.OP_READ) != 0) {
                    channel.setInterestOpsNow(Channel.OP_READ);
                } else {
                    channel.setInterestOpsNow(Channel.OP_NONE);
                }
                changed = true;
            }

            future.setSuccess();
            if (changed) {
                synchronized (channel.interestOpsLock) {
                    channel.setInterestOpsNow(interestOps);

                    // Notify the worker so it stops or continues reading.
                    Thread currentThread = Thread.currentThread();
                    Thread workerThread = channel.workerThread;
                    if (workerThread != null && currentThread != workerThread) {
                        workerThread.interrupt();
                    }
                }

                fireChannelInterestChanged(channel);
            }
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }
    
    static void close(AbstractOioChannel channel, ChannelFuture future) {
        boolean connected = channel.isConnected();
        boolean bound = channel.isBound();
        try {
            channel.closeSocket();
            if (channel.setClosed()) {
                future.setSuccess();
                if (connected) {
                    // Notify the worker so it stops reading.
                    Thread currentThread = Thread.currentThread();
                    Thread workerThread = channel.workerThread;
                    if (workerThread != null && currentThread != workerThread) {
                        workerThread.interrupt();
                    }
                    fireChannelDisconnected(channel);
                }
                if (bound) {
                    fireChannelUnbound(channel);
                }
                fireChannelClosed(channel);
            } else {
                future.setSuccess();
            }
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }
}
