/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.example.factorial;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.compression.ZlibDecoder;
import org.jboss.netty.handler.codec.compression.ZlibEncoder;
import org.jboss.netty.handler.codec.compression.ZlibWrapper;
import org.jboss.netty.handler.ssl.SslContext;

import static org.jboss.netty.channel.Channels.*;

/**
 * Creates a newly configured {@link ChannelPipeline} for a server-side channel.
 */
public class FactorialServerPipelineFactory implements ChannelPipelineFactory {

    private final SslContext sslCtx;

    public FactorialServerPipelineFactory(SslContext sslCtx) {
        this.sslCtx = sslCtx;
    }

    public ChannelPipeline getPipeline() {
        ChannelPipeline pipeline = pipeline();

        if (sslCtx != null) {
            pipeline.addLast("ssl", sslCtx.newHandler());
        }

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
