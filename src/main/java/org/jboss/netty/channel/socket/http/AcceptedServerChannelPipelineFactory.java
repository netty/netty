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
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;

/**
 * Creates pipelines for incoming http tunnel connections, capable of decoding the incoming HTTP
 * requests, determining their type (client sending data, client polling data, or unknown) and
 * handling them appropriately.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author OneDrum Ltd.
 */
class AcceptedServerChannelPipelineFactory implements ChannelPipelineFactory {

    private final ServerMessageSwitch messageSwitch;

    public AcceptedServerChannelPipelineFactory(
            ServerMessageSwitch messageSwitch) {
        this.messageSwitch = messageSwitch;
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();

        pipeline.addLast("httpResponseEncoder", new HttpResponseEncoder());
        pipeline.addLast("httpRequestDecoder", new HttpRequestDecoder());
        pipeline.addLast("httpChunkAggregator", new HttpChunkAggregator(
                HttpTunnelMessageUtils.MAX_BODY_SIZE));
        pipeline.addLast("messageSwitchClient",
                new AcceptedServerChannelRequestDispatch(messageSwitch));

        return pipeline;
    }
}
