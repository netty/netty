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
package org.jboss.netty.channel;

/**
 * The default downstream {@link ChannelStateEvent} implementation.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 */
public class DownstreamChannelStateEvent implements ChannelStateEvent {

    private final Channel channel;
    private final ChannelFuture future;
    private final ChannelState state;
    private final Object value;

    /**
     * Creates a new instance.
     */
    public DownstreamChannelStateEvent(
            Channel channel, ChannelFuture future,
            ChannelState state, Object value) {

        if (channel == null) {
            throw new NullPointerException("channel");
        }
        if (future == null) {
            throw new NullPointerException("future");
        }
        if (state == null) {
            throw new NullPointerException("state");
        }
        this.channel = channel;
        this.future = future;
        this.state = state;
        this.value = value;
    }

    public Channel getChannel() {
        return channel;
    }

    public ChannelFuture getFuture() {
        return future;
    }

    public ChannelState getState() {
        return state;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        String channelString = getChannel().toString();
        StringBuilder buf = new StringBuilder(channelString.length() + 64);
        buf.append(channelString);
        switch (getState()) {
        case OPEN:
            if (Boolean.TRUE.equals(getValue())) {
                buf.append(" OPEN");
            } else {
                buf.append(" CLOSE");
            }
            break;
        case BOUND:
            if (getValue() != null) {
                buf.append(" BIND: ");
                buf.append(getValue());
            } else {
                buf.append(" UNBIND");
            }
            break;
        case CONNECTED:
            if (getValue() != null) {
                buf.append(" CONNECT: ");
                buf.append(getValue());
            } else {
                buf.append(" DISCONNECT");
            }
            break;
        case INTEREST_OPS:
            buf.append(" CHANGE_INTEREST: ");
            buf.append(getValue());
            break;
        default:
            buf.append(' ');
            buf.append(getState().name());
            buf.append(": ");
            buf.append(getValue());
        }
        return buf.toString();
    }
}
