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
package io.netty.example.http.snoop;

import static io.netty.channel.Channels.*;

import javax.net.ssl.SSLEngine;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPipelineFactory;
import io.netty.example.securechat.SecureChatSslContextFactory;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.logging.InternalLogLevel;

/**
 * @author <a href="http://netty.io/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 */
public class HttpClientPipelineFactory implements ChannelPipelineFactory {

    private final boolean ssl;

    public HttpClientPipelineFactory(boolean ssl) {
        this.ssl = ssl;
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = pipeline();

        pipeline.addLast("log", new LoggingHandler(InternalLogLevel.INFO));
        // Enable HTTPS if necessary.
        if (ssl) {
            SSLEngine engine =
                SecureChatSslContextFactory.getClientContext().createSSLEngine();
            engine.setUseClientMode(true);

            pipeline.addLast("ssl", new SslHandler(engine));
        }

        pipeline.addLast("codec", new HttpClientCodec());

        // Remove the following line if you don't want automatic content decompression.
        pipeline.addLast("inflater", new HttpContentDecompressor());

        // Uncomment the following line if you don't want to handle HttpChunks.
        //pipeline.addLast("aggregator", new HttpChunkAggregator(1048576));

        pipeline.addLast("handler", new HttpResponseHandler());
        return pipeline;
    }
}
