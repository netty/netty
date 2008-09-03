/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.netty.channel.socket.oio;

import static org.jboss.netty.channel.Channels.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.Socket;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;

/**
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
class OioAcceptedSocketChannel extends OioSocketChannel {

    private final PushbackInputStream in;
    private final OutputStream out;

    OioAcceptedSocketChannel(
            Channel parent,
            ChannelFactory factory,
            ChannelPipeline pipeline,
            ChannelSink sink,
            Socket socket) {

        super(parent, factory, pipeline, sink, socket);

        try {
            in = new PushbackInputStream(socket.getInputStream(), 1);
        } catch (IOException e) {
            throw new ChannelException("Failed to obtain an InputStream.", e);
        }
        try {
            out = socket.getOutputStream();
        } catch (IOException e) {
            throw new ChannelException("Failed to obtain an OutputStream.", e);
        }

        fireChannelOpen(this);
        fireChannelBound(this, getLocalAddress());
        fireChannelConnected(this, getRemoteAddress());
    }

    @Override
    PushbackInputStream getInputStream() {
        return in;
    }

    @Override
    OutputStream getOutputStream() {
        return out;
    }
}
