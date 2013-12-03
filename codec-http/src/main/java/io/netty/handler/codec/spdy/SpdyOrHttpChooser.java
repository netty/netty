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
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;
import java.util.List;

/**
 * {@link ChannelHandler} which is responsible to setup the {@link ChannelPipeline} either for
 * HTTP or SPDY. This offers an easy way for users to support both at the same time while not care to
 * much about the low-level details.
 */
public abstract class SpdyOrHttpChooser extends ByteToMessageDecoder {

    // TODO: Replace with generic NPN handler

    public enum SelectedProtocol {
        SPDY_3,
        SPDY_3_1,
        HTTP_1_1,
        HTTP_1_0,
        UNKNOWN
    }

    private final int maxSpdyContentLength;
    private final int maxHttpContentLength;

    protected SpdyOrHttpChooser(int maxSpdyContentLength, int maxHttpContentLength) {
        this.maxSpdyContentLength = maxSpdyContentLength;
        this.maxHttpContentLength = maxHttpContentLength;
    }

    /**
     * Return the {@link SelectedProtocol} for the {@link SSLEngine}. If its not known yet implementations
     * MUST return {@link SelectedProtocol#UNKNOWN}.
     *
     */
    protected abstract SelectedProtocol getProtocol(SSLEngine engine);

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (initPipeline(ctx)) {
            // When we reached here we can remove this handler as its now clear what protocol we want to use
            // from this point on. This will also take care of forward all messages.
            ctx.pipeline().remove(this);
        }
    }

    private boolean initPipeline(ChannelHandlerContext ctx) {
        // Get the SslHandler from the ChannelPipeline so we can obtain the SslEngine from it.
        SslHandler handler = ctx.pipeline().get(SslHandler.class);
        if (handler == null) {
            // SslHandler is needed by SPDY by design.
            throw new IllegalStateException("SslHandler is needed for SPDY");
        }

        SelectedProtocol protocol = getProtocol(handler.engine());
        switch (protocol) {
        case UNKNOWN:
            // Not done with choosing the protocol, so just return here for now,
            return false;
        case SPDY_3:
            addSpdyHandlers(ctx, SpdyVersion.SPDY_3);
            break;
        case SPDY_3_1:
            addSpdyHandlers(ctx, SpdyVersion.SPDY_3_1);
            break;
        case HTTP_1_0:
        case HTTP_1_1:
            addHttpHandlers(ctx);
            break;
        default:
            throw new IllegalStateException("Unknown SelectedProtocol");
        }
        return true;
    }

    /**
     * Add all {@link ChannelHandler}'s that are needed for SPDY with the given version.
     */
    protected void addSpdyHandlers(ChannelHandlerContext ctx, SpdyVersion version) {
        ChannelPipeline pipeline = ctx.pipeline();
        pipeline.addLast("spdyDecoder", new SpdyFrameDecoder(version));
        pipeline.addLast("spdyEncoder", new SpdyFrameEncoder(version));
        pipeline.addLast("spdySessionHandler", new SpdySessionHandler(version, true));
        pipeline.addLast("spdyHttpEncoder", new SpdyHttpEncoder(version));
        pipeline.addLast("spdyHttpDecoder", new SpdyHttpDecoder(version, maxSpdyContentLength));
        pipeline.addLast("spdyStreamIdHandler", new SpdyHttpResponseStreamIdHandler());
        pipeline.addLast("httpRquestHandler", createHttpRequestHandlerForSpdy());
    }

    /**
     * Add all {@link ChannelHandler}'s that are needed for HTTP.
     */
    protected void addHttpHandlers(ChannelHandlerContext ctx) {
        ChannelPipeline pipeline = ctx.pipeline();
        pipeline.addLast("httpRquestDecoder", new HttpRequestDecoder());
        pipeline.addLast("httpResponseEncoder", new HttpResponseEncoder());
        pipeline.addLast("httpChunkAggregator", new HttpObjectAggregator(maxHttpContentLength));
        pipeline.addLast("httpRquestHandler", createHttpRequestHandlerForHttp());
    }

    /**
     * Create the {@link ChannelHandler} that is responsible for handling the http requests
     * when the {@link SelectedProtocol} was {@link SelectedProtocol#HTTP_1_0} or
     * {@link SelectedProtocol#HTTP_1_1}
     */
    protected abstract ChannelHandler createHttpRequestHandlerForHttp();

    /**
     * Create the {@link ChannelHandler} that is responsible for handling the http responses
     * when the {@link SelectedProtocol} was {@link SelectedProtocol#SPDY_3} or
     * {@link SelectedProtocol#SPDY_3_1}.
     *
     * By default this getMethod will just delecate to {@link #createHttpRequestHandlerForHttp()}, but
     * sub-classes may override this to change the behaviour.
     */
    protected ChannelHandler createHttpRequestHandlerForSpdy() {
        return createHttpRequestHandlerForHttp();
    }
}
