/*
 * Copyright 2017 The Netty Project
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
package io.netty.handler.ssl.ocsp;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.ReferenceCountedOpenSslContext;
import io.netty.handler.ssl.ReferenceCountedOpenSslEngine;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.UnstableApi;

import javax.net.ssl.SSLHandshakeException;

/**
 * A handler for SSL clients to handle and act upon stapled OCSP responses.
 *
 * @see ReferenceCountedOpenSslContext#enableOcsp()
 * @see ReferenceCountedOpenSslEngine#getOcspResponse()
 */
@UnstableApi
public abstract class OcspClientHandler extends ChannelInboundHandlerAdapter {

    private static final SSLHandshakeException OCSP_VERIFICATION_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new SSLHandshakeException("Bad OCSP response"), OcspClientHandler.class, "verify(...)");

    private final ReferenceCountedOpenSslEngine engine;

    protected OcspClientHandler(ReferenceCountedOpenSslEngine engine) {
        this.engine = ObjectUtil.checkNotNull(engine, "engine");
    }

    /**
     * @see ReferenceCountedOpenSslEngine#getOcspResponse()
     */
    protected abstract boolean verify(ChannelHandlerContext ctx, ReferenceCountedOpenSslEngine engine) throws Exception;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent) {
            ctx.pipeline().remove(this);

            SslHandshakeCompletionEvent event = (SslHandshakeCompletionEvent) evt;
            if (event.isSuccess() && !verify(ctx, engine)) {
                throw OCSP_VERIFICATION_EXCEPTION;
            }
        }

        ctx.fireUserEventTriggered(evt);
    }
}
