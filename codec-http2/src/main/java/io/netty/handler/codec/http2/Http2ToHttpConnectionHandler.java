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

/**
 * Light weight wrapper around {@link Http2ConnectionHandler} to provide HTTP/1.x objects to HTTP/2 frames
 * <p>
 * See {@link InboundHttp2ToHttpAdapter} to get translation from HTTP/2 frames to HTTP/1.x objects
 */
public class Http2ToHttpConnectionHandler extends Http2ConnectionHandler {
    public Http2ToHttpConnectionHandler(boolean server, Http2FrameListener listener) {
        super(server, listener);
    }

    public Http2ToHttpConnectionHandler(Http2Connection connection, Http2FrameListener listener) {
        super(connection, listener);
    }

    public Http2ToHttpConnectionHandler(Http2Connection connection, Http2FrameListener listener,
            Http2FrameReader frameReader, Http2FrameWriter frameWriter) {
        super(connection, listener, frameReader, frameWriter);
    }

    public Http2ToHttpConnectionHandler(Http2Connection connection, Http2FrameListener listener,
            Http2FrameReader frameReader, Http2InboundFlowController inboundFlow,
            Http2OutboundConnectionAdapter outbound) {
        super(connection, listener, frameReader, inboundFlow, outbound);
    }

    /**
     * Get the next stream id either from the {@link HttpHeaders} object or HTTP/2 codec
     *
     * @param httpHeaders The HTTP/1.x headers object to look for the stream id
     * @return The stream id to use with this {@link HttpHeaders} object
     * @throws Http2Exception If the {@code httpHeaders} object specifies an invalid stream id
     */
    private int getStreamId(HttpHeaders httpHeaders) throws Http2Exception {
        int streamId = 0;
        String value = httpHeaders.get(HttpUtil.ExtensionHeaderNames.STREAM_ID.text());
        if (value == null) {
            streamId = connection.local().nextStreamId();
        } else {
            try {
                streamId = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw Http2Exception.format(Http2Error.INTERNAL_ERROR, "Invalid user-specified stream id value '%s'",
                        value);
            }
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

            // Provide the user the opportunity to specify the streamId
            int streamId = 0;
            try {
                streamId = getStreamId(httpMsg.headers());
            } catch (Http2Exception e) {
                httpMsg.release();
                promise.setFailure(e);
                return;
            }

            // Convert and write the headers.
            Http2Headers http2Headers = HttpUtil.toHttp2Headers(httpMsg);

            if (hasData) {
                ChannelPromiseAggregator promiseAggregator = new ChannelPromiseAggregator(promise);
                ChannelPromise headerPromise = ctx.newPromise();
                ChannelPromise dataPromise = ctx.newPromise();
                promiseAggregator.add(headerPromise, dataPromise);
                writeHeaders(ctx, streamId, http2Headers, 0, false, headerPromise);
                writeData(ctx, streamId, httpMsg.content(), 0, true, dataPromise);
            } else {
                writeHeaders(ctx, streamId, http2Headers, 0, true, promise);
            }
        } else {
            ctx.write(msg, promise);
        }
    }
}
