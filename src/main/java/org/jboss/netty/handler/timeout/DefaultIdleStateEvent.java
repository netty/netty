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
package org.jboss.netty.handler.timeout;

import static org.jboss.netty.channel.Channels.*;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;

/**
 * The default {@link IdleStateEvent} implementation.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class DefaultIdleStateEvent implements IdleStateEvent {

    private final Channel channel;
    private final IdleState state;
    private final long lastActivityTimeMillis;

    /**
     * Creates a new instance.
     */
    public DefaultIdleStateEvent(
            Channel channel, IdleState state, long lastActivityTimeMillis) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        if (state == null) {
            throw new NullPointerException("state");
        }
        this.channel = channel;
        this.state = state;
        this.lastActivityTimeMillis = lastActivityTimeMillis;
    }

    public Channel getChannel() {
        return channel;
    }

    public ChannelFuture getFuture() {
        return succeededFuture(getChannel());
    }

    public IdleState getState() {
        return state;
    }

    public long getLastActivityTimeMillis() {
        return lastActivityTimeMillis;
    }

    @Override
    public String toString() {
        return getChannel().toString() + ' ' + getState() + " since " +
               DateFormat.getDateTimeInstance(
                       DateFormat.SHORT, DateFormat.SHORT, Locale.US).format(
                               new Date(getLastActivityTimeMillis()));
    }
}
