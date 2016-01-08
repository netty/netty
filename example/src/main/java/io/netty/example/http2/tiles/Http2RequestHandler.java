/*
 * Copyright 2015 The Netty Project
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

package io.netty.example.http2.tiles;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.example.http2.Http2ExampleUtil.firstValue;
import static io.netty.example.http2.Http2ExampleUtil.toInt;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpUtil.setContentLength;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.lang.Integer.parseInt;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapter;

import java.util.concurrent.TimeUnit;

/**
 * Handles all the requests for data. It receives a {@link FullHttpRequest},
 * which has been converted by a {@link InboundHttp2ToHttpAdapter} before it
 * arrived here. For further details, check {@link Http2OrHttpHandler} where the
 * pipeline is setup.
 */
public class Http2RequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final String LATENCY_FIELD_NAME = "latency";
    private static final int MIN_LATENCY = 0;
    private static final int MAX_LATENCY = 1000;
    private static final String IMAGE_COORDINATE_Y = "y";
    private static final String IMAGE_COORDINATE_X = "x";

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        QueryStringDecoder queryString = new QueryStringDecoder(request.uri());
        String streamId = streamId(request);
        int latency = toInt(firstValue(queryString, LATENCY_FIELD_NAME), 0);
        if (latency < MIN_LATENCY || latency > MAX_LATENCY) {
            sendBadRequest(ctx, streamId);
            return;
        }
        String x = firstValue(queryString, IMAGE_COORDINATE_X);
        String y = firstValue(queryString, IMAGE_COORDINATE_Y);
        if (x == null || y == null) {
            handlePage(ctx, streamId, latency, request);
        } else {
            handleImage(x, y, ctx, streamId, latency, request);
        }
    }

    private void sendBadRequest(ChannelHandlerContext ctx, String streamId) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST, EMPTY_BUFFER);
        streamId(response, streamId);
        ctx.writeAndFlush(response);
    }

    private void handleImage(String x, String y, ChannelHandlerContext ctx, String streamId, int latency,
            FullHttpRequest request) {
        ByteBuf image = ImageCache.INSTANCE.image(parseInt(x), parseInt(y));
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, image.duplicate());
        response.headers().set(CONTENT_TYPE, "image/jpeg");
        sendResponse(ctx, streamId, latency, response, request);
    }

    private void handlePage(ChannelHandlerContext ctx, String streamId, int latency, FullHttpRequest request) {
        byte[] body = Html.body(latency);
        ByteBuf content = ctx.alloc().buffer(Html.HEADER.length + body.length + Html.FOOTER.length);
        content.writeBytes(Html.HEADER);
        content.writeBytes(body);
        content.writeBytes(Html.FOOTER);
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, content);
        response.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");
        sendResponse(ctx, streamId, latency, response, request);
    }

    protected void sendResponse(final ChannelHandlerContext ctx, String streamId, int latency,
            final FullHttpResponse response, final FullHttpRequest request) {
        setContentLength(response, response.content().readableBytes());
        streamId(response, streamId);
        ctx.executor().schedule(new Runnable() {
            @Override
            public void run() {
                ctx.writeAndFlush(response);
            }
        }, latency, TimeUnit.MILLISECONDS);
    }

    private String streamId(FullHttpRequest request) {
        return request.headers().get(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
    }

    private void streamId(FullHttpResponse response, String streamId) {
        response.headers().set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), streamId);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
