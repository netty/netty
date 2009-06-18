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
package org.jboss.netty.channel.xnio;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ServerChannelFactory;
import org.jboss.xnio.channels.BoundServer;

/**
 * A {@link ServerChannelFactory} which uses
 * <a href="http://www.jboss.org/xnio/>JBoss XNIO</a> as its I/O provider.
 * <p>
 * Please note that you must specify an {@link XnioAcceptedChannelHandlerFactory}
 * when you create a {@link BoundServer} to integrate XNIO into Netty.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
@SuppressWarnings("unchecked")
public class XnioServerChannelFactory implements ServerChannelFactory {

    private final BoundServer xnioServer;
    final XnioServerChannelSink sink;

    public XnioServerChannelFactory(BoundServer xnioServer) {
        if (xnioServer == null) {
            throw new NullPointerException("xnioServer");
        }
        this.xnioServer = xnioServer;
        sink = new XnioServerChannelSink();
    }

    public XnioServerChannel newChannel(ChannelPipeline pipeline) {
        return new DefaultXnioServerChannel(this, pipeline, sink, xnioServer);
    }

    public void releaseExternalResources() {
        // Nothing to release.
    }

}
