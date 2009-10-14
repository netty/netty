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
package org.jboss.netty.channel.xnio;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ServerChannelFactory;
import org.jboss.xnio.channels.BoundServer;

/**
 * A {@link ServerChannelFactory} which uses
 * <a href="http://www.jboss.org/xnio/">JBoss XNIO</a> as its I/O provider.
 * <p>
 * Please note that you must specify an {@link XnioAcceptedChannelHandlerFactory}
 * when you create a {@link BoundServer} to integrate XNIO into Netty.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
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
