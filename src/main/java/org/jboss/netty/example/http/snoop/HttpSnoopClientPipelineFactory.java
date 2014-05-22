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
package org.jboss.netty.example.http.snoop;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpContentDecompressor;
import org.jboss.netty.handler.ssl.SslContext;

import static org.jboss.netty.channel.Channels.*;

public class HttpSnoopClientPipelineFactory implements ChannelPipelineFactory {

    private final SslContext sslCtx;
    private final String host;
    private final int port;

    public HttpSnoopClientPipelineFactory(SslContext sslCtx, String host, int port) {
        this.sslCtx = sslCtx;
        this.host = host;
        this.port = port;
    }

    public ChannelPipeline getPipeline() {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = pipeline();

        // Enable HTTPS if necessary.
        if (sslCtx != null) {
            pipeline.addLast("ssl", sslCtx.newHandler(host, port));
        }

        pipeline.addLast("codec", new HttpClientCodec());

        // Remove the following line if you don't want automatic content decompression.
        pipeline.addLast("inflater", new HttpContentDecompressor());

        // Uncomment the following line if you don't want to handle HttpChunks.
        //pipeline.addLast("aggregator", new HttpChunkAggregator(1048576));

        pipeline.addLast("handler", new HttpSnoopClientHandler());
        return pipeline;
    }
}
