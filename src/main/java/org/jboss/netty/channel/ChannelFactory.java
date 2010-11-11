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
package org.jboss.netty.channel;

import java.util.concurrent.Executor;

import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.util.ExternalResourceReleasable;


/**
 * The main interface to a transport that creates a {@link Channel} associated
 * with a certain communication entity such as a network socket.  For example,
 * the {@link NioServerSocketChannelFactory} creates a channel which has a
 * NIO-based server socket as its underlying communication entity.
 * <p>
 * Once a new {@link Channel} is created, the {@link ChannelPipeline} which
 * was specified as a parameter in the {@link #newChannel(ChannelPipeline)}
 * is attached to the new {@link Channel}, and starts to handle all associated
 * {@link ChannelEvent}s.
 *
 * <h3>Graceful shutdown</h3>
 * <p>
 * To shut down a network application service which is managed by a factory.
 * you should follow the following steps:
 * <ol>
 * <li>close all channels created by the factory and their child channels
 *     usually using {@link ChannelGroup#close()}, and</li>
 * <li>call {@link #releaseExternalResources()}.</li>
 * </ol>
 * <p>
 * For detailed transport-specific information on shutting down a factory,
 * please refer to the Javadoc of {@link ChannelFactory}'s subtypes, such as
 * {@link NioServerSocketChannelFactory}.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 * @apiviz.landmark
 * @apiviz.has        org.jboss.netty.channel.Channel oneway - - creates
 *
 * @apiviz.exclude ^org\.jboss\.netty\.channel\.([a-z]+\.)+.*ChannelFactory$
 */
public interface ChannelFactory extends ExternalResourceReleasable {

    /**
     * Creates and opens a new {@link Channel} and attaches the specified
     * {@link ChannelPipeline} to the new {@link Channel}.
     *
     * @param pipeline the {@link ChannelPipeline} which is going to be
     *                 attached to the new {@link Channel}
     *
     * @return the newly open channel
     *
     * @throws ChannelException if failed to create and open a new channel
     */
    Channel newChannel(ChannelPipeline pipeline);

    /**
     * Releases the external resources that this factory depends on to function.
     * An external resource is a resource that this factory didn't create by
     * itself.  For example, {@link Executor}s that you specified in the factory
     * constructor are external resources.  You can call this method to release
     * all external resources conveniently when the resources are not used by
     * this factory or any other part of your application.  An unexpected
     * behavior will be resulted in if the resources are released when there's
     * an open channel which is managed by this factory.
     */
    void releaseExternalResources();
}
