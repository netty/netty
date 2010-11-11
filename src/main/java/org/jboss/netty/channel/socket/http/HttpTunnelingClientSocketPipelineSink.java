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
package org.jboss.netty.channel.socket.http;

import java.net.SocketAddress;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
final class HttpTunnelingClientSocketPipelineSink extends AbstractChannelSink {

    HttpTunnelingClientSocketPipelineSink() {
        super();
    }

    public void eventSunk(
            ChannelPipeline pipeline, ChannelEvent e) throws Exception {
        HttpTunnelingClientSocketChannel channel = (HttpTunnelingClientSocketChannel) e.getChannel();
        ChannelFuture future = e.getFuture();
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent stateEvent = (ChannelStateEvent) e;
            ChannelState state = stateEvent.getState();
            Object value = stateEvent.getValue();
            switch (state) {
            case OPEN:
                if (Boolean.FALSE.equals(value)) {
                    channel.closeReal(future);
                }
                break;
            case BOUND:
                if (value != null) {
                    channel.bindReal((SocketAddress) value, future);
                } else {
                    channel.unbindReal(future);
                }
                break;
            case CONNECTED:
                if (value != null) {
                    channel.connectReal((SocketAddress) value, future);
                } else {
                    channel.closeReal(future);
                }
                break;
            case INTEREST_OPS:
                channel.setInterestOpsReal(((Integer) value).intValue(), future);
                break;
            }
        } else if (e instanceof MessageEvent) {
            channel.writeReal(((ChannelBuffer) ((MessageEvent) e).getMessage()), future);
        }
    }
}
