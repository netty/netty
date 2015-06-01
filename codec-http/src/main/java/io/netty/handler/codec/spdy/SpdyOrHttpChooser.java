/*
 * Copyright 2014 The Netty Project
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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.List;

/**
 * @deprecated Use {@link ApplicationProtocolNegotiationHandler} instead.
 */
public abstract class SpdyOrHttpChooser extends ByteToMessageDecoder {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SpdyOrHttpChooser.class);

    public enum SelectedProtocol {
        SPDY_3_1("spdy/3.1"),
        HTTP_1_1("http/1.1"),
        HTTP_1_0("http/1.0");

        private final String name;

        SelectedProtocol(String defaultName) {
            name = defaultName;
        }

        public String protocolName() {
            return name;
        }

        /**
         * Get an instance of this enum based on the protocol name returned by the NPN server provider
         *
         * @param name the protocol name
         * @return the selected protocol or {@code null} if there is no match
         */
        public static SelectedProtocol protocol(String name) {
            for (SelectedProtocol protocol : SelectedProtocol.values()) {
                if (protocol.protocolName().equals(name)) {
                    return protocol;
                }
            }
            return null;
        }
    }

    protected SpdyOrHttpChooser() { }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (configurePipeline(ctx)) {
            // When we reached here we can remove this handler as its now clear
            // what protocol we want to use
            // from this point on. This will also take care of forward all
            // messages.
            ctx.pipeline().remove(this);
        }
    }

    private boolean configurePipeline(ChannelHandlerContext ctx) {
        // Get the SslHandler from the ChannelPipeline so we can obtain the
        // SslEngine from it.
        SslHandler handler = ctx.pipeline().get(SslHandler.class);
        if (handler == null) {
            // SslHandler is needed by SPDY by design.
            throw new IllegalStateException("cannot find a SslHandler in the pipeline (required for SPDY)");
        }

        if (!handler.handshakeFuture().isDone()) {
            return false;
        }

        SelectedProtocol protocol;
        try {
            protocol = selectProtocol(handler);
        } catch (Exception e) {
            throw new IllegalStateException("failed to get the selected protocol", e);
        }

        if (protocol == null) {
            throw new IllegalStateException("unknown protocol");
        }

        switch (protocol) {
        case SPDY_3_1:
            try {
                configureSpdy(ctx, SpdyVersion.SPDY_3_1);
            } catch (Exception e) {
                throw new IllegalStateException("failed to configure a SPDY pipeline", e);
            }
            break;
        case HTTP_1_0:
        case HTTP_1_1:
            try {
                configureHttp1(ctx);
            } catch (Exception e) {
                throw new IllegalStateException("failed to configure a HTTP/1 pipeline", e);
            }
            break;
        }
        return true;
    }

    /**
     * Returns the {@link SelectedProtocol} for the current SSL session.  By default, this method returns the first
     * known protocol.
     *
     * @return the selected application-level protocol, or {@code null} if the application-level protocol name of
     *         the specified {@code sslHandler} is neither {@code "http/1.1"}, {@code "http/1.0"} nor {@code "spdy/3.1"}
     */
    protected SelectedProtocol selectProtocol(SslHandler sslHandler) throws Exception {
        final String appProto = sslHandler.applicationProtocol();
        return appProto != null? SelectedProtocol.protocol(appProto) : SelectedProtocol.HTTP_1_1;
    }

    /**
     * Configures the {@link Channel} of the specified {@code ctx} for HTTP/2.
     * <p>
     * A typical implementation of this method will look like the following:
     * <pre>
     * {@link ChannelPipeline} p = ctx.pipeline();
     * p.addLast(new {@link SpdyFrameCodec}(version));
     * p.addLast(new {@link SpdySessionHandler}(version, true));
     * p.addLast(new {@link SpdyHttpEncoder}(version));
     * p.addLast(new {@link SpdyHttpDecoder}(version, <i>maxSpdyContentLength</i>));
     * p.addLast(new {@link SpdyHttpResponseStreamIdHandler}());
     * p.addLast(new <i>YourHttpRequestHandler</i>());
     * </pre>
     * </p>
     */
    protected abstract void configureSpdy(ChannelHandlerContext ctx, SpdyVersion version) throws Exception;

    /**
     * Configures the {@link Channel} of the specified {@code ctx} for HTTP/1.
     * <p>
     * A typical implementation of this method will look like the following:
     * <pre>
     * {@link ChannelPipeline} p = ctx.pipeline();
     * p.addLast(new {@link HttpServerCodec}());
     * p.addLast(new <i>YourHttpRequestHandler</i>());
     * </pre>
     * </p>
     */
    protected abstract void configureHttp1(ChannelHandlerContext ctx) throws Exception;

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.warn("{} Failed to select the application-level protocol:", ctx.channel(), cause);
        ctx.close();
    }
}
