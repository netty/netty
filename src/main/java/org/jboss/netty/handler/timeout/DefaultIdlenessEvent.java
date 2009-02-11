/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.timeout;

import static org.jboss.netty.channel.Channels.*;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class DefaultIdlenessEvent implements IdlenessEvent {

    private final Channel channel;
    private final long lastReadTime;
    private final long lastWriteTime;
    private final long readerIdleTime;
    private final long writerIdleTime;

    public DefaultIdlenessEvent(
            Channel channel,
            long lastReadTime, long lastWriteTime,
            long readerIdleTime, long writerIdleTime) {

        if (channel == null) {
            throw new NullPointerException("channel");
        }

        this.channel = channel;
        this.lastReadTime = lastReadTime;
        this.lastWriteTime = lastWriteTime;
        this.readerIdleTime = readerIdleTime;
        this.writerIdleTime = writerIdleTime;
    }

    public Channel getChannel() {
        return channel;
    }

    public boolean isReaderIdle() {
        return System.currentTimeMillis() + readerIdleTime > lastReadTime;
    }

    public boolean isWriterIdle() {
        return System.currentTimeMillis() + writerIdleTime > lastWriteTime;
    }

    public ChannelFuture getFuture() {
        return succeededFuture(getChannel());
    }

    @Override
    public String toString() {
        return getChannel().toString() + " - (IDLE: " +
               (isReaderIdle()? 'R' : '_') +
               (isWriterIdle()? 'W' : '_') + ')';
    }
}
