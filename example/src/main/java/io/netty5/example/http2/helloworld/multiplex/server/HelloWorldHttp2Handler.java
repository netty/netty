/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty5.example.http2.helloworld.multiplex.server;

import io.netty5.buffer.Buffer;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty5.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty5.handler.codec.http2.Http2DataFrame;
import io.netty5.handler.codec.http2.Http2HeadersFrame;
import io.netty5.handler.codec.http2.headers.Http2Headers;

import java.nio.charset.StandardCharsets;

import static io.netty5.handler.codec.http.HttpResponseStatus.OK;

/**
 * A simple handler that responds with the message "Hello World!".
 */
public class HelloWorldHttp2Handler implements ChannelHandler {

    static final byte[] RESPONSE_BYTES = "Hello World".getBytes(StandardCharsets.UTF_8);

    @Override
    public boolean isSharable() {
        return true;
    }

    @Override
    public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireChannelExceptionCaught(cause);
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Http2HeadersFrame) {
            onHeadersRead(ctx, (Http2HeadersFrame) msg);
        } else if (msg instanceof Http2DataFrame) {
            onDataRead(ctx, (Http2DataFrame) msg);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    /**
     * If receive a frame with end-of-stream set, send a pre-canned response.
     */
    private static void onDataRead(ChannelHandlerContext ctx, Http2DataFrame data) throws Exception {
        if (data.isEndStream()) {
            sendResponse(ctx, data.content());
        } else {
            // We do not send back the response to the remote-peer, so we need to release it.
            data.close();
        }
    }

    /**
     * If receive a frame with end-of-stream set, send a pre-canned response.
     */
    private static void onHeadersRead(ChannelHandlerContext ctx, Http2HeadersFrame headers)
            throws Exception {
        if (headers.isEndStream()) {
            Buffer content = ctx.bufferAllocator().copyOf(RESPONSE_BYTES);
            content.writeCharSequence(" - via HTTP/2", StandardCharsets.US_ASCII);
            sendResponse(ctx, content);
        }
    }

    /**
     * Sends a "Hello World" DATA frame to the client.
     */
    private static void sendResponse(ChannelHandlerContext ctx, Buffer payload) {
        // Send a frame for the response status
        Http2Headers headers = Http2Headers.newHeaders().status(OK.codeAsText());
        ctx.write(new DefaultHttp2HeadersFrame(headers));
        ctx.write(new DefaultHttp2DataFrame(payload.send(), true));
    }
}
