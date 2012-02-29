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

import static io.netty.channel.Channels.*;

import java.net.SocketAddress;
import java.util.concurrent.Executor;

import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelState;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.MessageEvent;
import io.netty.util.internal.DeadLockProofWorker;

class OioDatagramPipelineSink extends AbstractOioChannelSink {

    private final Executor workerExecutor;

    OioDatagramPipelineSink(Executor workerExecutor) {
        this.workerExecutor = workerExecutor;
    }

    @Override
    public void eventSunk(
            ChannelPipeline pipeline, ChannelEvent e) throws Exception {
        OioDatagramChannel channel = (OioDatagramChannel) e.getChannel();
        ChannelFuture future = e.getFuture();
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent stateEvent = (ChannelStateEvent) e;
            ChannelState state = stateEvent.getState();
            Object value = stateEvent.getValue();
            switch (state) {
            case OPEN:
                if (Boolean.FALSE.equals(value)) {
                    OioDatagramWorker.close(channel, future);
                }
                break;
            case BOUND:
                if (value != null) {
                    bind(channel, future, (SocketAddress) value);
                } else {
                    OioDatagramWorker.close(channel, future);
                }
                break;
            case CONNECTED:
                if (value != null) {
                    connect(channel, future, (SocketAddress) value);
                } else {
                    OioDatagramWorker.disconnect(channel, future);
                }
                break;
            case INTEREST_OPS:
                OioDatagramWorker.setInterestOps(channel, future, ((Integer) value).intValue());
                break;
            }
        } else if (e instanceof MessageEvent) {
            MessageEvent evt = (MessageEvent) e;
            OioDatagramWorker.write(
                    channel, future, evt.getMessage(), evt.getRemoteAddress());
        }
    }

    private void bind(
            OioDatagramChannel channel, ChannelFuture future,
            SocketAddress localAddress) {
        boolean bound = false;
        boolean workerStarted = false;
        try {
            channel.socket.bind(localAddress);
            bound = true;

            // Fire events
            future.setSuccess();
            fireChannelBound(channel, channel.getLocalAddress());

            // Start the business.
            DeadLockProofWorker.start(
                    workerExecutor,
                    new OioDatagramWorker(channel));
            workerStarted = true;
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        } finally {
            if (bound && !workerStarted) {
                OioDatagramWorker.close(channel, future);
            }
        }
    }

    private void connect(
            OioDatagramChannel channel, ChannelFuture future,
            SocketAddress remoteAddress) {

        boolean bound = channel.isBound();
        boolean connected = false;
        boolean workerStarted = false;

        future.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);

        // Clear the cached address so that the next getRemoteAddress() call
        // updates the cache.
        channel.remoteAddress = null;

        try {
            channel.socket.connect(remoteAddress);
            connected = true;

            // Fire events.
            future.setSuccess();
            if (!bound) {
                fireChannelBound(channel, channel.getLocalAddress());
            }
            fireChannelConnected(channel, channel.getRemoteAddress());

            if (!bound) {
                // Start the business.
                DeadLockProofWorker.start(
                        workerExecutor,
                        new OioDatagramWorker(channel));
            } else {
                // Worker started by bind() - nothing to do.
            }

            workerStarted = true;
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        } finally {
            if (connected && !workerStarted) {
                OioDatagramWorker.close(channel, future);
            }
        }
    }
}
