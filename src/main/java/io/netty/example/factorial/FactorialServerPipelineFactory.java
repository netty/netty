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
package io.netty.example.factorial;

import static io.netty.channel.Channels.*;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPipelineFactory;
import io.netty.handler.codec.compression.ZlibDecoder;
import io.netty.handler.codec.compression.ZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;

/**
 * Creates a newly configured {@link ChannelPipeline} for a server-side channel.
 *
 * @author <a href="http://netty.io/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 */
public class FactorialServerPipelineFactory implements
        ChannelPipelineFactory {

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = pipeline();

        // Enable stream compression (you can remove these two if unnecessary)
        pipeline.addLast("deflater", new ZlibEncoder(ZlibWrapper.GZIP));
        pipeline.addLast("inflater", new ZlibDecoder(ZlibWrapper.GZIP));

        // Add the number codec first,
        pipeline.addLast("decoder", new BigIntegerDecoder());
        pipeline.addLast("encoder", new NumberEncoder());

        // and then business logic.
        // Please note we create a handler for every new channel
        // because it has stateful properties.
        pipeline.addLast("handler", new FactorialServerHandler());

        return pipeline;
    }
}
