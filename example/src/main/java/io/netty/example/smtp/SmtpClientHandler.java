/*
 * Copyright 2020 The Netty Project
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
package io.netty.example.smtp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.smtp.DefaultLastSmtpContent;
import io.netty.handler.codec.smtp.SmtpRequest;
import io.netty.handler.codec.smtp.SmtpResponse;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FutureCompletionStage;
import io.netty.util.concurrent.Promise;

import java.util.concurrent.atomic.AtomicReference;

/**
 * An example smtp client handler
 */
public class SmtpClientHandler extends SimpleChannelInboundHandler<SmtpResponse> {

    private ChannelHandlerContext ctx;
    private final AtomicReference<Promise<SmtpResponse>> responseFuture = new AtomicReference<>();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, SmtpResponse response) throws Exception {
        Promise<SmtpResponse> promise = responseFuture.get();
        if (promise == null) {
            throw new RuntimeException("Unexpected response received: " + response);
        } else {
            if (response.code() >= 500) {
                throw new RuntimeException("receive an error: " + response);
            }
            printResponse(response);
            promise.setSuccess(response);
            responseFuture.set(null);
        }
    }

    private static void printResponse(SmtpResponse response) {
        System.out.println(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        responseFuture.get().setFailure(cause);
        ctx.close();
    }

    public FutureCompletionStage<SmtpResponse> send(SmtpRequest request) {
        FutureCompletionStage<SmtpResponse> future = createResponseFuture(ctx.executor());
        ctx.writeAndFlush(request);
        return future;
    }

    public FutureCompletionStage<SmtpResponse> sendMailContent(ByteBuf content) {
        FutureCompletionStage<SmtpResponse> future = createResponseFuture(ctx.executor());
        ctx.writeAndFlush(new DefaultLastSmtpContent(content));
        return future;
    }

    public FutureCompletionStage<SmtpResponse> createResponseFuture(EventExecutor executor) {
        Promise<SmtpResponse> promise = executor.newPromise();
        while (!responseFuture.compareAndSet(null, promise)) {
            Promise<SmtpResponse> previousFuture = responseFuture.get();
            if (previousFuture != null) {
                throw new RuntimeException("Still waiting for the past response");
            }
        }
        return promise.toFuture().asStage();
    }
}
