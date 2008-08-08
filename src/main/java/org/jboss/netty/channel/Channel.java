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
package org.jboss.netty.channel;

import java.net.SocketAddress;
import java.util.UUID;


/**
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @apiviz.composedOf org.jboss.netty.channel.ChannelConfig
 * @apiviz.composedOf org.jboss.netty.channel.ChannelPipeline
 */
public interface Channel {
    static int OP_NONE = 0;
    static int OP_READ = 1;
    static int OP_WRITE = 4;
    static int OP_READ_WRITE = OP_READ | OP_WRITE;

    UUID getId();
    ChannelFactory getFactory();
    Channel getParent();
    ChannelConfig getConfig();
    ChannelPipeline getPipeline();

    boolean isOpen();
    boolean isBound();
    boolean isConnected();

    SocketAddress getLocalAddress();
    SocketAddress getRemoteAddress();

    ChannelFuture write(Object message);
    ChannelFuture write(Object message, SocketAddress remoteAddress);

    ChannelFuture bind(SocketAddress localAddress);
    ChannelFuture connect(SocketAddress remoteAddress);
    ChannelFuture disconnect();
    ChannelFuture close();

    int getInterestOps();
    boolean isReadable();
    boolean isWritable();
    ChannelFuture setInterestOps(int interestOps);
    ChannelFuture setReadable(boolean readable);
}
