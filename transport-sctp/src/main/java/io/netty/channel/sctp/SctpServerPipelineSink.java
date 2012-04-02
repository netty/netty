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
package io.netty.channel.sctp;

import static io.netty.channel.Channels.fireChannelBound;
import static io.netty.channel.Channels.fireExceptionCaught;
import io.netty.channel.Channel;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelState;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.MessageEvent;
import io.netty.channel.socket.nio.AbstractNioChannelSink;
import io.netty.channel.socket.nio.WorkerPool;

import java.net.InetAddress;
import java.net.SocketAddress;

/**
 */
class SctpServerPipelineSink extends AbstractNioChannelSink {

    private final WorkerPool<SctpWorker> workerPool;

    SctpServerPipelineSink(WorkerPool<SctpWorker> workerPool) {
        this.workerPool = workerPool;
    }

    @Override
    public void eventSunk(
            ChannelPipeline pipeline, ChannelEvent e) throws Exception {
        Channel channel = e.getChannel();
        if (channel instanceof SctpServerChannelImpl) {
            handleServerSocket(e);
        } else if (channel instanceof SctpChannelImpl) {
            handleAcceptedSocket(e);
        }
    }

    private void handleServerSocket(ChannelEvent e) {
        if (!(e instanceof ChannelStateEvent)) {
            return;
        }

        ChannelStateEvent event = (ChannelStateEvent) e;
        SctpServerChannelImpl channel =
            (SctpServerChannelImpl) event.getChannel();
        ChannelFuture future = event.getFuture();
        ChannelState state = event.getState();
        Object value = event.getValue();

        switch (state) {
        case OPEN:
            if (Boolean.FALSE.equals(value)) {
                channel.getWorker().close(channel, future);
            }
            break;
        case BOUND:
            if (value != null) {
                bind(channel, future, (SocketAddress) value);
            } else {
                channel.getWorker().close(channel, future);
            }
        case INTEREST_OPS:
            if (event instanceof SctpBindAddressEvent) {
                SctpBindAddressEvent bindAddressEvent = (SctpBindAddressEvent) event;
                bindAddress(channel, bindAddressEvent.getFuture(), bindAddressEvent.getValue());
            }

            if (event instanceof SctpUnbindAddressEvent) {
                SctpUnbindAddressEvent unbindAddressEvent = (SctpUnbindAddressEvent) event;
                unbindAddress(channel, unbindAddressEvent.getFuture(), unbindAddressEvent.getValue());
            }
            break;
        }
    }

    private void handleAcceptedSocket(ChannelEvent e) {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent event = (ChannelStateEvent) e;
            SctpChannelImpl channel = (SctpChannelImpl) event.getChannel();
            ChannelFuture future = event.getFuture();
            ChannelState state = event.getState();
            Object value = event.getValue();

            switch (state) {
            case OPEN:
                if (Boolean.FALSE.equals(value)) {
                    channel.getWorker().close(channel, future);
                }
                break;
            case BOUND:
            case CONNECTED:
                if (value == null) {
                    channel.getWorker().close(channel, future);
                }
                break;
            case INTEREST_OPS:
                channel.getWorker().setInterestOps(channel, future, (Integer) value);
                break;
            }
        } else if (e instanceof MessageEvent) {
            MessageEvent event = (MessageEvent) e;
            SctpChannelImpl channel = (SctpChannelImpl) event.getChannel();
            boolean offered = channel.getWriteBufferQueue().offer(event);
            assert offered;
            channel.getWorker().writeFromUserCode(channel);
        }
    }

    private void bind(
            SctpServerChannelImpl channel, ChannelFuture future,
            SocketAddress localAddress) {
        boolean bound = false;
        try {
            channel.serverChannel.bind(localAddress, channel.getConfig().getBacklog());
            bound = true;

            future.setSuccess();
            fireChannelBound(channel, channel.getLocalAddress());

            workerPool.nextWorker().registerWithWorker(channel, future);
            
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        } finally {
            if (!bound) {
                channel.getWorker().close(channel, future);
            }
        }
    }

    private void bindAddress(
            SctpServerChannelImpl channel, ChannelFuture future,
            InetAddress localAddress) {
        try {
            channel.serverChannel.bindAddress(localAddress);
            future.setSuccess();
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    private void unbindAddress(
            SctpServerChannelImpl channel, ChannelFuture future,
            InetAddress localAddress) {
        try {
            channel.serverChannel.unbindAddress(localAddress);
            future.setSuccess();
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    
}
