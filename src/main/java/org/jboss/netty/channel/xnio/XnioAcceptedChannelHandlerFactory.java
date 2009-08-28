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

import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.channels.BoundServer;

/**
 * An XNIO {@link IoHandlerFactory} implementation that must be specified when
 * you create a {@link BoundServer} to integrate XNIO into Netty.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class XnioAcceptedChannelHandlerFactory implements IoHandlerFactory<java.nio.channels.Channel> {

    private final XnioAcceptedChannelHandler handler = new XnioAcceptedChannelHandler();

    public IoHandler<java.nio.channels.Channel> createHandler() {
        return handler;
    }
}
