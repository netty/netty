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
package org.jboss.netty.handler.codec.spdy;

import javax.net.ssl.SSLEngine;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.ssl.SslHandler;

/**
 * {@link ChannelUpstreamHandler} which is responsible to setup the {@link ChannelPipeline} either for
 * HTTP or SPDY. This offers an easy way for users to support both at the same time while not care to
 * much about the low-level details.
 *
 */
public abstract class SpdyOrHttpChooser implements ChannelUpstreamHandler {

    public enum SelectedProtocol {
        SpdyVersion3_1,
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

    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        // Get the SslHandler from the ChannelPipeline so we can obtain the SslEngine from it.
        SslHandler handler = ctx.getPipeline().get(SslHandler.class);
        if (handler == null) {
            // SslHandler is needed by SPDY by design.
            throw new IllegalStateException("SslHandler is needed for SPDY");
        }

        ChannelPipeline pipeline = ctx.getPipeline();
        SelectedProtocol protocol = getProtocol(handler.getEngine());
        switch (protocol) {
        case None:
            // Not done with choosing the protocol, so just return here for now,
            return;
        case SpdyVersion3:
            addSpdyHandlers(ctx, SpdyVersion.SPDY_3);
            break;
        case SpdyVersion3_1:
            addSpdyHandlers(ctx, SpdyVersion.SPDY_3_1);
            break;
        case HttpVersion1_0:
        case HttpVersion1_1:
            addHttpHandlers(ctx);
            break;
        default:
            throw new IllegalStateException("Unknown SelectedProtocol");
        }
        // When we reached here we can remove this handler as its now clear what protocol we want to use
        // from this point on.
        pipeline.remove(this);
        ctx.sendUpstream(e);
    }

    /**
     * Add all {@link ChannelHandler}'s that are needed for SPDY with the given version.
     */
    protected void addSpdyHandlers(ChannelHandlerContext ctx, SpdyVersion version) {
        ChannelPipeline pipeline = ctx.getPipeline();
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
        ChannelPipeline pipeline = ctx.getPipeline();
        pipeline.addLast("httpRquestDecoder", new HttpRequestDecoder());
        pipeline.addLast("httpResponseEncoder", new HttpResponseEncoder());
        pipeline.addLast("httpChunkAggregator", new HttpChunkAggregator(maxHttpContentLength));
        pipeline.addLast("httpRquestHandler", createHttpRequestHandlerForHttp());
    }

    /**
     * Create the {@link ChannelUpstreamHandler} that is responsible for handling the {@link HttpRequest}'s
     * when the {@link SelectedProtocol} was {@link SelectedProtocol#HttpVersion1_0} or
     * {@link SelectedProtocol#HttpVersion1_1}
     */
    protected abstract ChannelUpstreamHandler createHttpRequestHandlerForHttp();

    /**
     * Create the {@link ChannelUpstreamHandler} that is responsible for handling the {@link HttpRequest}'s
     * when the {@link SelectedProtocol} was {@link SelectedProtocol#SpdyVersion3} or
     * {@link SelectedProtocol#SpdyVersion3_1}.
     *
     * Bye default this method will just delecate to {@link #createHttpRequestHandlerForHttp()}, but
     * sub-classes may override this to change the behaviour.
     */
    protected ChannelUpstreamHandler createHttpRequestHandlerForSpdy() {
        return createHttpRequestHandlerForHttp();
    }
}
