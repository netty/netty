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
package io.netty.example.http.rest;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.rest.RestArgument;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;

import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;

/**
 * Example of RestClient handler
 */
public class RestClientSimpleResponseHandler extends SimpleChannelInboundHandler<HttpObject> {
    public static final AttributeKey<RestArgument> RESTARGUMENT = AttributeKey.valueOf("RestClient.Argument");
    private ByteBuf cumulativeBody;

    /**
     * Example of simple action from response, validating the result in the Promise,
     * the Promise containing the response body
     */
    protected void actionFromResponse(ChannelHandlerContext ctx, RestArgument restArgument) {
        if (restArgument.status().equals(HttpResponseStatus.OK)) {
            restArgument.getPromise().setSuccess(restArgument.body());
        } else {
            restArgument.getPromise().cancel(false);
            if (ctx.channel().isActive()) {
                ctx.close();
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        cause.printStackTrace();
        cumulativeBody = null;
        RestArgument restArgument = ctx.channel().attr(RESTARGUMENT).get();
        if (cause instanceof ClosedChannelException) {
            restArgument.setResponseBody(cause.getMessage());
            restArgument.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
            actionFromResponse(ctx, restArgument);
            return;
        } else if (cause instanceof ConnectException) {
            restArgument.setResponseBody(cause.getMessage());
            restArgument.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
            actionFromResponse(ctx, restArgument);
            if (ctx.channel().isActive()) {
                ctx.close();
            }
            return;
        }
        restArgument.setResponseBody(cause.getMessage());
        restArgument.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
        actionFromResponse(ctx, restArgument);
        ctx.close();
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        RestArgument restArgument = ctx.channel().attr(RESTARGUMENT).get();
        if (msg instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) msg;
            restArgument.setStatus(response.status());
        }
        if (msg instanceof FullHttpResponse) {
            FullHttpResponse resp = (FullHttpResponse) msg;
            ByteBuf content = resp.content();
            restArgument.setResponseBody("No Data");
            if (content != null && content.isReadable()) {
                restArgument.setResponseBody(content.toString(CharsetUtil.UTF_8));
            }
            actionFromResponse(ctx, restArgument);
            return;
        }
        if (msg instanceof HttpContent) {
            HttpContent chunk = (HttpContent) msg;
            ByteBuf content = chunk.content();
            if (chunk instanceof LastHttpContent) {
                if (content == null || ! content.isReadable()) {
                    if (cumulativeBody != null) {
                        restArgument.setResponseBody(cumulativeBody.toString(CharsetUtil.UTF_8));
                    } else {
                        restArgument.setResponseBody("");
                    }
                } else {
                    if (cumulativeBody == null) {
                        restArgument.setResponseBody(content.toString(CharsetUtil.UTF_8));
                    } else {
                        cumulativeBody = Unpooled.wrappedBuffer(cumulativeBody, content.retain());
                        restArgument.setResponseBody(cumulativeBody.toString(CharsetUtil.UTF_8));
                    }
                }
                cumulativeBody = null;
                actionFromResponse(ctx, restArgument);
            } else {
                if (content != null && content.isReadable()) {
                    if (cumulativeBody != null) {
                        cumulativeBody = Unpooled.wrappedBuffer(cumulativeBody, content.retain());
                    } else {
                        cumulativeBody = content.retain();
                    }
                }
            }
        }
    }
}
