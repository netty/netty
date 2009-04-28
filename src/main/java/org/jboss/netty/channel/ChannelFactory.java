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
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
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
