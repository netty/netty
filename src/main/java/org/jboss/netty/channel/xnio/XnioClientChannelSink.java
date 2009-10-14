/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.xnio;

import static org.jboss.netty.channel.Channels.*;

import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.GatheringByteChannel;

import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.xnio.FutureConnection;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoFuture.Notifier;
import org.jboss.xnio.channels.MultipointWritableMessageChannel;
import org.jboss.xnio.channels.SuspendableReadChannel;
import org.jboss.xnio.channels.SuspendableWriteChannel;
import org.jboss.xnio.channels.WritableMessageChannel;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 */
final class XnioClientChannelSink extends AbstractChannelSink {

    private static final XnioClientChannelHandler HANDLER = new XnioClientChannelHandler();

    XnioClientChannelSink() {
        super();
    }

    @SuppressWarnings("unchecked")
    public void eventSunk(
            ChannelPipeline pipeline, ChannelEvent e) throws Exception {
        BaseXnioChannel channel = (BaseXnioChannel) e.getChannel();
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent event = (ChannelStateEvent) e;
            ChannelFuture future = event.getFuture();
            ChannelState state = event.getState();
            Object value = event.getValue();

            switch (state) {
            case OPEN:
                if (Boolean.FALSE.equals(value)) {
                    channel.closeNow(future);
                }
                break;
            case BOUND:
            case CONNECTED:
                if (value != null) {
                    if (channel instanceof XnioClientChannel) {
                        final XnioClientChannel cc = (XnioClientChannel) channel;
                        synchronized (cc.connectLock) {
                            if (cc.connecting) {
                                Exception cause = new ConnectionPendingException();
                                future.setFailure(cause);
                                fireExceptionCaught(channel, cause);
                            } else {
                                cc.connecting = true;
                                java.nio.channels.Channel xnioChannel = cc.xnioChannel;
                                if (xnioChannel == null) {
                                    FutureConnection fc =
                                        cc.xnioConnector.connectTo(value, HANDLER);
                                    fc.addNotifier(new FutureConnectionNotifier(cc), future);
                                } else {
                                    Exception cause = new AlreadyConnectedException();
                                    future.setFailure(cause);
                                    fireExceptionCaught(cc, cause);
                                }
                            }
                        }
                    } else {
                        Exception cause = new UnsupportedOperationException();
                        future.setFailure(cause);
                        fireExceptionCaught(channel, cause);
                    }
                } else {
                    channel.closeNow(future);
                }
                break;
            case INTEREST_OPS:
                int interestOps = ((Integer) value).intValue();
                java.nio.channels.Channel xnioChannel = channel.xnioChannel;
                if (xnioChannel instanceof SuspendableReadChannel) {
                    if ((interestOps & Channel.OP_READ) == 0) {
                        ((SuspendableReadChannel) xnioChannel).suspendReads();
                        channel.setRawInterestOpsNow(Channel.OP_NONE);
                    } else {
                        ((SuspendableReadChannel) xnioChannel).resumeReads();
                        channel.setRawInterestOpsNow(Channel.OP_READ);
                    }
                }
                e.getFuture().setSuccess();
                break;
            }
        } else if (e instanceof MessageEvent) {
            MessageEvent event = (MessageEvent) e;
            java.nio.channels.Channel xnioChannel = channel.xnioChannel;
            if (xnioChannel instanceof GatheringByteChannel ||
                xnioChannel instanceof MultipointWritableMessageChannel ||
                xnioChannel instanceof WritableMessageChannel) {
                boolean offered = channel.writeBuffer.offer(event);
                assert offered;
                if (xnioChannel instanceof SuspendableWriteChannel) {
                    ((SuspendableWriteChannel) xnioChannel).resumeWrites();
                }
            } else {
                event.getFuture().setFailure(new IllegalStateException());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static final class FutureConnectionNotifier implements Notifier {

        private final XnioClientChannel cc;

        FutureConnectionNotifier(XnioClientChannel cc) {
            this.cc = cc;
        }

        public void notify(IoFuture future, Object attachment) {
            ChannelFuture cf = (ChannelFuture) attachment;
            try {
                java.nio.channels.Channel xnioChannel = (java.nio.channels.Channel) future.get();
                cc.xnioChannel = xnioChannel;
                XnioChannelRegistry.registerChannelMapping(cc);
                cf.setSuccess();
            } catch (Throwable t) {
                cf.setFailure(t);
                fireExceptionCaught(cc, t);
            } finally {
                cc.connecting = false;
            }
        }
    }
}
