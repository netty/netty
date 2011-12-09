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
package io.netty.channel.socket.sctp;

import com.sun.nio.sctp.Notification;
import io.netty.channel.Channel;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelFuture;
import io.netty.channel.Channels;

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://github.com/jestan">Jestan Nirojan</a>
 */
public class SctpNotificationEvent implements ChannelEvent {
    private Channel channel;
    private Notification notification;
    private Object value;

    public SctpNotificationEvent(Channel channel, Notification notification, Object value) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        if (notification == null) {
            throw new NullPointerException("notification");
        }

        this.channel = channel;
        this.notification = notification;
        this.value = value;
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    public ChannelFuture getFuture() {
        return Channels.succeededFuture(channel);
    }

    public Notification getNotification() {
        return notification;
    }

    /**
     * Return the attachment comes with SCTP notification
     * Please note that, it may be null
     */
    public Object getValue() {
        return value;
    }
}
