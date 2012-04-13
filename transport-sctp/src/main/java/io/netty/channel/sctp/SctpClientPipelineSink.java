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
import static io.netty.channel.Channels.succeededFuture;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelState;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.MessageEvent;
import io.netty.channel.socket.nio.AbstractNioChannelSink;

import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

/**
 */
class SctpClientPipelineSink extends AbstractNioChannelSink {

    @Override
    public void eventSunk(
            ChannelPipeline pipeline, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent event = (ChannelStateEvent) e;
            SctpClientChannel channel =
                (SctpClientChannel) event.getChannel();
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
                break;
            case CONNECTED:
                if (value != null) {
                    connect(channel, future, (SocketAddress) value);
                } else {
                    channel.getWorker().close(channel, future);
                }
                break;
            case INTEREST_OPS:
                if (event instanceof SctpBindAddressEvent) {
                   SctpBindAddressEvent bindAddressEvent = (SctpBindAddressEvent) event;
                   bindAddress(channel, bindAddressEvent.getFuture(), bindAddressEvent.getValue());
                } else if (event instanceof SctpUnbindAddressEvent) {
                    SctpUnbindAddressEvent unbindAddressEvent = (SctpUnbindAddressEvent) event;
                    unbindAddress(channel, unbindAddressEvent.getFuture(), unbindAddressEvent.getValue());
                } else {
                    channel.getWorker().setInterestOps(channel, future, ((Integer) value).intValue());
                }
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
            SctpClientChannel channel, ChannelFuture future,
            SocketAddress localAddress) {
        try {
            channel.getJdkChannel().bind(localAddress);
            channel.boundManually = true;
            channel.setBound();
            future.setSuccess();
            fireChannelBound(channel, channel.getLocalAddress());
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    private void bindAddress(
            SctpClientChannel channel, ChannelFuture future,
            InetAddress localAddress) {
        try {
            channel.getJdkChannel().getChannel().bindAddress(localAddress);
            future.setSuccess();
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    private void unbindAddress(
            SctpClientChannel channel, ChannelFuture future,
            InetAddress localAddress) {
        try {
            channel.getJdkChannel().getChannel().unbindAddress(localAddress);
            future.setSuccess();
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }



    private void connect(
            final SctpClientChannel channel, final ChannelFuture cf,
            SocketAddress remoteAddress) {
        try {
            channel.getJdkChannel().connect(remoteAddress);

            channel.getCloseFuture().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture f)
                        throws Exception {
                    if (!cf.isDone()) {
                        cf.setFailure(new ClosedChannelException());
                    }
                }
            });
            cf.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            channel.connectFuture = cf;
            channel.getWorker().registerWithWorker(channel, cf);
            

        } catch (Throwable t) {
            cf.setFailure(t);
            fireExceptionCaught(channel, t);
            channel.getWorker().close(channel, succeededFuture(channel));
        }
    }

   
}
