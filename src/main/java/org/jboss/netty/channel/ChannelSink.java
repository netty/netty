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


/**
 * Receives and processes the terminal downstream {@link ChannelEvent}s.
 * <p>
 * A {@link ChannelSink} is an internal component which is supposed to be
 * implemented by a transport provider.  Most users will not see this type
 * in their code.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.uses org.jboss.netty.channel.ChannelPipeline - - sends events upstream
 */
public interface ChannelSink {

    /**
     * Invoked by {@link ChannelPipeline} when a downstream {@link ChannelEvent}
     * has reached its terminal (the head of the pipeline).
     */
    void eventSunk(ChannelPipeline pipeline, ChannelEvent e) throws Exception;

    /**
     * Invoked by {@link ChannelPipeline} when an exception was raised while
     * one of its {@link ChannelHandler}s process a {@link ChannelEvent}.
     */
    void exceptionCaught(ChannelPipeline pipeline, ChannelEvent e, ChannelPipelineException cause) throws Exception;
}
