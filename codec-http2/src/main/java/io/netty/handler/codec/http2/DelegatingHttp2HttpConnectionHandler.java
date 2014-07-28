/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelPromiseAggregator;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;

import java.util.Map;

/**
 * Light weight wrapper around {@link DelegatingHttp2ConnectionHandler} to provide HTTP/1.x object to HTTP/2 encoding
 */
public class DelegatingHttp2HttpConnectionHandler extends DelegatingHttp2ConnectionHandler {

    public DelegatingHttp2HttpConnectionHandler(boolean server, Http2FrameObserver observer) {
        super(server, observer);
    }

    public DelegatingHttp2HttpConnectionHandler(Http2Connection connection, Http2FrameReader frameReader,
                    Http2FrameWriter frameWriter, Http2InboundFlowController inboundFlow,
                    Http2OutboundFlowController outboundFlow, Http2FrameObserver observer) {
        super(connection, frameReader, frameWriter, inboundFlow, outboundFlow, observer);
    }

    public DelegatingHttp2HttpConnectionHandler(Http2Connection connection, Http2FrameObserver observer) {
        super(connection, observer);
    }

    protected void addRequestHeaders(HttpRequest httpRequest, DefaultHttp2Headers.Builder http2Headers) {
        HttpHeaders httpHeaders = httpRequest.headers();
        http2Headers.path(httpRequest.uri());
        http2Headers.method(httpRequest.method().toString());
        String value = httpHeaders.get(HttpHeaders.Names.HOST);
        if (value != null) {
            http2Headers.authority(value);
            httpHeaders.remove(HttpHeaders.Names.HOST);
        }
        value = httpHeaders.get(Http2HttpHeaders.Names.SCHEME);
        if (value != null) {
            http2Headers.scheme(value);
            httpHeaders.remove(Http2HttpHeaders.Names.SCHEME);
        }
    }

    protected int getStreamId(HttpHeaders httpHeaders) throws Http2Exception {
        int streamId = 0;
        String value = httpHeaders.get(Http2HttpHeaders.Names.STREAM_ID);
        if (value == null) {
            streamId = nextStreamId();
        } else {
            try {
                streamId = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw Http2Exception.format(Http2Error.INTERNAL_ERROR,
                                    "Invalid user-specified stream id value '%s'", value);
            }
            // Simple stream id validation for user-specified streamId
            if (streamId < 1 || (connection().isServer() && (streamId % 2 != 0))
                            || (!connection().isServer() && (streamId % 2 == 0))) {
                throw Http2Exception.format(Http2Error.INTERNAL_ERROR,
                                    "Invalid user-specified stream id value '%d'", streamId);
            }
            httpHeaders.remove(Http2HttpHeaders.Names.STREAM_ID);
        }

        return streamId;
    }

    /**
     * Handles conversion of a {@link FullHttpMessage} to HTTP/2 frames.
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof FullHttpMessage) {
            FullHttpMessage httpMsg = (FullHttpMessage) msg;
            boolean hasData = httpMsg.content().isReadable();

            // Convert and write the headers.
            String value = null;
            HttpHeaders httpHeaders = httpMsg.headers();
            DefaultHttp2Headers.Builder http2Headers = DefaultHttp2Headers.newBuilder();
            if (msg instanceof HttpRequest) {
                addRequestHeaders((HttpRequest) msg, http2Headers);
            }

            // Provide the user the opportunity to specify the streamId
            int streamId = 0;
            try {
                streamId = getStreamId(httpHeaders);
            } catch (Http2Exception e) {
                httpMsg.release();
                promise.setFailure(e);
                return;
            }

            // The Connection, Keep-Alive, Proxy-Connection, Transfer-Encoding,
            // and Upgrade headers are not valid and MUST not be sent.
            httpHeaders.remove(HttpHeaders.Names.CONNECTION);
            httpHeaders.remove("Keep-Alive");
            httpHeaders.remove("Proxy-Connection");
            httpHeaders.remove(HttpHeaders.Names.TRANSFER_ENCODING);

            // Add the HTTP headers which have not been consumed above
            for (Map.Entry<String, String> entry : httpHeaders.entries()) {
                http2Headers.add(entry.getKey(), entry.getValue());
            }

            if (hasData) {
                ChannelPromiseAggregator promiseAggregator = new ChannelPromiseAggregator(promise);
                ChannelPromise headerPromise = ctx.newPromise();
                ChannelPromise dataPromise = ctx.newPromise();
                promiseAggregator.add(headerPromise, dataPromise);
                writeHeaders(ctx, headerPromise, streamId, http2Headers.build(), 0, false, false);
                writeData(ctx, dataPromise, streamId, httpMsg.content(), 0, true, true, false);
            } else {
                writeHeaders(ctx, promise, streamId, http2Headers.build(), 0, true, true);
            }
        } else {
            ctx.write(msg, promise);
        }
    }
}
