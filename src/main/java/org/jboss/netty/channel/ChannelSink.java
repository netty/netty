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


/**
 * Receives and processes the terminal downstream {@link ChannelEvent}s.
 * <p>
 * A {@link ChannelSink} is an internal component which is supposed to be
 * implemented by a transport provider.  Most users will not see this type
 * in their code.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
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
