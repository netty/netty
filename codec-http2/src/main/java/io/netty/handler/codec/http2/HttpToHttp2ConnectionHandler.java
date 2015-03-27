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
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http2.Http2CodecUtil.SimpleChannelPromiseAggregator;

/**
 * Translates HTTP/1.x object writes into HTTP/2 frames.
 * <p>
 * See {@link InboundHttp2ToHttpAdapter} to get translation from HTTP/2 frames to HTTP/1.x objects.
 */
public class HttpToHttp2ConnectionHandler extends Http2ConnectionHandler {
    public HttpToHttp2ConnectionHandler(boolean server, Http2FrameListener listener) {
        super(server, listener);
    }

    public HttpToHttp2ConnectionHandler(Http2Connection connection, Http2FrameListener listener) {
        super(connection, listener);
    }

    public HttpToHttp2ConnectionHandler(Http2Connection connection, Http2FrameReader frameReader,
            Http2FrameWriter frameWriter, Http2FrameListener listener) {
        super(connection, frameReader, frameWriter, listener);
    }

    public HttpToHttp2ConnectionHandler(Http2ConnectionDecoder decoder,
                                        Http2ConnectionEncoder encoder) {
        super(decoder, encoder);
    }

    /**
     * Get the next stream id either from the {@link HttpHeaders} object or HTTP/2 codec
     *
     * @param httpHeaders The HTTP/1.x headers object to look for the stream id
     * @return The stream id to use with this {@link HttpHeaders} object
     * @throws Exception If the {@code httpHeaders} object specifies an invalid stream id
     */
    private int getStreamId(HttpHeaders httpHeaders) throws Exception {
        return httpHeaders.getInt(HttpUtil.ExtensionHeaderNames.STREAM_ID.text(), connection().local().nextStreamId());
    }

    /**
     * Handles conversion of a {@link FullHttpMessage} to HTTP/2 frames.
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof FullHttpMessage) {
            FullHttpMessage httpMsg = (FullHttpMessage) msg;
            boolean hasData = httpMsg.content().isReadable();
            boolean httpMsgNeedRelease = true;
            SimpleChannelPromiseAggregator promiseAggregator = null;
            try {
                // Provide the user the opportunity to specify the streamId
                int streamId = getStreamId(httpMsg.headers());

                // Convert and write the headers.
                Http2Headers http2Headers = HttpUtil.toHttp2Headers(httpMsg);
                Http2ConnectionEncoder encoder = encoder();

                if (hasData) {
                    promiseAggregator = new SimpleChannelPromiseAggregator(promise, ctx.channel(), ctx.executor());
                    encoder.writeHeaders(ctx, streamId, http2Headers, 0, false, promiseAggregator.newPromise());
                    httpMsgNeedRelease = false;
                    encoder.writeData(ctx, streamId, httpMsg.content(), 0, true, promiseAggregator.newPromise());
                    promiseAggregator.doneAllocatingPromises();
                } else {
                    encoder.writeHeaders(ctx, streamId, http2Headers, 0, true, promise);
                }
            } catch (Throwable t) {
                if (promiseAggregator == null) {
                    promise.tryFailure(t);
                } else {
                    promiseAggregator.setFailure(t);
                }
            } finally {
                if (httpMsgNeedRelease) {
                    httpMsg.release();
                }
            }
        } else {
            ctx.write(msg, promise);
        }
    }
}
