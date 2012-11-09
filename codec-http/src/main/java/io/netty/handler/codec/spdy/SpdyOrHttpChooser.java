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

import javax.net.ssl.SSLEngine;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandler;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpChunkAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.ssl.SslHandler;

/**
 * {@link ChannelInboundByteHandler} which is responsible to setup the {@link ChannelPipeline} either for
 * HTTP or SPDY. This offers an easy way for users to support both at the same time while not care to
 * much about the low-level details.
 *
 */
public abstract class SpdyOrHttpChooser extends ChannelHandlerAdapter implements ChannelInboundByteHandler {

    public enum SelectedProtocol {
        SpdyVersion2,
        SpdyVersion3,
        HttpVersion1_1,
        HttpVersion1_0,
        None
    }

    private final int maxSpdyContentLength;
    private final int maxHttpContentLength;

    protected SpdyOrHttpChooser(int maxSpdyContentLength, int maxHttpContentLength) {
        this.maxSpdyContentLength = maxSpdyContentLength;
        this.maxHttpContentLength = maxHttpContentLength;
    }

    /**
     * Return the {@link SelectedProtocol} for the {@link SSLEngine}. If its not known yet implementations
     * MUST return {@link SelectedProtocol#None}.
     *
     */
    protected abstract SelectedProtocol getProtocol(SSLEngine engine);

    @Override
    public ByteBuf newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return Unpooled.buffer();
    }

    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
        if (initPipeline(ctx)) {
            ctx.nextInboundByteBuffer().writeBytes(ctx.inboundByteBuffer());

            // When we reached here we can remove this handler as its now clear what protocol we want to use
            // from this point on.
            ctx.pipeline().remove(this);

            ctx.fireInboundBufferUpdated();

        }
    }

    private boolean initPipeline(ChannelHandlerContext ctx) {
        // Get the SslHandler from the ChannelPipeline so we can obtain the SslEngine from it.
        SslHandler handler = ctx.pipeline().get(SslHandler.class);
        if (handler == null) {
            // SslHandler is needed by SPDY by design.
            throw new IllegalStateException("SslHandler is needed for SPDY");
        }

        ChannelPipeline pipeline = ctx.pipeline();
        SelectedProtocol protocol = getProtocol(handler.getEngine());
        switch (protocol) {
        case None:
            // Not done with choosing the protocol, so just return here for now,
            return false;
        case SpdyVersion2:
            addSpdyHandlers(ctx, 2);
            break;
        case SpdyVersion3:
            addSpdyHandlers(ctx, 3);
            break;
        case HttpVersion1_0:
        case HttpVersion1_1:
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
    protected void addSpdyHandlers(ChannelHandlerContext ctx, int version) {
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
        pipeline.addLast("httpChunkAggregator", new HttpChunkAggregator(maxHttpContentLength));
        pipeline.addLast("httpRquestHandler", createHttpRequestHandlerForHttp());
    }

    /**
     * Create the {@link ChannelInboundHandler} that is responsible for handling the {@link HttpRequest}'s
     * when the {@link SelectedProtocol} was {@link SelectedProtocol#HttpVersion1_0} or
     * {@link SelectedProtocol#HttpVersion1_1}
     */
    protected abstract ChannelInboundHandler createHttpRequestHandlerForHttp();

    /**
     * Create the {@link ChannelInboundHandler} that is responsible for handling the {@link HttpRequest}'s
     * when the {@link SelectedProtocol} was {@link SelectedProtocol#SpdyVersion2} or
     * {@link SelectedProtocol#SpdyVersion3}.
     *
     * Bye default this method will just delecate to {@link #createHttpRequestHandlerForHttp()}, but
     * sub-classes may override this to change the behaviour.
     */
    protected ChannelInboundHandler createHttpRequestHandlerForSpdy() {
        return createHttpRequestHandlerForHttp();
    }
}
