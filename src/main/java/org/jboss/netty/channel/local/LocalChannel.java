/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
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
package org.jboss.netty.channel.local;

import static org.jboss.netty.channel.Channels.*;

import java.util.Queue;

import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.util.LinkedTransferQueue;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 * @author Trustin Lee (tlee@redhat.com)
 */
public class LocalChannel extends AbstractChannel {
    //final BlockingQueue<MessageEvent> writeBuffer = new LinkedBlockingQueue<MessageEvent>();
    private final ThreadLocal<Boolean> delivering = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    volatile LocalChannel pairedChannel = null;
    private final LocalChannelConfig config;
    final Queue<MessageEvent> writeBuffer = new LinkedTransferQueue<MessageEvent>();

    protected LocalChannel(ChannelFactory factory, ChannelPipeline pipeline, ChannelSink sink) {
        super(null, factory, pipeline, sink);
        config = new LocalChannelConfig();
        fireChannelOpen(this);
    }

    public ChannelConfig getConfig() {
        return config;
    }

    public boolean isBound() {
        return true;
    }

    public boolean isConnected() {
        return true;
    }

    public LocalAddress getLocalAddress() {
        // FIXME: should return LocalAddress
        return null;
    }

    public LocalAddress getRemoteAddress() {
        // FIXME: should return LocalAddress
        return null;
    }

    void writeNow(LocalChannel pairedChannel) {
        if (!delivering.get()) {
            delivering.set(true);
            try {
                for (;;) {
                    MessageEvent e = writeBuffer.poll();
                    if(e == null) {
                        break;
                    }

                    e.getFuture().setSuccess();
                    fireMessageReceived(pairedChannel, e.getMessage());
                }
            } finally {
                delivering.set(false);
            }
        }
    }
}
