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
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
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
