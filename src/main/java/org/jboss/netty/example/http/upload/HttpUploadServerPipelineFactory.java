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
package org.jboss.netty.example.http.upload;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.ssl.SslContext;
import org.jboss.netty.handler.ssl.SslHandler;

import static org.jboss.netty.channel.Channels.*;

public class HttpUploadServerPipelineFactory implements ChannelPipelineFactory {

    private final SslContext sslCtx;

    public HttpUploadServerPipelineFactory(SslContext sslCtx) {
        this.sslCtx = sslCtx;
    }

    public ChannelPipeline getPipeline() throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = pipeline();

        if (sslCtx != null) {
            SslHandler handler = sslCtx.newHandler();
            handler.setIssueHandshake(true);
            pipeline.addLast("ssl", handler);
        }

        pipeline.addLast("decoder", new HttpRequestDecoder());

        pipeline.addLast("encoder", new HttpResponseEncoder());

        // Remove the following line if you don't want automatic content compression.
        pipeline.addLast("deflater", new HttpContentCompressor());

        pipeline.addLast("handler", new HttpUploadServerHandler());
        return pipeline;
    }
}
