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

package io.netty.example.http2.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandler;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.CharsetUtil;

/**
 * A simple handler that responds with the message "Hello World!".
 */
public class HelloWorldHttp2Handler extends AbstractHttp2ConnectionHandler {

    static final byte[] RESPONSE_BYTES = "Hello World".getBytes(CharsetUtil.UTF_8);

    public HelloWorldHttp2Handler() {
        super(true);
    }

    @Override
    public void onDataRead(int streamId, ByteBuf data, int padding, boolean endOfStream,
            boolean endOfSegment, boolean compressed) throws Http2Exception {
        if (endOfStream) {
            sendResponse(ctx(), streamId);
        }
    }

    @Override
    public void onHeadersRead(int streamId,
            io.netty.handler.codec.http2.Http2Headers headers, int padding,
            boolean endStream, boolean endSegment) throws Http2Exception {
        if (endStream) {
            sendResponse(ctx(), streamId);
        }
    }

    @Override
    public void onHeadersRead(int streamId,
            io.netty.handler.codec.http2.Http2Headers headers, int streamDependency,
            short weight, boolean exclusive, int padding, boolean endStream, boolean endSegment)
            throws Http2Exception {
        if (endStream) {
            sendResponse(ctx(), streamId);
        }
    }

    @Override
    public void onPriorityRead(int streamId, int streamDependency, short weight, boolean exclusive)
            throws Http2Exception {
    }

    @Override
    public void onRstStreamRead(int streamId, long errorCode) throws Http2Exception {
    }

    @Override
    public void onSettingsAckRead() throws Http2Exception {
    }

    @Override
    public void onSettingsRead(Http2Settings settings) throws Http2Exception {
    }

    @Override
    public void onPingRead(ByteBuf data) throws Http2Exception {
    }

    @Override
    public void onPingAckRead(ByteBuf data) throws Http2Exception {
    }

    @Override
    public void onPushPromiseRead(int streamId, int promisedStreamId,
            io.netty.handler.codec.http2.Http2Headers headers, int padding)
            throws Http2Exception {
    }

    @Override
    public void onGoAwayRead(int lastStreamId, long errorCode, ByteBuf debugData)
            throws Http2Exception {
    }

    @Override
    public void onWindowUpdateRead(int streamId, int windowSizeIncrement) throws Http2Exception {
    }

    @Override
    public void onAltSvcRead(int streamId, long maxAge, int port, ByteBuf protocolId, String host,
            String origin) throws Http2Exception {
    }

    @Override
    public void onBlockedRead(int streamId) throws Http2Exception {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        super.exceptionCaught(ctx, cause);
    }

    private void sendResponse(ChannelHandlerContext ctx, int streamId) throws Http2Exception {
        // Send a frame for the response status
        Http2Headers headers = DefaultHttp2Headers.newBuilder().status("200").build();
        writeHeaders(ctx(), ctx().newPromise(), streamId, headers, 0, false, false);

        // Send a data frame with the response message.
        ByteBuf content = ctx.alloc().buffer();
        content.writeBytes(RESPONSE_BYTES);
        writeData(ctx(), ctx().newPromise(), streamId, content, 0, true, true, false);
    }
}
