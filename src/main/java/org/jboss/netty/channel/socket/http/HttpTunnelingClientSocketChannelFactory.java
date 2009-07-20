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
package org.jboss.netty.channel.socket.http;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.SocketChannel;

/**
 * Creates a client-side {@link SocketChannel} which connects to an
 * {@link HttpTunnelingServlet} to communicate with the server application
 * behind the {@link HttpTunnelingServlet}.  Please refer to the
 * <a href="package-summary.html#package_description">package summary</a> for
 * the detailed usage.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
public class HttpTunnelingClientSocketChannelFactory implements ClientSocketChannelFactory {

    private final ChannelSink sink = new HttpTunnelingClientSocketPipelineSink();
    private final ClientSocketChannelFactory clientSocketChannelFactory;

    /**
     * Creates a new instance.
     */
    public HttpTunnelingClientSocketChannelFactory(ClientSocketChannelFactory clientSocketChannelFactory) {
        this.clientSocketChannelFactory = clientSocketChannelFactory;
    }

    public SocketChannel newChannel(ChannelPipeline pipeline) {
        return new HttpTunnelingClientSocketChannel(this, pipeline, sink, clientSocketChannelFactory);
    }

    public void releaseExternalResources() {
        clientSocketChannelFactory.releaseExternalResources();
    }
}