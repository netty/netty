/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl.ocsp;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

final class OcspHttpHandler extends ChannelDuplexHandler {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(OcspHttpHandler.class);
    private final Promise<OCSPResp> responseFuture;
    private final long timeoutMillis;
    private Future<?> timeoutFuture;
    static final String OCSP_REQUEST_TYPE = "application/ocsp-request";
    static final String OCSP_RESPONSE_TYPE = "application/ocsp-response";

    /**
     * Create new {@link OcspHttpHandler} instance
     *
     * @param responsePromise   {@link Promise} of {@link OCSPResp}
     * @param timeoutMillis     the timeout in milliseconds how long a response can take to before we fail the promise.
     */
    OcspHttpHandler(Promise<OCSPResp> responsePromise, long timeoutMillis) {
        this.responseFuture = checkNotNull(responsePromise, "ResponsePromise");
        this.timeoutMillis = ObjectUtil.checkPositive(timeoutMillis, "timeoutMillis");
        this.responseFuture.addListener(f -> {
            if (timeoutFuture != null) {
                timeoutFuture.cancel(true);
            }
        });
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        FullHttpResponse response = (FullHttpResponse) msg;
        try {
            // If DEBUG is enabled then log the response
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Received OCSP HTTP Response: {}", response);
            }

            // Response headers must contain 'Content-Type'
            String contentType = response.headers().get(HttpHeaderNames.CONTENT_TYPE);
            if (contentType == null) {
                throw new OCSPException("HTTP Response does not contain 'CONTENT-TYPE' header");
            }

            // Response headers must contain 'application/ocsp-response'
            if (!contentType.equalsIgnoreCase(OCSP_RESPONSE_TYPE)) {
                throw new OCSPException("Response Content-Type was: " + contentType +
                        "; Expected: " + OCSP_RESPONSE_TYPE);
            }

            // Status must be OK for successful lookup
            if (response.status() != OK) {
                throw new IllegalArgumentException("HTTP Response Code was: " + response.status().code() +
                        "; Expected: 200");
            }

            responseFuture.trySuccess(new OCSPResp(ByteBufUtil.getBytes(response.content())));
        } finally {
            response.release();
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        responseFuture.tryFailure(cause);
        ctx.close();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        super.write(ctx, msg, promise);
        timeoutFuture = ctx.executor().schedule(() -> {
            if (!responseFuture.isDone()) {
                responseFuture.tryFailure(new OCSPException("OCSP response was not received within "
                        + timeoutMillis + "ms"));
                ctx.close();
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (!responseFuture.isDone()) {
            responseFuture.tryFailure(new ClosedChannelException());
        }
        super.channelInactive(ctx);
    }
}
